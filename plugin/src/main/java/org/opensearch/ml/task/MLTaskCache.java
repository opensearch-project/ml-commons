/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.task;

import java.util.concurrent.Semaphore;

import lombok.Builder;
import lombok.Getter;

import org.opensearch.ml.model.MLTask;

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
