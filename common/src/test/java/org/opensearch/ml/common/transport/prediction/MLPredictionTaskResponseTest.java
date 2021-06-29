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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;

import org.junit.Test;
import org.opensearch.action.ActionResponse;
import org.opensearch.common.Strings;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class MLPredictionTaskResponseTest {

    @Test
    public void writeTo_Success() throws IOException {
        MLPredictionTaskResponse response = MLPredictionTaskResponse.builder()
                .taskId("taskId")
                .status("Success")
                .predictionResult(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        response.writeTo(bytesStreamOutput);
        assertEquals(35, bytesStreamOutput.size());
        response = new MLPredictionTaskResponse(bytesStreamOutput.bytes().streamInput());
        assertEquals("taskId", response.getTaskId());
        assertEquals("Success", response.getStatus());
        assertEquals(1, response.getPredictionResult().size());
    }

    @Test
    public void fromActionResponse_WithMLPredictionTaskResponse() {
        MLPredictionTaskResponse response = MLPredictionTaskResponse.builder()
                .taskId("taskId")
                .status("Success")
                .predictionResult(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build();
        assertSame(response, MLPredictionTaskResponse.fromActionResponse(response));
    }

    @Test
    public void fromActionResponse_WithNonMLPredictionTaskResponse() {
        MLPredictionTaskResponse response = MLPredictionTaskResponse.builder()
                .taskId("taskId")
                .status("Success")
                .predictionResult(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build();
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                response.writeTo(out);
            }
        };
        MLPredictionTaskResponse result = MLPredictionTaskResponse.fromActionResponse(actionResponse);
        assertNotSame(response, result);
        assertEquals(response.getTaskId(), result.getTaskId());
        assertEquals(response.getStatus(), result.getStatus());
        assertEquals(response.getPredictionResult().size(), result.getPredictionResult().size());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponse_IOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };


        MLPredictionTaskResponse.fromActionResponse(actionResponse);
    }

    @Test
    public void toXContentTest() throws IOException {
        MLPredictionTaskResponse response = MLPredictionTaskResponse.builder()
            .taskId("b5009b99-268f-476d-a676-379a30f82457")
            .status("Success")
            .predictionResult(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                put("Cluster ID", 0);
            }})))
            .build();

        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);
        assertEquals("{\"task_id\":\"b5009b99-268f-476d-a676-379a30f82457\"," +
             "\"status\":\"Success\"," +
             "\"prediction_result\":{" +
             "\"column_metas\":[{\"name\":\"Cluster ID\",\"column_type\":\"INTEGER\"}]," +
             "\"rows\":[{\"values\":[{\"column_type\":\"INTEGER\",\"value\":0}]}]}}", jsonStr);
    }
}