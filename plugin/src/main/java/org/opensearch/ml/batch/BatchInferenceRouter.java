/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_IDLE_TTL;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_CEILING;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_FLOOR;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_BATCH_QUEUE_MEMORY_FRACTION;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.lifecycle.LifecycleListener;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
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
    private final ModelBatchQueueManager queueManager;

    private volatile double memoryFraction;
    private volatile long memoryFloorBytes;
    private volatile long memoryCeilingBytes;
    private volatile long idleTtlNanos;

    public BatchInferenceRouter(ThreadPool threadPool, ClusterService clusterService, Settings settings) {
        BatchableInputRegistry registry = new BatchableInputRegistry();
        BatchSplitter splitter = new BatchSplitter();
        this.executor = new BatchInferenceExecutor(registry, splitter);

        this.memoryFraction = ML_COMMONS_BATCH_QUEUE_MEMORY_FRACTION.get(settings);
        this.memoryFloorBytes = ML_COMMONS_BATCH_QUEUE_MEMORY_FLOOR.get(settings).getBytes();
        this.memoryCeilingBytes = ML_COMMONS_BATCH_QUEUE_MEMORY_CEILING.get(settings).getBytes();
        this.idleTtlNanos = ML_COMMONS_BATCH_QUEUE_IDLE_TTL.get(settings).nanos();

        QueueMemoryBudget budget = new QueueMemoryBudget(clampBudget(memoryFraction, memoryFloorBytes, memoryCeilingBytes));

        ClusterSettings clusterSettings = clusterService.getClusterSettings();
        clusterSettings.addSettingsUpdateConsumer(ML_COMMONS_BATCH_QUEUE_MEMORY_FRACTION, value -> {
            memoryFraction = value;
            budget.setMaxBytes(clampBudget(memoryFraction, memoryFloorBytes, memoryCeilingBytes));
        });
        clusterSettings.addSettingsUpdateConsumer(ML_COMMONS_BATCH_QUEUE_MEMORY_FLOOR, value -> {
            memoryFloorBytes = value.getBytes();
            budget.setMaxBytes(clampBudget(memoryFraction, memoryFloorBytes, memoryCeilingBytes));
        });
        clusterSettings.addSettingsUpdateConsumer(ML_COMMONS_BATCH_QUEUE_MEMORY_CEILING, value -> {
            memoryCeilingBytes = value.getBytes();
            budget.setMaxBytes(clampBudget(memoryFraction, memoryFloorBytes, memoryCeilingBytes));
        });
        clusterSettings.addSettingsUpdateConsumer(ML_COMMONS_BATCH_QUEUE_IDLE_TTL, value -> idleTtlNanos = value.nanos());

        this.queueManager = new ModelBatchQueueManager(registry, splitter, threadPool, budget, () -> idleTtlNanos);

        // Cancel the manager's idle-eviction sweep on node shutdown so the recurring task does not outlive the node.
        clusterService.addLifecycleListener(new LifecycleListener() {
            @Override
            public void beforeStop() {
                queueManager.close();
            }
        });
    }

    BatchInferenceRouter(BatchInferenceExecutor executor, ModelBatchQueueManager queueManager) {
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
        if (channel == null && queueManager.shouldQueue(config)) {
            queueManager.enqueue(modelId, config, input, predictor, channel, listener);
        } else {
            executor.execute(input, config, predictor, channel, listener);
        }
    }

    private static long clampBudget(double fraction, long floorBytes, long ceilingBytes) {
        long heapMax = JvmInfo.jvmInfo().getMem().getHeapMax().getBytes();
        long scaled = (long) (heapMax * fraction);
        return Math.min(Math.max(scaled, floorBytes), ceilingBytes);
    }
}
