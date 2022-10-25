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
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.upload.MLUploadInput;
import org.opensearch.ml.utils.TestHelper;

public class RestMLUnloadModelActionIT extends MLCommonsRestTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private MLUploadInput uploadInput;

    @Before
    public void setup() {
        uploadInput = createUploadModelInput();
    }

    public void testUnloadModelAPI_WrongModelId() throws IOException {
        Map result = unloadModel("wrong_model_id");
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    public void testUnloadModelAPI_Success() throws IOException, InterruptedException {
        String taskId = uploadModel(TestHelper.toJsonString(uploadInput));
        waitForTask(taskId, MLTaskState.COMPLETED);
        getTask(client(), taskId, response -> {
            String algorithm = (String) response.get(FUNCTION_NAME_FIELD);
            assertNotNull(response.get(MODEL_ID_FIELD));
            assertEquals(MLTaskState.COMPLETED.name(), response.get(STATE_FIELD));
            String modelId = (String) response.get(MODEL_ID_FIELD);
            try {
                loadModel(modelId);
                waitForTask(taskId, MLTaskState.COMPLETED);
                Map<String, Object> result = unloadModel(modelId);
                for (Map.Entry<String, Object> entry : result.entrySet()) {
                    Map stats = (Map) ((Map) entry.getValue()).get("stats");
                    assertEquals("unloaded", stats.get(modelId));
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
