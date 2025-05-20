package org.opensearch.ml.common.transport.register;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.HttpConnectorTest;
import org.opensearch.ml.common.model.BaseModelConfig;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MetricsCorrelationModelConfig;
import org.opensearch.ml.common.model.RemoteModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.search.SearchModule;

@RunWith(MockitoJUnitRunner.class)
public class MLRegisterModelInputTest {

    @Mock
    MLModelConfig config;
    MLRegisterModelInput input;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    private final String expectedInputStr = "{\"function_name\":\"TEXT_EMBEDDING\",\"name\":\"modelName\","
        + "\"version\":\"version\",\"model_group_id\":\"modelGroupId\",\"description\":\"test description\","
        + "\"url\":\"url\",\"model_content_hash_value\":\"hash_value_test\",\"model_format\":\"ONNX\","
        + "\"model_config\":{\"model_type\":\"testModelType\",\"embedding_dimension\":100,"
        + "\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\","
        + "\\\"field2\\\":\\\"value2\\\"}\"},\"deploy_model\":true,\"model_node_ids\":[\"modelNodeIds\"],"
        + "\"connector\":{\"name\":\"test_connector_name\",\"version\":\"1\","
        + "\"description\":\"this is a test connector\",\"protocol\":\"http\","
        + "\"parameters\":{\"input\":\"test input value\"},\"credential\":{\"key\":\"test_key_value\"},"
        + "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\","
        + "\"headers\":{\"api_key\":\"${credential.key}\"},"
        + "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\","
        + "\"pre_process_function\":\"connector.pre_process.openai.embedding\","
        + "\"post_process_function\":\"connector.post_process.openai.embedding\"}],"
        + "\"backend_roles\":[\"role1\",\"role2\"],\"access\":\"public\","
        + "\"client_config\":{\"max_connection\":30,\"connection_timeout\":30000,\"read_timeout\":30000,"
        + "\"retry_backoff_millis\":10,\"retry_timeout_seconds\":10,\"max_retry_times\":-1,\"retry_backoff_policy\":\"constant\"}},\"is_hidden\":false}";
    private final FunctionName functionName = FunctionName.TEXT_EMBEDDING;
    private final String modelName = "modelName";
    private final String version = "version";
    private final String url = "url";

    private final String modelGroupId = "modelGroupId";

    @Before
    public void setUp() throws Exception {
        config = TextEmbeddingModelConfig
            .builder()
            .modelType("testModelType")
            .embeddingDimension(100)
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .build();
        HttpConnector connector = HttpConnectorTest.createHttpConnector();

        input = MLRegisterModelInput
            .builder()
            .functionName(functionName)
            .modelName(modelName)
            .version(version)
            .modelGroupId(modelGroupId)
            .url(url)
            .modelFormat(MLModelFormat.ONNX)
            .modelConfig(config)
            .deployModel(true)
            .modelNodeIds(new String[] { "modelNodeIds" })
            .isHidden(false)
            .description("test description")
            .hashValue("hash_value_test")
            .connector(connector)
            .build();
    }

