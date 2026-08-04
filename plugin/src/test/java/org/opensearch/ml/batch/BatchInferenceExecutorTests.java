/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

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
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.collect.ImmutableList;

public class BatchInferenceExecutorTests {

    private BatchInferenceExecutor executor;
    private ThreadPool threadPool;

    @Before
    public void setUp() {
        threadPool = mock(ThreadPool.class);
        // Run scheduled retries immediately so backoff does not slow the test.
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(threadPool).schedule(any(Runnable.class), any(), any(String.class));
        executor = new BatchInferenceExecutor(new BatchableInputRegistry(), new BatchSplitter(), threadPool, "test-pool");
    }

    private MLInput textInput(String... docs) {
        TextDocsInputDataSet dataSet = TextDocsInputDataSet.builder().docs(ImmutableList.copyOf(docs)).build();
        return MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(dataSet).build();
    }

    /** Invoker that returns one tensor per doc, named by doc content, to verify ordering. */
    private SingleBatchInvoker echoInvoker() {
        return (subInput, listener) -> listener.onResponse(outputFor(subInput));
    }

    // One model call returns a single ModelTensors group with one tensor per doc (remote-embedding shape).
    private MLOutput outputFor(MLInput subInput) {
        List<String> docs = ((TextDocsInputDataSet) subInput.getInputDataset()).getDocs();
        List<ModelTensor> tensors = new ArrayList<>();
        for (String doc : docs) {
            tensors.add(ModelTensor.builder().name(doc).build());
        }
        return ModelTensorOutput.builder().mlModelOutputs(ImmutableList.of(ModelTensors.builder().mlModelTensors(tensors).build())).build();
    }

    private List<String> resultNames(MLOutput output) {
        List<String> names = new ArrayList<>();
        for (ModelTensors group : ((ModelTensorOutput) output).getMlModelOutputs()) {
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
        AtomicReference<MLOutput> result = new AtomicReference<>();
        SingleBatchInvoker invoker = (subInput, listener) -> {
            seen.set(subInput);
            listener.onResponse(outputFor(subInput));
        };

        executor.execute(input, null, invoker, ActionListener.wrap(result::set, e -> { throw new AssertionError(e); }));

        assertSame(input, seen.get()); // no config -> original input, no rebuild
        assertEquals(ImmutableList.of("a", "b"), resultNames(result.get()));
    }

    @Test
    public void doesNotResplitWhenAlreadyUnderLimits() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(10).build();
        MLInput input = textInput("a", "b");
        AtomicInteger invocations = new AtomicInteger(0);
        AtomicReference<MLOutput> result = new AtomicReference<>();
        SingleBatchInvoker invoker = (subInput, listener) -> {
            invocations.incrementAndGet();
            listener.onResponse(outputFor(subInput));
        };

        executor.execute(input, config, invoker, ActionListener.wrap(result::set, e -> { throw new AssertionError(e); }));

        assertEquals(1, invocations.get()); // single call, split computed exactly once
        assertEquals(ImmutableList.of("a", "b"), resultNames(result.get()));
    }

    @Test
    public void executeSplitsAndReassemblesInOrder() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(2).build();
        AtomicReference<MLOutput> result = new AtomicReference<>();
        AtomicInteger invocations = new AtomicInteger(0);
        SingleBatchInvoker counting = (subInput, listener) -> {
            invocations.incrementAndGet();
            listener.onResponse(outputFor(subInput));
        };

