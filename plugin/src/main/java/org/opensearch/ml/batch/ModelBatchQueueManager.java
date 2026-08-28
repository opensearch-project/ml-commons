/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportChannel;

import lombok.extern.log4j.Log4j2;

/**
 * Owns the per-model ModelBatchQueues and routes predict requests into them. A queue is created lazily on
 * the first request for a queue-enabled model and replaced when that model's queue-relevant config changes;
 * the old queue keeps its timer so any entries left in it still drain. Requests for models with no config
 * or a disabled queue never reach here.
 */
@Log4j2
public class ModelBatchQueueManager {

    private final BatchableInputRegistry registry;
    private final BatchSplitter splitter;
    private final ThreadPool threadPool;
    private final ConcurrentHashMap<String, ModelBatchQueue> queues = new ConcurrentHashMap<>();

    public ModelBatchQueueManager(BatchableInputRegistry registry, BatchSplitter splitter, ThreadPool threadPool) {
        this.registry = registry;
        this.splitter = splitter;
        this.threadPool = threadPool;
    }

    public boolean shouldQueue(BatchInferenceConfig config) {
        return config != null && config.isQueueEnabled();
    }

    public void enqueue(
        String modelId,
        BatchInferenceConfig config,
        MLInput input,
        Predictable predictor,
        TransportChannel channel,
        ActionListener<MLTaskResponse> listener
    ) {
        queueFor(modelId, config).enqueue(toEntry(input, listener, predictor, channel));
    }

    private ModelBatchQueue queueFor(String modelId, BatchInferenceConfig config) {
        return queues
            .compute(
                modelId,
                (id, existing) -> existing != null && sameQueueConfig(existing.getConfig(), config)
                    ? existing
                    : new ModelBatchQueue(id, config, registry, splitter, threadPool)
            );
    }

    private QueueEntry toEntry(MLInput input, ActionListener<MLTaskResponse> listener, Predictable predictor, TransportChannel channel) {
        BatchableInput handler = registry.get(input);
        if (handler == null) {
            return new QueueEntry(input, listener, predictor, channel, null, null);
        }
        List<BatchItem> items = handler.toItems(input);
        String groupKey;
        try {
            groupKey = handler.groupKey(input);
        } catch (Exception e) {
            groupKey = null;
        }
        return new QueueEntry(input, listener, predictor, channel, items, groupKey);
    }

    private boolean sameQueueConfig(BatchInferenceConfig existing, BatchInferenceConfig incoming) {
        return existing.getMaxItemsPerRequest() == incoming.getMaxItemsPerRequest()
            && existing.getMaxBytesPerRequest() == incoming.getMaxBytesPerRequest()
            && existing.getQueue().getFlushTimeoutMs() == incoming.getQueue().getFlushTimeoutMs()
            && existing.getQueue().isEnabled() == incoming.getQueue().isEnabled();
    }
}
