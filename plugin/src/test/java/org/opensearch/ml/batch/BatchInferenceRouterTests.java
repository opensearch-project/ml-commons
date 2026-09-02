/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_IDLE_TTL;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_CEILING;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_FLOOR;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_FRACTION;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.lifecycle.LifecycleListener;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.model.BatchQueueConfig;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.threadpool.Scheduler;
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
            .queue(BatchQueueConfig.builder().enabled(true).flushTimeoutMs(10L).build())
            .build();
    }

    @Test
    public void nonStreamingWithQueueEnabledGoesToTheQueue() {
        BatchInferenceConfig config = queued();
        when(queueManager.shouldQueue(config)).thenReturn(true);

        router.route("m", input, config, predictor, null, listener);

        verify(queueManager).enqueue("m", config, input, predictor, null, listener);
        verify(executor, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    public void streamingBypassesTheQueueEvenWhenEnabled() {
        BatchInferenceConfig config = queued();
        TransportChannel channel = mock(TransportChannel.class);
        // shouldQueue is not even consulted once a streaming channel is present.

        router.route("m", input, config, predictor, channel, listener);

        verify(executor).execute(input, config, predictor, channel, listener);
        verify(queueManager, never()).enqueue(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void withoutQueueGoesToTheSizeBasedSplitter() {
        when(queueManager.shouldQueue(eq(null))).thenReturn(false);

        router.route("m", input, null, predictor, null, listener);

        verify(executor).execute(input, null, predictor, null, listener);
        verify(queueManager, never()).enqueue(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void productionConstructorWiresSettingsUpdatesAndShutdownHook() {
        ThreadPool threadPool = mock(ThreadPool.class);
        when(threadPool.scheduleWithFixedDelay(any(), any(), anyString())).thenReturn(mock(Scheduler.Cancellable.class));
        ClusterService clusterService = mock(ClusterService.class);
        Settings settings = Settings.EMPTY;
        ClusterSettings clusterSettings = new ClusterSettings(
            settings,
            new HashSet<>(
                Arrays
                    .asList(
                        ML_COMMONS_BATCH_QUEUE_MEMORY_FRACTION,
                        ML_COMMONS_BATCH_QUEUE_MEMORY_FLOOR,
                        ML_COMMONS_BATCH_QUEUE_MEMORY_CEILING,
                        ML_COMMONS_BATCH_QUEUE_IDLE_TTL
                    )
            )
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);

        new BatchInferenceRouter(threadPool, clusterService, settings);

        clusterSettings
            .applySettings(
                Settings
                    .builder()
                    .put(ML_COMMONS_BATCH_QUEUE_MEMORY_FRACTION.getKey(), 0.02)
                    .put(ML_COMMONS_BATCH_QUEUE_MEMORY_FLOOR.getKey(), "128mb")
                    .put(ML_COMMONS_BATCH_QUEUE_MEMORY_CEILING.getKey(), "256mb")
                    .put(ML_COMMONS_BATCH_QUEUE_IDLE_TTL.getKey(), "10m")
                    .build()
            );

        ArgumentCaptor<LifecycleListener> lifecycle = ArgumentCaptor.forClass(LifecycleListener.class);
        verify(clusterService).addLifecycleListener(lifecycle.capture());
        lifecycle.getValue().beforeStop();
    }
}
