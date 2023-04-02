/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.metricscorrelation;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensor;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensors;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

public class MCorrModelTensorsTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    private MCorrModelTensors mcorrModelTensors;
    private ModelResultFilter modelResultFilter;

    @Before
    public void setUp() {
        String sentence = "test sentence";
        String column = "model_tensor";
        Integer position = 1;
        modelResultFilter = ModelResultFilter.builder()
                .targetResponse(Arrays.asList(column))
                .targetResponsePositions(Arrays.asList(position))
                .build();

        MCorrModelTensor mCorrModelTensor = MCorrModelTensor.builder()
                .event(new long[]{1, 2, 3})
                .range(new float[]{4.0f, 5.0f, 6.0f})
                .metrics(new float[]{1.0f, 2.0f})
                .build();

        mcorrModelTensors = MCorrModelTensors.builder().mCorrModelTensors(Arrays.asList(mCorrModelTensor)).build();
    }

    @Test
    public void test_ModelTensortoXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mcorrModelTensors.toXContent(builder, EMPTY_PARAMS);
        String modelTensorContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{\"output\":[{\"range\":[4.0,5.0,6.0],\"event\":[1,2,3],\"metrics\":[1.0,2.0]}]}", modelTensorContent);
    }

    @Test
    public void test_ModelTensortoXContent_NullValue() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        MCorrModelTensors tensors = MCorrModelTensors.builder().build();
        tensors.toXContent(builder, EMPTY_PARAMS);
        String modelTensorContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{}", modelTensorContent);
    }

    @Test
    public void test_StreamInAndOut_NullValue() throws IOException {
        MCorrModelTensors tensors = MCorrModelTensors.builder().build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        tensors.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MCorrModelTensors parsedTensors = new MCorrModelTensors(streamInput);
        assertEquals(parsedTensors.getMCorrModelTensors(), tensors.getMCorrModelTensors());
    }
}

