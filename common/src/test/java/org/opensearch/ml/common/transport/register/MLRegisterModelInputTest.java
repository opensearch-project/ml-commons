package org.opensearch.ml.common.transport.register;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.HttpConnectorTest;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MetricsCorrelationModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.search.SearchModule;

import java.io.IOException;
import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(MockitoJUnitRunner.class)
public class MLRegisterModelInputTest {

    @Mock
    MLModelConfig config;
    MLRegisterModelInput input;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    private final String expectedInputStr = "{\"function_name\":\"LINEAR_REGRESSION\",\"name\":\"modelName\"," +
            "\"version\":\"version\",\"model_group_id\":\"modelGroupId\",\"description\":\"test description\"," +
            "\"url\":\"url\",\"model_content_hash_value\":\"hash_value_test\",\"model_format\":\"ONNX\"," +
            "\"model_config\":{\"model_type\":\"testModelType\",\"embedding_dimension\":100," +
            "\"framework_type\":\"SENTENCE_TRANSFORMERS\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\"," +
            "\\\"field2\\\":\\\"value2\\\"}\"},\"deploy_model\":true,\"model_node_ids\":[\"modelNodeIds\"]," +
            "\"connector\":{\"name\":\"test_connector_name\",\"version\":\"1\"," +
            "\"description\":\"this is a test connector\",\"protocol\":\"http\"," +
            "\"parameters\":{\"input\":\"test input value\"},\"credential\":{\"key\":\"test_key_value\"}," +
            "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\"https://test.com\"," +
            "\"headers\":{\"api_key\":\"${credential.key}\"}," +
            "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\"," +
            "\"pre_process_function\":\"connector.pre_process.openai.embedding\"," +
            "\"post_process_function\":\"connector.post_process.openai.embedding\"}]," +
            "\"backend_roles\":[\"role1\",\"role2\"],\"access\":\"public\"," +
            "\"client_config\":{\"max_connection\":30,\"connection_timeout\":30000," +
            "\"read_timeout\":30000}},\"is_hidden\":false}";
    private final FunctionName functionName = FunctionName.LINEAR_REGRESSION;
    private final String modelName = "modelName";
    private final String version = "version";
    private final String url = "url";

    private final String modelGroupId = "modelGroupId";

    @Before
    public void setUp() throws Exception {
        config = TextEmbeddingModelConfig.builder()
                .modelType("testModelType")
                .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
                .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
                .embeddingDimension(100)
                .build();
        HttpConnector connector = HttpConnectorTest.createHttpConnector();

        input = MLRegisterModelInput.builder()
                .functionName(functionName)
                .modelName(modelName)
                .version(version)
                .modelGroupId(modelGroupId)
                .url(url)
                .modelFormat(MLModelFormat.ONNX)
                .modelConfig(config)
                .deployModel(true)
                .modelNodeIds(new String[]{"modelNodeIds" })
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
        MLRegisterModelInput.builder()
                .functionName(functionName)
                .modelGroupId(modelGroupId)
                .modelName(null)
                .build();
    }

