/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.Predictable;
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

    public BatchInferenceRouter(ThreadPool threadPool) {
        BatchableInputRegistry registry = new BatchableInputRegistry();
        BatchSplitter splitter = new BatchSplitter();
        this.executor = new BatchInferenceExecutor(registry, splitter);
        this.queueManager = new ModelBatchQueueManager(registry, splitter, threadPool);
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
}
