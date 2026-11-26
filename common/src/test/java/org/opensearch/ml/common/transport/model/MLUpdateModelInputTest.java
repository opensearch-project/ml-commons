/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;
import static org.opensearch.ml.common.CommonValue.VERSION_3_8_0;
import static org.opensearch.ml.common.CommonValue.VERSION_3_9_0;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.controller.MLRateLimiter;
import org.opensearch.ml.common.model.BaseModelConfig;
import org.opensearch.ml.common.model.BatchInferenceConfig;
import org.opensearch.ml.common.model.MLDeploySetting;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.search.SearchModule;

public class MLUpdateModelInputTest {

    private MLUpdateModelInput updateModelInput;
    private final String expectedInputStr =
        "{\"model_id\":\"test-model_id\",\"name\":\"name\",\"description\":\"description\",\"model_version\":"
            + "\"2\",\"model_group_id\":\"modelGroupId\",\"is_enabled\":false,\"rate_limiter\":"
            + "{\"limit\":\"1\",\"unit\":\"MILLISECONDS\"},\"model_config\":"
            + "{\"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\""
            + "{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"},\"connector_id\":"
            + "\"test-connector_id\",\"connector\":{\"description\":\"updated description\",\"version\":\"1\"},\"last_updated_time\":1}";

    private final String expectedOutputStrForUpdateRequestDoc =
        "{\"model_id\":\"test-model_id\",\"name\":\"name\",\"description\":\"description\",\"model_version\":"
            + "\"2\",\"model_group_id\":\"modelGroupId\",\"is_enabled\":false,\"rate_limiter\":"
            + "{\"limit\":\"1\",\"unit\":\"MILLISECONDS\"},\"model_config\":"
            + "{\"model_type\":\"testModelType\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\","
            + "\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\"},\"connector\":"
            + "{\"name\":\"test\",\"version\":\"1\",\"protocol\":\"http\",\"parameters\":{\"param1\":\"value1\"},\"credential\":"
            + "{\"api_key\":\"credential_value\"},\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":"
            + "\"https://api.openai.com/v1/chat/completions\",\"headers\":{\"Authorization\":\"Bearer ${credential.api_key}\"},\"request_body\":"
            + "\"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"messages\\\": ${parameters.messages} }\"}]},"
            + "\"connector_id\":\"test-connector_id\",\"last_updated_time\":1}";

    private final String expectedOutputStr = "{\"model_id\":null,\"name\":\"name\",\"description\":\"description\",\"model_group_id\":"
        + "\"modelGroupId\",\"is_enabled\":false,\"rate_limiter\":"
        + "{\"limit\":\"1\",\"unit\":\"MILLISECONDS\"},\"model_config\":"
        + "{\"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\""
        + "{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"},\"connector_id\":"
        + "\"test-connector_id\"}";

    private final String expectedOutputStrSpaceType =
        "{\"model_id\":\"test-model-id\",\"name\":\"name\",\"description\":\"description\",\"model_group_id\":"
            + "\"modelGroupId\",\"model_config\":"
            + "{\"model_type\":\"sparse_encoding\",\"additional_config\":{\"space_type\":\"l2\"}}}";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        MLModelConfig config = BaseModelConfig
            .baseModelConfigBuilder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(100)
            .build();

        Connector updatedConnector = HttpConnector
            .builder()
            .name("test")
            .protocol("http")
            .version("1")
            .credential(Map.of("api_key", "credential_value"))
            .parameters(Map.of("param1", "value1"))
            .actions(
                Arrays
                    .asList(
                        ConnectorAction
                            .builder()
                            .actionType(ConnectorAction.ActionType.PREDICT)
                            .method("POST")
                            .url("https://api.openai.com/v1/chat/completions")
                            .headers(Map.of("Authorization", "Bearer ${credential.api_key}"))
                            .requestBody("{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }")
                            .build()
                    )
            )
            .build();

        MLCreateConnectorInput updateContent = MLCreateConnectorInput
            .builder()
            .updateConnector(true)
            .version("1")
            .description("updated description")
            .build();

        MLRateLimiter rateLimiter = MLRateLimiter.builder().limit("1").unit(TimeUnit.MILLISECONDS).build();

        updateModelInput = MLUpdateModelInput
            .builder()
            .modelId("test-model_id")
            .modelGroupId("modelGroupId")
            .version("2")
            .name("name")
            .description("description")
            .isEnabled(false)
            .rateLimiter(rateLimiter)
            .modelConfig(config)
            .updatedConnector(updatedConnector)
            .connectorId("test-connector_id")
            .connector(updateContent)
            .lastUpdateTime(Instant.ofEpochMilli(1))
            .build();
    }

    @Test
    public void readInputStreamSuccess() throws IOException {
        readInputStream(updateModelInput, parsedInput -> {
            assertEquals("test-model_id", parsedInput.getModelId());
            assertEquals(updateModelInput.getName(), parsedInput.getName());
        });
    }

    @Test
    public void readInputStreamSuccessWithNullFields() throws IOException {
        updateModelInput.setModelConfig(null);
        readInputStream(updateModelInput, parsedInput -> { assertNull(parsedInput.getModelConfig()); });
    }

    @Test
    public void testToXContent() throws Exception {
        String jsonStr = serializationWithToXContent(updateModelInput);

        assertEquals(expectedOutputStrForUpdateRequestDoc, jsonStr);
    }

    @Test
    public void testToXContentIncomplete() throws Exception {
        String expectedIncompleteInputStr = "{\"model_id\":\"test-model_id\"}";
        updateModelInput = MLUpdateModelInput.builder().modelId("test-model_id").build();
        String jsonStr = serializationWithToXContent(updateModelInput);
        assertEquals(expectedIncompleteInputStr, jsonStr);
    }

    @Test
    public void parseSuccess() throws Exception {
        testParseFromJsonString(expectedInputStr, parsedInput -> { assertEquals("name", parsedInput.getName()); });
    }

    @Test
    public void parseWithNullFieldWithoutModel() throws Exception {
        exceptionRule.expect(IllegalStateException.class);
        String expectedInputStrWithNullField =
            "{\"model_id\":\"test-model_id\",\"name\":null,\"description\":\"description\",\"model_version\":"
                + "\"2\",\"model_group_id\":\"modelGroupId\",\"is_enabled\":false,\"rate_limiter\":"
                + "{\"limit\":\"1\",\"unit\":\"MILLISECONDS\"},\"model_config\":"
                + "{\"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\""
                + "{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"},\"connector_id\":\"test-connector_id\"}";
        testParseFromJsonString(expectedInputStrWithNullField, parsedInput -> {
            try {
                assertEquals(expectedOutputStr, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void parseRejectsUnknownField() throws Exception {
        String expectedInputStrWithIllegalField = "{\"model_id\":\"test-model_id\",\"name\":\"name\",\"description\":\"description\","
            + "\"last_updated_time\":1,\"illegal_field\":\"This field must be rejected.\"}";
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> testParseFromJsonString(expectedInputStrWithIllegalField, parsedInput -> {})
        );
        assertEquals("Unknown field [illegal_field] in update model request", e.getMessage());
    }

    @Test
    public void parseRejectsMisspelledFields() throws Exception {
        for (String field : new String[] { "connectors", "descriptoin", "foo", "updated_connector" }) {
            String json = "{\"" + field + "\":{\"name\":\"x\"}}";
            IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> testParseFromJsonString(json, parsedInput -> {})
            );
            assertEquals("Unknown field [" + field + "] in update model request", e.getMessage());
        }
    }

    @Test
    public void parseAcceptsReadOnlyFieldsFromGetModelResponse() throws Exception {
        // A GET model response body sent back as an update: the read-only fields are accepted and not written.
        String json = "{\"name\":\"renamed\",\"model_group_id\":\"g\",\"algorithm\":\"REMOTE\",\"model_version\":\"1\","
            + "\"model_state\":\"DEPLOYED\",\"created_time\":1,\"last_updated_time\":2,\"last_registered_time\":3,"
            + "\"last_deployed_time\":4,\"last_undeployed_time\":5,\"auto_redeploy_retry_times\":0,"
            + "\"planning_worker_node_count\":1,\"current_worker_node_count\":1,\"planning_worker_nodes\":[\"n1\"],"
            + "\"deploy_to_all_nodes\":true,\"is_hidden\":false,\"is_controller_enabled\":false,"
            + "\"user\":{\"name\":\"u\",\"backend_roles\":[],\"roles\":[],\"custom_attribute_names\":[]},"
            + "\"provisioned_by\":\"agent\",\"model_format\":\"TORCH_SCRIPT\",\"model_content_size_in_bytes\":10,"
            + "\"model_content_hash_value\":\"h\",\"total_chunks\":1,\"chunk_number\":0,\"function_name\":\"REMOTE\"}";
        testParseFromJsonString(json, parsedInput -> {
            assertEquals("renamed", parsedInput.getName());
            assertEquals("g", parsedInput.getModelGroupId());
            assertNull("model_version is never taken from the request", parsedInput.getVersion());
            assertNull("last_updated_time is never taken from the request", parsedInput.getLastUpdateTime());
            try {
                String jsonStr = serializationWithToXContent(parsedInput);
                for (String readOnly : new String[] { "model_state", "algorithm", "created_time", "is_hidden", "user", "provisioned_by" }) {
                    assertFalse(readOnly + " must not be written: " + jsonStr, jsonStr.contains("\"" + readOnly + "\""));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void parseAcceptsEveryFieldGetModelCanReturn() throws Exception {
        // Guards READ_ONLY_FIELDS: if MLModel.toXContent starts writing a new field that update model neither handles
        // nor lists as read-only, sending a GET response body back as an update would start failing with a 400.
        Instant now = Instant.ofEpochMilli(1_000L);
        MLModel model = MLModel
            .builder()
            .name("m")
            .modelGroupId("g")
            .algorithm(FunctionName.REMOTE)
            .version("1")
            .content("content")
            .user(new User("u", Collections.emptyList(), Collections.emptyList(), Collections.emptyList()))
            .description("d")
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelState(MLModelState.DEPLOYED)
            .modelContentSizeInBytes(10L)
            .modelContentHash("h")
            .isEnabled(true)
            .isControllerEnabled(false)
            .rateLimiter(MLRateLimiter.builder().limit("1").unit(TimeUnit.MILLISECONDS).build())
            .modelConfig(BaseModelConfig.baseModelConfigBuilder().modelType("t").allConfig("{}").build())
            .createdTime(now)
            .lastUpdateTime(now)
            .lastRegisteredTime(now)
            .lastDeployedTime(now)
            .lastUndeployedTime(now)
            .autoRedeployRetryTimes(0)
            .modelId("model-id")
            .chunkNumber(0)
            .totalChunks(1)
            .planningWorkerNodeCount(1)
            .currentWorkerNodeCount(1)
            .planningWorkerNodes(new String[] { "n1" })
            .deployToAllNodes(true)
            .isHidden(false)
            .deploySetting(MLDeploySetting.builder().isAutoDeployEnabled(true).build())
            .connectorId("c")
            .modelInterface(Map.of("input", "{}", "output", "{}"))
            .tenantId("tenant")
            .provisionedBy("agent")
            .batchInferenceConfig(BatchInferenceConfig.builder().maxItemsPerRequest(8).build())
            .build();
        XContentBuilder builder = XContentFactory.jsonBuilder();
        model.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String getResponseBody = builder.toString();

        testParseFromJsonString(getResponseBody, parsedInput -> {
            assertEquals("m", parsedInput.getName());
            assertTrue(parsedInput.hasUpdatableField());
        });
    }

    @Test
    public void everyUpdatableFieldCountsAsAnUpdate() throws Exception {
        // Keeps UPDATABLE_FIELDS and hasUpdatableField() in step: a new updatable field must be added to both.
        Map<String, String> samples = Map
            .ofEntries(
                Map.entry("name", "\"n\""),
                Map.entry("description", "\"d\""),
                Map.entry("model_group_id", "\"g\""),
                Map.entry("is_enabled", "true"),
                Map.entry("rate_limiter", "{\"limit\":\"1\",\"unit\":\"MILLISECONDS\"}"),
                Map.entry("model_config", "{\"model_type\":\"t\",\"all_config\":\"{}\"}"),
                Map.entry("deploy_setting", "{\"is_auto_deploy_enabled\":true}"),
                Map.entry("connector_id", "\"c\""),
                Map.entry("connector", "{\"description\":\"d\"}"),
                Map.entry("guardrails", "{\"type\":\"local_regex\"}"),
                Map.entry("interface", "{\"input\":\"{}\"}"),
                Map.entry("batch_inference_config", "{\"max_items_per_request\":8}")
            );
        assertEquals(new HashSet<>(MLUpdateModelInput.UPDATABLE_FIELDS), samples.keySet());
        for (Map.Entry<String, String> sample : samples.entrySet()) {
            testParseFromJsonString(
                "{\"" + sample.getKey() + "\":" + sample.getValue() + "}",
                parsedInput -> assertTrue(sample.getKey(), parsedInput.hasUpdatableField())
            );
        }
    }

    @Test
    public void hasUpdatableField() throws Exception {
        testParseFromJsonString(
            "{\"model_state\":\"DEPLOYED\",\"is_hidden\":false}",
            parsedInput -> assertFalse(parsedInput.hasUpdatableField())
        );
        testParseFromJsonString("{}", parsedInput -> assertFalse(parsedInput.hasUpdatableField()));
        // is_hidden is a read-only field: accepted and ignored, so on its own it is not an update.
        testParseFromJsonString("{\"is_hidden\":true}", parsedInput -> assertFalse(parsedInput.hasUpdatableField()));
        testParseFromJsonString("{\"is_enabled\":false}", parsedInput -> assertTrue(parsedInput.hasUpdatableField()));
        testParseFromJsonString("{\"interface\":{}}", parsedInput -> assertTrue(parsedInput.hasUpdatableField()));
    }

    @Test
    public void parseWithSpaceType() throws Exception {
        String expectedInputStrWithSpaceType = "{\"model_id\":\"test-model-id\",\"name\":\"name\",\"description\":\"description\","
            + "\"model_group_id\":\"modelGroupId\",\"model_config\":{\"model_type\":\"sparse_encoding\","
            + "\"additional_config\":{\"space_type\":\"l2\"}}}";
        testParseFromJsonString(expectedInputStrWithSpaceType, parsedInput -> {
            try {
                assertEquals(expectedOutputStrSpaceType, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void serializationWithTenantId_Success() throws IOException {
        MLUpdateModelInput input = updateModelInput.toBuilder().tenantId("tenant-1").build();

        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(VERSION_2_19_0); // Version with tenantId support
        input.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        in.setVersion(VERSION_2_19_0);
        MLUpdateModelInput parsedInput = new MLUpdateModelInput(in);

        assertEquals(input.getTenantId(), parsedInput.getTenantId());
    }

    @Test
    public void serializationWithoutTenantId_Success() throws IOException {
        MLUpdateModelInput input = updateModelInput.toBuilder().tenantId(null).build();

        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(VERSION_2_19_0); // Version with tenantId support
        input.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        in.setVersion(VERSION_2_19_0);
        MLUpdateModelInput parsedInput = new MLUpdateModelInput(in);

        assertNull(parsedInput.getTenantId());
    }

    @Test
    public void parseWithTenantId_Success() throws Exception {
        String jsonWithTenantId =
            "{\"model_id\":\"test-model_id\",\"tenant_id\":\"tenant-1\",\"name\":\"name\",\"description\":\"description\"}";
        testParseFromJsonString(jsonWithTenantId, parsedInput -> {
            assertEquals("tenant-1", parsedInput.getTenantId());
            assertEquals("test-model_id", parsedInput.getModelId());
        });
    }

    @Test
    public void toXContentWithTenantId_Success() throws IOException {
        MLUpdateModelInput input = updateModelInput.toBuilder().tenantId("tenant-1").build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);

        String jsonOutput = builder.toString();

        // Validate that tenantId is present in the serialized JSON
        assertTrue(jsonOutput.contains("\"tenant_id\":\"tenant-1\""));
    }

    @Test
    public void parseWithBatchInferenceConfig_Success() throws Exception {
        String json = "{\"model_id\":\"test-model_id\",\"batch_inference_config\":{\"max_items_per_request\":96,"
            + "\"dynamic_batching\":{\"enabled\":true,\"flush_timeout_ms\":25}}}";
        testParseFromJsonString(json, parsedInput -> {
            assertNotNull(parsedInput.getBatchInferenceConfig());
            assertEquals(96, parsedInput.getBatchInferenceConfig().getMaxItemsPerRequest());
            assertTrue(parsedInput.getBatchInferenceConfig().isDynamicBatchingEnabled());
        });
    }

    @Test
    public void parseRejectsUnknownFieldInBatchInferenceConfig() throws Exception {
        String json = "{\"model_id\":\"test-model_id\",\"batch_inference_config\":{\"max_items\":2}}";
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported field [max_items]");
        testParseFromJsonString(json, parsedInput -> {});
    }

    @Test
    public void toXContentWithBatchInferenceConfig_Success() throws IOException {
        MLUpdateModelInput input = updateModelInput
            .toBuilder()
            .batchInferenceConfig(BatchInferenceConfig.builder().maxItemsPerRequest(96).build())
            .build();
        XContentBuilder builder = XContentFactory.jsonBuilder();
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertTrue(builder.toString().contains("\"batch_inference_config\":{"));
    }

    @Test
    public void serializationWithBatchInferenceConfig_Success() throws IOException {
        MLUpdateModelInput input = updateModelInput
            .toBuilder()
            .batchInferenceConfig(BatchInferenceConfig.builder().maxItemsPerRequest(96).build())
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(VERSION_3_9_0);
        input.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        in.setVersion(VERSION_3_9_0);
        MLUpdateModelInput parsedInput = new MLUpdateModelInput(in);

        assertNotNull(parsedInput.getBatchInferenceConfig());
        assertEquals(96, parsedInput.getBatchInferenceConfig().getMaxItemsPerRequest());
    }

    @Test
    public void serializationBeforeBatchInferenceConfigVersionDropsIt() throws IOException {
        MLUpdateModelInput input = updateModelInput
            .toBuilder()
            .batchInferenceConfig(BatchInferenceConfig.builder().maxItemsPerRequest(96).build())
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(VERSION_3_8_0);
        input.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        in.setVersion(VERSION_3_8_0);
        MLUpdateModelInput parsedInput = new MLUpdateModelInput(in);

        assertNull("an older node never receives the field on the wire", parsedInput.getBatchInferenceConfig());
    }

    private void testParseFromJsonString(String expectedInputStr, Consumer<MLUpdateModelInput> verify) throws Exception {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                expectedInputStr
            );
        parser.nextToken();
        MLUpdateModelInput parsedInput = MLUpdateModelInput.parse(parser);
        verify.accept(parsedInput);
    }

    private void readInputStream(MLUpdateModelInput input, Consumer<MLUpdateModelInput> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLUpdateModelInput parsedInput = new MLUpdateModelInput(streamInput);
        verify.accept(parsedInput);
    }

    private String serializationWithToXContent(MLUpdateModelInput input) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        return builder.toString();
    }

}
