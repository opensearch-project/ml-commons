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

package org.opensearch.ml.common.transport.prediiction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;
import org.junit.Before;
import org.junit.Test;

import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.parameter.MLParameterBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class MLPredictionTaskRequestTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder()
                .algorithm("algo")
                .parameters(Collections.singletonList(MLParameterBuilder.parameter("k1", 1)))
                .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        assertEquals(40, bytesStreamOutput.size());
        request = new MLPredictionTaskRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals("algo", request.getAlgorithm());
        assertEquals(1, request.getParameters().size());
        assertNull(request.getModelId());
    }

    @Test
    public void validate_Success() {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder()
                .algorithm("algo")
                .parameters(Collections.singletonList(MLParameterBuilder.parameter("k1", 1)))
                .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build();

        assertNull(request.validate());
    }

    @Test
    public void validate_Exception_NullAlgorithmName() {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder()
                .algorithm(null)
                .parameters(Collections.singletonList(MLParameterBuilder.parameter("k1", 1)))
                .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build();

        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: algorithm name can't be null or empty;", exception.getMessage());
    }

    @Test
    public void validate_Exception_EmptyDataFrame() {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder()
                .algorithm("algo")
                .parameters(Collections.singletonList(MLParameterBuilder.parameter("k1", 1)))
                .dataFrame(DataFrameBuilder.emptyDataFrame(new ColumnMeta[]{
                        ColumnMeta.builder()
                                .name("name")
                                .columnType(ColumnType.DOUBLE)
                                .build()
                }))
                .build();

        ActionRequestValidationException exception = request.validate();

        assertEquals("Validation Failed: 1: input data can't be null or empty;", exception.getMessage());
    }

    @Test
    public void validate_Exception_NullDataFrame() {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder()
                .algorithm("algo")
                .parameters(Collections.singletonList(MLParameterBuilder.parameter("k1", 1)))
                .dataFrame(null)
                .build();

        ActionRequestValidationException exception = request.validate();

        assertEquals("Validation Failed: 1: input data can't be null or empty;", exception.getMessage());
    }


    @Test
    public void fromActionRequest_Success_WithMLPredictionTaskRequest() {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder()
                .algorithm("algo")
                .parameters(Collections.singletonList(MLParameterBuilder.parameter("k1", 1)))
                .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build();
        assertSame(MLPredictionTaskRequest.fromActionRequest(request), request);
    }

    @Test
    public void fromActionRequest_Success_WithNonMLPredictionTaskRequest() {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder()
                .algorithm("algo")
                .parameters(Collections.singletonList(MLParameterBuilder.parameter("k1", 1)))
                .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                request.writeTo(out);
            }
        };
        MLPredictionTaskRequest result = MLPredictionTaskRequest.fromActionRequest(actionRequest);
        assertNotSame(result, request);
        assertEquals(request.getAlgorithm(), result.getAlgorithm());
        assertEquals(request.getDataFrame().size(), result.getDataFrame().size());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() throws IOException {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };
        MLPredictionTaskRequest.fromActionRequest(actionRequest);
    }
}