        executor.execute(textInput("a", "b", "c", "d", "e"), config, counting, ActionListener.wrap(result::set, e -> {
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
        AtomicReference<MLOutput> result = new AtomicReference<>();
        SingleBatchInvoker invoker = (subInput, listener) -> {
            seen.set(subInput);
            listener.onResponse(outputFor(subInput));
        };

        executor.execute(input, config, invoker, ActionListener.wrap(result::set, e -> { throw new AssertionError(e); }));

        assertSame(input, seen.get()); // original input, no rebuild
        assertEquals(ImmutableList.of("a", "b"), resultNames(result.get()));
    }

    @Test
    public void badInputFailsWholeRequest() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SingleBatchInvoker invoker = (subInput, listener) -> {
            List<String> docs = ((TextDocsInputDataSet) subInput.getInputDataset()).getDocs();
            if (docs.contains("bad")) {
                listener.onFailure(new OpenSearchStatusException("bad input", RestStatus.BAD_REQUEST));
            } else {
                listener.onResponse(outputFor(subInput));
            }
        };

        executor.execute(textInput("ok", "bad", "ok2"), config, invoker, ActionListener.wrap(r -> {
            throw new AssertionError("should have failed");
        }, failure::set));

        assertTrue(failure.get() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) failure.get()).status());
    }

    @Test
    public void retriesOnThrottleThenSucceeds() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<MLOutput> result = new AtomicReference<>();
        AtomicInteger attemptsForBad = new AtomicInteger(0);
        SingleBatchInvoker invoker = (subInput, listener) -> {
            List<String> docs = ((TextDocsInputDataSet) subInput.getInputDataset()).getDocs();
            if (docs.contains("flaky") && attemptsForBad.getAndIncrement() < 2) {
                listener.onFailure(new OpenSearchStatusException("throttled", RestStatus.TOO_MANY_REQUESTS));
            } else {
                listener.onResponse(outputFor(subInput));
            }
        };

        executor
            .execute(
                textInput("a", "flaky", "b"),
                config,
                invoker,
                ActionListener.wrap(result::set, e -> { throw new AssertionError(e); })
            );

        assertEquals(3, attemptsForBad.get()); // 2 failures + 1 success
        assertEquals(ImmutableList.of("a", "flaky", "b"), resultNames(result.get()));
    }

    @Test
    public void throttleExhaustsRetriesAndFails() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SingleBatchInvoker invoker = (subInput, listener) -> {
            List<String> docs = ((TextDocsInputDataSet) subInput.getInputDataset()).getDocs();
            if (docs.contains("bad")) {
                listener.onFailure(new OpenSearchStatusException("throttled", RestStatus.TOO_MANY_REQUESTS));
            } else {
                listener.onResponse(outputFor(subInput));
            }
        };

        executor
            .execute(
                textInput("a", "bad"),
                config,
                invoker,
                ActionListener.wrap(r -> { throw new AssertionError("should have failed"); }, failure::set)
            );

        assertTrue(failure.get() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.TOO_MANY_REQUESTS, ((OpenSearchStatusException) failure.get()).status());
    }

    @Test
    public void allSubBatchesThrottleAndFailWithTheOriginalStatus() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger(0);
        SingleBatchInvoker invoker = (subInput, listener) -> listener
            .onFailure(new OpenSearchStatusException("throttled", RestStatus.TOO_MANY_REQUESTS));

        executor
            .execute(
                textInput("a", "b"),
                config,
                invoker,
                ActionListener.wrap(r -> { throw new AssertionError("should have failed"); }, e -> {
                    completions.incrementAndGet();
                    failure.set(e);
                })
            );

        assertEquals("listener must be completed exactly once", 1, completions.get());
        assertTrue(failure.get() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.TOO_MANY_REQUESTS, ((OpenSearchStatusException) failure.get()).status());
        assertEquals(0, failure.get().getSuppressed().length);
    }

    @Test
    public void nonRetryableErrorFailsWithoutRetry() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicReference<Exception> failure = new AtomicReference<>();
        SingleBatchInvoker invoker = (subInput, listener) -> {
            attempts.incrementAndGet();
            listener.onFailure(new RuntimeException("boom"));
        };

        executor
            .execute(
                textInput("a", "b"),
                config,
                invoker,
                ActionListener.wrap(r -> { throw new AssertionError("should have failed"); }, failure::set)
            );

        assertTrue(failure.get() instanceof RuntimeException);
        // 2 sub-batches, each attempted exactly once (no retries for a non-retryable error)
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
        SingleBatchInvoker invoker = (subInput, listener) -> { throw new AssertionError("invoker should not be called"); };

        executor
            .execute(input, config, invoker, ActionListener.wrap(r -> { throw new AssertionError("should have failed"); }, failure::set));

        assertTrue(failure.get() instanceof IllegalArgumentException);
    }

    @Test
    public void firstSubBatchFailureWinsAndLaterFailuresAreDropped() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger(0);
        SingleBatchInvoker invoker = (subInput, listener) -> {
            List<String> docs = ((TextDocsInputDataSet) subInput.getInputDataset()).getDocs();
            if (docs.contains("bad1")) {
                listener.onFailure(new OpenSearchStatusException("first failure", RestStatus.BAD_REQUEST));
            } else if (docs.contains("bad2")) {
                listener.onFailure(new OpenSearchStatusException("second failure", RestStatus.NOT_FOUND));
            } else {
                listener.onResponse(outputFor(subInput));
            }
        };

        executor.execute(textInput("bad1", "ok", "bad2"), config, invoker, ActionListener.wrap(r -> {
            throw new AssertionError("should have failed");
        }, e -> {
            completions.incrementAndGet();
            failure.set(e);
        }));

        assertEquals("listener must be completed exactly once", 1, completions.get());
        assertEquals("first failure", failure.get().getMessage());
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) failure.get()).status());
    }

    @Test
    public void singleSubBatchFailureIsReportedAsIs() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SingleBatchInvoker invoker = (subInput, listener) -> {
            List<String> docs = ((TextDocsInputDataSet) subInput.getInputDataset()).getDocs();
            if (docs.contains("bad")) {
                listener.onFailure(new OpenSearchStatusException("bad input", RestStatus.BAD_REQUEST));
            } else {
                listener.onResponse(outputFor(subInput));
            }
        };

        executor
            .execute(
                textInput("ok", "bad"),
                config,
                invoker,
                ActionListener.wrap(r -> { throw new AssertionError("should have failed"); }, failure::set)
            );

        assertTrue(failure.get() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) failure.get()).status());
    }

    @Test
    public void successSettlingBeforeFailureDoesNotCombinePartialResults() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SingleBatchInvoker invoker = (subInput, listener) -> {
            List<String> docs = ((TextDocsInputDataSet) subInput.getInputDataset()).getDocs();
            if (docs.contains("bad")) {
                listener.onFailure(new OpenSearchStatusException("bad input", RestStatus.BAD_REQUEST));
            } else {
                listener.onResponse(outputFor(subInput));
            }
        };

        // "ok" settles first (dispatched first, in list order); "bad" settles last.
        executor.execute(textInput("ok", "bad"), config, invoker, ActionListener.wrap(r -> {
            throw new AssertionError("should have failed, not combined a partial result");
        }, failure::set));

        assertTrue(failure.get() instanceof OpenSearchStatusException);
    }

    @Test
    public void successSettlingAfterFailureDoesNotCompleteTheRequestAgain() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(1).build();
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger(0);
        SingleBatchInvoker invoker = (subInput, listener) -> {
            List<String> docs = ((TextDocsInputDataSet) subInput.getInputDataset()).getDocs();
            if (docs.contains("bad")) {
                listener.onFailure(new OpenSearchStatusException("bad input", RestStatus.BAD_REQUEST));
            } else {
                listener.onResponse(outputFor(subInput));
            }
        };

        // "bad" settles first (dispatched first, in list order); "ok" succeeds afterwards.
        executor.execute(textInput("bad", "ok"), config, invoker, ActionListener.wrap(r -> {
            throw new AssertionError("must not respond after the request already failed");
        }, e -> {
            completions.incrementAndGet();
            failure.set(e);
        }));

        assertEquals("listener must be completed exactly once", 1, completions.get());
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) failure.get()).status());
    }

    @Test
    public void isRetryableClassification() {
        assertTrue(BatchInferenceExecutor.isRetryable(new OpenSearchStatusException("t", RestStatus.TOO_MANY_REQUESTS)));
        assertTrue(BatchInferenceExecutor.isRetryable(new OpenSearchStatusException("u", RestStatus.SERVICE_UNAVAILABLE)));
        assertFalse(BatchInferenceExecutor.isRetryable(new OpenSearchStatusException("b", RestStatus.BAD_REQUEST)));
        assertFalse(BatchInferenceExecutor.isRetryable(new RuntimeException("x")));
    }
}
