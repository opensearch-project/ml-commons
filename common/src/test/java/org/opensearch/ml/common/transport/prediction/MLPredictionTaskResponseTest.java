/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.common.transport.prediction;

import org.junit.Test;
import org.opensearch.action.ActionResponse;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.parameter.MLPredictionOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class MLPredictionTaskResponseTest {

    @Test
    public void writeTo_Success() throws IOException {
        MLPredictionOutput output = MLPredictionOutput.builder()
                .taskId("taskId")
                .status("Success")
                .predictionResult(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build();
        MLTaskResponse response = MLTaskResponse.builder()
                .output(output)
                .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        response.writeTo(bytesStreamOutput);
        response = new MLTaskResponse(bytesStreamOutput.bytes().streamInput());
        MLPredictionOutput mlPredictionOutput = (MLPredictionOutput)response.getOutput();
        assertEquals("taskId", mlPredictionOutput.getTaskId());
        assertEquals("Success", mlPredictionOutput.getStatus());
        assertEquals(1, mlPredictionOutput.getPredictionResult().size());
    }

    @Test
    public void fromActionResponse_WithMLPredictionTaskResponse() {
        MLPredictionOutput output = MLPredictionOutput.builder()
                .taskId("taskId")
                .status("Success")
                .predictionResult(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build();
        MLTaskResponse response = MLTaskResponse.builder()
                .output(output)
                .build();
        assertSame(response, MLTaskResponse.fromActionResponse(response));
    }

    @Test
    public void fromActionResponse_WithNonMLPredictionTaskResponse() {
        MLPredictionOutput output = MLPredictionOutput.builder()
                .taskId("taskId")
                .status("Success")
                .predictionResult(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build();
        MLTaskResponse response = MLTaskResponse.builder()
                .output(output)
                .build();
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                response.writeTo(out);
            }
        };
        MLTaskResponse result = MLTaskResponse.fromActionResponse(actionResponse);
        assertNotSame(response, result);

        MLPredictionOutput mlPredictionOutput = (MLPredictionOutput) response.getOutput();
        MLPredictionOutput resultMlPredictionOutput = (MLPredictionOutput) result.getOutput();
        assertEquals(mlPredictionOutput.getTaskId(), resultMlPredictionOutput.getTaskId());
        assertEquals(mlPredictionOutput.getStatus(), resultMlPredictionOutput.getStatus());
        assertEquals(mlPredictionOutput.getPredictionResult().size(), resultMlPredictionOutput.getPredictionResult().size());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponse_IOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };


        MLTaskResponse.fromActionResponse(actionResponse);
    }

    @Test
    public void toXContentTest() throws IOException {
        MLPredictionOutput output = MLPredictionOutput.builder()
                .taskId("b5009b99-268f-476d-a676-379a30f82457")
                .status("Success")
                .predictionResult(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("ClusterID", 0);
                }})))
                .build();
        MLTaskResponse response = MLTaskResponse.builder()
            .output(output)
            .build();

        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);
        assertEquals("{\"task_id\":\"b5009b99-268f-476d-a676-379a30f82457\"," +
             "\"status\":\"Success\"," +
             "\"prediction_result\":{" +
             "\"column_metas\":[{\"name\":\"ClusterID\",\"column_type\":\"INTEGER\"}]," +
             "\"rows\":[{\"values\":[{\"column_type\":\"INTEGER\",\"value\":0}]}]}}", jsonStr);
    }
}