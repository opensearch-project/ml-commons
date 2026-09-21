/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MAX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MIN;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.unit.ByteSizeUnit;
import org.opensearch.core.common.unit.ByteSizeValue;
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
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportChannel;

public class BatchInferenceRouterTests {

    private BatchInferenceExecutor executor;
    private ModelBatchQueueManager queueManager;
    private BatchInferenceRouter router;

    private final MLInput input = mock(MLInput.class);
    private final Predictable predictor = mock(Predictable.class);
    @SuppressWarnings("unchecked")
    private final ActionListener<MLTaskResponse> listener = mock(ActionListener.class);

    @Before
    public void setUp() {
        executor = mock(BatchInferenceExecutor.class);
        queueManager = mock(ModelBatchQueueManager.class);
        router = new BatchInferenceRouter(executor, queueManager);
    }

    private BatchInferenceConfig queued() {
        return BatchInferenceConfig
            .builder()
            .maxItemsPerRequest(96)
            .dynamicBatching(DynamicBatchingConfig.builder().enabled(true).flushTimeoutMs(10L).build())
            .build();
    }

    @Test
    public void nonStreamingWithQueueEnabledGoesToTheQueue() {
        BatchInferenceConfig config = queued();
        when(queueManager.shouldQueue(config)).thenReturn(true);
        when(queueManager.enqueue("m", config, input, predictor, null, listener)).thenReturn(true);

        router.route("m", input, config, predictor, null, listener);

        verify(queueManager).enqueue("m", config, input, predictor, null, listener);
        verify(executor, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void requestTheQueueCannotAdmitFallsBackToTheSplitter() {
        // The queue declines a request larger than the whole node budget. Running it through the splitter still
        // honours the model's size limits, where a "retry after backoff" rejection could never succeed.
        BatchInferenceConfig config = queued();
        when(queueManager.shouldQueue(config)).thenReturn(true);
        when(queueManager.enqueue("m", config, input, predictor, null, listener)).thenReturn(false);

        router.route("m", input, config, predictor, null, listener);

        verify(executor).execute("m", input, config, predictor, null, listener);
    }

    @Test
    public void requestTooLargeForTheQueueIsStillSplitByTheModelsSizeLimits() {
        // The mocked test above proves the router delegates; this one wires a real queue manager and a real executor
        // to prove what the delegation is actually worth — an over-budget request is not just "run unqueued", it is
        // still broken into sub-batches honouring max_items_per_request.
        BatchableInputRegistry registry = new BatchableInputRegistry();
        BatchSplitter splitter = new BatchSplitter();
        ThreadPool threadPool = mock(ThreadPool.class);
        BatchInferenceRouter realRouter = new BatchInferenceRouter(
            new BatchInferenceExecutor(registry, splitter),
            new ModelBatchQueueManager(registry, splitter, threadPool, new QueueMemoryBudget(1L))
        );
        BatchInferenceConfig config = BatchInferenceConfig
            .builder()
            .maxItemsPerRequest(2)
            .dynamicBatching(DynamicBatchingConfig.builder().enabled(true).flushTimeoutMs(10L).build())
            .build();
        MLInput fiveDocs = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .inputDataset(TextDocsInputDataSet.builder().docs(List.of("a", "b", "c", "d", "e")).build())
            .build();

        List<Integer> subBatchSizes = new ArrayList<>();
        AtomicReference<MLTaskResponse> result = new AtomicReference<>();
        realRouter.route("m", fiveDocs, config, echoModel(subBatchSizes), null, ActionListener.wrap(result::set, e -> {
            throw new AssertionError(e);
        }));

        // A 1-byte budget cannot admit anything, so the queue declines and the splitter runs it: 2 + 2 + 1.
        assertEquals(List.of(2, 2, 1), subBatchSizes);
        List<String> names = new ArrayList<>();
        for (ModelTensors group : ((ModelTensorOutput) result.get().getOutput()).getMlModelOutputs()) {
            group.getMlModelTensors().forEach(t -> names.add(t.getName()));
        }
        assertEquals("every caller result still comes back, in order", List.of("a", "b", "c", "d", "e"), names);
    }

    /** Records each sub-batch's size and echoes one tensor per doc. */
    private static Predictable echoModel(List<Integer> subBatchSizes) {
        return new Predictable() {
            @Override
            public MLOutput predict(MLInput mlInput, MLModel model) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> listener, TransportChannel channel) {
                List<String> docs = ((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs();
                subBatchSizes.add(docs.size());
                List<ModelTensor> tensors = new ArrayList<>();
                for (String doc : docs) {
                    tensors.add(ModelTensor.builder().name(doc).build());
                }
                listener
                    .onResponse(
                        new MLTaskResponse(
                            ModelTensorOutput
                                .builder()
                                .mlModelOutputs(List.of(ModelTensors.builder().mlModelTensors(tensors).build()))
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

    @Test
    public void memoryCeilingBelowFloorIsRejected() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> BatchInferenceRouter.validateMemoryBounds(new ByteSizeValue(64L, ByteSizeUnit.MB), new ByteSizeValue(1L, ByteSizeUnit.MB))
        );
        assertTrue(e.getMessage().contains(ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MAX.getKey()));
        assertTrue(e.getMessage().contains(ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MIN.getKey()));
    }

    @Test
    public void memoryCeilingEqualToOrAboveFloorIsAccepted() {
        ByteSizeValue floor = new ByteSizeValue(64L, ByteSizeUnit.MB);
        BatchInferenceRouter.validateMemoryBounds(floor, floor);
        BatchInferenceRouter.validateMemoryBounds(floor, new ByteSizeValue(512L, ByteSizeUnit.MB));
    }

    @Test
    public void streamingBypassesTheQueueEvenWhenEnabled() {
        BatchInferenceConfig config = queued();
        TransportChannel channel = mock(TransportChannel.class);
        // shouldQueue is not even consulted once a streaming channel is present.

        router.route("m", input, config, predictor, channel, listener);

        verify(executor).execute("m", input, config, predictor, channel, listener);
        verify(queueManager, never()).enqueue(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void withoutQueueGoesToTheSizeBasedSplitter() {
        when(queueManager.shouldQueue(eq(null))).thenReturn(false);

        router.route("m", input, null, predictor, null, listener);

        verify(executor).execute("m", input, null, predictor, null, listener);
        verify(queueManager, never()).enqueue(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void productionConstructorWiresSettingsUpdates() {
        ThreadPool threadPool = mock(ThreadPool.class);
        ClusterService clusterService = mock(ClusterService.class);
        Settings settings = Settings.EMPTY;
        ClusterSettings clusterSettings = new ClusterSettings(
            settings,
            new HashSet<>(
                Arrays
                    .asList(
                        ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE,
                        ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MIN,
                        ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MAX
                    )
            )
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);

        new BatchInferenceRouter(threadPool, clusterService, settings);

        // Applying valid memory settings exercises the registered update consumers; a wiring or validation
        // mistake would throw here.
        clusterSettings
            .applySettings(
                Settings
                    .builder()
                    .put(ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE.getKey(), 0.02)
                    .put(ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MIN.getKey(), "128mb")
                    .put(ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MAX.getKey(), "256mb")
                    .build()
            );
    }

    @Test
    public void clusterUpdateWithCeilingBelowFloorIsRejected() {
        ClusterSettings clusterSettings = batchQueueClusterSettings();
        newProductionRouter(clusterSettings);

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> clusterSettings
                .applySettings(
                    Settings
                        .builder()
                        .put(ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MIN.getKey(), "128mb")
                        .put(ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MAX.getKey(), "1mb")
                        .build()
                )
        );
        assertTrue(e.getMessage().contains(ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MAX.getKey()));
    }

    @Test
    public void nodeStartsUpRejectingCeilingBelowFloorInSettings() {
        Settings bad = Settings
            .builder()
            .put(ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MIN.getKey(), "128mb")
            .put(ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MAX.getKey(), "1mb")
            .build();
        ThreadPool threadPool = mock(ThreadPool.class);
        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.getClusterSettings()).thenReturn(batchQueueClusterSettings());

        assertThrows(IllegalArgumentException.class, () -> new BatchInferenceRouter(threadPool, clusterService, bad));
    }

    private static ClusterSettings batchQueueClusterSettings() {
        return new ClusterSettings(
            Settings.EMPTY,
            new HashSet<>(
                Arrays
                    .asList(
                        ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE,
                        ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MIN,
                        ML_COMMONS_DYNAMIC_BATCHING_MEMORY_SIZE_MAX
                    )
            )
        );
    }

    private static BatchInferenceRouter newProductionRouter(ClusterSettings clusterSettings) {
        ThreadPool threadPool = mock(ThreadPool.class);
        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        return new BatchInferenceRouter(threadPool, clusterService, Settings.EMPTY);
    }
}
