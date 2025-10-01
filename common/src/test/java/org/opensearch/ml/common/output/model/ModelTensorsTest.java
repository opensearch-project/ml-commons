/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class ModelTensorsTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    private ModelTensors modelTensors;
    private ModelResultFilter modelResultFilter;
    private Number[] testData;

    @Before
    public void setUp() {
        String sentence = "test sentence";
        String column = "model_tensor";
        Integer position = 1;
        modelResultFilter = ModelResultFilter
            .builder()
            .targetResponse(Arrays.asList(column))
            .targetResponsePositions(Arrays.asList(position))
            .build();

        testData = new Number[] { 1, 2, 3 };
        ModelTensor modelTensor = ModelTensor
            .builder()
            .name("model_tensor")
            .data(testData)
            .shape(new long[] { 1, 2, 3, })
            .dataType(MLResultDataType.INT32)
            .byteBuffer(ByteBuffer.wrap(new byte[] { 0, 1, 0, 1 }))
            .build();

        modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
    }

    @Test
    public void test_ModelTensortoXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        modelTensors.toXContent(builder, EMPTY_PARAMS);
        String modelTensorContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"output\":[{\"name\":\"model_tensor\",\"data_type\":\"INT32\",\"shape\":[1,2,3],\"data\":[1,2,3],\"byte_buffer\":{\"array\":\"AAEAAQ==\",\"order\":\"BIG_ENDIAN\"}}]}",
            modelTensorContent
        );
    }

    @Test
    public void test_ModelTensortoXContent_NullValue() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        ModelTensors tensors = ModelTensors.builder().build();
        tensors.toXContent(builder, EMPTY_PARAMS);
        String modelTensorContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{}", modelTensorContent);
    }

    @Test
    public void test_StreamInAndOut_NullValue() throws IOException {
        ModelTensors tensors = ModelTensors.builder().build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        tensors.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        ModelTensors parsedTensors = new ModelTensors(streamInput);
        assertEquals(parsedTensors.getMlModelTensors(), tensors.getMlModelTensors());
    }

    @Test
    public void test_Filter() {
        ModelTensor modelTensorFiltered = ModelTensor
            .builder()
            .name("model_tensor")
            .shape(new long[] { 1, 2, 3, })
            .dataType(MLResultDataType.INT32)
            .build();
        modelTensors.filter(modelResultFilter);
        assertEquals(modelTensors.getMlModelTensors().size(), 1);
        // assertEquals(modelTensors.getMlModelTensors().get(0), modelTensorFiltered);
    }

    @Test
    public void test_Filter_NullTargetResponse() {
        ModelResultFilter resultFilter = ModelResultFilter.builder().build();
        modelTensors.filter(resultFilter);
        assertEquals(modelTensors.getMlModelTensors().size(), 1);
    }

    @Test
    public void test_Filter_NullMLModelTensors() {
        ModelTensors tensors = ModelTensors.builder().build();
        tensors.filter(modelResultFilter);
        assertEquals(modelTensors.getMlModelTensors().size(), 1);
    }

    @Test
    public void test_ToAndFromBytes() throws IOException {
        byte[] bytes = modelTensors.toBytes();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        modelTensors.writeTo(bytesStreamOutput);
        assertEquals(bytes.length, bytesStreamOutput.bytes().toBytesRef().bytes.length);

        ModelTensors tensors = ModelTensors.fromBytes(bytes);
        // assertEquals(modelTensors.getMlModelTensors(), tensors.getMlModelTensors());
    }

    @Test
    public void parse_Success_WithOutput() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.startArray(ModelTensors.OUTPUT_FIELD);

        builder.startObject();
        builder.field("name", "test_tensor");
        builder.field("data_type", "FLOAT32");
        builder.field("shape", new long[] { 1, 3 });
        builder.field("data", new Float[] { 1.0f, 2.0f, 3.0f });
        builder.endObject();

        builder.endArray();
        builder.endObject();

        String jsonStr = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        ModelTensors parsedTensors = ModelTensors.parse(parser);

        assertNotNull(parsedTensors.getMlModelTensors());
        assertEquals(1, parsedTensors.getMlModelTensors().size());
        ModelTensor modelTensor = parsedTensors.getMlModelTensors().get(0);
        assertEquals("test_tensor", modelTensor.getName());
        assertEquals(3, modelTensor.getData().length);
        // Compare the first value using double conversion to handle type differences
        assertEquals(1.0, modelTensor.getData()[0].doubleValue(), 0.0001);
        assertNull(parsedTensors.getStatusCode());
    }

    @Test
    public void parse_Success_WithStatusCode() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.field(ModelTensors.STATUS_CODE_FIELD, 200);
        builder.endObject();

        String jsonStr = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        ModelTensors parsedTensors = ModelTensors.parse(parser);

        assertEquals(Integer.valueOf(200), parsedTensors.getStatusCode());
        assertNotNull(parsedTensors.getMlModelTensors());
        assertEquals(0, parsedTensors.getMlModelTensors().size());
    }

    @Test
    public void parse_Success_WithOutputAndStatusCode() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();

        builder.field(ModelTensors.STATUS_CODE_FIELD, 200);

        builder.startArray(ModelTensors.OUTPUT_FIELD);
        builder.startObject();
        builder.field("name", "test_tensor");
        builder.field("data_type", "INT32");
        builder.field("shape", new long[] { 1, 2 });
        builder.field("data", new Integer[] { 1, 2 });
        builder.endObject();
        builder.endArray();

        builder.endObject();

        String jsonStr = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        ModelTensors parsedTensors = ModelTensors.parse(parser);

        assertEquals(Integer.valueOf(200), parsedTensors.getStatusCode());
        assertNotNull(parsedTensors.getMlModelTensors());
        assertEquals(1, parsedTensors.getMlModelTensors().size());
        ModelTensor modelTensor = parsedTensors.getMlModelTensors().get(0);
        assertEquals("test_tensor", modelTensor.getName());
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

        ModelTensors parsedTensors = ModelTensors.parse(parser);

        assertNotNull(parsedTensors.getMlModelTensors());
        assertEquals(0, parsedTensors.getMlModelTensors().size());
        assertNull(parsedTensors.getStatusCode());
    }

    @Test
    public void parse_SkipIrrelevantFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();

        builder.field("irrelevant_field", "irrelevant_value");

        builder.startArray(ModelTensors.OUTPUT_FIELD);
        builder.startObject();
        builder.field("name", "test_tensor");
        builder.field("data_type", "INT32");
        builder.field("shape", new long[] { 1, 2 });
        builder.field("data", new Integer[] { 1, 2 });
        builder.endObject();
        builder.endArray();

        builder.field(ModelTensors.STATUS_CODE_FIELD, 404);

        builder.field("another_irrelevant_field", "another_value");

        builder.endObject();

        String jsonStr = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        ModelTensors parsedTensors = ModelTensors.parse(parser);

        assertEquals(Integer.valueOf(404), parsedTensors.getStatusCode());
        assertNotNull(parsedTensors.getMlModelTensors());
        assertEquals(1, parsedTensors.getMlModelTensors().size());
        ModelTensor modelTensor = parsedTensors.getMlModelTensors().get(0);
        assertEquals("test_tensor", modelTensor.getName());
    }

    @Test
    public void test_ToString() {
        String result = modelTensors.toString();
        String expected =
            "{\"output\":[{\"name\":\"model_tensor\",\"data_type\":\"INT32\",\"shape\":[1,2,3],\"data\":[1,2,3],\"byte_buffer\":{\"array\":\"AAEAAQ==\",\"order\":\"BIG_ENDIAN\"}}]}";
        assertEquals(expected, result);
    }

    @Test
    public void test_ToString_ThrowsException() throws IOException {
        ModelTensors spyTensors = spy(modelTensors);
        doThrow(new IOException("Mock IOException")).when(spyTensors).toXContent(any(XContentBuilder.class), any());

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Can't convert ModelTensors to string");

        spyTensors.toString();
    }
}
