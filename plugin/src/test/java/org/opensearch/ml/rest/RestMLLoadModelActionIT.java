/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.MLTask.FUNCTION_NAME_FIELD;
import static org.opensearch.ml.common.MLTask.MODEL_ID_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTask.TASK_ID_FIELD;

import java.io.IOException;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.upload.MLUploadInput;
import org.opensearch.ml.utils.TestHelper;

public class RestMLLoadModelActionIT extends MLCommonsRestTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private MLUploadInput uploadInput;

    @Before
    public void setup() {
        uploadInput = createUploadModelInput();
    }

    public void testLoadModelAPI_NoIndex() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no such index [.plugins-ml-model]");
        loadModel("wrong_model_id");
    }

    public void testLoadModelAPI_Success() throws IOException, InterruptedException {
        String uploadTaskId = uploadModel();
        waitForTask(uploadTaskId, MLTaskState.COMPLETED);

        getTask(client(), uploadTaskId, response -> {
            String algorithm = (String) response.get(FUNCTION_NAME_FIELD);
            assertEquals(uploadInput.getFunctionName().name(), algorithm);
            assertNotNull(response.get(MODEL_ID_FIELD));
            assertEquals(MLTaskState.COMPLETED.name(), response.get(STATE_FIELD));
            String modelId = (String) response.get(MODEL_ID_FIELD);
            try {
                String loadTaskId = loadModel(modelId);
                waitForTask(loadTaskId, MLTaskState.COMPLETED);
                getTask(client(), loadTaskId, loadTaskResponse -> {
                    assertEquals(modelId, loadTaskResponse.get(MODEL_ID_FIELD));
                    assertEquals(MLTaskState.COMPLETED.name(), response.get(STATE_FIELD));
                });
                getModelProfile(modelId, verifyTextEmbeddingModelLoaded());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private String uploadModel() throws IOException {
        String input = TestHelper.toJsonString(uploadInput);
        Response uploadResponse = TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/models/_upload", null, input, null);
        HttpEntity entity = uploadResponse.getEntity();
        assertNotNull(uploadResponse);
        String entityString = TestHelper.httpEntityToString(entity);
        Map map = gson.fromJson(entityString, Map.class);
        String taskId = (String) map.get(TASK_ID_FIELD);
        return taskId;
    }
}
