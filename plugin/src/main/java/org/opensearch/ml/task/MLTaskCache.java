/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import lombok.Builder;
import lombok.Getter;

import org.opensearch.ml.common.MLTask;

@Getter
public class MLTaskCache {
    MLTask mlTask;
    Semaphore updateTaskIndexSemaphore;
    // List of worker nodes.
    // For example when load model on ML nodes, these ML nodes are worker nodes. When model
    // loaded/failed on some node, the node will be removed from worker nodes.
    Set<String> workerNodes;
    Map<String, String> errors;
    // This is the original worker node count. It may not equal to size of workerNodes as
    // worker node may be removed later.
    Integer workerNodeSize;

    @Builder
    public MLTaskCache(MLTask mlTask, List<String> workerNodes) {
        this.mlTask = mlTask;
        if (mlTask.isAsync()) {
            updateTaskIndexSemaphore = new Semaphore(1);
        }
        this.workerNodes = ConcurrentHashMap.newKeySet();
        if (workerNodes != null) {
            this.workerNodes.addAll(workerNodes);
            workerNodeSize = workerNodes.size();
        }
        this.errors = new ConcurrentHashMap<>();
    }

    public MLTaskCache(MLTask mlTask) {
        this(mlTask, null);
    }

    public void addError(String nodeId, String error) {
        this.errors.put(nodeId, error);
    }

    public boolean hasError() {
        return errors.size() > 0;
    }

    public boolean allNodeFailed() {
        return workerNodeSize != null && errors.size() == workerNodeSize;
    }
}
