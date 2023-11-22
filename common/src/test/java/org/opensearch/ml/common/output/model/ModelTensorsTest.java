/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.model;

import static org.junit.Assert.assertEquals;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;

public class ModelTensorsTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    private ModelTensors modelTensors;
    private ModelResultFilter modelResultFilter;

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

        ModelTensor modelTensor = ModelTensor
            .builder()
            .name("model_tensor")
            .data(new Number[] { 1, 2, 3 })
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
}
