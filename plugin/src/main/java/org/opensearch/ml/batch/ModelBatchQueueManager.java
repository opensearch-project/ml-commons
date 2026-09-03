/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.threadpool.Scheduler;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportChannel;

import lombok.extern.log4j.Log4j2;

/**
 * Owns the per-model ModelBatchQueues and routes predict requests into them. A queue is created lazily on
 * the first request for a queue-enabled model and replaced when that model's queue-relevant config changes;
 * the old queue is flushed after replacement so its callers are not stranded. Admission and idle eviction
 * both run through the map's per-key compute operation, so a queue cannot be removed between lookup and
 * enqueue. Idle queues are evicted by a periodic sweep so the map does not grow unbounded with transient
 * model IDs. Requests for models with no config or a disabled queue never reach here.
 */
@Log4j2
public class ModelBatchQueueManager {

    private static final TimeValue SWEEP_INTERVAL = TimeValue.timeValueMinutes(1);

    private final BatchableInputRegistry registry;
    private final BatchSplitter splitter;
    private final ThreadPool threadPool;
    private final QueueMemoryBudget budget;
    private final LongSupplier idleTtlNanos;
    private final ConcurrentHashMap<String, ModelBatchQueue> queues = new ConcurrentHashMap<>();
    private final Scheduler.Cancellable idleSweep;

    public ModelBatchQueueManager(
        BatchableInputRegistry registry,
        BatchSplitter splitter,
        ThreadPool threadPool,
        QueueMemoryBudget budget,
        LongSupplier idleTtlNanos
    ) {
        this.registry = registry;
        this.splitter = splitter;
        this.threadPool = threadPool;
        this.budget = budget;
        this.idleTtlNanos = idleTtlNanos;
        this.idleSweep = threadPool.scheduleWithFixedDelay(this::evictIdleQueues, SWEEP_INTERVAL, ThreadPool.Names.GENERIC);
    }

    /** Stops the idle-eviction sweep. Wired to node shutdown so the recurring task does not outlive the manager. */
    public void close() {
        if (idleSweep != null) {
            idleSweep.cancel();
        }
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
        QueueEntry entry;
        try {
            entry = toEntry(input, listener, predictor, channel);
        } catch (Exception e) {
            notifyFailure(listener, e);
            return;
        }

        ModelBatchQueue[] replaced = new ModelBatchQueue[1];
        ModelBatchQueue[] target = new ModelBatchQueue[1];
        ModelBatchQueue.EnqueueDecision[] decision = new ModelBatchQueue.EnqueueDecision[1];
        queues.compute(modelId, (id, existing) -> {
            ModelBatchQueue queue = existing;
            if (queue == null || !queue.getConfig().equals(config)) {
                replaced[0] = queue; // null on first create; the previous queue when config changed
                queue = new ModelBatchQueue(id, config, registry, splitter, threadPool, budget);
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
    }

    private QueueEntry toEntry(MLInput input, ActionListener<MLTaskResponse> listener, Predictable predictor, TransportChannel channel) {
        BatchableInput handler = registry.get(input);
        if (handler == null) {
            throw unsupportedInputType(input);
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

    void evictIdleQueues() {
        long ttl = idleTtlNanos.getAsLong();
        long now = System.nanoTime();
        for (String modelId : queues.keySet()) {
            queues.computeIfPresent(modelId, (id, queue) -> now - queue.getLastUsedNanos() > ttl && queue.isIdle() ? null : queue);
        }
    }

    // Test seam: number of live per-model queues.
    int queueCount() {
        return queues.size();
    }
}
