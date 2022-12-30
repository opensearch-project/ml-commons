package org.opensearch.ml.common.transport.upload;

import org.junit.Rule;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.*;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.search.SearchModule;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;


import java.io.IOException;
import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class MLUploadInputTest {

    @Mock
    MLModelConfig config;
    MLUploadInput input;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    private final String expectedInputStr = "{\"function_name\":\"LINEAR_REGRESSION\",\"name\":\"modelName\",\"version\":\"version\",\"url\":\"url\",\"model_format\":\"ONNX\"," +
            "\"model_config\":{\"model_type\":\"testModelType\",\"embedding_dimension\":100,\"framework_type\":\"SENTENCE_TRANSFORMERS\"," +
            "\"all_config\":\"{\\\"field1\\\":\\\"value1\\\",\\\"field2\\\":\\\"value2\\\"}\"" +
            ",\"pooling_method\":\"MEAN\",\"normalize_result\":false},\"load_model\":true,\"model_node_ids\":[\"modelNodeIds\"]}";
    private final FunctionName functionName = FunctionName.LINEAR_REGRESSION;
    private final String modelName = "modelName";
    private final String version = "version";
    private final String url = "url";

    @Before
    public void setUp() throws Exception {
        config = TextEmbeddingModelConfig.builder()
                .modelType("testModelType")
                .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
                .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
                .embeddingDimension(100)
                .build();

        input = MLUploadInput.builder()
                .functionName(functionName)
                .modelName(modelName)
                .version(version)
                .url(url)
                .modelFormat(MLModelFormat.ONNX)
                .modelConfig(config)
                .loadModel(true)
                .modelNodeIds(new String[]{"modelNodeIds" })
                .build();

    }

    @Test
    public void constructor_NullModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model name is null");
        MLUploadInput.builder().build();
    }

    @Test
    public void constructor_NullModelName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model name is null");
        MLUploadInput.builder()
                .functionName(functionName)
                .modelName(null)
                .build();
    }

    @Test
    public void constructor_NullModelVersion() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model version is null");
        MLUploadInput.builder()
                .functionName(functionName)
                .modelName(modelName)
                .version(null)
                .build();
    }

    @Test
    public void constructor_NullModelFormat() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model format is null");
        MLUploadInput.builder()
                .functionName(functionName)
                .modelName(modelName)
                .version(version)
                .modelFormat(null)
                .url(url)
                .build();
    }

    @Test
    public void constructor_NullModelConfig() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model config is null");
        MLUploadInput.builder()
                .functionName(functionName)
                .modelName(modelName)
                .version(version)
                .modelFormat(MLModelFormat.ONNX)
                .modelConfig(null)
                .url(url)
                .build();
    }

    @Test
    public void constructor_SuccessWithMinimalSetup() {
        MLUploadInput input = MLUploadInput.builder()
                .modelName(modelName)
                .version(version)
                .modelFormat(MLModelFormat.ONNX)
                .modelConfig(config)
                .url(url)
                .build();
        // MLUploadInput.functionName is set to FunctionName.TEXT_EMBEDDING if not explicitly passed, with no exception thrown
        assertEquals(FunctionName.TEXT_EMBEDDING, input.getFunctionName());
        // MLUploadInput.loadModel is set to false if not explicitly passed, with no exception thrown
        assertFalse(input.isLoadModel());
        // MLUploadInput.loadModel is set to null if not explicitly passed, with no exception thrown
        assertNull(input.getModelNodeIds());
    }

    @Test
    public void testToXContent() throws Exception {
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);
        assertEquals(expectedInputStr, jsonStr);
    }

    @Test
    public void testToXContent_Incomplete() throws Exception {
        String expectedIncompleteInputStr =
                "{\"function_name\":\"LINEAR_REGRESSION\",\"name\":\"modelName\"," +
                "\"version\":\"version\",\"load_model\":true}";
        input.setUrl(null);
        input.setModelConfig(null);
        input.setModelFormat(null);
        input.setModelNodeIds(null);
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);
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
            assertFalse(parsedInput.isLoadModel());
            assertEquals("modelName", parsedInput.getModelName());
            assertEquals("version", parsedInput.getVersion());
        });
    }

    private void testParseFromJsonString(String modelName, String version, Boolean loadModel, String expectedInputStr, Consumer<MLUploadInput> verify) throws Exception {
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), LoggingDeprecationHandler.INSTANCE, expectedInputStr);
        parser.nextToken();
        MLUploadInput parsedInput = MLUploadInput.parse(parser, modelName, version, loadModel);
        verify.accept(parsedInput);
    }

    private void testParseFromJsonString(Boolean loadModel,String expectedInputStr, Consumer<MLUploadInput> verify) throws Exception {
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), LoggingDeprecationHandler.INSTANCE, expectedInputStr);
        parser.nextToken();
        MLUploadInput parsedInput = MLUploadInput.parse(parser, loadModel);
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


    private void readInputStream(MLUploadInput input, Consumer<MLUploadInput> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLUploadInput parsedInput = new MLUploadInput(streamInput);
        verify.accept(parsedInput);
    }
}
