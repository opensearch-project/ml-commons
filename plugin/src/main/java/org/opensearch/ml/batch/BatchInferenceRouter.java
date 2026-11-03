/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_FRACTION;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MAX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MIN;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.unit.ByteSizeValue;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.monitor.jvm.JvmInfo;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportChannel;

/**
 * Single entry point for server-side batch inference. It owns the two paths — the size-based splitter
 * and the cross-request queue — and picks between them so callers of the predict path do not have to.
 * The two paths share one input registry and one splitter.
 */
public class BatchInferenceRouter {

    private final BatchInferenceExecutor executor;
    private final DynamicBatchingQueueManager queueManager;

    private volatile double memoryFraction;
    private volatile long memoryFloorBytes;
    private volatile long memoryCeilingBytes;

    public BatchInferenceRouter(ThreadPool threadPool, ClusterService clusterService, Settings settings) {
        BatchableInputRegistry registry = new BatchableInputRegistry();
        BatchSplitter splitter = new BatchSplitter();
        this.executor = new BatchInferenceExecutor(registry, splitter);

        ByteSizeValue floor = ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MIN.get(settings);
        ByteSizeValue ceiling = ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MAX.get(settings);
        validateMemoryBounds(floor, ceiling);

        this.memoryFraction = ML_COMMONS_DYNAMIC_BATCHING_MEMORY_FRACTION.get(settings);
        this.memoryFloorBytes = floor.getBytes();
        this.memoryCeilingBytes = ceiling.getBytes();

        QueueMemoryBudget budget = new QueueMemoryBudget(clampBudget(memoryFraction, memoryFloorBytes, memoryCeilingBytes));

        ClusterSettings clusterSettings = clusterService.getClusterSettings();
        clusterSettings.addSettingsUpdateConsumer(ML_COMMONS_DYNAMIC_BATCHING_MEMORY_FRACTION, value -> {
            memoryFraction = value;
            budget.setMaxBytes(clampBudget(memoryFraction, memoryFloorBytes, memoryCeilingBytes));
        });
        // Updated as a pair so the ceiling can be checked against the floor it will clamp, and rejected at
        // validation time instead of silently clamping the budget to an unusable value.
        clusterSettings
            .addSettingsUpdateConsumer(
                ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MIN,
                ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MAX,
                (newFloor, newCeiling) -> {
                    memoryFloorBytes = newFloor.getBytes();
                    memoryCeilingBytes = newCeiling.getBytes();
                    budget.setMaxBytes(clampBudget(memoryFraction, memoryFloorBytes, memoryCeilingBytes));
                },
                BatchInferenceRouter::validateMemoryBounds
            );

        this.queueManager = new DynamicBatchingQueueManager(registry, splitter, threadPool, budget);
    }

    BatchInferenceRouter(BatchInferenceExecutor executor, DynamicBatchingQueueManager queueManager) {
        this.executor = executor;
        this.queueManager = queueManager;
    }

    public void route(
        String modelId,
        MLInput input,
        BatchInferenceConfig config,
        Predictable predictor,
        TransportChannel channel,
        ActionListener<MLTaskResponse> listener
    ) {
        // A request the queue cannot ever admit falls through to the splitter, which still honours the model's
        // size limits — running it unqueued is correct, where telling the caller to retry never would be. Such a
        // request is then outside the queue's memory budget, which is the intended scope: that budget bounds what
        // pending entries retain while they wait for a flush, and a request that is never queued retains nothing
        // against it. This is the same path every model without an enabled queue already takes.
        if (channel == null && queueManager.shouldQueue(config)) {
            if (queueManager.enqueue(modelId, config, input, predictor, channel, listener)) {
                return;
            }
        }
        executor.execute(modelId, input, config, predictor, channel, listener);
    }

    /**
     * A ceiling below the floor would clamp the queue's budget below the floor an operator asked for — at the
     * extreme to nothing at all, which rejects every queued predict request for the life of the node with a 429
     * that no backoff can clear. Rejected outright rather than clamped, so the operator sees the mistake.
     */
    static void validateMemoryBounds(ByteSizeValue floorBytes, ByteSizeValue ceilingBytes) {
        if (ceilingBytes.getBytes() < floorBytes.getBytes()) {
            throw new IllegalArgumentException(
                ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MAX.getKey()
                    + " ["
                    + ceilingBytes
                    + "] must be at least "
                    + ML_COMMONS_DYNAMIC_BATCHING_MEMORY_MIN.getKey()
                    + " ["
                    + floorBytes
                    + "]"
            );
        }
    }

    private static long clampBudget(double fraction, long floorBytes, long ceilingBytes) {
        long heapMax = JvmInfo.jvmInfo().getMem().getHeapMax().getBytes();
        long scaled = (long) (heapMax * fraction);
        return Math.min(Math.max(scaled, floorBytes), ceilingBytes);
    }
}
