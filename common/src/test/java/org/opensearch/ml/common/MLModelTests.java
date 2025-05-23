/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.model.BaseModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.telemetry.metrics.tags.Tags;

public class MLModelTests {

    MLModel mlModel;
    BaseModelConfig config;
    Function<XContentParser, MLModel> function;

    @Before
    public void setUp() {
        FunctionName algorithm = FunctionName.KMEANS;
        User user = new User();
        config = BaseModelConfig
            .baseModelConfigBuilder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .build();
        Instant now = Instant.now();
        mlModel = MLModel
            .builder()
            .name("some model")
            .algorithm(algorithm)
            .version("1.0.0")
            .content("some content")
            .user(user)
            .description("test description")
            .modelFormat(MLModelFormat.ONNX)
            .modelState(MLModelState.DEPLOYED)
            .modelContentSizeInBytes(10_000_000l)
            .modelContentHash("test_hash")
            .modelConfig(config)
            .createdTime(now)
            .lastRegisteredTime(now)
            .lastDeployedTime(now)
            .lastUndeployedTime(now)
            .modelId("model_id")
            .chunkNumber(1)
            .totalChunks(10)
            .isHidden(false)
            .build();
        function = parser -> {
            try {
                return MLModel.parse(parser, algorithm.name());
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse BaseModelConfig", e);
            }
        };
    }

