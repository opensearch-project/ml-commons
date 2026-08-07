/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.transport.TransportChannel;

import com.google.common.collect.ImmutableList;

public class BatchInferenceExecutorTests {

    private BatchInferenceExecutor executor;

    @Before
    public void setUp() {
        executor = new BatchInferenceExecutor(new BatchableInputRegistry(), new BatchSplitter());
    }

    /** Fake deployed model that answers each sub-batch with the given behaviour. */
    private interface SubBatchCall {
        void answer(MLInput subInput, ActionListener<MLTaskResponse> listener);
    }

    private static Predictable model(SubBatchCall call) {
        return new Predictable() {
            @Override
            public MLOutput predict(MLInput mlInput, MLModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
                call.answer(mlInput, listener);
            }

            @Override
            public boolean isModelReady() {
                return true;
            }

            @Override
            public void close() {}
        };
    }

    private MLInput textInput(String... docs) {
        TextDocsInputDataSet dataSet = TextDocsInputDataSet.builder().docs(ImmutableList.copyOf(docs)).build();
        return MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(dataSet).build();
    }

    // One model call returns a single ModelTensors group with one tensor per doc (remote-embedding shape).
    private static MLTaskResponse responseFor(MLInput subInput) {
        List<String> docs = ((TextDocsInputDataSet) subInput.getInputDataset()).getDocs();
        List<ModelTensor> tensors = new ArrayList<>();
        for (String doc : docs) {
            tensors.add(ModelTensor.builder().name(doc).build());
        }
        ModelTensorOutput output = ModelTensorOutput
            .builder()
            .mlModelOutputs(ImmutableList.of(ModelTensors.builder().mlModelTensors(tensors).build()))
            .build();
        return new MLTaskResponse(output);
    }

    private static List<String> docsOf(MLInput input) {
        return ((TextDocsInputDataSet) input.getInputDataset()).getDocs();
    }

    private List<String> resultNames(MLTaskResponse response) {
        List<String> names = new ArrayList<>();
        for (ModelTensors group : ((ModelTensorOutput) response.getOutput()).getMlModelOutputs()) {
            for (ModelTensor tensor : group.getMlModelTensors()) {
                names.add(tensor.getName());
            }
        }
        return names;
    }

    @Test
    public void passesThroughWhenConfigNull() {
        MLInput input = textInput("a", "b");
        AtomicReference<MLInput> seen = new AtomicReference<>();
        AtomicReference<MLTaskResponse> result = new AtomicReference<>();
        Predictable predictor = model((subInput, listener) -> {
            seen.set(subInput);
            listener.onResponse(responseFor(subInput));
        });

        executor.execute(input, null, predictor, null, ActionListener.wrap(result::set, e -> { throw new AssertionError(e); }));

        assertSame(input, seen.get()); // no config -> original input, no rebuild
        assertEquals(ImmutableList.of("a", "b"), resultNames(result.get()));
    }

    @Test
    public void doesNotResplitWhenAlreadyUnderLimits() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(10).build();
        MLInput input = textInput("a", "b");
        AtomicInteger invocations = new AtomicInteger(0);
        AtomicReference<MLTaskResponse> result = new AtomicReference<>();
        Predictable predictor = model((subInput, listener) -> {
            invocations.incrementAndGet();
            listener.onResponse(responseFor(subInput));
        });

        executor.execute(input, config, predictor, null, ActionListener.wrap(result::set, e -> { throw new AssertionError(e); }));

