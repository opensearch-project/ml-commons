/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.REMOTE_SERVICE_ERROR;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.AbstractRunnable;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.concurrency.OpenSearchRejectedExecutionException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.input.parameter.textembedding.AsymmetricTextEmbeddingParameters;
import org.opensearch.ml.common.input.parameter.textembedding.AsymmetricTextEmbeddingParameters.EmbeddingContentType;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.model.DynamicBatchingConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.threadpool.Scheduler;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportChannel;

import com.google.common.collect.ImmutableList;

public class DynamicBatchingQueueTests {

    private BatchableInputRegistry registry;
    private BatchSplitter splitter;
    private ThreadPool threadPool;
    private AtomicReference<Runnable> scheduledFlush;
    private final QueueMemoryBudget budget = new QueueMemoryBudget(Long.MAX_VALUE);

    @Before
    public void setUp() {
        registry = new BatchableInputRegistry();
        splitter = new BatchSplitter();
        threadPool = mock(ThreadPool.class);
        scheduledFlush = new AtomicReference<>();
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(invocation -> {
            scheduledFlush.set(invocation.getArgument(0));
            return mock(Scheduler.ScheduledCancellable.class);
        });
    }

    /** A model whose each call echoes one tensor per doc, named after the doc, unless the doc is failDoc. */
    private Predictable model(AtomicInteger calls, String failDoc) {
        return new Predictable() {
            @Override
            public MLOutput predict(MLInput mlInput, MLModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
                if (calls != null) {
                    calls.incrementAndGet();
                }
                List<String> docs = ((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs();
                if (failDoc != null && docs.contains(failDoc)) {
                    listener.onFailure(new RuntimeException("boom on " + failDoc));
                    return;
                }
                List<ModelTensor> tensors = new ArrayList<>();
                for (String doc : docs) {
                    tensors.add(ModelTensor.builder().name(doc).build());
                }
                ModelTensorOutput output = ModelTensorOutput
                    .builder()
                    .mlModelOutputs(ImmutableList.of(ModelTensors.builder().mlModelTensors(tensors).build()))
                    .build();
                listener.onResponse(new MLTaskResponse(output));
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

    private QueueEntry entry(Predictable predictor, ActionListener<MLTaskResponse> listener, String... docs) {
        return queueEntry(textInput(docs), listener, predictor);
    }

    private QueueEntry filteredEntry(Predictable predictor, ModelResultFilter filter, ActionListener<MLTaskResponse> listener, String doc) {
        TextDocsInputDataSet dataSet = TextDocsInputDataSet.builder().docs(ImmutableList.of(doc)).resultFilter(filter).build();
        return queueEntry(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(dataSet).build(), listener, predictor);
    }

    private QueueEntry paramsEntry(Predictable predictor, MLAlgoParams params, ActionListener<MLTaskResponse> listener, String doc) {
        TextDocsInputDataSet dataSet = TextDocsInputDataSet.builder().docs(ImmutableList.of(doc)).build();
        MLInput input = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).parameters(params).inputDataset(dataSet).build();
        return queueEntry(input, listener, predictor);
    }

    // Build a QueueEntry the way the manager does: decompose and key the input once, up front (null for
    // an input type with no batch handler).
    private QueueEntry queueEntry(MLInput input, ActionListener<MLTaskResponse> listener, Predictable predictor) {
        input.setCallerAlgorithm(FunctionName.TEXT_EMBEDDING);
        BatchableInput handler = registry.get(input);
        if (handler == null) {
            return new QueueEntry(input, listener, predictor, null, null, null);
        }
        return new QueueEntry(input, listener, predictor, null, handler.toItems(input), handler.groupKey(input));
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

    private BatchInferenceConfig config(Integer maxItems, Long maxBytes, long flushMs) {
        return BatchInferenceConfig
            .builder()
            .maxItemsPerRequest(maxItems)
            .maxBytesPerRequest(maxBytes)
            .dynamicBatching(DynamicBatchingConfig.builder().enabled(true).flushTimeoutMs(flushMs).build())
            .build();
    }

    @Test
    public void flushesOnCountThresholdAndRoutesEachResultToItsCaller() {
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(3, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);

        List<MLTaskResponse> responses = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            responses.add(null);
        }
        // Three separate single-doc callers; the third pushes item count to the threshold and flushes.
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> responses.set(0, r), e -> {}), "a"));
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> responses.set(1, r), e -> {}), "b"));
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> responses.set(2, r), e -> {}), "c"));

        assertEquals("3 items at limit 3 pack into one model call", 1, calls.get());
        assertEquals(ImmutableList.of("a"), resultNames(responses.get(0)));
        assertEquals(ImmutableList.of("b"), resultNames(responses.get(1)));
        assertEquals(ImmutableList.of("c"), resultNames(responses.get(2)));
    }

    @Test
    public void flushesViaTimerWhenBelowThreshold() {
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);

        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(a::set, e -> {}), "a"));
        queue.enqueue(entry(predictor, ActionListener.wrap(b::set, e -> {}), "b"));

        assertEquals("no flush before the timer fires", 0, calls.get());
        assertNull(a.get());
        scheduledFlush.get().run(); // simulate the flush timer elapsing

        assertEquals(1, calls.get());
        assertEquals(ImmutableList.of("a"), resultNames(a.get()));
        assertEquals(ImmutableList.of("b"), resultNames(b.get()));
    }

    @Test
    public void multiDocEntryKeepsOrderAndOwnershipAcrossCallers() {
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        Predictable predictor = model(null, null);

        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(a::set, e -> {}), "a1", "a2", "a3"));
        queue.enqueue(entry(predictor, ActionListener.wrap(b::set, e -> {}), "b1"));
        scheduledFlush.get().run();

        assertEquals("caller A gets exactly its 3 docs in order", ImmutableList.of("a1", "a2", "a3"), resultNames(a.get()));
        assertEquals("caller B gets exactly its 1 doc", ImmutableList.of("b1"), resultNames(b.get()));
    }

    @Test
    public void oversizeSingleEntryIsSplitButReassembledForItsOneCaller() {
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);

        AtomicReference<MLTaskResponse> result = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(result::set, e -> {}), "d1", "d2", "d3", "d4", "d5"));

        assertEquals("only the full calls are sent on the threshold", 2, calls.get());
        assertNull("the request is not complete while its tail is still queued", result.get());
        assertFalse("the carried tail keeps the queue alive", queue.isIdle());

        scheduledFlush.get().run();

        assertEquals("the timer sends the tail on its own", 3, calls.get());
        assertEquals(ImmutableList.of("d1", "d2", "d3", "d4", "d5"), resultNames(result.get()));
        assertTrue(queue.isIdle());
    }

    @Test
    public void partialTailIsCarriedOverAndFilledByTheNextRequests() {
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(4, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        List<List<String>> sentCalls = new ArrayList<>();
        Predictable echo = model(null, null);
        Predictable predictor = new Predictable() {
            @Override
            public MLOutput predict(MLInput mlInput, MLModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
                sentCalls.add(((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs());
                echo.asyncPredict(mlInput, listener, channel);
            }

            @Override
            public boolean isModelReady() {
                return true;
            }

            @Override
            public void close() {}
        };

        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        AtomicReference<MLTaskResponse> c = new AtomicReference<>();
        AtomicReference<MLTaskResponse> d = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(a::set, e -> {}), "a1", "a2", "a3", "a4", "a5"));
        assertEquals(ImmutableList.of(ImmutableList.of("a1", "a2", "a3", "a4")), sentCalls);

        queue.enqueue(entry(predictor, ActionListener.wrap(b::set, e -> {}), "b1"));
        queue.enqueue(entry(predictor, ActionListener.wrap(c::set, e -> {}), "c1"));
        assertEquals("3 pending items stay below the limit of 4", 1, sentCalls.size());
        assertNull(a.get());

        queue.enqueue(entry(predictor, ActionListener.wrap(d::set, e -> {}), "d1", "d2", "d3"));
        assertEquals("the carried tail leads the next full call", ImmutableList.of("a5", "b1", "c1", "d1"), sentCalls.get(1));
        assertEquals(ImmutableList.of("a1", "a2", "a3", "a4", "a5"), resultNames(a.get()));
        assertEquals(ImmutableList.of("b1"), resultNames(b.get()));
        assertEquals(ImmutableList.of("c1"), resultNames(c.get()));
        assertNull("D still has two docs queued", d.get());

        scheduledFlush.get().run();

        assertEquals(ImmutableList.of("d2", "d3"), sentCalls.get(2));
        assertEquals("10 docs at limit 4 cost 3 calls, not the 4 an immediate tail flush would", 3, sentCalls.size());
        assertEquals(ImmutableList.of("d1", "d2", "d3"), resultNames(d.get()));
        assertTrue(queue.isIdle());
    }

    @Test
    public void thresholdFlushKeepsTheDeadlineOfOtherGroupsPendingRequests() {
        AtomicLong nowNanos = new AtomicLong();
        when(threadPool.preciseRelativeTimeInNanos()).thenAnswer(invocation -> nowNanos.get());
        List<TimeValue> delays = new ArrayList<>();
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(invocation -> {
            scheduledFlush.set(invocation.getArgument(0));
            delays.add(invocation.getArgument(1));
            return mock(Scheduler.ScheduledCancellable.class);
        });
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 100L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);
        ModelResultFilter groupX = new ModelResultFilter(false, true, null, null);
        ModelResultFilter groupY = new ModelResultFilter(true, false, null, null);

        AtomicReference<MLTaskResponse> x = new AtomicReference<>();
        queue.enqueue(filteredEntry(predictor, groupX, ActionListener.wrap(x::set, e -> {}), "x"));
        nowNanos.set(TimeUnit.MILLISECONDS.toNanos(60));
        queue.enqueue(filteredEntry(predictor, groupY, ActionListener.wrap(r -> {}, e -> {}), "y1"));
        queue.enqueue(filteredEntry(predictor, groupY, ActionListener.wrap(r -> {}, e -> {}), "y2"));

        assertEquals("only the full group is sent", 1, calls.get());
        assertNull(x.get());
        assertEquals(ImmutableList.of(TimeValue.timeValueMillis(100), TimeValue.timeValueMillis(40)), delays);

        scheduledFlush.get().run();
        assertEquals(ImmutableList.of("x"), resultNames(x.get()));
    }

    @Test
    public void requestSpanningTwoFlushesCompletesExactlyOnceWhenItsTailFails() {
        List<ActionListener<MLTaskResponse>> inFlight = new ArrayList<>();
        Predictable predictor = new Predictable() {
            @Override
            public MLOutput predict(MLInput mlInput, MLModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
                inFlight.add(listener);
            }

            @Override
            public boolean isModelReady() {
                return true;
            }

            @Override
            public void close() {}
        };
        QueueMemoryBudget trackedBudget = new QueueMemoryBudget(Long.MAX_VALUE);
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            trackedBudget,
            ignored -> {}
        );
        AtomicInteger notifications = new AtomicInteger();
        AtomicReference<Exception> err = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> notifications.incrementAndGet(), e -> {
            notifications.incrementAndGet();
            err.set(e);
        }), "a1", "a2", "a3"));
        assertEquals("a1 and a2 are sent, a3 is carried", 1, inFlight.size());

        ((AbstractRunnable) scheduledFlush.get()).onRejection(new OpenSearchRejectedExecutionException("pool full"));
        assertEquals("A is not complete while its first call is still in flight", 0, notifications.get());
        assertTrue(trackedBudget.getReservedBytes() > 0);

        ModelTensorOutput output = ModelTensorOutput
            .builder()
            .mlModelOutputs(
                ImmutableList
                    .of(
                        ModelTensors
                            .builder()
                            .mlModelTensors(
                                ImmutableList.of(ModelTensor.builder().name("a1").build(), ModelTensor.builder().name("a2").build())
                            )
                            .build()
                    )
            )
            .build();
        inFlight.get(0).onResponse(new MLTaskResponse(output));

        assertEquals(1, notifications.get());
        assertTrue(err.get() instanceof OpenSearchRejectedExecutionException);
        assertEquals(0L, trackedBudget.getReservedBytes());
    }

    @Test
    public void unbatchableRequestsFailAtEnqueueWithoutReservingBudget() {
        QueueMemoryBudget trackedBudget = new QueueMemoryBudget(Long.MAX_VALUE);
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10_000L),
            registry,
            splitter,
            threadPool,
            trackedBudget,
            ignored -> {}
        );
        Predictable predictor = model(new AtomicInteger(), null);
        MLInput input = textInput("a");
        input.setCallerAlgorithm(FunctionName.TEXT_EMBEDDING);

        AtomicReference<Exception> noKeyErr = new AtomicReference<>();
        AtomicReference<Exception> noItemsErr = new AtomicReference<>();
        List<BatchItem> items = registry.get(input).toItems(input);
        assertTrue(queue.enqueue(new QueueEntry(input, ActionListener.wrap(r -> {}, noKeyErr::set), predictor, null, items, null)));
        assertTrue(queue.enqueue(new QueueEntry(input, ActionListener.wrap(r -> {}, noItemsErr::set), predictor, null, List.of(), "k")));

        assertTrue(noKeyErr.get().getMessage().contains("Could not compute a batch group key"));
        assertTrue(noItemsErr.get().getMessage().contains("no input items"));
        assertEquals(0L, trackedBudget.getReservedBytes());
        assertNull("nothing was queued, so no timer is scheduled", scheduledFlush.get());
        assertTrue(queue.isIdle());
    }

    @Test
    public void subBatchFailureFailsOnlyItsCallerNotTheOthers() {
        // Byte limit only: each 30-byte doc lands in its own sub-batch, so callers do not share a call.
        String docA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; // 30 bytes
        String docB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"; // 30 bytes
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(null, 40L, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        Predictable predictor = model(null, docB); // fail the sub-batch carrying docB

        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        AtomicReference<Exception> aErr = new AtomicReference<>();
        AtomicReference<Exception> bErr = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(a::set, aErr::set), docA));
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, bErr::set), docB)); // second enqueue crosses 40 bytes -> flush
        scheduledFlush.get().run(); // docB's call is a partial tail, sent by the timer

        assertEquals("unaffected caller still succeeds", ImmutableList.of(docA), resultNames(a.get()));
        assertNull(aErr.get());
        assertTrue("only the caller in the failed sub-batch fails", bErr.get().getMessage().contains(docB));
    }

    /** A model whose call fails with the given exception, whatever the docs are. */
    private Predictable failingModel(Exception failure) {
        return new Predictable() {
            @Override
            public MLOutput predict(MLInput mlInput, MLModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
                listener.onFailure(failure);
            }

            @Override
            public boolean isModelReady() {
                return true;
            }

            @Override
            public void close() {}
        };
    }

    /**
     * A provider error carries the provider's raw response body, which describes the merged call rather than any
     * one request in it, so a merged sub-batch reports a generic failure instead of passing it to each request.
     */
    @Test
    public void mergedSubBatchFailureReportsAGenericErrorToEachRequest() {
        // Count limit 2 with no byte limit: both callers' docs go out in one call.
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        Predictable predictor = failingModel(
            new OpenSearchStatusException(REMOTE_SERVICE_ERROR + "{\"error\":\"bad input doc-of-b\"}", RestStatus.BAD_REQUEST)
        );

        AtomicReference<Exception> aErr = new AtomicReference<>();
        AtomicReference<Exception> bErr = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, aErr::set), "doc-of-a"));
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, bErr::set), "doc-of-b"));

        assertNotNull("both callers in the merged call fail", aErr.get());
        assertNotNull(bErr.get());
        for (Exception e : new Exception[] { aErr.get(), bErr.get() }) {
            assertFalse("the provider message was passed through to an individual request", e.getMessage().contains("doc-of-b"));
            assertTrue(e.getMessage().contains("merged with other requests"));
            // The status has to survive, or a client retrying on 429 and giving up on 500 would stop retrying a
            // temporary provider throttle.
            assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) e).status());
        }
    }

    /**
     * The failure listener also receives errors raised before the provider was called - throttling, a guardrail
     * rejection, a model that is not deployed. Masking those would hide the caller's own reason from them.
     */
    @Test
    public void mergedSubBatchPassesThroughErrorsWeRaisedOurselves() {
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        Predictable predictor = failingModel(
            new OpenSearchStatusException("Request is throttled at user level.", RestStatus.TOO_MANY_REQUESTS)
        );

        AtomicReference<Exception> aErr = new AtomicReference<>();
        AtomicReference<Exception> bErr = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, aErr::set), "doc-of-a"));
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, bErr::set), "doc-of-b"));

        for (Exception e : new Exception[] { aErr.get(), bErr.get() }) {
            assertEquals("Request is throttled at user level.", e.getMessage());
            assertEquals(RestStatus.TOO_MANY_REQUESTS, ((OpenSearchStatusException) e).status());
        }
    }

    /**
     * The other half: a sub-batch holding one caller's items keeps the provider's message, which is what makes
     * the failure diagnosable. This is also the only shape that exists when coalescing is off.
     */
    @Test
    public void singleCallerSubBatchFailureKeepsTheProviderError() {
        // Byte limit only, so each 30-byte doc lands in its own call.
        String docA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String docB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(null, 40L, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        String providerBody = REMOTE_SERVICE_ERROR + "{\"error\":\"bad input\"}";
        Predictable predictor = failingModel(new OpenSearchStatusException(providerBody, RestStatus.BAD_REQUEST));

        AtomicReference<Exception> bErr = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), docA));
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, bErr::set), docB));
        scheduledFlush.get().run(); // docB's call is a partial tail, sent by the timer

        assertEquals(providerBody, bErr.get().getMessage());
    }

    @Test
    public void notifiesEachListenerExactlyOnce() {
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        Predictable predictor = model(null, null);
        AtomicInteger aCount = new AtomicInteger();
        AtomicInteger bCount = new AtomicInteger();

        queue.enqueue(entry(predictor, ActionListener.wrap(r -> aCount.incrementAndGet(), e -> aCount.incrementAndGet()), "a"));
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> bCount.incrementAndGet(), e -> bCount.incrementAndGet()), "b"));

        assertEquals(1, aCount.get());
        assertEquals(1, bCount.get());
    }

    @Test
    public void mismatchedTypeEntryIsIsolatedAndDoesNotFailOtherCallers() {
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        Predictable predictor = model(null, null);

        AtomicReference<MLTaskResponse> aResult = new AtomicReference<>();
        AtomicReference<Exception> aErr = new AtomicReference<>();
        AtomicReference<Exception> bErr = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(aResult::set, aErr::set), "a"));

        MLInput mismatched = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_SIMILARITY)
            .inputDataset(new org.opensearch.ml.common.dataset.TextSimilarityInputDataSet("q", ImmutableList.of("d")))
            .build();
        queue.enqueue(queueEntry(mismatched, ActionListener.wrap(r -> {}, bErr::set), predictor));

        scheduledFlush.get().run();

        assertNull("valid caller is unaffected by the mismatched one", aErr.get());
        assertEquals(ImmutableList.of("a"), resultNames(aResult.get()));
        assertNotNull("mismatched caller is isolated and fails on its own", bErr.get());
        assertTrue(bErr.get().getMessage().contains("does not support batch inference"));
    }

    @Test
    public void aThrowingListenerDoesNotStopOtherCallersFromBeingSettled() {
        // A flush settles many independent callers; one whose listener throws must not strand the rest.
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        Predictable predictor = model(null, null);

        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> { throw new RuntimeException("boom in listener"); }, e -> {}), "a"));
        queue.enqueue(entry(predictor, ActionListener.wrap(b::set, e -> {}), "b"));

        assertEquals("the other caller is still settled despite a throwing listener", ImmutableList.of("b"), resultNames(b.get()));
    }

    @Test
    public void timerIsRescheduledAfterAFailedSchedule() {
        // If scheduling the flush timer fails (e.g. pool rejection), the model's timed flush must not be
        // stuck off: a later enqueue has to be able to schedule again.
        AtomicInteger scheduleAttempts = new AtomicInteger();
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(invocation -> {
            if (scheduleAttempts.incrementAndGet() == 1) {
                throw new RuntimeException("scheduler rejected");
            }
            scheduledFlush.set(invocation.getArgument(0));
            return mock(Scheduler.ScheduledCancellable.class);
        });
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        Predictable predictor = model(new AtomicInteger(), null);

        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "a")); // first schedule fails, swallowed
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "b")); // must retry the schedule

        assertEquals("a failed schedule must not disable the timer permanently", 2, scheduleAttempts.get());
        assertNotNull("the retry actually scheduled a flush", scheduledFlush.get());
    }

    @Test
    public void timerReschedulesAfterFireTimeRejection() {
        // Pool rejection happens when the timer fires, not when schedule() is called, so onRejection (not
        // the schedule() call site) must clear the flag. Otherwise the model's timed flush is stuck off.
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        Predictable predictor = model(new AtomicInteger(), null);

        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "a"));
        // Simulate the executor rejecting the scheduled flush at fire time.
        ((AbstractRunnable) scheduledFlush.get()).onRejection(new RuntimeException("rejected at fire time"));

        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "b"));

        verify(threadPool, times(2)).schedule(any(Runnable.class), any(TimeValue.class), anyString());
    }

    @Test
    public void timerReschedulesAfterTheScheduledTaskFails() {
        // If the scheduled flush task fails (onFailure), the flag must be cleared so the timer reschedules.
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        Predictable predictor = model(new AtomicInteger(), null);

        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "a"));
        ((AbstractRunnable) scheduledFlush.get()).onFailure(new RuntimeException("timer task failed"));

        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "b"));

        verify(threadPool, times(2)).schedule(any(Runnable.class), any(TimeValue.class), anyString());
    }

    @Test
    public void fireTimeRejectionFailsQueuedCallersWithoutDispatchingOnTheSchedulerThread() {
        // Under pool rejection the queue must surface the failure to callers, not run dispatch (which
        // would burn the shared scheduler thread and self-feed a reschedule loop) and not strand them.
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicInteger predictCalls = new AtomicInteger();
        Predictable predictor = model(predictCalls, null);

        AtomicReference<Exception> err = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, err::set), "a"));
        ((AbstractRunnable) scheduledFlush.get()).onRejection(new RuntimeException("pool full"));

        assertNotNull("the queued caller is failed, not stranded", err.get());
        assertEquals("no dispatch happens on rejection", 0, predictCalls.get());
    }

    @Test
    public void divergentParametersAreNotCoalescedIntoOneCall() {
        // Two callers to the same model send different result filters. They must not share a model call,
        // or one caller's parameters would be applied to the other's document.
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);

        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        queue.enqueue(filteredEntry(predictor, new ModelResultFilter(false, true, null, null), ActionListener.wrap(a::set, e -> {}), "a"));
        queue.enqueue(filteredEntry(predictor, new ModelResultFilter(true, false, null, null), ActionListener.wrap(b::set, e -> {}), "b"));
        scheduledFlush.get().run();

        assertEquals("different parameters must not coalesce into one call", 2, calls.get());
        assertEquals(ImmutableList.of("a"), resultNames(a.get()));
        assertEquals(ImmutableList.of("b"), resultNames(b.get()));
    }

    @Test
    public void divergentContentTypeParametersAreNotCoalescedIntoOneCall() {
        // The query-vs-passage content type is the classic leakage hazard: two callers to the same model
        // with different embedding content types must not share a call.
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);

        MLAlgoParams query = AsymmetricTextEmbeddingParameters.builder().embeddingContentType(EmbeddingContentType.QUERY).build();
        MLAlgoParams passage = AsymmetricTextEmbeddingParameters.builder().embeddingContentType(EmbeddingContentType.PASSAGE).build();
        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        queue.enqueue(paramsEntry(predictor, query, ActionListener.wrap(a::set, e -> {}), "a"));
        queue.enqueue(paramsEntry(predictor, passage, ActionListener.wrap(b::set, e -> {}), "b"));
        scheduledFlush.get().run();

        assertEquals("query and passage requests must not coalesce", 2, calls.get());
        assertEquals(ImmutableList.of("a"), resultNames(a.get()));
        assertEquals(ImmutableList.of("b"), resultNames(b.get()));
    }

    @Test
    public void enqueueRejectsWithBackpressureWhenMemoryBudgetIsExhausted() {
        String doc = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);
        // Room for one entry and no more, and a threshold high enough that the first entry stays pending, so the
        // second is rejected because the budget is held rather than because it is too large to ever admit.
        long oneEntry = entry(predictor, ActionListener.wrap(r -> {}, e -> {}), doc).getRetainedByteSize();
        QueueMemoryBudget fullBudget = new QueueMemoryBudget(oneEntry + 1);
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10_000L),
            registry,
            splitter,
            threadPool,
            fullBudget,
            ignored -> {}
        );

        AtomicReference<Exception> admittedErr = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        assertTrue(queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, admittedErr::set), doc)));
        assertTrue(
            "a request rejected for backpressure is settled here, not handed back to run unqueued",
            queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, err::set), doc))
        );

        assertNull("the admitted request is unaffected", admittedErr.get());
        assertNotNull("the caller is failed, not silently dropped", err.get());
        assertTrue(err.get() instanceof OpenSearchRejectedExecutionException);
        assertEquals("a rejected request never reaches the model", 0, calls.get());
        assertEquals("a rejected request holds no reservation of its own", oneEntry, fullBudget.getReservedBytes());
    }

    @Test
    public void requestTooLargeForTheWholeBudgetIsHandedBackToRunUnqueued() {
        // No amount of backoff frees enough budget for this request, so failing it with a retryable 429 would be
        // a permanent rejection. The queue declines it and the router runs it through the splitter instead.
        QueueMemoryBudget tinyBudget = new QueueMemoryBudget(10L);
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10_000L),
            registry,
            splitter,
            threadPool,
            tinyBudget,
            ignored -> {}
        );
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);

        AtomicReference<Exception> err = new AtomicReference<>();
        AtomicReference<MLTaskResponse> response = new AtomicReference<>();
        boolean queued = queue.enqueue(entry(predictor, ActionListener.wrap(response::set, err::set), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

        assertFalse("the caller must be told to run the request itself", queued);
        assertNull("the listener is left for the caller to settle", err.get());
        assertNull(response.get());
        assertEquals("the queue does not call the model for a request it declined", 0, calls.get());
        assertNull("no timer is scheduled for a request that was never queued", scheduledFlush.get());
        assertEquals("a declined request holds no reservation", 0, tinyBudget.getReservedBytes());
    }

    @Test
    public void resultCountMismatchFailsTheCallersInsteadOfMisroutingResults() {
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        // Two single-doc callers coalesce into one call, and the model answers with three tensors.
        Predictable predictor = new Predictable() {
            @Override
            public MLOutput predict(MLInput mlInput, MLModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
                List<ModelTensor> tensors = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    tensors.add(ModelTensor.builder().name("t" + i).build());
                }
                listener
                    .onResponse(
                        new MLTaskResponse(
                            ModelTensorOutput
                                .builder()
                                .mlModelOutputs(ImmutableList.of(ModelTensors.builder().mlModelTensors(tensors).build()))
                                .build()
                        )
                    );
            }

            @Override
            public boolean isModelReady() {
                return true;
            }

            @Override
            public void close() {}
        };

        AtomicReference<Exception> aErr = new AtomicReference<>();
        AtomicReference<Exception> bErr = new AtomicReference<>();
        queue
            .enqueue(
                entry(
                    predictor,
                    ActionListener.wrap(r -> { throw new AssertionError("must not receive a misrouted result"); }, aErr::set),
                    "a"
                )
            );
        queue
            .enqueue(
                entry(
                    predictor,
                    ActionListener.wrap(r -> { throw new AssertionError("must not receive a misrouted result"); }, bErr::set),
                    "b"
                )
            );

        assertTrue(aErr.get().getMessage().contains("Model returned 3 results for a sub-batch of 2 items"));
        assertTrue(bErr.get().getMessage().contains("Model returned 3 results for a sub-batch of 2 items"));
    }

    @Test
    public void memoryBudgetIsReleasedAfterCompletionSoLaterRequestsAreAdmitted() {
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);
        String doc = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        QueueEntry first = entry(predictor, ActionListener.wrap(r -> {}, e -> {}), doc);
        QueueMemoryBudget exactBudget = new QueueMemoryBudget(first.getRetainedByteSize());
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(1, null, 10_000L),
            registry,
            splitter,
            threadPool,
            exactBudget,
            ignored -> {}
        );

        AtomicReference<Exception> err1 = new AtomicReference<>();
        AtomicReference<Exception> err2 = new AtomicReference<>();
        first = entry(predictor, ActionListener.wrap(r -> {}, err1::set), doc);
        queue.enqueue(first);
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, err2::set), doc));

        assertNull(err1.get());
        assertNull("the first request released its reservation on flush, admitting the second", err2.get());
        assertEquals(2, calls.get());
        assertEquals("nothing stays reserved once both have completed", 0, exactBudget.getReservedBytes());
    }

    @Test
    public void retainedSizeIncludesRequestAndItemOverheadWithoutChangingPayloadSize() {
        QueueEntry entry = entry(model(null, null), ActionListener.wrap(r -> {}, e -> {}), "abc");

        assertEquals(3L, entry.getPayloadByteSize());
        assertEquals(
            3L + QueueEntry.ESTIMATED_ENTRY_OVERHEAD_BYTES + QueueEntry.ESTIMATED_ITEM_OVERHEAD_BYTES,
            entry.getRetainedByteSize()
        );
    }

    @Test
    public void unsupportedEntryHasNonZeroRetainedSize() {
        QueueEntry entry = new QueueEntry(textInput("a"), ActionListener.wrap(r -> {}, e -> {}), model(null, null), null, null, null);

        assertEquals(0L, entry.getPayloadByteSize());
        assertEquals(QueueEntry.ESTIMATED_ENTRY_OVERHEAD_BYTES, entry.getRetainedByteSize());
    }

    @Test
    public void memoryBudgetRemainsReservedUntilRemotePredictionSettles() {
        AtomicReference<ActionListener<MLTaskResponse>> remoteListener = new AtomicReference<>();
        Predictable predictor = new Predictable() {
            @Override
            public MLOutput predict(MLInput mlInput, MLModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
                remoteListener.set(listener);
            }

            @Override
            public boolean isModelReady() {
                return true;
            }

            @Override
            public void close() {}
        };
        QueueEntry entry = entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "a");
        QueueMemoryBudget inFlightBudget = new QueueMemoryBudget(entry.getRetainedByteSize());
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(1, null, 10_000L),
            registry,
            splitter,
            threadPool,
            inFlightBudget,
            ignored -> {}
        );

        queue.enqueue(entry);

        assertNotNull(remoteListener.get());
        assertEquals(
            "dispatch must not release memory still retained by the in-flight callback",
            entry.getRetainedByteSize(),
            inFlightBudget.getReservedBytes()
        );

        ModelTensorOutput output = ModelTensorOutput
            .builder()
            .mlModelOutputs(
                ImmutableList.of(ModelTensors.builder().mlModelTensors(ImmutableList.of(ModelTensor.builder().name("a").build())).build())
            )
            .build();
        remoteListener.get().onResponse(new MLTaskResponse(output));

        assertEquals(0L, inFlightBudget.getReservedBytes());
    }

    @Test
    public void enqueueAndDrainCannotObservePartiallyAdmittedEntry() throws Exception {
        CountDownLatch reserveEntered = new CountDownLatch(1);
        CountDownLatch allowReserve = new CountDownLatch(1);
        QueueMemoryBudget blockingBudget = new QueueMemoryBudget(Long.MAX_VALUE) {
            @Override
            boolean tryReserve(long bytes) {
                reserveEntered.countDown();
                try {
                    if (!allowReserve.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("timed out waiting to release reservation");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                return super.tryReserve(bytes);
            }
        };
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10_000L),
            registry,
            splitter,
            threadPool,
            blockingBudget,
            ignored -> {}
        );
        AtomicReference<MLTaskResponse> response = new AtomicReference<>();
        QueueEntry entry = entry(model(null, null), ActionListener.wrap(response::set, e -> {}), "a");

        Thread enqueueThread = new Thread(() -> queue.enqueue(entry));
        Thread flushThread = new Thread(queue::flush);
        enqueueThread.start();
        assertTrue(reserveEntered.await(5, TimeUnit.SECONDS));
        flushThread.start();
        allowReserve.countDown();
        enqueueThread.join(5_000L);
        flushThread.join(5_000L);
        queue.flush();

        assertFalse(enqueueThread.isAlive());
        assertFalse(flushThread.isAlive());
        assertNotNull("the entry is drained and completed rather than stranded between queue and totals", response.get());
        assertEquals(0L, blockingBudget.getReservedBytes());
    }

    @Test
    public void thresholdFlushCancelsThePendingTimer() {
        AtomicReference<Scheduler.ScheduledCancellable> timer = new AtomicReference<>();
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(invocation -> {
            scheduledFlush.set(invocation.getArgument(0));
            Scheduler.ScheduledCancellable cancellable = mock(Scheduler.ScheduledCancellable.class);
            timer.set(cancellable);
            return cancellable;
        });
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        Predictable predictor = model(new AtomicInteger(), null);

        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "a"));
        assertNotNull(timer.get());
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "b"));

        verify(timer.get()).cancel();
    }

    @Test
    public void isIdleTracksPendingEntries() {
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        Predictable predictor = model(new AtomicInteger(), null);

        assertTrue("a fresh queue is idle", queue.isIdle());
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "a"));
        assertFalse("a queue with a pending entry is not idle", queue.isIdle());
        scheduledFlush.get().run();
        assertTrue("a drained queue is idle again", queue.isIdle());
    }

    @Test
    public void unsupportedInputTypeFailsInIsolationConsistentlyWithTheNonQueuedPath() {
        // A model configured for batching must be sent a splittable input type. An unsupported type fails
        // just that entry (isolated), matching the non-queued path, rather than being sent unsplit.
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(1, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicInteger predictCalls = new AtomicInteger();
        Predictable predictor = model(predictCalls, null);

        MLInput unsupported = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_SIMILARITY)
            .inputDataset(new org.opensearch.ml.common.dataset.TextSimilarityInputDataSet("q", ImmutableList.of("d")))
            .build();
        AtomicReference<Exception> err = new AtomicReference<>();
        queue.enqueue(queueEntry(unsupported, ActionListener.wrap(r -> {}, err::set), predictor));

        assertEquals("an unsupported type is never sent to the model", 0, predictCalls.get());
        assertNotNull(err.get());
        assertTrue(err.get().getMessage().contains("does not support batch inference"));
    }

    private static ModelTensorOutput namedTensors(List<String> names) {
        List<ModelTensor> tensors = new ArrayList<>();
        for (String name : names) {
            tensors.add(ModelTensor.builder().name(name).build());
        }
        return ModelTensorOutput.builder().mlModelOutputs(ImmutableList.of(ModelTensors.builder().mlModelTensors(tensors).build())).build();
    }

    private Predictable holdingModel(List<List<String>> docsPerCall, List<ActionListener<MLTaskResponse>> listeners) {
        return new Predictable() {
            @Override
            public MLOutput predict(MLInput mlInput, MLModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
                docsPerCall.add(((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs());
                listeners.add(listener);
            }

            @Override
            public boolean isModelReady() {
                return true;
            }

            @Override
            public void close() {}
        };
    }

    @Test
    public void supersededTimerThatStillRunsDoesNothing() {
        List<AbstractRunnable> timers = new ArrayList<>();
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(invocation -> {
            timers.add(invocation.getArgument(0));
            return mock(Scheduler.ScheduledCancellable.class);
        });
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);

        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        AtomicReference<Exception> bErr = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "a1"));
        queue.enqueue(entry(predictor, ActionListener.wrap(b::set, bErr::set), "b1", "b2")); // sends [a1, b1], carries b2
        assertEquals(1, calls.get());
        assertEquals("the round re-armed a timer for the carried tail", 2, timers.size());

        timers.get(0).run();
        timers.get(0).onRejection(new OpenSearchRejectedExecutionException("late rejection of the superseded timer"));
        assertEquals("a superseded timer must not send the tail", 1, calls.get());
        assertNull("a superseded timer must not fail the waiting request", bErr.get());
        assertNull(b.get());

        timers.get(1).run();
        assertEquals(2, calls.get());
        assertEquals(ImmutableList.of("b1", "b2"), resultNames(b.get()));
        assertTrue(queue.isIdle());
    }

    @Test
    public void flushAskedForDuringARunningRoundIsNotDropped() throws Exception {
        CountDownLatch splitEntered = new CountDownLatch(1);
        CountDownLatch releaseSplit = new CountDownLatch(1);
        AtomicInteger splits = new AtomicInteger();
        BatchSplitter blockingSplitter = new BatchSplitter() {
            @Override
            public List<List<BatchItem>> split(List<BatchItem> items, BatchInferenceConfig config) {
                if (splits.incrementAndGet() == 1) {
                    splitEntered.countDown();
                    try {
                        assertTrue(releaseSplit.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }
                return super.split(items, config);
            }
        };
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(3, null, 10_000L),
            registry,
            blockingSplitter,
            threadPool,
            budget,
            ignored -> {}
        );
        List<List<String>> sent = new ArrayList<>();
        Predictable echo = model(null, null);
        Predictable predictor = new Predictable() {
            @Override
            public MLOutput predict(MLInput mlInput, MLModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
                sent.add(((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs());
                echo.asyncPredict(mlInput, listener, channel);
            }

            @Override
            public boolean isModelReady() {
                return true;
            }

            @Override
            public void close() {}
        };

        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        Thread drainer = new Thread(() -> queue.enqueue(entry(predictor, ActionListener.wrap(a::set, e -> {}), "a1", "a2", "a3", "a4")));
        drainer.start();
        assertTrue(splitEntered.await(5, TimeUnit.SECONDS));

        queue.enqueue(entry(predictor, ActionListener.wrap(b::set, e -> {}), "b1")); // a fresh group while A is detached
        queue.flush(); // the timer firing mid-round
        assertFalse("the round is still running, so it is not idle", queue.isIdle());

        releaseSplit.countDown();
        drainer.join(5_000L);
        assertFalse(drainer.isAlive());

        assertEquals(ImmutableList.of(ImmutableList.of("a1", "a2", "a3"), ImmutableList.of("a4", "b1")), sent);
        assertEquals(ImmutableList.of("a1", "a2", "a3", "a4"), resultNames(a.get()));
        assertEquals(ImmutableList.of("b1"), resultNames(b.get()));
        assertTrue(queue.isIdle());
    }

    @Test
    public void carriedTailTimerIsArmedBeforeTheModelIsCalled() {
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicReference<Boolean> timerArmedAtFirstCall = new AtomicReference<>();
        Predictable echo = model(null, null);
        Predictable predictor = new Predictable() {
            @Override
            public MLOutput predict(MLInput mlInput, MLModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
                timerArmedAtFirstCall.compareAndSet(null, scheduledFlush.get() != null);
                echo.asyncPredict(mlInput, listener, channel);
            }

            @Override
            public boolean isModelReady() {
                return true;
            }

            @Override
            public void close() {}
        };

        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "a1", "a2", "a3"));

        assertEquals(Boolean.TRUE, timerArmedAtFirstCall.get());
    }

    @Test
    public void concurrentCallbacksCompleteEachRequestExactlyOnce() throws Exception {
        List<List<String>> docsPerCall = new ArrayList<>();
        List<ActionListener<MLTaskResponse>> listeners = new ArrayList<>();
        Predictable predictor = holdingModel(docsPerCall, listeners);
        QueueMemoryBudget trackedBudget = new QueueMemoryBudget(Long.MAX_VALUE);
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            trackedBudget,
            ignored -> {}
        );

        String[][] requests = { { "a1", "a2", "a3" }, { "b1" }, { "c1", "c2" } };
        List<AtomicInteger> notifications = new ArrayList<>();
        List<AtomicReference<MLTaskResponse>> responses = new ArrayList<>();
        for (String[] docs : requests) {
            AtomicInteger count = new AtomicInteger();
            AtomicReference<MLTaskResponse> response = new AtomicReference<>();
            notifications.add(count);
            responses.add(response);
            queue.enqueue(entry(predictor, ActionListener.wrap(r -> {
                count.incrementAndGet();
                response.set(r);
            }, e -> count.incrementAndGet()), docs));
        }
        assertEquals(
            ImmutableList.of(ImmutableList.of("a1", "a2"), ImmutableList.of("a3", "b1"), ImmutableList.of("c1", "c2")),
            docsPerCall
        );

        CountDownLatch go = new CountDownLatch(1);
        List<Thread> answerers = new ArrayList<>();
        for (int call = listeners.size() - 1; call >= 0; call--) {
            ActionListener<MLTaskResponse> listener = listeners.get(call);
            List<String> docs = docsPerCall.get(call);
            Thread answerer = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                listener.onResponse(new MLTaskResponse(namedTensors(docs)));
            });
            answerers.add(answerer);
            answerer.start();
        }
        go.countDown();
        for (Thread answerer : answerers) {
            answerer.join(5_000L);
            assertFalse(answerer.isAlive());
        }

        for (int i = 0; i < requests.length; i++) {
            assertEquals("each request completes exactly once", 1, notifications.get(i).get());
            assertEquals(ImmutableList.copyOf(requests[i]), resultNames(responses.get(i).get()));
        }
        assertEquals(0L, trackedBudget.getReservedBytes());
    }

    @Test
    public void byteLimitTailIsCarriedAndFilledByTheNextRequest() {
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(null, 10L, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);

        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(a::set, e -> {}), "aaaaaa", "bbbbbb"));
        assertEquals(1, calls.get());
        assertNull(a.get());

        queue.enqueue(entry(predictor, ActionListener.wrap(b::set, e -> {}), "cccc"));

        assertEquals("6 + 4 bytes fill the 10-byte call, so no timer is needed", 2, calls.get());
        assertEquals(ImmutableList.of("aaaaaa", "bbbbbb"), resultNames(a.get()));
        assertEquals(ImmutableList.of("cccc"), resultNames(b.get()));
        assertTrue(queue.isIdle());
    }

    @Test
    public void outOfOrderCallbacksCompleteEachRequestWithItsOwnResults() {
        List<List<String>> docsPerCall = new ArrayList<>();
        List<ActionListener<MLTaskResponse>> listeners = new ArrayList<>();
        Predictable predictor = holdingModel(docsPerCall, listeners);
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        AtomicReference<MLTaskResponse> c = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(a::set, e -> {}), "a1", "a2", "a3"));
        queue.enqueue(entry(predictor, ActionListener.wrap(b::set, e -> {}), "b1"));
        queue.enqueue(entry(predictor, ActionListener.wrap(c::set, e -> {}), "c1", "c2"));
        assertEquals(3, listeners.size());

        listeners.get(1).onResponse(new MLTaskResponse(namedTensors(docsPerCall.get(1))));
        assertEquals(ImmutableList.of("b1"), resultNames(b.get()));
        assertNull("A still waits for its first call", a.get());

        listeners.get(2).onResponse(new MLTaskResponse(namedTensors(docsPerCall.get(2))));
        assertEquals(ImmutableList.of("c1", "c2"), resultNames(c.get()));
        assertNull(a.get());

        listeners.get(0).onResponse(new MLTaskResponse(namedTensors(docsPerCall.get(0))));
        assertEquals(ImmutableList.of("a1", "a2", "a3"), resultNames(a.get()));
    }

    @Test
    public void predictorThatAnswersAndThenThrowsSettlesItsItemsOnce() {
        AtomicInteger calls = new AtomicInteger();
        List<ActionListener<MLTaskResponse>> held = new ArrayList<>();
        Predictable predictor = new Predictable() {
            @Override
            public MLOutput predict(MLInput mlInput, MLModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
                List<String> docs = ((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs();
                if (calls.incrementAndGet() == 1) {
                    listener.onResponse(new MLTaskResponse(namedTensors(docs)));
                    throw new IllegalStateException("predictor threw after answering");
                }
                held.add(listener);
            }

            @Override
            public boolean isModelReady() {
                return true;
            }

            @Override
            public void close() {}
        };
        QueueMemoryBudget trackedBudget = new QueueMemoryBudget(Long.MAX_VALUE);
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            trackedBudget,
            ignored -> {}
        );
        AtomicInteger aNotifications = new AtomicInteger();
        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        AtomicReference<Exception> aErr = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {
            aNotifications.incrementAndGet();
            a.set(r);
        }, e -> {
            aNotifications.incrementAndGet();
            aErr.set(e);
        }), "a1", "a2", "a3"));
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "b1"));
        assertEquals("A's second call is still in flight", 0, aNotifications.get());

        held.get(0).onResponse(new MLTaskResponse(namedTensors(ImmutableList.of("a3", "b1"))));

        assertEquals(1, aNotifications.get());
        assertNull(aErr.get());
        assertEquals(ImmutableList.of("a1", "a2", "a3"), resultNames(a.get()));
        assertEquals(0L, trackedBudget.getReservedBytes());
    }

    @Test
    public void scheduleFailureFailsItsRequestsBeforeTheRoundCallsTheModel() {
        AtomicInteger scheduleAttempts = new AtomicInteger();
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(invocation -> {
            if (scheduleAttempts.incrementAndGet() == 2) {
                throw new OpenSearchRejectedExecutionException("scheduler is shutting down");
            }
            scheduledFlush.set(invocation.getArgument(0));
            return mock(Scheduler.ScheduledCancellable.class);
        });
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicReference<Exception> xErr = new AtomicReference<>();
        AtomicReference<Boolean> xFailedBeforeTheCall = new AtomicReference<>();
        Predictable echo = model(null, null);
        Predictable predictor = new Predictable() {
            @Override
            public MLOutput predict(MLInput mlInput, MLModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
                xFailedBeforeTheCall.set(xErr.get() != null);
                echo.asyncPredict(mlInput, listener, channel);
            }

            @Override
            public boolean isModelReady() {
                return true;
            }

            @Override
            public void close() {}
        };
        ModelResultFilter groupX = new ModelResultFilter(false, true, null, null);
        ModelResultFilter groupY = new ModelResultFilter(true, false, null, null);

        AtomicReference<MLTaskResponse> y = new AtomicReference<>();
        queue.enqueue(filteredEntry(predictor, groupX, ActionListener.wrap(r -> {}, xErr::set), "x"));
        queue.enqueue(filteredEntry(predictor, groupY, ActionListener.wrap(r -> {}, e -> {}), "y1"));
        queue.enqueue(filteredEntry(predictor, groupY, ActionListener.wrap(y::set, e -> {}), "y2"));

        assertTrue(xErr.get() instanceof OpenSearchRejectedExecutionException);
        assertEquals(Boolean.TRUE, xFailedBeforeTheCall.get());
        assertEquals(ImmutableList.of("y2"), resultNames(y.get()));
        assertTrue(queue.isIdle());
    }

    @Test
    public void immediateRerunSurvivesTimerScheduleFailure() throws Exception {
        CountDownLatch splitEntered = new CountDownLatch(1);
        CountDownLatch releaseSplit = new CountDownLatch(1);
        AtomicInteger splits = new AtomicInteger();
        BatchSplitter blockingSplitter = new BatchSplitter() {
            @Override
            public List<List<BatchItem>> split(List<BatchItem> items, BatchInferenceConfig config) {
                if (splits.incrementAndGet() == 1) {
                    splitEntered.countDown();
                    try {
                        assertTrue(releaseSplit.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }
                return super.split(items, config);
            }
        };
        AtomicInteger scheduleAttempts = new AtomicInteger();
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(invocation -> {
            scheduleAttempts.incrementAndGet();
            throw new OpenSearchRejectedExecutionException("scheduler is shutting down");
        });
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            blockingSplitter,
            threadPool,
            budget,
            ignored -> {}
        );
        Predictable predictor = model(new AtomicInteger(), null);
        ModelResultFilter groupB = new ModelResultFilter(true, false, null, null);
        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        AtomicReference<MLTaskResponse> b1 = new AtomicReference<>();
        AtomicReference<MLTaskResponse> b2 = new AtomicReference<>();
        AtomicReference<Exception> bErr = new AtomicReference<>();

        Thread drainer = new Thread(() -> queue.enqueue(entry(predictor, ActionListener.wrap(a::set, e -> {}), "a1", "a2")));
        drainer.start();
        assertTrue(splitEntered.await(5, TimeUnit.SECONDS));

        queue.enqueue(filteredEntry(predictor, groupB, ActionListener.wrap(b1::set, bErr::set), "b1"));
        queue.enqueue(filteredEntry(predictor, groupB, ActionListener.wrap(b2::set, bErr::set), "b2"));

        releaseSplit.countDown();
        drainer.join(5_000L);
        assertFalse(drainer.isAlive());

        assertEquals(1, scheduleAttempts.get());
        assertEquals(ImmutableList.of("a1", "a2"), resultNames(a.get()));
        assertEquals(ImmutableList.of("b1"), resultNames(b1.get()));
        assertEquals(ImmutableList.of("b2"), resultNames(b2.get()));
        assertNull(bErr.get());
        assertTrue(queue.isIdle());
    }

    @Test
    public void staleThresholdHandoffArmsTimerForPartialWork() throws Exception {
        CountDownLatch splitEntered = new CountDownLatch(1);
        CountDownLatch releaseSplit = new CountDownLatch(1);
        AtomicInteger splits = new AtomicInteger();
        BatchSplitter blockingSplitter = new BatchSplitter() {
            @Override
            public List<List<BatchItem>> split(List<BatchItem> items, BatchInferenceConfig config) {
                if (splits.incrementAndGet() == 1) {
                    splitEntered.countDown();
                    try {
                        assertTrue(releaseSplit.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }
                return super.split(items, config);
            }
        };
        // The round end cannot arm B's timer because a rerun is due; the empty rerun round must arm it.
        AtomicInteger scheduleAttempts = new AtomicInteger();
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(invocation -> {
            if (scheduleAttempts.incrementAndGet() == 1) {
                throw new OpenSearchRejectedExecutionException("first schedule fails");
            }
            scheduledFlush.set(invocation.getArgument(0));
            return mock(Scheduler.ScheduledCancellable.class);
        });
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            blockingSplitter,
            threadPool,
            budget,
            ignored -> {}
        );
        Predictable predictor = model(new AtomicInteger(), null);
        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        QueueEntry entryA = entry(predictor, ActionListener.wrap(a::set, e -> {}), "a1", "a2");
        DynamicBatchingQueue.EnqueueDecision staleFlush = queue.offer(entryA);
        assertEquals(DynamicBatchingQueue.EnqueueDecision.FLUSH, staleFlush);

        Thread drainer = new Thread(queue::flush);
        drainer.start();
        assertTrue(splitEntered.await(5, TimeUnit.SECONDS));

        queue.enqueue(entry(predictor, ActionListener.wrap(b::set, e -> {}), "b"));
        queue.completeEnqueue(entryA, staleFlush);

        releaseSplit.countDown();
        drainer.join(5_000L);
        assertFalse(drainer.isAlive());

        assertEquals(ImmutableList.of("a1", "a2"), resultNames(a.get()));
        assertNull(b.get());
        assertEquals("the round end failed to arm B's timer and the empty rerun round retried", 2, scheduleAttempts.get());
        assertNotNull("the handed-off empty threshold round supplied B's missing timer", scheduledFlush.get());

        scheduledFlush.get().run();
        assertEquals(ImmutableList.of("b"), resultNames(b.get()));
        assertTrue(queue.isIdle());
    }

    @Test
    public void staleScheduleDecisionFlushesAGroupThatNowFillsACall() {
        AtomicInteger scheduleAttempts = new AtomicInteger();
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(invocation -> {
            scheduleAttempts.incrementAndGet();
            throw new OpenSearchRejectedExecutionException("scheduler is shutting down");
        });
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);
        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        AtomicReference<Exception> aErr = new AtomicReference<>();
        AtomicReference<Exception> bErr = new AtomicReference<>();
        QueueEntry entryA = entry(predictor, ActionListener.wrap(a::set, aErr::set), "a");
        QueueEntry entryB = entry(predictor, ActionListener.wrap(b::set, bErr::set), "b");

        DynamicBatchingQueue.EnqueueDecision decisionA = queue.offer(entryA);
        DynamicBatchingQueue.EnqueueDecision decisionB = queue.offer(entryB);
        assertEquals(DynamicBatchingQueue.EnqueueDecision.SCHEDULE_TIMER, decisionA);
        assertEquals(DynamicBatchingQueue.EnqueueDecision.FLUSH, decisionB);

        queue.completeEnqueue(entryA, decisionA);
        queue.completeEnqueue(entryB, decisionB);

        assertEquals(0, scheduleAttempts.get());
        assertEquals(1, calls.get());
        assertEquals(ImmutableList.of("a"), resultNames(a.get()));
        assertEquals(ImmutableList.of("b"), resultNames(b.get()));
        assertNull(aErr.get());
        assertNull(bErr.get());
        assertTrue(queue.isIdle());
    }

    @Test
    public void scheduleFailureLeavesRequestsAdmittedAfterItAlone() throws Exception {
        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        AtomicReference<Exception> bErr = new AtomicReference<>();
        AtomicReference<Exception> aErr = new AtomicReference<>();
        AtomicReference<Thread> admitB = new AtomicReference<>();
        AtomicReference<Thread.State> bStateWhileSchedulerThrew = new AtomicReference<>();
        AtomicInteger scheduleAttempts = new AtomicInteger();
        List<Scheduler.ScheduledCancellable> cancellables = new ArrayList<>();
        Predictable predictor = model(new AtomicInteger(), null);
        DynamicBatchingQueue[] queueRef = new DynamicBatchingQueue[1];
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(invocation -> {
            if (scheduleAttempts.incrementAndGet() == 1) {
                Thread thread = new Thread(() -> queueRef[0].enqueue(entry(predictor, ActionListener.wrap(b::set, bErr::set), "b")));
                admitB.set(thread);
                thread.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                bStateWhileSchedulerThrew.set(thread.getState());
                throw new OpenSearchRejectedExecutionException("scheduler rejected");
            }
            scheduledFlush.set(invocation.getArgument(0));
            Scheduler.ScheduledCancellable cancellable = mock(Scheduler.ScheduledCancellable.class);
            cancellables.add(cancellable);
            return cancellable;
        });
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        queueRef[0] = queue;

        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, aErr::set), "a"));
        admitB.get().join(5_000L);
        assertFalse(admitB.get().isAlive());

        assertEquals(
            "B could not be admitted while A's scheduling failure was handled",
            Thread.State.BLOCKED,
            bStateWhileSchedulerThrew.get()
        );
        assertTrue(aErr.get() instanceof OpenSearchRejectedExecutionException);
        assertNull("B was admitted after A's failed requests were taken", bErr.get());
        assertEquals("B armed its own timer", 2, scheduleAttempts.get());
        verify(cancellables.get(0), times(0)).cancel();

        scheduledFlush.get().run();
        assertEquals(ImmutableList.of("b"), resultNames(b.get()));
    }

    @Test
    public void staleThresholdDecisionLeavesALaterRequestsTimerAlone() {
        AtomicBoolean schedulerFails = new AtomicBoolean();
        List<Scheduler.ScheduledCancellable> cancellables = new ArrayList<>();
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(invocation -> {
            if (schedulerFails.get()) {
                throw new OpenSearchRejectedExecutionException("scheduler is shutting down");
            }
            scheduledFlush.set(invocation.getArgument(0));
            Scheduler.ScheduledCancellable cancellable = mock(Scheduler.ScheduledCancellable.class);
            cancellables.add(cancellable);
            return cancellable;
        });
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(2, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            ignored -> {}
        );
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);

        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        QueueEntry entryA = entry(predictor, ActionListener.wrap(a::set, e -> {}), "a1", "a2");
        assertEquals(DynamicBatchingQueue.EnqueueDecision.FLUSH, queue.offer(entryA));
        queue.flush(); // another flush sends A first
        assertEquals(ImmutableList.of("a1", "a2"), resultNames(a.get()));

        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        AtomicReference<Exception> bErr = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(b::set, bErr::set), "b"));
        assertEquals(1, cancellables.size());
        schedulerFails.set(true);

        queue.completeEnqueue(entryA, DynamicBatchingQueue.EnqueueDecision.FLUSH); // A's stale decision

        verify(cancellables.get(0), times(0)).cancel();
        assertNull("B keeps its timer and is not failed", bErr.get());
        assertEquals(1, calls.get());

        scheduledFlush.get().run();
        assertEquals(ImmutableList.of("b"), resultNames(b.get()));
    }

    @Test
    public void flushTimerThatThrowsGoesThroughOnFailureAndLeavesTheQueueUsable() {
        AtomicInteger idleCalls = new AtomicInteger();
        DynamicBatchingQueue queue = new DynamicBatchingQueue(
            "m",
            config(100, null, 10_000L),
            registry,
            splitter,
            threadPool,
            budget,
            q -> {
                if (idleCalls.incrementAndGet() == 1) {
                    throw new IllegalStateException("idle callback failed");
                }
            }
        );
        Predictable predictor = model(new AtomicInteger(), null);
        AtomicInteger aNotifications = new AtomicInteger();
        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {
            aNotifications.incrementAndGet();
            a.set(r);
        }, e -> aNotifications.incrementAndGet()), "a"));

        Runnable firstTimer = scheduledFlush.get();
        firstTimer.run(); // the flush sends A, then the idle callback throws inside doRun

        assertEquals(1, aNotifications.get());
        assertEquals(ImmutableList.of("a"), resultNames(a.get()));

        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(b::set, e -> {}), "b"));
        assertTrue("a fresh timer was armed for the next request", scheduledFlush.get() != firstTimer);
        scheduledFlush.get().run();
        assertEquals(ImmutableList.of("b"), resultNames(b.get()));
        assertTrue(queue.isIdle());
    }
}
