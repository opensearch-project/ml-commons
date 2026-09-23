/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.concurrency.OpenSearchRejectedExecutionException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.model.DynamicBatchingConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.threadpool.Scheduler;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportChannel;

import com.google.common.collect.ImmutableList;

public class DynamicBatchingQueueManagerTests {

    private DynamicBatchingQueueManager manager;
    private ThreadPool threadPool;
    private AtomicReference<Runnable> scheduledFlush;

    @Before
    public void setUp() {
        threadPool = mock(ThreadPool.class);
        scheduledFlush = new AtomicReference<>();
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(invocation -> {
            scheduledFlush.set(invocation.getArgument(0));
            return mock(Scheduler.ScheduledCancellable.class);
        });
        manager = new DynamicBatchingQueueManager(
            new BatchableInputRegistry(),
            new BatchSplitter(),
            threadPool,
            new QueueMemoryBudget(Long.MAX_VALUE)
        );
    }

    private Predictable model(AtomicInteger calls) {
        return new Predictable() {
            @Override
            public MLOutput predict(MLInput mlInput, MLModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
                calls.incrementAndGet();
                List<ModelTensor> tensors = new ArrayList<>();
                for (String doc : ((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs()) {
                    tensors.add(ModelTensor.builder().name(doc).build());
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
    }

    private MLInput textInput(String... docs) {
        TextDocsInputDataSet dataSet = TextDocsInputDataSet.builder().docs(ImmutableList.copyOf(docs)).build();
        MLInput input = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(dataSet).build();
        input.setCallerAlgorithm(FunctionName.TEXT_EMBEDDING);
        return input;
    }

    private BatchInferenceConfig queued(int maxItems, long flushMs) {
        return BatchInferenceConfig
            .builder()
            .maxItemsPerRequest(maxItems)
            .dynamicBatching(DynamicBatchingConfig.builder().enabled(true).flushTimeoutMs(flushMs).build())
            .build();
    }

    @Test
    public void shouldQueueOnlyWhenConfigEnablesIt() {
        assertFalse(manager.shouldQueue(null));
        assertFalse(manager.shouldQueue(BatchInferenceConfig.builder().maxItemsPerRequest(96).build()));
        assertTrue(manager.shouldQueue(queued(96, 10L)));
    }

    @Test
    public void requestsToSameModelShareOneQueueAndCoalesce() {
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls);
        BatchInferenceConfig config = queued(2, 10_000L);

        // If each enqueue made its own queue, the first entry would never flush with the second.
        manager.enqueue("model-1", config, textInput("a"), predictor, null, ActionListener.wrap(r -> {}, e -> {}));
        manager.enqueue("model-1", config, textInput("b"), predictor, null, ActionListener.wrap(r -> {}, e -> {}));

        assertEquals("two single-doc requests at limit 2 coalesce into one model call", 1, calls.get());
    }

    @Test
    public void sameModelDifferentCallerAlgorithmsDoNotCoalesce() {
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls);
        BatchInferenceConfig config = queued(100, 10_000L);

        MLInput embedding = textInput("a");
        embedding.setCallerAlgorithm(FunctionName.TEXT_EMBEDDING);
        MLInput sparse = textInput("b");
        sparse.setCallerAlgorithm(FunctionName.SPARSE_ENCODING);

        manager.enqueue("model-1", config, embedding, predictor, null, ActionListener.wrap(r -> {}, e -> {}));
        manager.enqueue("model-1", config, sparse, predictor, null, ActionListener.wrap(r -> {}, e -> {}));
        scheduledFlush.get().run();

        assertEquals("requests differing only by caller algorithm must not share a model call", 2, calls.get());
    }

    @Test
    public void requestsWithUnknownCallerAlgorithmDoNotCoalesce() {
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls);
        BatchInferenceConfig config = queued(100, 10_000L);

        MLInput a = textInput("a");
        a.setCallerAlgorithm(null);
        MLInput b = textInput("b");
        b.setCallerAlgorithm(null);

        manager.enqueue("model-1", config, a, predictor, null, ActionListener.wrap(r -> {}, e -> {}));
        manager.enqueue("model-1", config, b, predictor, null, ActionListener.wrap(r -> {}, e -> {}));
        scheduledFlush.get().run();

        assertEquals("requests with an unknown caller algorithm must not coalesce", 2, calls.get());
    }

    @Test
    public void requestsWithRemoteCallerAlgorithmCoalesce() {
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls);
        BatchInferenceConfig config = queued(2, 10_000L);

        MLInput a = textInput("a");
        a.setCallerAlgorithm(FunctionName.REMOTE);
        MLInput b = textInput("b");
        b.setCallerAlgorithm(FunctionName.REMOTE);

        manager.enqueue("model-1", config, a, predictor, null, ActionListener.wrap(r -> {}, e -> {}));
        manager.enqueue("model-1", config, b, predictor, null, ActionListener.wrap(r -> {}, e -> {}));

        assertEquals("direct remote predict requests should share a model call", 1, calls.get());
    }

    @Test
    public void differentModelsDoNotCoalesceTogether() {
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls);
        BatchInferenceConfig config = queued(2, 10_000L);

        manager.enqueue("model-1", config, textInput("a"), predictor, null, ActionListener.wrap(r -> {}, e -> {}));
        manager.enqueue("model-2", config, textInput("b"), predictor, null, ActionListener.wrap(r -> {}, e -> {}));

        assertEquals("one item each on two separate model queues -> neither hits the limit of 2", 0, calls.get());
    }

