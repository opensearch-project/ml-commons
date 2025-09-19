package org.opensearch.ml.common.output.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.output.MLOutputType;

public class ModelTensorOutputTest {

    Float[] value;
    ModelTensorOutput modelTensorOutput;

    @Before
    public void setUp() throws Exception {
        value = new Float[] { 1.0f, 2.0f, 3.0f };
        List<ModelTensors> outputs = new ArrayList<>();
        ModelTensor tensor = ModelTensor
            .builder()
            .data(value)
            .name("test")
            .shape(new long[] { 1, 3 })
            .dataType(MLResultDataType.FLOAT32)
            .byteBuffer(ByteBuffer.wrap(new byte[] { 0, 1, 0, 1 }))
            .build();
        List<ModelTensor> mlModelTensors = Arrays.asList(tensor);
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(mlModelTensors).build();
        outputs.add(modelTensors);
        modelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(outputs).build();
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(modelTensorOutput, parsedTensorOutput -> {
            assertEquals(MLOutputType.MODEL_TENSOR, parsedTensorOutput.getType());
            assertEquals(1, parsedTensorOutput.getMlModelOutputs().size());
            ModelTensors modelTensors = parsedTensorOutput.getMlModelOutputs().get(0);
            assertEquals(1, modelTensors.getMlModelTensors().size());
            ModelTensor modelTensor = modelTensors.getMlModelTensors().get(0);
            assertArrayEquals(value, modelTensor.getData());
        });
    }

    @Test
    public void readInputStream_NullField() throws IOException {
        ModelTensorOutput modelTensorOutput = ModelTensorOutput.builder().build();
        readInputStream(modelTensorOutput, parsedTensorOutput -> {
            assertEquals(MLOutputType.MODEL_TENSOR, parsedTensorOutput.getType());
            assertNull(parsedTensorOutput.getMlModelOutputs());
        });
    }

    @Test
    public void parse_Success() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.startArray(ModelTensorOutput.INFERENCE_RESULT_FIELD);

        builder.startObject();
        builder.startArray("output");

        builder.startObject();
        builder.field("name", "test");
        builder.field("data_type", "FLOAT32");
        builder.field("shape", new long[] { 1, 3 });
        builder.field("data", value);
        builder.endObject();

        builder.endArray();
        builder.endObject();

        builder.endArray();
        builder.endObject();

        String jsonStr = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        ModelTensorOutput parsedOutput = ModelTensorOutput.parse(parser);

        assertEquals(1, parsedOutput.getMlModelOutputs().size());
        ModelTensors modelTensors = parsedOutput.getMlModelOutputs().get(0);
        assertEquals(1, modelTensors.getMlModelTensors().size());
        ModelTensor modelTensor = modelTensors.getMlModelTensors().get(0);
        assertEquals("test", modelTensor.getName());
        assertEquals(value.length, modelTensor.getData().length);
        assertEquals(value[0].doubleValue(), modelTensor.getData()[0].doubleValue(), 0.0001);
        assertArrayEquals(new long[] { 1, 3 }, modelTensor.getShape());
        assertEquals(MLResultDataType.FLOAT32, modelTensor.getDataType());
    }

    @Test
    public void parse_EmptyObject() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.endObject();

        String jsonStr = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        ModelTensorOutput parsedOutput = ModelTensorOutput.parse(parser);

        assertEquals(0, parsedOutput.getMlModelOutputs().size());
    }

    @Test
    public void parse_SkipIrrelevantFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();

        builder.field("irrelevant_field", "irrelevant_value");

        builder.startArray(ModelTensorOutput.INFERENCE_RESULT_FIELD);
        builder.startObject();
        builder.startArray("output");
        builder.startObject();
        builder.field("name", "test");
        builder.field("data_type", "FLOAT32");
        builder.field("shape", new long[] { 1, 3 });
        builder.field("data", value);
        builder.endObject();
        builder.endArray();
        builder.endObject();
        builder.endArray();

        builder.field("another_irrelevant_field", "another_value");

        builder.endObject();

        String jsonStr = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        ModelTensorOutput parsedOutput = ModelTensorOutput.parse(parser);

        assertEquals(1, parsedOutput.getMlModelOutputs().size());
        ModelTensors modelTensors = parsedOutput.getMlModelOutputs().get(0);
        assertEquals(1, modelTensors.getMlModelTensors().size());
        ModelTensor modelTensor = modelTensors.getMlModelTensors().get(0);
        assertEquals("test", modelTensor.getName());
        assertEquals(value.length, modelTensor.getData().length);
        assertEquals(value[0].doubleValue(), modelTensor.getData()[0].doubleValue(), 0.0001);
        assertArrayEquals(new long[] { 1, 3 }, modelTensor.getShape());
    }

    @Test
    public void test_ToString() {
        String result = modelTensorOutput.toString();
        String expected =
            "{\"inference_results\":[{\"output\":[{\"name\":\"test\",\"data_type\":\"FLOAT32\",\"shape\":[1,3],\"data\":[1.0,2.0,3.0],\"byte_buffer\":{\"array\":\"AAEAAQ==\",\"order\":\"BIG_ENDIAN\"}}]}]}";
        assertEquals(expected, result);
    }

    private void readInputStream(ModelTensorOutput input, Consumer<ModelTensorOutput> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLOutputType outputType = streamInput.readEnum(MLOutputType.class);
        assertEquals(MLOutputType.MODEL_TENSOR, outputType);
        verify.accept(new ModelTensorOutput(streamInput));
    }
}
