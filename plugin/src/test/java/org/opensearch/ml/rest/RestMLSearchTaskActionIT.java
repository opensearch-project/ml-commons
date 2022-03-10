/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLTaskState;

public class RestMLSearchTaskActionIT extends MLCommonsRestTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    public void testSearchTaskAPI_Success() throws IOException, InterruptedException {
        trainAsyncWithSample(trainResult -> {
            assertFalse(trainResult.containsKey("model_id"));
            String taskId = (String) trainResult.get("task_id");
            assertNotNull(taskId);
            String status = (String) trainResult.get("status");
            assertEquals(MLTaskState.CREATED.name(), status);
            try {
                searchTasksWithAlgoName(client(), FunctionName.SAMPLE_ALGO.name(), searchResponse -> {
                    ArrayList<Object> hits = (ArrayList) ((Map<String, Object>) searchResponse.get("hits")).get("hits");
                    assertTrue(hits.size() > 0);
                });
            } catch (IOException e) {
                assertNull(e);
            }
        }, true);
    }
}
