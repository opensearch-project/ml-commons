/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.MLTask.ERROR_FIELD;
import static org.opensearch.ml.common.MLTask.FUNCTION_NAME_FIELD;
import static org.opensearch.ml.common.MLTask.MODEL_ID_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;

import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.upload.MLUploadInput;
import org.opensearch.ml.utils.TestHelper;

public class RestMLUploadModelActionIT extends MLCommonsRestTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private MLUploadInput uploadInput;

    @Before
    public void setup() {
        uploadInput = createUploadModelInput();
    }

    public void testUploadModelAPI_WrongParameter() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("Wrong model format");
        String input = TestHelper.toJsonString(uploadInput);
        input = input.replace("TORCH_SCRIPT", "wrong_format");
        uploadModel(input);
    }

    public void testUploadModelAPI_WrongUrl() throws IOException, InterruptedException {
        uploadInput.setUrl("https://this-is-a-wrong-url-" + randomAlphaOfLength(20));
        String taskId = uploadModel(TestHelper.toJsonString(uploadInput));
        waitForTask(taskId, MLTaskState.FAILED);
        getTask(client(), taskId, response -> {
            String algorithm = (String) response.get(FUNCTION_NAME_FIELD);
            assertEquals(uploadInput.getFunctionName().name(), algorithm);
            assertNull(response.get(MODEL_ID_FIELD));
            assertEquals(MLTaskState.FAILED.name(), response.get(STATE_FIELD));
            assertTrue(((String) response.get(ERROR_FIELD)).contains("UnknownHostException"));
        });
    }

    public void testUploadModelAPI_Success() throws IOException, InterruptedException {
        String taskId = uploadModel(TestHelper.toJsonString(uploadInput));
        waitForTask(taskId, MLTaskState.COMPLETED);
        getTask(client(), taskId, response -> {
            String algorithm = (String) response.get(FUNCTION_NAME_FIELD);
            assertEquals(uploadInput.getFunctionName().name(), algorithm);
            assertNotNull(response.get(MODEL_ID_FIELD));
            assertEquals(MLTaskState.COMPLETED.name(), response.get(STATE_FIELD));
        });
    }
}
