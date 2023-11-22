/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.metricscorrelation;

import static org.junit.Assert.assertEquals;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensor;

public class MCorrModelTensorTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private MCorrModelTensor mCorrModelTensor;

    @Before
    public void setUp() {
        mCorrModelTensor = MCorrModelTensor
            .builder()
            .event_pattern(new float[] { 1.0f, 2.0f, 3.0f })
            .event_window(new float[] { 4.0f, 5.0f, 6.0f })
            .suspected_metrics(new long[] { 1, 2 })
            .build();
    }

    @Test
    public void test_StreamInAndOut() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mCorrModelTensor.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MCorrModelTensor parsedTensor = new MCorrModelTensor(streamInput);
        assertEquals(parsedTensor, mCorrModelTensor);
    }

    @Test
    public void test_ModelTensorSuccess() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mCorrModelTensor.toXContent(builder, EMPTY_PARAMS);
        String modelTensorContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{\"event_window\":[4.0,5.0,6.0],\"event_pattern\":[1.0,2.0,3.0],\"suspected_metrics\":[1,2]}", modelTensorContent);
    }

    @Test
    public void toXContent_NullValue() throws IOException {
        MCorrModelTensor tensor = MCorrModelTensor.builder().build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        tensor.toXContent(builder, EMPTY_PARAMS);
        String modelTensorContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{}", modelTensorContent);
    }

    @Test
    public void test_StreamInAndOut_NullValue() throws IOException {
        MCorrModelTensor tensor = MCorrModelTensor.builder().build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        tensor.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MCorrModelTensor parsedTensor = new MCorrModelTensor(streamInput);
        assertEquals(parsedTensor, tensor);
    }
}
