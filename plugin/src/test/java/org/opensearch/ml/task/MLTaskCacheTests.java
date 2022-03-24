/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import org.opensearch.ml.common.MLTask;
import org.opensearch.test.OpenSearchTestCase;

public class MLTaskCacheTests extends OpenSearchTestCase {

    public void testConstructor() {
        MLTaskCache mlTaskCache;
        MLTask mlTask_sync = MLTask.builder().taskId("test id").async(false).build();
        mlTaskCache = MLTaskCache.builder().mlTask(mlTask_sync).build();
        assertNotNull(mlTaskCache);
        assertNull(mlTaskCache.updateTaskIndexSemaphore);

        MLTask mlTask_async = MLTask.builder().taskId("test id").async(true).build();
        mlTaskCache = MLTaskCache.builder().mlTask(mlTask_async).build();
        assertNotNull(mlTaskCache);
        assertNotNull(mlTaskCache.updateTaskIndexSemaphore);
    }
}
