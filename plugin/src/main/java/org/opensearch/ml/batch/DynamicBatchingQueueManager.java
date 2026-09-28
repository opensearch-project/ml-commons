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
 * Owns the per-model DynamicBatchingQueues and routes predict requests into them. A queue is created lazily on
 * the first request for a queue-enabled model and replaced when that model's queue-relevant config changes;
 * the old queue is flushed after replacement so its callers are not stranded. Admission and empty-queue
 * removal both run through the map's per-key compute operation, so a queue cannot be removed between lookup
 * and enqueue. A queue is removed as soon as it drains empty, and the next request recreates one — creating a
 * queue is cheap, so the map does not accumulate idle queues for transient model IDs. Requests for models
 * with no config or a disabled queue never reach here.
 */
@Log4j2
public class DynamicBatchingQueueManager {

    private final BatchableInputRegistry registry;
    private final BatchSplitter splitter;
    private final ThreadPool threadPool;
    private final QueueMemoryBudget budget;
    private final ConcurrentHashMap<String, DynamicBatchingQueue> queues = new ConcurrentHashMap<>();

    public DynamicBatchingQueueManager(
        BatchableInputRegistry registry,
        BatchSplitter splitter,
        ThreadPool threadPool,
        QueueMemoryBudget budget
    ) {
        this.registry = registry;
        this.splitter = splitter;
        this.threadPool = threadPool;
        this.budget = budget;
    }

    public boolean shouldQueue(BatchInferenceConfig config) {
        return config != null && config.isDynamicBatchingEnabled();
    }

    /**
     * Returns false when the request was not queued and no listener callback was made, so the caller must run it
     * itself. That happens for a request too large for the whole node budget, which could never be admitted.
     */
    public boolean enqueue(
        String modelId,
        BatchInferenceConfig config,
        MLInput input,
        Predictable predictor,
        TransportChannel channel,
        ActionListener<MLTaskResponse> listener
    ) {
        QueueEntry entry;
        try {
            entry = toEntry(modelId, input, listener, predictor, channel);
        } catch (Exception e) {
            notifyFailure(listener, e);
            return true;
        }

        DynamicBatchingQueue[] replaced = new DynamicBatchingQueue[1];
        DynamicBatchingQueue[] target = new DynamicBatchingQueue[1];
        DynamicBatchingQueue.EnqueueDecision[] decision = new DynamicBatchingQueue.EnqueueDecision[1];
        queues.compute(modelId, (id, existing) -> {
            DynamicBatchingQueue queue = existing;
            if (queue == null || !queue.getConfig().equals(config)) {
                replaced[0] = queue; // null on first create; the previous queue when config changed
                queue = new DynamicBatchingQueue(id, config, registry, splitter, threadPool, budget, this::removeIfIdle);
            }
            target[0] = queue;
            decision[0] = queue.offer(entry);
            return queue;
        });

        // Model calls, timer scheduling and listener callbacks must not run under the map's bin lock.
        if (replaced[0] != null) {
            replaced[0].flush();
        }
        target[0].completeEnqueue(entry, decision[0]);
        return decision[0] != DynamicBatchingQueue.EnqueueDecision.TOO_LARGE;
    }

    private QueueEntry toEntry(
        String modelId,
        MLInput input,
        ActionListener<MLTaskResponse> listener,
        Predictable predictor,
        TransportChannel channel
    ) {
        BatchableInput handler = registry.get(input);
        if (handler == null) {
            throw unsupportedInputType(input);
        }
        List<BatchItem> items = handler.toItems(input);
        String groupKey;
        try {
            groupKey = handler.groupKey(input);
        } catch (Exception e) {
            // A null key makes the request fail at flush rather than risk coalescing it with requests it may not
            // match, so the request is not lost — but the reason it happened only exists in this exception.
            log.warn("Failed to compute a batch group key for a predict request to model {}", modelId, e);
            groupKey = null;
        }
        return new QueueEntry(input, listener, predictor, channel, items, groupKey);
    }

    private IllegalArgumentException unsupportedInputType(MLInput input) {
        Object type = input == null || input.getInputDataset() == null ? "null" : input.getInputDataset().getInputDataType();
        return new IllegalArgumentException(
            "This model has batch_inference_config set, so its predict requests must be splittable, but input type "
                + type
                + " does not support batch inference. Send a supported input type, or remove "
                + "batch_inference_config from the model to run requests unsplit."
        );
    }

    private void notifyFailure(ActionListener<MLTaskResponse> listener, Exception failure) {
        try {
            listener.onFailure(failure);
        } catch (Exception e) {
            log.error("Batch queue listener threw while handling a request validation failure", e);
        }
    }

    /**
     * Removes a queue once it has drained empty. Runs under the map's per-key compute lock, which the enqueue
     * path also holds, so a request that arrives first keeps the queue and a request that arrives after removal
     * simply recreates one. The identity check leaves a replacement queue (installed on a config change) in place.
     */
    private void removeIfIdle(DynamicBatchingQueue queue) {
        queues.computeIfPresent(queue.getModelId(), (id, existing) -> existing == queue && existing.isIdle() ? null : existing);
    }

    // Test seam: number of live per-model queues.
    int queueCount() {
        return queues.size();
    }

    // Test seam: runs the same idle-removal check the flush path performs, for the given model.
    void removeIfIdleForTest(String modelId) {
        DynamicBatchingQueue queue = queues.get(modelId);
        if (queue != null) {
            removeIfIdle(queue);
        }
    }
}
