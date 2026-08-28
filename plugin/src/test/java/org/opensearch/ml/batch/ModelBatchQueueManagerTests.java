/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Before
    public void setUp() {
        threadPool = mock(ThreadPool.class);
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString()))
            .thenReturn(mock(Scheduler.ScheduledCancellable.class));
        manager = new ModelBatchQueueManager(new BatchableInputRegistry(), new BatchSplitter(), threadPool);
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
    public void changingConfigReplacesQueueForModel() {
        AtomicInteger calls = new AtomicInteger();
        Predictable predictor = model(calls);

        // First request under a high limit sits in the queue (timer only, not flushed here).
        manager.enqueue("model-1", queued(100, 10_000L), textInput("a"), predictor, null, ActionListener.wrap(r -> {}, e -> {}));
        assertEquals(0, calls.get());

        // A new config with limit 1 replaces the queue; this request flushes on its own immediately.
        manager.enqueue("model-1", queued(1, 10_000L), textInput("b"), predictor, null, ActionListener.wrap(r -> {}, e -> {}));
        assertEquals("replaced queue flushes the new request without pulling in the stranded one", 1, calls.get());
    }
}
