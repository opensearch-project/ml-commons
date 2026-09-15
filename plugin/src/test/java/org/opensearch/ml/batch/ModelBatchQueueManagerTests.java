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
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.model.BatchQueueConfig;
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

public class ModelBatchQueueManagerTests {

    private ModelBatchQueueManager manager;
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
        manager = new ModelBatchQueueManager(
            new BatchableInputRegistry(),
            new BatchSplitter(),
            threadPool,
            new QueueMemoryBudget(Long.MAX_VALUE),
            () -> Long.MAX_VALUE
        );
    }

    private ModelBatchQueueManager managerWithIdleTtlNanos(long ttlNanos) {
        return new ModelBatchQueueManager(
            new BatchableInputRegistry(),
            new BatchSplitter(),
            threadPool,
            new QueueMemoryBudget(Long.MAX_VALUE),
            () -> ttlNanos
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
        return MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(dataSet).build();
    }

    private BatchInferenceConfig queued(int maxItems, long flushMs) {
        return BatchInferenceConfig
            .builder()
            .maxItemsPerRequest(maxItems)
            .queue(BatchQueueConfig.builder().enabled(true).flushTimeoutMs(flushMs).build())
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
    public void idleSweepEvictsDrainedQueuesButKeepsBusyOnes() {
        ModelBatchQueueManager m = managerWithIdleTtlNanos(0L);
        Predictable predictor = model(new AtomicInteger());

        m.enqueue("model-1", queued(100, 10_000L), textInput("a"), predictor, null, ActionListener.wrap(r -> {}, e -> {}));
        assertEquals(1, m.queueCount());

        m.evictIdleQueues();
        assertEquals("a queue with a pending entry is not evicted", 1, m.queueCount());

        scheduledFlush.get().run();
        assertEquals("flushing does not by itself remove the queue", 1, m.queueCount());

        m.evictIdleQueues();
        assertEquals("an idle queue past its TTL is evicted", 0, m.queueCount());
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

        manager.enqueue("model-1", queued(100, 10_000L), unsupported, model(calls), null, ActionListener.wrap(r -> {}, error::set));

        assertEquals(0, calls.get());
        assertTrue(error.get() instanceof IllegalArgumentException);
        assertEquals("an invalid request should not create a retained per-model queue", 0, manager.queueCount());
    }

    @Test
    public void idleEvictionCannotRemoveQueueDuringAdmission() throws Exception {
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
        ModelBatchQueueManager m = new ModelBatchQueueManager(
            new BatchableInputRegistry(),
            new BatchSplitter(),
            threadPool,
            blockingBudget,
            () -> 0L
        );
        BatchInferenceConfig config = queued(100, 10_000L);
        m.enqueue("model-1", config, textInput("seed"), model(new AtomicInteger()), null, ActionListener.wrap(r -> {}, e -> {}));
        scheduledFlush.get().run();
        assertEquals("the existing queue is idle but still present before the sweep", 1, m.queueCount());

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
                m.evictIdleQueues();
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
        assertFalse("eviction must serialize with admission for the same model", evictionDone.await(100, TimeUnit.MILLISECONDS));

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
