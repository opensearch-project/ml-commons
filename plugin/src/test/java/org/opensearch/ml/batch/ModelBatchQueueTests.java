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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.AbstractRunnable;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.concurrency.OpenSearchRejectedExecutionException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.input.parameter.textembedding.AsymmetricTextEmbeddingParameters;
import org.opensearch.ml.common.input.parameter.textembedding.AsymmetricTextEmbeddingParameters.EmbeddingContentType;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.model.BatchQueueConfig;
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

public class ModelBatchQueueTests {

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
            .queue(BatchQueueConfig.builder().enabled(true).flushTimeoutMs(flushMs).build())
            .build();
    }

    @Test
    public void flushesOnCountThresholdAndRoutesEachResultToItsCaller() {
        ModelBatchQueue queue = new ModelBatchQueue("m", config(3, null, 10_000L), registry, splitter, threadPool, budget);
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
        ModelBatchQueue queue = new ModelBatchQueue("m", config(100, null, 10L), registry, splitter, threadPool, budget);
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
        ModelBatchQueue queue = new ModelBatchQueue("m", config(100, null, 10L), registry, splitter, threadPool, budget);
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
        // One caller with 5 docs against a 2-item limit: 5 items >= 2 flushes, splitter makes 3 sub-batches.
        ModelBatchQueue queue = new ModelBatchQueue("m", config(2, null, 10_000L), registry, splitter, threadPool, budget);
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);

        AtomicReference<MLTaskResponse> result = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(result::set, e -> {}), "d1", "d2", "d3", "d4", "d5"));

        assertEquals("5 items at limit 2 -> 3 sub-batches -> 3 model calls", 3, calls.get());
        assertEquals(ImmutableList.of("d1", "d2", "d3", "d4", "d5"), resultNames(result.get()));
    }

    @Test
    public void subBatchFailureFailsOnlyItsCallerNotTheOthers() {
        // Byte limit only: each 30-byte doc lands in its own sub-batch, so callers do not share a call.
        String docA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; // 30 bytes
        String docB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"; // 30 bytes
        ModelBatchQueue queue = new ModelBatchQueue("m", config(null, 40L, 10_000L), registry, splitter, threadPool, budget);
        Predictable predictor = model(null, docB); // fail the sub-batch carrying docB

        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        AtomicReference<Exception> aErr = new AtomicReference<>();
        AtomicReference<Exception> bErr = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(a::set, aErr::set), docA));
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, bErr::set), docB)); // second enqueue crosses 40 bytes -> flush

        assertEquals("unaffected caller still succeeds", ImmutableList.of(docA), resultNames(a.get()));
        assertNull(aErr.get());
        assertTrue("only the caller in the failed sub-batch fails", bErr.get().getMessage().contains(docB));
    }

    @Test
    public void notifiesEachListenerExactlyOnce() {
        ModelBatchQueue queue = new ModelBatchQueue("m", config(2, null, 10_000L), registry, splitter, threadPool, budget);
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
        // A valid text-docs request and a mismatched-type request land in the same flush. The mismatched
        // one has no handler, so it is dispatched on its own and fails; the valid one is in its own group
        // and still succeeds.
        ModelBatchQueue queue = new ModelBatchQueue("m", config(100, null, 10L), registry, splitter, threadPool, budget);
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
        ModelBatchQueue queue = new ModelBatchQueue("m", config(2, null, 10_000L), registry, splitter, threadPool, budget);
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
        ModelBatchQueue queue = new ModelBatchQueue("m", config(100, null, 10L), registry, splitter, threadPool, budget);
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
        ModelBatchQueue queue = new ModelBatchQueue("m", config(100, null, 10L), registry, splitter, threadPool, budget);
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
        ModelBatchQueue queue = new ModelBatchQueue("m", config(100, null, 10L), registry, splitter, threadPool, budget);
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
        ModelBatchQueue queue = new ModelBatchQueue("m", config(100, null, 10L), registry, splitter, threadPool, budget);
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
        ModelBatchQueue queue = new ModelBatchQueue("m", config(100, null, 10L), registry, splitter, threadPool, budget);
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
        ModelBatchQueue queue = new ModelBatchQueue("m", config(100, null, 10L), registry, splitter, threadPool, budget);
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
        QueueMemoryBudget tinyBudget = new QueueMemoryBudget(10L);
        ModelBatchQueue queue = new ModelBatchQueue("m", config(100, null, 10_000L), registry, splitter, threadPool, tinyBudget);
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);

        AtomicReference<Exception> err = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, err::set), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

        assertNotNull("the caller is failed, not silently dropped", err.get());
        assertTrue(err.get() instanceof OpenSearchRejectedExecutionException);
        assertEquals("a rejected request never reaches the model", 0, calls.get());
        assertNull("no timer is scheduled for a rejected request", scheduledFlush.get());
        assertEquals("a rejected request holds no reservation", 0, tinyBudget.getReservedBytes());
    }

    @Test
    public void memoryBudgetIsReleasedAfterFlushSoLaterRequestsAreAdmitted() {
        QueueMemoryBudget budget30 = new QueueMemoryBudget(30L);
        ModelBatchQueue queue = new ModelBatchQueue("m", config(1, null, 10_000L), registry, splitter, threadPool, budget30);
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls, null);

        String doc = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        AtomicReference<Exception> err1 = new AtomicReference<>();
        AtomicReference<Exception> err2 = new AtomicReference<>();
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, err1::set), doc));
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, err2::set), doc));

        assertNull(err1.get());
        assertNull("the first request released its reservation on flush, admitting the second", err2.get());
        assertEquals(2, calls.get());
        assertEquals("nothing stays reserved once both have flushed", 0, budget30.getReservedBytes());
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
        ModelBatchQueue queue = new ModelBatchQueue("m", config(2, null, 10_000L), registry, splitter, threadPool, budget);
        Predictable predictor = model(new AtomicInteger(), null);

        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "a"));
        assertNotNull(timer.get());
        queue.enqueue(entry(predictor, ActionListener.wrap(r -> {}, e -> {}), "b"));

        verify(timer.get()).cancel();
    }

    @Test
    public void isIdleTracksPendingEntries() {
        ModelBatchQueue queue = new ModelBatchQueue("m", config(100, null, 10_000L), registry, splitter, threadPool, budget);
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
        ModelBatchQueue queue = new ModelBatchQueue("m", config(1, null, 10_000L), registry, splitter, threadPool, budget);
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
}
