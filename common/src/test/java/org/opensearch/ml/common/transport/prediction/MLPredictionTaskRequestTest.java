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
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.parameter.MLParameterBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class MLPredictionTaskRequestTest {

    @Test
    public void writeTo_Success() throws IOException {

        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder()
            .algorithm("algo")
            .parameters(Collections.singletonList(MLParameterBuilder.parameter("k1", 1)))
            .inputDataset(DataFrameInputDataset.builder()
                .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build())
            .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        assertEquals(41, bytesStreamOutput.size());
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
            .inputDataset(DataFrameInputDataset.builder()
                .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build())
            .build();

        assertNull(request.validate());
    }

    @Test
    public void validate_Exception_NullAlgorithmName() {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder()
            .algorithm(null)
            .parameters(Collections.singletonList(MLParameterBuilder.parameter("k1", 1)))
            .inputDataset(DataFrameInputDataset.builder()
                .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build())
            .build();

        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: algorithm name can't be null or empty;", exception.getMessage());
    }

    @Test
    public void validate_Exception_NullDataFrame() {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder()
            .algorithm("algo")
            .parameters(Collections.singletonList(MLParameterBuilder.parameter("k1", 1)))
            .inputDataset(null)
            .build();

        ActionRequestValidationException exception = request.validate();

        assertEquals("Validation Failed: 1: input data can't be null;", exception.getMessage());
    }


    @Test
    public void fromActionRequest_Success_WithMLPredictionTaskRequest() {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder()
            .algorithm("algo")
            .parameters(Collections.singletonList(MLParameterBuilder.parameter("k1", 1)))
            .inputDataset(DataFrameInputDataset.builder()
                .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build())

            .build();
        assertSame(MLPredictionTaskRequest.fromActionRequest(request), request);
    }

    @Test
    public void fromActionRequest_Success_WithNonMLPredictionTaskRequest() {
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder()
            .algorithm("algo")
            .parameters(Collections.singletonList(MLParameterBuilder.parameter("k1", 1)))
            .inputDataset(DataFrameInputDataset.builder()
                .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build())
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
        assertEquals(request.getInputDataset().getInputDataType(), result.getInputDataset().getInputDataType());
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