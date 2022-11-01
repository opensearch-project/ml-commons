package org.opensearch.ml.common.transport.upload;

import org.hamcrest.Matcher;
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
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.search.SearchModule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class MLUploadInputTest {

    @Mock
    private MLModelConfig config;
    MLUploadInput input;


    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    private final FunctionName functionName = FunctionName.LINEAR_REGRESSION;
    private final String modelName = "modelName";
    private final String version = "version";
    private final String url = "url";
    private final boolean loadModel = true;

    @Before
    public void setUp() throws Exception {
        input = MLUploadInput.builder()
                .functionName(functionName)
                .modelName(modelName)
                .version(version)
                .url(url)
                .modelFormat(MLModelFormat.ONNX)
                .modelConfig(config)
                .loadModel(loadModel)
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
                .build();
    }

    @Test
    public void constructor_NullModelFileUrl() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model file url is null");
        MLUploadInput.builder()
                .functionName(functionName)
                .modelName(modelName)
                .version(version)
                .modelFormat(MLModelFormat.ONNX)
                .modelConfig(config)
                .url(null)
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

/*

    @Test
    public void testParse1() throws Exception {
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
    }

    @Test
    public void parse_TextEmbedding_NullResultFilter() throws Exception {
        String expectedInputStr = "{\"algorithm\":\"TEXT_EMBEDDING\",\"text_docs\":[\"test sentence\"]}";
        testParse("modelName", "version", true, expectedInputStr, parsedInput -> {
            assertNotNull(parsedInput.getFunctionName());});
    }


    private void testParse(String modelName, String version, Boolean loadModel, String expectedInputStr, Consumer<MLUploadInput> verify) throws Exception {
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        // JsonGenerationException: Can not write a field name, expecting a value
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);
        assertEquals(expectedInputStr, jsonStr);
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();
        MLUploadInput parsedInput = MLUploadInput.parse(parser, modelName, version, loadModel);
        verify.accept(parsedInput);
    }

*/
}
