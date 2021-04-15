/*
 *  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 *  A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.opensearch.ml.common.transport.prediction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;

import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;
import org.junit.Test;

import org.opensearch.ml.common.dataframe.DataFrameBuilder;

import static org.junit.Assert.assertEquals;
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
}