    @Test
    public void countsItemsFromTheInputNotPerRequest() {
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls);

        // A single 3-doc request must be counted as 3 items, hitting the limit of 3 and flushing.
        manager.enqueue("model-1", queued(3, 10_000L), textInput("x", "y", "z"), predictor, null, ActionListener.wrap(r -> {}, e -> {}));

        assertEquals(1, calls.get());
    }

    @Test
    public void changingConfigReplacesQueueForModelAndDrainsTheOldQueue() {
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls);

        AtomicReference<MLTaskResponse> a = new AtomicReference<>();
        manager.enqueue("model-1", queued(100, 10_000L), textInput("a"), predictor, null, ActionListener.wrap(a::set, e -> {}));
        assertEquals(0, calls.get());
        assertNull("first request is still waiting in the queue", a.get());

        AtomicReference<MLTaskResponse> b = new AtomicReference<>();
        manager.enqueue("model-1", queued(1, 10_000L), textInput("b"), predictor, null, ActionListener.wrap(b::set, e -> {}));

        assertEquals("replaced queue is drained separately from the new request", 2, calls.get());
        assertEquals("the stranded caller receives its own response", ImmutableList.of("a"), resultNames(a.get()));
        assertEquals(ImmutableList.of("b"), resultNames(b.get()));
    }

    @Test
    public void emptyQueueIsRemovedAfterFlushButKeptWhilePending() {
        Predictable predictor = model(new AtomicInteger());

        manager.enqueue("model-1", queued(100, 10_000L), textInput("a"), predictor, null, ActionListener.wrap(r -> {}, e -> {}));
        assertEquals("a queue with a pending entry is retained", 1, manager.queueCount());

        // The timer flush drains the only entry, leaving the queue empty; it is removed rather than kept around.
        scheduledFlush.get().run();
        assertEquals("a queue is removed as soon as a flush drains it empty", 0, manager.queueCount());
    }

    @Test
    public void unsupportedInputIsRejectedBeforeItCanConsumeQueueMemory() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Exception> error = new AtomicReference<>();
        MLInput unsupported = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_SIMILARITY)
            .inputDataset(new org.opensearch.ml.common.dataset.TextSimilarityInputDataSet("q", ImmutableList.of("d")))
            .build();

        boolean handled = manager
            .enqueue("model-1", queued(100, 10_000L), unsupported, model(calls), null, ActionListener.wrap(r -> {}, error::set));

        assertTrue("an invalid request is settled here, not handed back to the caller", handled);
        assertEquals(0, calls.get());
        assertTrue(error.get() instanceof IllegalArgumentException);
        assertEquals("an invalid request should not create a retained per-model queue", 0, manager.queueCount());
    }

    @Test
    public void requestTooLargeForTheBudgetIsHandedBackWithoutSettlingTheListener() {
        DynamicBatchingQueueManager tightManager = new DynamicBatchingQueueManager(
            new BatchableInputRegistry(),
            new BatchSplitter(),
            threadPool,
            new QueueMemoryBudget(10L)
        );
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Exception> error = new AtomicReference<>();
        AtomicReference<MLTaskResponse> response = new AtomicReference<>();

        boolean handled = tightManager
            .enqueue(
                "model-1",
                queued(100, 10_000L),
                textInput("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                model(calls),
                null,
                ActionListener.wrap(response::set, error::set)
            );

        assertFalse("the caller must run a request the queue can never admit", handled);
        assertNull("the listener is left untouched for the caller", error.get());
        assertNull(response.get());
        assertEquals(0, calls.get());
        assertEquals("a too-large request must not leave an empty queue behind", 0, tightManager.queueCount());
    }

    @Test
    public void rejectedRequestDoesNotLeaveAnEmptyQueue() {
        // Budget exhausted: request fits capacity but cannot be reserved, so it is REJECTED.
        QueueMemoryBudget exhaustedBudget = new QueueMemoryBudget(Long.MAX_VALUE) {
            @Override
            boolean tryReserve(long bytes) {
                return false;
            }
        };
        DynamicBatchingQueueManager m = new DynamicBatchingQueueManager(
            new BatchableInputRegistry(),
            new BatchSplitter(),
            threadPool,
            exhaustedBudget
        );
        AtomicReference<Exception> error = new AtomicReference<>();

        boolean handled = m
            .enqueue(
                "model-1",
                queued(100, 10_000L),
                textInput("a"),
                model(new AtomicInteger()),
                null,
                ActionListener.wrap(r -> {}, error::set)
            );

        assertTrue("a rejected request is settled here, not handed back to the caller", handled);
        assertTrue(error.get() instanceof OpenSearchRejectedExecutionException);
        assertEquals("a rejected request must not leave an empty queue behind", 0, m.queueCount());
    }

    @Test
    public void idleRemovalCannotRemoveQueueDuringAdmission() throws Exception {
        CountDownLatch reserveEntered = new CountDownLatch(1);
        CountDownLatch allowReserve = new CountDownLatch(1);
        AtomicBoolean blockReservations = new AtomicBoolean(false);
        QueueMemoryBudget blockingBudget = new QueueMemoryBudget(Long.MAX_VALUE) {
            @Override
            boolean tryReserve(long bytes) {
                if (!blockReservations.get()) {
                    return super.tryReserve(bytes);
                }
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
        DynamicBatchingQueueManager m = new DynamicBatchingQueueManager(
            new BatchableInputRegistry(),
            new BatchSplitter(),
            threadPool,
            blockingBudget
        );
        BatchInferenceConfig config = queued(100, 10_000L);
        m.enqueue("model-1", config, textInput("seed"), model(new AtomicInteger()), null, ActionListener.wrap(r -> {}, e -> {}));

        blockReservations.set(true);
        AtomicReference<MLTaskResponse> response = new AtomicReference<>();
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        CountDownLatch evictionStarted = new CountDownLatch(1);
        CountDownLatch evictionDone = new CountDownLatch(1);

        Thread enqueueThread = new Thread(() -> {
            try {
                m
                    .enqueue(
                        "model-1",
                        config,
                        textInput("a"),
                        model(new AtomicInteger()),
                        null,
                        ActionListener.wrap(response::set, threadFailure::set)
                    );
            } catch (Throwable t) {
                threadFailure.set(t);
            }
        });
        Thread evictionThread = new Thread(() -> {
            evictionStarted.countDown();
            try {
                m.removeIfIdleForTest("model-1");
            } catch (Throwable t) {
                threadFailure.set(t);
            } finally {
                evictionDone.countDown();
            }
        });

        enqueueThread.start();
        assertTrue(reserveEntered.await(5, TimeUnit.SECONDS));
        evictionThread.start();
        assertTrue(evictionStarted.await(5, TimeUnit.SECONDS));
        assertFalse("removal must serialize with admission for the same model", evictionDone.await(100, TimeUnit.MILLISECONDS));

        allowReserve.countDown();
        enqueueThread.join(5_000L);
        evictionThread.join(5_000L);

        assertFalse(enqueueThread.isAlive());
        assertFalse(evictionThread.isAlive());
        assertNull(threadFailure.get());
        assertEquals("the admitted non-idle queue remains owned by the manager", 1, m.queueCount());

        scheduledFlush.get().run();
        assertEquals(ImmutableList.of("a"), resultNames(response.get()));
        assertEquals(0L, blockingBudget.getReservedBytes());
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
}