    @Test
    public void constructor_NullModelFormat() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model format is null");
        MLRegisterModelInput.builder()
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
        MLRegisterModelInput.builder()
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
        MLRegisterModelInput input = MLRegisterModelInput.builder()
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
        XContentBuilder builder = XContentFactory.jsonBuilder();
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals(expectedInputStr, jsonStr);
    }

    @Test
    public void testToXContent_Incomplete() throws Exception {
        String expectedIncompleteInputStr = "{\"function_name\":\"LINEAR_REGRESSION\"," +
                "\"name\":\"modelName\",\"version\":\"version\",\"model_group_id\":\"modelGroupId\"," +
                "\"description\":\"test description\",\"model_content_hash_value\":\"hash_value_test\"," +
                "\"deploy_model\":true,\"connector\":{\"name\":\"test_connector_name\",\"version\":\"1\"," +
                "\"description\":\"this is a test connector\",\"protocol\":\"http\"," +
                "\"parameters\":{\"input\":\"test input value\"}," +
                "\"credential\":{\"key\":\"test_key_value\"},\"actions\":[{\"action_type\":\"PREDICT\"," +
                "\"method\":\"POST\",\"url\":\"https://test.com\",\"headers\":{\"api_key\":\"${credential.key}\"}," +
                "\"request_body\":\"{\\\"input\\\": \\\"${parameters.input}\\\"}\"," +
                "\"pre_process_function\":\"connector.pre_process.openai.embedding\"," +
                "\"post_process_function\":\"connector.post_process.openai.embedding\"}]," +
                "\"backend_roles\":[\"role1\",\"role2\"],\"access\":\"public\"," +
                "\"client_config\":{\"max_connection\":30,\"connection_timeout\":30000," +
                "\"read_timeout\":30000}},\"is_hidden\":false}";
        input.setUrl(null);
        input.setModelConfig(null);
        input.setModelFormat(null);
        input.setModelNodeIds(null);
        XContentBuilder builder = XContentFactory.jsonBuilder();
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals(expectedIncompleteInputStr, jsonStr);
    }

    @Test
    public void parse_WithModel() throws Exception {
        testParseFromJsonString("modelNameInsideTest", "versionInsideTest", true, expectedInputStr, parsedInput -> {
            assertEquals(FunctionName.LINEAR_REGRESSION, parsedInput.getFunctionName());
            assertEquals("modelNameInsideTest", parsedInput.getModelName());
            assertEquals("versionInsideTest", parsedInput.getVersion());
        });
    }

    @Test
    public void parse_WithoutModel() throws Exception {
        testParseFromJsonString( false, expectedInputStr, parsedInput -> {
            assertFalse(parsedInput.isDeployModel());
            assertEquals("modelName", parsedInput.getModelName());
            assertEquals("version", parsedInput.getVersion());
        });
    }

    private void testParseFromJsonString(String modelName, String version, Boolean deployModel, String expectedInputStr, Consumer<MLRegisterModelInput> verify) throws Exception {
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), LoggingDeprecationHandler.INSTANCE, expectedInputStr);
        parser.nextToken();
        MLRegisterModelInput parsedInput = MLRegisterModelInput.parse(parser, modelName, version, deployModel);
        verify.accept(parsedInput);
    }

    private void testParseFromJsonString(Boolean deployModel,String expectedInputStr, Consumer<MLRegisterModelInput> verify) throws Exception {
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), LoggingDeprecationHandler.INSTANCE, expectedInputStr);
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
        input = MLRegisterModelInput.builder()
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
        input = MLRegisterModelInput.builder()
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
        String testString = "{\"function_name\":\"METRICS_CORRELATION\",\"name\":\"METRICS_CORRELATION\",\"version\":\"1.0.0b1\",\"model_group_id\":\"modelGroupId\",\"url\":\"url\",\"model_format\":\"TORCH_SCRIPT\",\"model_config\":{\"model_type\":\"testModelType\",\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"},\"deploy_model\":true,\"model_node_ids\":[\"modelNodeIds\"]}";

        MetricsCorrelationModelConfig mcorrConfig = MetricsCorrelationModelConfig.builder()
                .modelType("testModelType")
                .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
                .build();

        MLRegisterModelInput mcorrInput = MLRegisterModelInput.builder()
                .functionName(FunctionName.METRICS_CORRELATION)
                .modelName(FunctionName.METRICS_CORRELATION.name())
                .version("1.0.0b1")
                .modelGroupId(modelGroupId)
                .url(url)
                .modelFormat(MLModelFormat.TORCH_SCRIPT)
                .modelConfig(mcorrConfig)
                .deployModel(true)
                .modelNodeIds(new String[]{"modelNodeIds" })
                .build();
        XContentBuilder builder = XContentFactory.jsonBuilder();
        mcorrInput.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertEquals(testString, jsonStr);
    }

    @Test
    public void readInputStream_MCorr() throws IOException {
        MetricsCorrelationModelConfig mcorrConfig = MetricsCorrelationModelConfig.builder()
                .modelType("testModelType")
                .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
                .build();

        MLRegisterModelInput mcorrInput = MLRegisterModelInput.builder()
                .functionName(FunctionName.METRICS_CORRELATION)
                .modelName(FunctionName.METRICS_CORRELATION.name())
                .version("1.0.0b1")
                .modelGroupId(modelGroupId)
                .url(url)
                .modelFormat(MLModelFormat.TORCH_SCRIPT)
                .modelConfig(mcorrConfig)
                .deployModel(true)
                .modelNodeIds(new String[]{"modelNodeIds" })
                .build();
        readInputStream(mcorrInput, parsedInput -> {
            assertEquals(parsedInput.getModelConfig().getModelType(), mcorrConfig.getModelType());
            assertEquals(parsedInput.getModelConfig().getAllConfig(), mcorrConfig.getAllConfig());
            assertEquals(parsedInput.getFunctionName(), FunctionName.METRICS_CORRELATION);
            assertEquals(parsedInput.getModelName(), FunctionName.METRICS_CORRELATION.name());
            assertEquals(parsedInput.getModelGroupId(), modelGroupId);
        });
    }

    private void readInputStream(MLRegisterModelInput input, Consumer<MLRegisterModelInput> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLRegisterModelInput parsedInput = new MLRegisterModelInput(streamInput);
        verify.accept(parsedInput);
    }
}
