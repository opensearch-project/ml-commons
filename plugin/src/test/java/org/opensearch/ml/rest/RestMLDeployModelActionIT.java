/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.rest;

import static org.opensearch.ml.common.MLTask.MODEL_ID_FIELD;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.utils.TestHelper;

public class RestMLDeployModelActionIT extends MLCommonsRestTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    private MLRegisterModelInput registerModelInput;
    private MLRegisterModelGroupInput mlRegisterModelGroupInput;
    private String modelGroupId;

    @Before
    public void setup() throws IOException {
        mlRegisterModelGroupInput = MLRegisterModelGroupInput.builder().name("testGroupID").description("This is test Group").build();
        registerModelGroup(client(), TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            this.modelGroupId = (String) registerModelGroupResult.get("model_group_id");
        });
        registerModelInput = createRegisterModelInput(modelGroupId);
    }

    public void testReDeployModel() throws InterruptedException, IOException {
        // Register Model
        String taskId = registerModel(TestHelper.toJsonString(registerModelInput));
        waitForTask(taskId, MLTaskState.COMPLETED);
        getTask(client(), taskId, response -> {
            String model_id = (String) response.get(MODEL_ID_FIELD);
            try {
                // Deploy Model
                String taskId1 = deployModel(model_id);
                getTask(client(), taskId1, innerResponse -> { assertEquals(model_id, innerResponse.get(MODEL_ID_FIELD)); });
                waitForTask(taskId1, MLTaskState.COMPLETED);

                // Undeploy Model
                Map<String, Object> undeployresponse = undeployModel(model_id);
                for (Map.Entry<String, Object> entry : undeployresponse.entrySet()) {
                    Map stats = (Map) ((Map) entry.getValue()).get("stats");
                    assertEquals("undeployed", stats.get(model_id));
                }

                // Deploy Model again
                taskId1 = deployModel(model_id);
                getTask(client(), taskId1, innerResponse -> { logger.info("Re-Deploy model {}", innerResponse); });
                waitForTask(taskId1, MLTaskState.COMPLETED);

                getModel(client(), model_id, model -> {
                    logger.info("Get Model after re-deploy {}", model);
                    assertEquals("DEPLOYED", model.get("model_state"));
                });

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void testDeployModel_InvalidTorchScriptFile_TaskFails() throws Exception {
        ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipBytes)) {
            zos.putNextEntry(new ZipEntry("model.pt"));
            zos.write("not a torchscript model".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("tokenizer.json"));
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] modelZip = zipBytes.toByteArray();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(modelZip));
        String metaInput = "{\"name\":\"invalid-torchscript-model\",\"version\":\"1.0.0\",\"model_format\":\"TORCH_SCRIPT\","
            + "\"function_name\":\"TEXT_EMBEDDING\",\"model_group_id\":\""
            + modelGroupId
            + "\",\"model_content_size_in_bytes\":"
            + modelZip.length
            + ",\"model_content_hash_value\":\""
            + hash
            + "\",\"total_chunks\":1,\"model_config\":{\"model_type\":\"bert\",\"embedding_dimension\":16,"
            + "\"framework_type\":\"sentence_transformers\"}}";

        Response metaResponse = TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/models/_register_meta", null, metaInput, null);
        String modelId = (String) parseResponseToMap(metaResponse).get(MODEL_ID_FIELD);
        TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/models/" + modelId + "/upload_chunk/0",
                null,
                new ByteArrayEntity(modelZip, ContentType.APPLICATION_JSON),
                null
            );

        String taskId = deployModel(modelId);
        waitForTask(taskId, MLTaskState.FAILED);
        getTask(client(), taskId, task -> {
            assertEquals(MLTaskState.FAILED.name(), task.get("state"));
            assertTrue(
                String.valueOf(task.get("error")),
                String.valueOf(task.get("error")).contains("is not a valid TorchScript zip archive")
            );
        });
    }
}
