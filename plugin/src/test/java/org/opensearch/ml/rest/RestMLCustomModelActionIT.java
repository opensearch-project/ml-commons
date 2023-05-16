/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.MLTask.FUNCTION_NAME_FIELD;
import static org.opensearch.ml.common.MLTask.MODEL_ID_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;

import java.io.IOException;
import java.util.Map;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.utils.TestHelper;

public class RestMLCustomModelActionIT extends MLCommonsRestTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private MLRegisterModelInput registerModelInput;

    @Before
    public void setup() {
        registerModelInput = createRegisterModelInput();
    }

    @Ignore
    public void testCustomModelWorkflow() throws IOException, InterruptedException {
        // register model
        String taskId = registerModel(TestHelper.toJsonString(registerModelInput));
        waitForTask(taskId, MLTaskState.COMPLETED);
        getTask(client(), taskId, response -> {
            String algorithm = (String) response.get(FUNCTION_NAME_FIELD);
            assertEquals(registerModelInput.getFunctionName().name(), algorithm);
            assertNotNull(response.get(MODEL_ID_FIELD));
            assertEquals(MLTaskState.COMPLETED.name(), response.get(STATE_FIELD));
            String modelId = (String) response.get(MODEL_ID_FIELD);
            try {
                // deploy model
                String deployTaskId = deployModel(modelId);
                waitForTask(deployTaskId, MLTaskState.COMPLETED);
                getTask(client(), deployTaskId, deployTaskResponse -> {
                    assertEquals(modelId, deployTaskResponse.get(MODEL_ID_FIELD));
                    assertEquals(MLTaskState.COMPLETED.name(), response.get(STATE_FIELD));
                });
                Thread.sleep(300);
                // profile
                getModelProfile(modelId, verifyTextEmbeddingModelDeployed());
                // predict
                predictTextEmbedding(modelId);
                // undeploy model
                Map<String, Object> result = undeployModel(modelId);
                for (Map.Entry<String, Object> entry : result.entrySet()) {
                    Map stats = (Map) ((Map) entry.getValue()).get("stats");
                    assertEquals("undeployed", stats.get(modelId));
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