        assertEquals(1, invocations.get()); // single call, split computed exactly once
        assertEquals(ImmutableList.of("a", "b"), resultNames(result.get()));
    }

    @Test
    public void executeSplitsAndReassemblesInOrder() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(2).build();
        AtomicReference<MLTaskResponse> result = new AtomicReference<>();
        AtomicInteger invocations = new AtomicInteger(0);
        Predictable predictor = model((subInput, listener) -> {
            invocations.incrementAndGet();
            listener.onResponse(responseFor(subInput));
        });

        executor.execute(textInput("a", "b", "c", "d", "e"), config, predictor, null, ActionListener.wrap(result::set, e -> {
            throw new AssertionError(e);
        }));

        assertEquals(3, invocations.get()); // 2 + 2 + 1
        assertEquals(ImmutableList.of("a", "b", "c", "d", "e"), resultNames(result.get()));
    }

    @Test
    public void executePassesThroughWhenSingleBatch() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(10).build();
        MLInput input = textInput("a", "b");
        AtomicReference<MLInput> seen = new AtomicReference<>();
        AtomicReference<MLTaskResponse> result = new AtomicReference<>();
        Predictable predictor = model((subInput, listener) -> {
            seen.set(subInput);
            listener.onResponse(responseFor(subInput));
        });

        executor.execute(input, config, predictor, null, ActionListener.wrap(result::set, e -> { throw new AssertionError(e); }));

        assertSame(input, seen.get()); // original input, no rebuild
        assertEquals(ImmutableList.of("a", "b"), resultNames(result.get()));
    }

    @Test
    public void badInputFailsWholeRequest() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        Predictable predictor = model((subInput, listener) -> {
            if (docsOf(subInput).contains("bad")) {
                listener.onFailure(new OpenSearchStatusException("bad input", RestStatus.BAD_REQUEST));
            } else {
                listener.onResponse(responseFor(subInput));
            }
        });

        executor.execute(textInput("ok", "bad", "ok2"), config, predictor, null, ActionListener.wrap(r -> {
            throw new AssertionError("should have failed");
        }, failure::set));

        assertTrue(failure.get() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) failure.get()).status());
    }

    @Test
    public void everySubBatchIsInvokedExactlyOnceWithoutRetrying() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicReference<Exception> failure = new AtomicReference<>();
        Predictable predictor = model((subInput, listener) -> {
            attempts.incrementAndGet();
            listener.onFailure(new OpenSearchStatusException("throttled", RestStatus.TOO_MANY_REQUESTS));
        });

        executor
            .execute(
                textInput("a", "b"),
                config,
                predictor,
                null,
                ActionListener.wrap(r -> { throw new AssertionError("should have failed"); }, failure::set)
            );

        assertEquals("2 sub-batches, one attempt each", 2, attempts.get());
        assertEquals(2, failure.get().getSuppressed().length);
    }

    @Test
    public void asyncPredictThrowingSynchronouslyIsTreatedAsThatSubBatchFailing() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger(0);
        Predictable predictor = model((subInput, listener) -> {
            if (docsOf(subInput).contains("boom")) {
                throw new IllegalStateException("dispatch failed");
            }
            listener.onResponse(responseFor(subInput));
        });

        executor.execute(textInput("ok", "boom"), config, predictor, null, ActionListener.wrap(r -> {
            throw new AssertionError("should have failed");
        }, e -> {
            completions.incrementAndGet();
            failure.set(e);
        }));

        assertEquals("listener must be completed exactly once", 1, completions.get());
        assertTrue(failure.get() instanceof IllegalStateException);
        assertEquals("dispatch failed", failure.get().getMessage());
    }

    @Test
    public void everyAsyncPredictThrowingSynchronouslyStillCompletesOnceWithAllErrors() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger(0);
        Predictable predictor = model((subInput, listener) -> { throw new IllegalStateException("dispatch failed"); });

        executor
            .execute(
                textInput("a", "b"),
                config,
                predictor,
                null,
                ActionListener.wrap(r -> { throw new AssertionError("should have failed"); }, e -> {
                    completions.incrementAndGet();
                    failure.set(e);
                })
            );

        assertEquals("listener must be completed exactly once", 1, completions.get());
        assertEquals(2, failure.get().getSuppressed().length);
    }

    @Test
    public void outputMergeFailureIsReportedOnceAndDoesNotHang() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger(0);
        Predictable predictor = model((subInput, listener) -> {
            int statusCode = docsOf(subInput).contains("a") ? 200 : 206;
            ModelTensors group = ModelTensors.builder().mlModelTensors(new ArrayList<>()).build();
            group.setStatusCode(statusCode);
            listener.onResponse(new MLTaskResponse(ModelTensorOutput.builder().mlModelOutputs(ImmutableList.of(group)).build()));
        });

        executor.execute(textInput("a", "b"), config, predictor, null, ActionListener.wrap(r -> {
            throw new AssertionError("should have failed on merge");
        }, e -> {
            completions.incrementAndGet();
            failure.set(e);
        }));

        assertEquals("listener must be completed exactly once", 1, completions.get());
        assertTrue(failure.get() instanceof IllegalStateException);
    }

    @Test
    public void nonRetryableErrorFailsWithoutRetry() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicReference<Exception> failure = new AtomicReference<>();
        Predictable predictor = model((subInput, listener) -> {
            attempts.incrementAndGet();
            listener.onFailure(new RuntimeException("boom"));
        });

        executor
            .execute(
                textInput("a", "b"),
                config,
                predictor,
                null,
                ActionListener.wrap(r -> { throw new AssertionError("should have failed"); }, failure::set)
            );

        // 2 sub-batches, each attempted exactly once
        assertEquals(2, attempts.get());
    }

    @Test
    public void failsWhenInputTypeNotBatchable() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        MLInput input = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_SIMILARITY)
            .inputDataset(new org.opensearch.ml.common.dataset.TextSimilarityInputDataSet("q", ImmutableList.of("d1", "d2")))
            .build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        Predictable predictor = model((subInput, listener) -> { throw new AssertionError("model should not be called"); });

        executor
            .execute(
                input,
                config,
                predictor,
                null,
                ActionListener.wrap(r -> { throw new AssertionError("should have failed"); }, failure::set)
            );

        assertTrue(failure.get() instanceof IllegalArgumentException);
    }

    @Test
    public void multipleSubBatchFailuresAreAllReported() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger(0);
        Predictable predictor = model((subInput, listener) -> {
            List<String> docs = docsOf(subInput);
            if (docs.contains("bad1")) {
                listener.onFailure(new OpenSearchStatusException("first failure", RestStatus.BAD_REQUEST));
            } else if (docs.contains("bad2")) {
                listener.onFailure(new OpenSearchStatusException("second failure", RestStatus.NOT_FOUND));
            } else {
                listener.onResponse(responseFor(subInput));
            }
        });

        executor.execute(textInput("bad1", "ok", "bad2"), config, predictor, null, ActionListener.wrap(r -> {
            throw new AssertionError("should have failed");
        }, e -> {
            completions.incrementAndGet();
            failure.set(e);
        }));

        assertEquals("listener must be completed exactly once", 1, completions.get());
        Exception error = failure.get();
        assertTrue(error.getMessage().contains("first failure"));
        assertTrue(error.getMessage().contains("second failure"));
        assertEquals(2, error.getSuppressed().length);
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) error).status());
    }

    @Test
    public void combinedFailureEscalatesWhenAnySubBatchHitAServerError() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        Predictable predictor = model((subInput, listener) -> {
            if (docsOf(subInput).contains("forbidden")) {
                listener.onFailure(new OpenSearchStatusException("forbidden", RestStatus.FORBIDDEN));
            } else {
                listener.onFailure(new OpenSearchStatusException("upstream down", RestStatus.BAD_GATEWAY));
            }
        });

        executor.execute(textInput("forbidden", "down"), config, predictor, null, ActionListener.wrap(r -> {
            throw new AssertionError("should have failed");
        }, failure::set));

        assertEquals(RestStatus.BAD_GATEWAY, ((OpenSearchStatusException) failure.get()).status());
    }

    @Test
    public void combinedFailureStatusDoesNotDependOnSettlementOrder() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        List<ActionListener<MLTaskResponse>> deferred = new ArrayList<>();
        Predictable predictor = model((subInput, listener) -> {
            if (docsOf(subInput).contains("first")) {
                deferred.add(listener);
            } else {
                listener.onFailure(new OpenSearchStatusException("too many requests", RestStatus.TOO_MANY_REQUESTS));
            }
        });

        executor.execute(textInput("first", "second"), config, predictor, null, ActionListener.wrap(r -> {
            throw new AssertionError("should have failed");
        }, failure::set));

        deferred.get(0).onFailure(new OpenSearchStatusException("forbidden", RestStatus.FORBIDDEN));

        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) failure.get()).status());
    }

    @Test
    public void combinedFailureKeepsTheStatusEverySubBatchShares() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        Predictable predictor = model(
            (subInput, listener) -> listener.onFailure(new OpenSearchStatusException("forbidden", RestStatus.FORBIDDEN))
        );

        executor
            .execute(
                textInput("a", "b"),
                config,
                predictor,
                null,
                ActionListener.wrap(r -> { throw new AssertionError("should have failed"); }, failure::set)
            );

        Exception error = failure.get();
        assertEquals(2, error.getSuppressed().length);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) error).status());
    }

    @Test
    public void singleSubBatchFailureIsReportedAsIs() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        Predictable predictor = model((subInput, listener) -> {
            if (docsOf(subInput).contains("bad")) {
                listener.onFailure(new OpenSearchStatusException("bad input", RestStatus.BAD_REQUEST));
            } else {
                listener.onResponse(responseFor(subInput));
            }
        });

        executor.execute(textInput("ok", "bad"), config, predictor, null, ActionListener.wrap(r -> {
            throw new AssertionError("should have failed");
        }, failure::set));

        assertTrue(failure.get() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) failure.get()).status());
        assertEquals("a lone failure must not be wrapped", 0, failure.get().getSuppressed().length);
    }

    @Test
    public void successSettlingBeforeFailureDoesNotCombinePartialResults() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        Predictable predictor = model((subInput, listener) -> {
            if (docsOf(subInput).contains("bad")) {
                listener.onFailure(new OpenSearchStatusException("bad input", RestStatus.BAD_REQUEST));
            } else {
                listener.onResponse(responseFor(subInput));
            }
        });

        // "ok" settles first (dispatched first, in list order); "bad" settles last.
        executor.execute(textInput("ok", "bad"), config, predictor, null, ActionListener.wrap(r -> {
            throw new AssertionError("should have failed, not combined a partial result");
        }, failure::set));

        assertTrue(failure.get() instanceof OpenSearchStatusException);
    }

    @Test
    public void successSettlingAfterFailureStillCompletesExactlyOnce() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger(0);
        Predictable predictor = model((subInput, listener) -> {
            if (docsOf(subInput).contains("bad")) {
                listener.onFailure(new OpenSearchStatusException("bad input", RestStatus.BAD_REQUEST));
            } else {
                listener.onResponse(responseFor(subInput));
            }
        });

        // "bad" settles first (dispatched first, in list order); "ok" succeeds afterwards.
        executor.execute(textInput("bad", "ok"), config, predictor, null, ActionListener.wrap(r -> {
            throw new AssertionError("must not respond when a sub-batch failed");
        }, e -> {
            completions.incrementAndGet();
            failure.set(e);
        }));

        assertEquals("listener must be completed exactly once", 1, completions.get());
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) failure.get()).status());
    }
}