    @Test
    public void constructor_NullModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model name is null");
        MLRegisterModelInput.builder().build();
    }

    @Test
    public void constructor_NullModelName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model name is null");
        MLRegisterModelInput.builder().functionName(functionName).modelGroupId(modelGroupId).modelName(null).build();
    }

    @Test
    public void constructor_NullModelFormat() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model format is null");
        MLRegisterModelInput
            .builder()
            .functionName(functionName)
            .modelName(modelName)
            .version(version)
            .modelGroupId(modelGroupId)
            .modelFormat(null)
            .url(url)
            .build();
    }

    @Test
    public void constructor_NullModelConfig() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model config is null");
        MLRegisterModelInput
            .builder()
            .functionName(functionName)
            .modelName(modelName)
            .version(version)
            .modelGroupId(modelGroupId)
            .modelFormat(MLModelFormat.ONNX)
            .modelConfig(null)
            .url(url)
            .build();
    }

    @Test
    public void constructor_SuccessWithMinimalSetup() {
        MLRegisterModelInput input = MLRegisterModelInput
            .builder()
            .modelName(modelName)
            .version(version)
            .modelGroupId(modelGroupId)
            .modelFormat(MLModelFormat.ONNX)
            .modelConfig(config)
            .url(url)
            .build();
        // MLRegisterModelInput.functionName is set to FunctionName.TEXT_EMBEDDING if not explicitly passed, with no exception thrown
        assertEquals(FunctionName.TEXT_EMBEDDING, input.getFunctionName());
        // MLRegisterModelInput.deployModel is set to false if not explicitly passed, with no exception thrown
        assertFalse(input.isDeployModel());
        // MLRegisterModelInput.deployModel is set to null if not explicitly passed, with no exception thrown
        assertNull(input.getModelNodeIds());
    }

    @Test
    public void testToXContent() throws Exception {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();

        String expectedFunctionInputStr = "{\"function_name\":\"TEXT_EMBEDDING\",\"name\":\"modelName\","
            + "\"version\":\"version\",\"model_group_id\":\"modelGroupId\",\"description\":\"test description\","
            + "\"url\":\"url\",\"model_content_hash_value\":\"hash_value_test\",\"model_format\":\"ONNX\","
            + "\"model_config\":{\"model_type\":\"testModelType\",\"embedding_dimension\":100,"
            + "\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\","
            + "\\\"field2\\\":\\\"value2\\\"}\"},\"deploy_model\":true,\"model_node_ids\":[\"modelNodeIds\"],"
            + "\"connector\":{\"name\":\"test_connector_name\",\"version\":\"1\","
            + "\"description\":\"this is a test connector\",\"protocol\":\"http\","
            + "\"parameters\":{\"input\":\"test input value\"},\"credential\":{\"key\":\"test_key_value\"},"
            + "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\","
            + "\"headers\":{\"api_key\":\"${credential.key}\"},"
            + "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\","
            + "\"pre_process_function\":\"connector.pre_process.openai.embedding\","
            + "\"post_process_function\":\"connector.post_process.openai.embedding\"}],"
            + "\"backend_roles\":[\"role1\",\"role2\"],\"access\":\"public\","
            + "\"client_config\":{\"max_connection\":30,\"connection_timeout\":30000,\"read_timeout\":30000,"
            + "\"retry_backoff_millis\":10,\"retry_timeout_seconds\":10,\"max_retry_times\":-1,\"retry_backoff_policy\":\"constant\"}},\"is_hidden\":false}";

        assertEquals(expectedFunctionInputStr, jsonStr);
    }

    @Test
    public void testToXContent_Incomplete() throws Exception {
        input.setUrl(null);
        input.setModelConfig(null);
        input.setModelFormat(null);
        input.setModelNodeIds(null);
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);

        String jsonStr = builder.toString();

        String expectedIncompleteInputStr = "{\"function_name\":\"TEXT_EMBEDDING\","
            + "\"name\":\"modelName\",\"version\":\"version\",\"model_group_id\":\"modelGroupId\","
            + "\"description\":\"test description\",\"model_content_hash_value\":\"hash_value_test\","
            + "\"deploy_model\":true,\"connector\":{\"name\":\"test_connector_name\",\"version\":\"1\","
            + "\"description\":\"this is a test connector\",\"protocol\":\"http\","
            + "\"parameters\":{\"input\":\"test input value\"},"
            + "\"credential\":{\"key\":\"test_key_value\"},\"actions\":[{\"action_type\":\"PREDICT\","
            + "\"method\":\"POST\",\"url\":\"https://test.com\",\"headers\":{\"api_key\":\"${credential.key}\"},"
            + "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\","
            + "\"pre_process_function\":\"connector.pre_process.openai.embedding\","
            + "\"post_process_function\":\"connector.post_process.openai.embedding\"}],"
            + "\"backend_roles\":[\"role1\",\"role2\"],\"access\":\"public\","
            + "\"client_config\":{\"max_connection\":30,\"connection_timeout\":30000,\"read_timeout\":30000,"
            + "\"retry_backoff_millis\":10,\"retry_timeout_seconds\":10,\"max_retry_times\":-1,\"retry_backoff_policy\":\"constant\"}},\"is_hidden\":false}";

        assertEquals(expectedIncompleteInputStr, jsonStr);
    }

    @Test
    public void parse_WithModel() throws Exception {
        testParseFromJsonString("modelNameInsideTest", "versionInsideTest", true, expectedInputStr, parsedInput -> {
            assertEquals(FunctionName.TEXT_EMBEDDING, parsedInput.getFunctionName());
            assertEquals("modelNameInsideTest", parsedInput.getModelName());
            assertEquals("versionInsideTest", parsedInput.getVersion());
        });
    }

    @Test
    public void parse_WithoutModel() throws Exception {
        testParseFromJsonString(false, expectedInputStr, parsedInput -> {
            assertFalse(parsedInput.isDeployModel());
            assertEquals("modelName", parsedInput.getModelName());
            assertEquals("version", parsedInput.getVersion());
        });
    }

    @Test
    public void parse_WithRemoteModel() throws Exception {
        String remoteModelInput = "{"
            + "\"function_name\": \"remote\","
            + "\"model_config\": {"
            + "\"model_type\": \"text_embedding\","
            + "\"embedding_dimension\": 768,"
            + "\"framework_type\": \"SENTENCE_TRANSFORMERS\","
            + "\"additional_config\": {"
            + "\"space_type\": \"l2\""
            + "}}}";

        testParseFromJsonString("remoteModelName", "1.0", true, remoteModelInput, parsedInput -> {
            assertEquals(FunctionName.REMOTE, parsedInput.getFunctionName());
            assertTrue(parsedInput.getModelConfig() instanceof RemoteModelConfig);
            RemoteModelConfig remoteConfig = (RemoteModelConfig) parsedInput.getModelConfig();
            assertEquals("text_embedding", remoteConfig.getModelType());
            assertEquals(768, remoteConfig.getEmbeddingDimension().intValue());
            assertEquals(RemoteModelConfig.FrameworkType.SENTENCE_TRANSFORMERS, remoteConfig.getFrameworkType());
            Map<String, Object> additionalConfig = remoteConfig.getAdditionalConfig();
            assertNotNull(additionalConfig);
            assertEquals("l2", additionalConfig.get("space_type"));
        });
    }

    @Test
    public void parse_WithBaseModel() throws Exception {
        String baseModelInput = "{"
            + "\"function_name\": \"SPARSE_ENCODING\","
            + "\"model_format\": \"TORCH_SCRIPT\","
            + "\"model_config\": {"
            + "\"model_type\": \"sparse_encoding\","
            + "\"all_config\": \"{\\\"key\\\": \\\"value\\\"}\","
            + "\"additional_config\": {"
            + "\"space_type\": \"l2\""
            + "}}}";

        testParseFromJsonString("baseModelName", "1.0", true, baseModelInput, parsedInput -> {
            assertEquals(FunctionName.SPARSE_ENCODING, parsedInput.getFunctionName());
            assertTrue(parsedInput.getModelConfig() instanceof BaseModelConfig);
            BaseModelConfig baseConfig = (BaseModelConfig) parsedInput.getModelConfig();
            assertEquals("sparse_encoding", baseConfig.getModelType());
            assertEquals("{\"key\": \"value\"}", baseConfig.getAllConfig());
            Map<String, Object> additionalConfig = baseConfig.getAdditionalConfig();
            assertNotNull(additionalConfig);
            assertEquals("l2", additionalConfig.get("space_type"));
        });
    }

    private void testParseFromJsonString(
        String modelName,
        String version,
        Boolean deployModel,
        String expectedInputStr,
        Consumer<MLRegisterModelInput> verify
    ) throws Exception {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                expectedInputStr
            );
        parser.nextToken();
        MLRegisterModelInput parsedInput = MLRegisterModelInput.parse(parser, modelName, version, deployModel);
        verify.accept(parsedInput);
    }

    private void testParseFromJsonString(Boolean deployModel, String expectedInputStr, Consumer<MLRegisterModelInput> verify)
        throws Exception {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                expectedInputStr
            );
        parser.nextToken();
        MLRegisterModelInput parsedInput = MLRegisterModelInput.parse(parser, deployModel);
        verify.accept(parsedInput);
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(input, parsedInput -> {
            assertEquals(input.getFunctionName(), parsedInput.getFunctionName());
            assertEquals(input.getModelName(), parsedInput.getModelName());
        });
    }

    @Test
    public void readInputStream_SuccessWithNullFields() throws IOException {
        input.setModelFormat(null);
        input.setModelConfig(null);
        readInputStream(input, parsedInput -> {
            assertNull(parsedInput.getModelConfig());
            assertNull(parsedInput.getModelFormat());
        });
    }

    @Test
    public void readInputStream_WithConnectorId() throws IOException {
        String connectorId = "test_connector_id";
        input = MLRegisterModelInput
            .builder()
            .functionName(FunctionName.REMOTE)
            .modelName(modelName)
            .description("test model input")
            .version(version)
            .modelGroupId(modelGroupId)
            .connectorId(connectorId)
            .build();
        readInputStream(input, parsedInput -> {
            assertNull(parsedInput.getModelConfig());
            assertNull(parsedInput.getModelFormat());
            assertNull(parsedInput.getConnector());
            assertEquals(connectorId, parsedInput.getConnectorId());
        });
    }

    @Test
    public void readInputStream_WithInternalConnector() throws IOException {
        HttpConnector connector = HttpConnectorTest.createHttpConnector();
        input = MLRegisterModelInput
            .builder()
            .functionName(FunctionName.REMOTE)
            .modelName(modelName)
            .description("test model input")
            .version(version)
            .modelGroupId(modelGroupId)
            .connector(connector)
            .build();
        readInputStream(input, parsedInput -> {
            assertNull(parsedInput.getModelConfig());
            assertNull(parsedInput.getModelFormat());
            assertEquals(input.getConnector(), parsedInput.getConnector());
        });
    }

    @Test
    public void testMCorrInput() throws IOException {
        String testString =
            "{\"function_name\":\"METRICS_CORRELATION\",\"name\":\"METRICS_CORRELATION\",\"version\":\"1.0.0b1\",\"model_group_id\":\"modelGroupId\",\"url\":\"url\",\"model_format\":\"TORCH_SCRIPT\",\"model_config\":{\"model_type\":\"testModelType\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"},\"deploy_model\":true,\"model_node_ids\":[\"modelNodeIds\"]}";

        MetricsCorrelationModelConfig mcorrConfig = MetricsCorrelationModelConfig
            .builder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .build();

        MLRegisterModelInput mcorrInput = MLRegisterModelInput
            .builder()
            .functionName(FunctionName.METRICS_CORRELATION)
            .modelName(FunctionName.METRICS_CORRELATION.name())
            .version("1.0.0b1")
            .modelGroupId(modelGroupId)
            .url(url)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelConfig(mcorrConfig)
            .deployModel(true)
            .modelNodeIds(new String[] { "modelNodeIds" })
            .build();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        mcorrInput.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertEquals(testString, jsonStr);
    }

    @Test
    public void readInputStream_MCorr() throws IOException {
        MetricsCorrelationModelConfig mcorrConfig = MetricsCorrelationModelConfig
            .builder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .build();

        MLRegisterModelInput mcorrInput = MLRegisterModelInput
            .builder()
            .functionName(FunctionName.METRICS_CORRELATION)
            .modelName(FunctionName.METRICS_CORRELATION.name())
            .version("1.0.0b1")
            .modelGroupId(modelGroupId)
            .url(url)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelConfig(mcorrConfig)
            .deployModel(true)
            .modelNodeIds(new String[] { "modelNodeIds" })
            .build();
        readInputStream(mcorrInput, parsedInput -> {
            assertEquals(parsedInput.getModelConfig().getModelType(), mcorrConfig.getModelType());
            assertEquals(parsedInput.getModelConfig().getAllConfig(), mcorrConfig.getAllConfig());
            assertEquals(parsedInput.getFunctionName(), FunctionName.METRICS_CORRELATION);
            assertEquals(parsedInput.getModelName(), FunctionName.METRICS_CORRELATION.name());
            assertEquals(parsedInput.getModelGroupId(), modelGroupId);
        });
    }

    @Test
    public void testEmbeddingInput() throws IOException {
        String testString =
            "{\"function_name\":\"TEXT_EMBEDDING\",\"name\":\"TEXT_EMBEDDING\",\"version\":\"1.0.0\",\"model_group_id\":\"modelGroupId\",\"url\":\"url\",\"model_format\":\"TORCH_SCRIPT\",\"model_config\":{\"model_type\":\"testModelType\",\"embedding_dimension\":768,\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\",\"normalize_result\":true},\"deploy_model\":true,\"model_node_ids\":[\"modelNodeIds\"]}";

        TextEmbeddingModelConfig embeddingConfig = TextEmbeddingModelConfig
            .builder()
            .modelType("testModelType")
            .embeddingDimension(768)
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .normalizeResult(true)
            .build();

        MLRegisterModelInput embeddingInput = MLRegisterModelInput
            .builder()
            .functionName(FunctionName.TEXT_EMBEDDING)
            .modelName(FunctionName.TEXT_EMBEDDING.name())
            .version("1.0.0")
            .modelGroupId(modelGroupId)
            .url(url)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelConfig(embeddingConfig)
            .deployModel(true)
            .modelNodeIds(new String[] { "modelNodeIds" })
            .build();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        embeddingInput.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertEquals(testString, jsonStr);
    }

    @Test
    public void readInputStream_Embedding() throws IOException {
        TextEmbeddingModelConfig embeddingConfig = TextEmbeddingModelConfig
            .builder()
            .modelType("testModelType")
            .embeddingDimension(768)
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .normalizeResult(true)
            .build();

        MLRegisterModelInput embeddingInput = MLRegisterModelInput
            .builder()
            .functionName(FunctionName.TEXT_EMBEDDING)
            .modelName(FunctionName.TEXT_EMBEDDING.name())
            .version("1.0.0")
            .modelGroupId(modelGroupId)
            .url(url)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelConfig(embeddingConfig)
            .deployModel(true)
            .modelNodeIds(new String[] { "modelNodeIds" })
            .build();
        readInputStream(embeddingInput, parsedInput -> {
            assertTrue(parsedInput.getModelConfig() instanceof TextEmbeddingModelConfig);
            TextEmbeddingModelConfig parsedConfig = (TextEmbeddingModelConfig) parsedInput.getModelConfig();

            assertEquals(parsedConfig.getModelType(), embeddingConfig.getModelType());
            assertEquals(parsedConfig.getAllConfig(), embeddingConfig.getAllConfig());
            assertEquals(parsedConfig.getEmbeddingDimension(), embeddingConfig.getEmbeddingDimension());
            assertEquals(parsedConfig.getFrameworkType(), embeddingConfig.getFrameworkType());
            assertEquals(parsedConfig.isNormalizeResult(), embeddingConfig.isNormalizeResult());

            assertEquals(parsedInput.getFunctionName(), FunctionName.TEXT_EMBEDDING);
            assertEquals(parsedInput.getModelName(), FunctionName.TEXT_EMBEDDING.name());
            assertEquals(parsedInput.getModelGroupId(), modelGroupId);
        });
    }

    @Test
    public void testBaseModelInput() throws IOException {
        String testString =
            "{\"function_name\":\"SPARSE_ENCODING\",\"name\":\"SPARSE_ENCODING\",\"version\":\"1.0.0\",\"model_group_id\":\"modelGroupId\",\"url\":\"url\",\"model_format\":\"TORCH_SCRIPT\",\"model_config\":{\"model_type\":\"testModelType\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\",\"additional_config\":{\"space_type\":\"l2\"}},\"deploy_model\":true,\"model_node_ids\":[\"modelNodeIds\"]}";

        Map<String, Object> additionalConfig = new HashMap<>();
        additionalConfig.put("space_type", "l2");

        BaseModelConfig baseConfig = BaseModelConfig
            .baseModelConfigBuilder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .additionalConfig(additionalConfig)
            .build();

        MLRegisterModelInput generalInput = MLRegisterModelInput
            .builder()
            .functionName(FunctionName.SPARSE_ENCODING)
            .modelName("SPARSE_ENCODING")
            .version("1.0.0")
            .modelGroupId(modelGroupId)
            .url(url)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelConfig(baseConfig)
            .deployModel(true)
            .modelNodeIds(new String[] { "modelNodeIds" })
            .build();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        generalInput.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertEquals(testString, jsonStr);
    }

    @Test
    public void readInputStream_Base() throws IOException {
        Map<String, Object> additionalConfig = new HashMap<>();
        additionalConfig.put("space_type", "l2");

        BaseModelConfig baseConfig = BaseModelConfig
            .baseModelConfigBuilder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .additionalConfig(additionalConfig)
            .build();

        MLRegisterModelInput generalInput = MLRegisterModelInput
            .builder()
            .functionName(FunctionName.SPARSE_ENCODING)
            .modelName("SPARSE_ENCODING")
            .version("1.0.0")
            .modelGroupId(modelGroupId)
            .url(url)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelConfig(baseConfig)
            .deployModel(true)
            .modelNodeIds(new String[] { "modelNodeIds" })
            .build();
        readInputStream(generalInput, parsedInput -> {
            assertEquals(parsedInput.getModelConfig().getModelType(), baseConfig.getModelType());
            assertEquals(parsedInput.getModelConfig().getAllConfig(), baseConfig.getAllConfig());
            assertEquals(((BaseModelConfig) parsedInput.getModelConfig()).getAdditionalConfig(), additionalConfig);
            assertEquals(parsedInput.getFunctionName(), FunctionName.SPARSE_ENCODING);
            assertEquals(parsedInput.getModelName(), "SPARSE_ENCODING");
            assertEquals(parsedInput.getModelGroupId(), modelGroupId);
        });
    }

    @Test
    public void testRemoteModelInput() throws IOException {
        String testString =
            "{\"function_name\":\"REMOTE\",\"name\":\"test_remote_model\",\"model_group_id\":\"modelGroupId\",\"model_config\":{\"model_type\":\"testModelType\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\",\"additional_config\":{\"space_type\":\"l2\"}},\"deploy_model\":false,\"connector_id\":\"connectorId\"}";
        String connectorId = "connectorId";
        Map<String, Object> additionalConfig = new HashMap<>();
        additionalConfig.put("space_type", "l2");
        RemoteModelConfig remoteConfig = RemoteModelConfig
            .builder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .additionalConfig(additionalConfig)
            .build();
        MLRegisterModelInput remoteInput = MLRegisterModelInput
            .builder()
            .functionName(FunctionName.REMOTE)
            .modelName("test_remote_model")
            .modelGroupId(modelGroupId)
            .connectorId(connectorId)
            .modelConfig(remoteConfig)
            .deployModel(false)
            .build();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        remoteInput.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertEquals(testString, jsonStr);
    }

    @Test
    public void readInputStream_Remote() throws IOException {
        Map<String, Object> additionalConfig = new HashMap<>();
        additionalConfig.put("space_type", "l2");
        String connectorId = "connectorId";
        RemoteModelConfig remoteConfig = RemoteModelConfig
            .builder()
            .modelType("test")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .additionalConfig(additionalConfig)
            .build();
        MLRegisterModelInput remoteInput = MLRegisterModelInput
            .builder()
            .functionName(FunctionName.REMOTE)
            .modelName("test_remote_model")
            .modelGroupId(modelGroupId)
            .connectorId(connectorId)
            .modelConfig(remoteConfig)
            .deployModel(false)
            .build();
        readInputStream(remoteInput, parsedInput -> {
            assertEquals(parsedInput.getModelConfig().getModelType(), remoteConfig.getModelType());
            assertEquals(parsedInput.getModelConfig().getAllConfig(), remoteConfig.getAllConfig());
            assertEquals(((BaseModelConfig) parsedInput.getModelConfig()).getAdditionalConfig(), additionalConfig);
            assertEquals(parsedInput.getFunctionName(), FunctionName.REMOTE);
            assertEquals(parsedInput.getModelName(), "test_remote_model");
            assertEquals(parsedInput.getModelGroupId(), modelGroupId);
            assertEquals(parsedInput.getConnectorId(), connectorId);
        });
    }

    @Test
    public void readInputStream_withTenantId_Success() throws IOException {
        // Add tenantId to input
        input = input.toBuilder().tenantId("tenant-1").build();

        // Serialize with newer version
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        bytesStreamOutput.setVersion(VERSION_2_19_0);
        input.writeTo(bytesStreamOutput);

        // Deserialize and verify tenantId
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        streamInput.setVersion(VERSION_2_19_0);
        MLRegisterModelInput parsedInput = new MLRegisterModelInput(streamInput);

        assertEquals("tenant-1", parsedInput.getTenantId());
    }

    @Test
    public void toXContent_withTenantId_Success() throws IOException {
        // Add tenantId to input
        input = input.toBuilder().tenantId("tenant-1").build();

        // Convert to XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        // Verify tenantId is serialized correctly
        assertTrue(jsonStr.contains("\"tenant_id\":\"tenant-1\""));
    }

    @Test
    public void toXContent_withoutTenantId_Success() throws IOException {
        // Ensure input does not have tenantId
        input = input.toBuilder().tenantId(null).build();

        // Convert to XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        // Verify tenantId is not present
        assertFalse(jsonStr.contains("\"tenant_id\""));
    }

    private void readInputStream(MLRegisterModelInput input, Consumer<MLRegisterModelInput> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLRegisterModelInput parsedInput = new MLRegisterModelInput(streamInput);
        verify.accept(parsedInput);
    }
}
