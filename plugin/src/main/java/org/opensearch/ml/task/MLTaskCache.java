/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import java.util.concurrent.Semaphore;

import lombok.Builder;
import lombok.Getter;

import org.opensearch.ml.common.MLTask;

@Getter
public class MLTaskCache {
    MLTask mlTask;
    Semaphore updateTaskIndexSemaphore;

    @Builder
    public MLTaskCache(MLTask mlTask) {
        this.mlTask = mlTask;
        if (mlTask.isAsync()) {
            updateTaskIndexSemaphore = new Semaphore(1);
        }
    }
}
