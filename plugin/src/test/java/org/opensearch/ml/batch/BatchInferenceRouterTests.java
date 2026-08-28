/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.model.BatchQueueConfig;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.Predictable;
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
}