    @Test
    public void toXContent() throws IOException {
        MLModel mlModel = MLModel
            .builder()
            .algorithm(FunctionName.KMEANS)
            .name("model_name")
            .version("1.0.0")
            .content("test_content")
            .isHidden(true)
            .build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlModel.toXContent(builder, EMPTY_PARAMS);
        String mlModelContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"name\":\"model_name\",\"algorithm\":\"KMEANS\",\"model_version\":\"1.0.0\",\"model_content\":\"test_content\",\"is_hidden\":true}",
            mlModelContent
        );
    }

    @Test
    public void toXContent_NullValue() throws IOException {
        MLModel mlModel = MLModel.builder().build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlModel.toXContent(builder, EMPTY_PARAMS);
        String mlModelContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{}", mlModelContent);
    }

    @Test
    public void parse() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlModel.toXContent(builder, EMPTY_PARAMS);
        String mlModelContent = TestHelper.xContentBuilderToString(builder);
        TestHelper.testParseFromString(config, mlModelContent, function);
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(mlModel);
    }

    public void readInputStream(MLModel mlModel) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlModel.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLModel parsedMLModel = new MLModel(streamInput);
        assertEquals(mlModel.getName(), parsedMLModel.getName());
        assertEquals(mlModel.getAlgorithm(), parsedMLModel.getAlgorithm());
        assertEquals(mlModel.getVersion(), parsedMLModel.getVersion());
        assertEquals(mlModel.getContent(), parsedMLModel.getContent());
        assertEquals(mlModel.getUser(), parsedMLModel.getUser());
        assertEquals(mlModel.getIsHidden(), parsedMLModel.getIsHidden());
        assertEquals(mlModel.getDescription(), parsedMLModel.getDescription());
        assertEquals(mlModel.getModelContentHash(), parsedMLModel.getModelContentHash());
        assertEquals(mlModel.getCreatedTime(), parsedMLModel.getCreatedTime());
        assertEquals(mlModel.getLastRegisteredTime(), parsedMLModel.getLastRegisteredTime());
        assertEquals(mlModel.getLastDeployedTime(), parsedMLModel.getLastDeployedTime());
        assertEquals(mlModel.getLastUndeployedTime(), parsedMLModel.getLastUndeployedTime());
        assertEquals(mlModel.getModelId(), parsedMLModel.getModelId());
        assertEquals(mlModel.getChunkNumber(), parsedMLModel.getChunkNumber());
        assertEquals(mlModel.getTotalChunks(), parsedMLModel.getTotalChunks());
    }

    @Test
    public void toXContent_WithTenantId() throws IOException {
        MLModel mlModel = MLModel
            .builder()
            .algorithm(FunctionName.KMEANS)
            .name("model_name")
            .version("1.0.0")
            .content("test_content")
            .isHidden(true)
            .tenantId("test_tenant")
            .build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlModel.toXContent(builder, EMPTY_PARAMS);
        String mlModelContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"name\":\"model_name\",\"algorithm\":\"KMEANS\",\"model_version\":\"1.0.0\",\"model_content\":\"test_content\",\"is_hidden\":true,\"tenant_id\":\"test_tenant\"}",
            mlModelContent
        );
    }

    @Test
    public void parse_WithTenantId() throws IOException {
        String modelJson = """
                {
                    "name": "model_name",
                    "algorithm": "KMEANS",
                    "model_version": "1.0.0",
                    "model_content": "test_content",
                    "is_hidden": true,
                    "tenant_id": "test_tenant"
                }
            """;
        TestHelper.testParseFromString(config, modelJson, function);
    }

    @Test
    public void toBuilder_WithTenantId() {
        MLModel mlModelWithTenantId = mlModel.toBuilder().tenantId("test_tenant").build();
        assertEquals("test_tenant", mlModelWithTenantId.getTenantId());
    }

    @Test
    public void testGetTags_RemoteModel() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "gpt-4");

        Connector connector = HttpConnector
            .builder()
            .name("test-connector")
            .protocol("http")
            .parameters(parameters)
            .actions(
                Collections
                    .singletonList(
                        ConnectorAction
                            .builder()
                            .actionType(ConnectorAction.ActionType.PREDICT)
                            .method("POST")
                            .url("https://api.openai.com/test-url")
                            .requestBody("{\"model\": \"${parameters.model}\"}")
                            .build()
                    )
            )
            .build();

        MLModel model = MLModel.builder().name("test-model").algorithm(FunctionName.REMOTE).connector(connector).build();

        Tags tags = model.getTags();
        assertNotNull(tags);
        assertEquals("remote", tags.getTagsMap().get("deployment"));
        assertEquals("openai", tags.getTagsMap().get("service_provider"));
        assertEquals("REMOTE", tags.getTagsMap().get("algorithm"));
        assertEquals("gpt-4", tags.getTagsMap().get("model"));
        assertEquals("llm", tags.getTagsMap().get("type"));

        // Unknown service-provider
        Connector unknownConnector = HttpConnector
            .builder()
            .name("unknown-connector")
            .protocol("http")
            .actions(
                Collections
                    .singletonList(
                        ConnectorAction
                            .builder()
                            .actionType(ConnectorAction.ActionType.PREDICT)
                            .method("POST")
                            .url("https://unknown-service.com/api/v1/predict")
                            .build()
                    )
            )
            .build();

        MLModel unknownModel = MLModel.builder().name("unknown-model").algorithm(FunctionName.REMOTE).connector(unknownConnector).build();

        tags = unknownModel.getTags();
        assertNotNull(tags);
        assertEquals("remote", tags.getTagsMap().get("deployment"));
        assertEquals("unknown", tags.getTagsMap().get("service_provider"));
        assertEquals("REMOTE", tags.getTagsMap().get("algorithm"));
        assertEquals("unknown", tags.getTagsMap().get("model"));
        assertEquals("unknown", tags.getTagsMap().get("type"));
        assertEquals("https://unknown-service.com/api/v1/predict", tags.getTagsMap().get("url"));

        // Unknown model
        Connector invalidConnector = HttpConnector
            .builder()
            .name("invalid-connector")
            .protocol("http")
            .actions(
                Collections
                    .singletonList(
                        ConnectorAction
                            .builder()
                            .actionType(ConnectorAction.ActionType.PREDICT)
                            .method("POST")
                            .url("https://api.openai.com/test-url")
                            .requestBody("{}")
                            .build()
                    )
            )
            .build();

        MLModel invalidModel = MLModel.builder().name("invalid-model").algorithm(FunctionName.REMOTE).connector(invalidConnector).build();

        tags = invalidModel.getTags();
        assertNotNull(tags);
        assertEquals("remote", tags.getTagsMap().get("deployment"));
        assertEquals("openai", tags.getTagsMap().get("service_provider"));
        assertEquals("REMOTE", tags.getTagsMap().get("algorithm"));
        assertEquals("unknown", tags.getTagsMap().get("model"));
        assertEquals("unknown", tags.getTagsMap().get("type"));
    }

    @Test
    public void testGetTags_WithPreTrainedModel() {
        TextEmbeddingModelConfig config = TextEmbeddingModelConfig
            .builder()
            .modelType("embedding-test-type")
            .embeddingDimension(1)
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS)
            .build();

        MLModel model = MLModel
            .builder()
            .name("huggingface/bert/bert-base-uncased")
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .modelConfig(config)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .build();

        Tags tags = model.getTags();
        assertNotNull(tags);
        assertEquals("local:pre_trained", tags.getTagsMap().get("deployment"));
        assertEquals("huggingface", tags.getTagsMap().get("service_provider"));
        assertEquals("TEXT_EMBEDDING", tags.getTagsMap().get("algorithm"));
        assertEquals("bert-base-uncased", tags.getTagsMap().get("model"));
        assertEquals("embedding-test-type", tags.getTagsMap().get("type"));
        assertEquals("TORCH_SCRIPT", tags.getTagsMap().get("model_format"));
    }

    @Test
    public void testGetTags_WithCustomModel() {
        TextEmbeddingModelConfig config = TextEmbeddingModelConfig
            .builder()
            .modelType("custom_embedding")
            .embeddingDimension(1)
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.HUGGINGFACE_TRANSFORMERS)
            .build();

        MLModel model = MLModel
            .builder()
            .name("custom-model")
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .modelConfig(config)
            .modelFormat(MLModelFormat.ONNX)
            .build();

        Tags tags = model.getTags();
        assertNotNull(tags);
        assertEquals("local:custom", tags.getTagsMap().get("deployment"));
        assertEquals("TEXT_EMBEDDING", tags.getTagsMap().get("algorithm"));
        assertEquals("custom_embedding", tags.getTagsMap().get("type"));
        assertEquals("ONNX", tags.getTagsMap().get("model_format"));

        // missing type
        MLModel noTypeModel = MLModel
            .builder()
            .name("custom-model")
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .modelFormat(MLModelFormat.ONNX)
            .build();

        tags = noTypeModel.getTags();
        assertNotNull(tags);
        assertEquals("local:custom", tags.getTagsMap().get("deployment"));
        assertEquals("TEXT_EMBEDDING", tags.getTagsMap().get("algorithm"));
        assertEquals("unknown", tags.getTagsMap().get("type"));
        assertEquals("ONNX", tags.getTagsMap().get("model_format"));

        // missing model format
        MLModel noFormatModel = MLModel.builder().name("custom-model").algorithm(FunctionName.TEXT_EMBEDDING).modelConfig(config).build();

        tags = noFormatModel.getTags();
        assertNotNull(tags);
        assertEquals("local:custom", tags.getTagsMap().get("deployment"));
        assertEquals("TEXT_EMBEDDING", tags.getTagsMap().get("algorithm"));
        assertEquals("custom_embedding", tags.getTagsMap().get("type"));
        assertNull(tags.getTagsMap().get("model_format"));
    }

    @Test
    public void testIdentifyServiceProvider() {
        assertEquals("bedrock", mlModel.identifyServiceProvider("https://test-bedrock-url.com/api"));
        assertEquals("sagemaker", mlModel.identifyServiceProvider("https://test-sagemaker-url.com/api"));
        assertEquals("azure", mlModel.identifyServiceProvider("https://test-azure-url.com/api"));
        assertEquals("google", mlModel.identifyServiceProvider("https://test-google-url.com/api"));
        assertEquals("anthropic", mlModel.identifyServiceProvider("https://test-anthropic-url.com/api"));
        assertEquals("openai", mlModel.identifyServiceProvider("https://test-openai-url.com/api"));
        assertEquals("deepseek", mlModel.identifyServiceProvider("https://test-deepseek-url.com/api"));
        assertEquals("cohere", mlModel.identifyServiceProvider("https://test-cohere-url.com/api"));
        assertEquals("vertexai", mlModel.identifyServiceProvider("https://test-vertexai-url.com/api"));
        assertEquals("aleph-alpha", mlModel.identifyServiceProvider("https://test-aleph-alpha-url.com/api"));
        assertEquals("comprehend", mlModel.identifyServiceProvider("https://test-comprehend-url.com/api"));
        assertEquals("textract", mlModel.identifyServiceProvider("https://test-textract-url.com/api"));
        assertEquals("mistral", mlModel.identifyServiceProvider("https://test-mistral-url.com/api"));
        assertEquals("x.ai", mlModel.identifyServiceProvider("https://test-x.ai-url.com/api"));
        assertEquals("unknown", mlModel.identifyServiceProvider("https://unknown-provider.com/api"));
    }

    @Test
    public void testIdentifyModel() {
        // Bedrock test case (/model/{model}/)
        Connector bedrockConnector = HttpConnector
            .builder()
            .name("bedrock-connector")
            .protocol("http")
            .actions(
                Collections
                    .singletonList(
                        ConnectorAction
                            .builder()
                            .actionType(ConnectorAction.ActionType.PREDICT)
                            .method("POST")
                            .url("https://test-bedrock-url.com/api/model/test-model/invoke")
                            .build()
                    )
            )
            .build();
        assertEquals(
            "test-model",
            mlModel.identifyModel("bedrock", "https://test-bedrock-url.com/api/model/test-model/invoke", null, bedrockConnector)
        );

        // Model in request body
        String requestBody = "{\"model\": \"test-model\"}";
        Connector openaiConnector = HttpConnector
            .builder()
            .name("openai-connector")
            .protocol("http")
            .actions(
                java.util.Arrays
                    .asList(
                        ConnectorAction
                            .builder()
                            .actionType(ConnectorAction.ActionType.PREDICT)
                            .method("POST")
                            .url("https://test-openai-url.com/api")
                            .requestBody(requestBody)
                            .build()
                    )
            )
            .build();
        assertEquals(
            "test-model",
            mlModel.identifyModel("openai", "https://test-openai-url.com/api", new JSONObject(requestBody), openaiConnector)
        );

        // Test with model in parameters but not in request body
        requestBody = "{\"messages\": [{\"role\": \"user\", \"content\": \"Hello\"}]}";
        Map<String, String> paramsOnly = new HashMap<>();
        paramsOnly.put("model", "test-model");
        Connector paramsOnlyConnector = HttpConnector
            .builder()
            .name("params-only-connector")
            .protocol("http")
            .parameters(paramsOnly)
            .actions(
                java.util.Arrays
                    .asList(
                        ConnectorAction
                            .builder()
                            .actionType(ConnectorAction.ActionType.PREDICT)
                            .method("POST")
                            .url("https://test-api.anthropic.com/v1/messages")
                            .requestBody(requestBody)
                            .build()
                    )
            )
            .build();
        assertEquals(
            "test-model",
            mlModel
                .identifyModel("anthropic", "https://test-api.anthropic.com/v1/messages", new JSONObject(requestBody), paramsOnlyConnector)
        );
    }
}
