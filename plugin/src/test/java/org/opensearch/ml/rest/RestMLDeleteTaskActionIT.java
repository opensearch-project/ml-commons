/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.MLTaskState;

@Ignore
public class RestMLDeleteTaskActionIT extends MLCommonsRestTestCase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    public void testDeleteTaskAPI_Success() throws IOException, InterruptedException {
        trainAsyncWithSample(trainResult -> {
            assertFalse(trainResult.containsKey("model_id"));
            String taskId = (String) trainResult.get("task_id");
            assertNotNull(taskId);
            String status = (String) trainResult.get("status");
            assertEquals(MLTaskState.CREATED.name(), status);
            try {
                deleteTask(client(), taskId, deleteModelResponse -> {
                    String deleted = (String) deleteModelResponse.get("result");
                    assertEquals(deleted, "deleted");
                });
            } catch (IOException e) {
                assertNull(e);
            }
        }, true);
    }
}
