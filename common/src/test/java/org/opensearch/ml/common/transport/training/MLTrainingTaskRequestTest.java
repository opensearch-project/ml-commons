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

package org.opensearch.ml.common.transport.training;

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
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.parameter.MLParameterBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class MLTrainingTaskRequestTest {

    @Test
    public void validate_Success() {
        MLTrainingTaskRequest request = MLTrainingTaskRequest.builder()
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
    public void validate_Exception_NullAlgoName() {
        MLTrainingTaskRequest request = MLTrainingTaskRequest.builder()
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
        MLTrainingTaskRequest request = MLTrainingTaskRequest.builder()
            .algorithm("algo")
            .parameters(Collections.singletonList(MLParameterBuilder.parameter("k1", 1)))
            .inputDataset(null)
            .build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: input data can't be null;", exception.getMessage());
    }

    @Test
    public void writeTo() throws IOException {
        MLTrainingTaskRequest request = MLTrainingTaskRequest.builder()
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
        assertEquals(40, bytesStreamOutput.size());
        request = new MLTrainingTaskRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals("algo", request.getAlgorithm());
        assertEquals(1, request.getParameters().size());
        assertEquals(MLInputDataType.DATA_FRAME, request.getInputDataset().getInputDataType());
    }

    @Test
    public void fromActionRequest_WithMLTrainingTaskRequest() {
        MLTrainingTaskRequest request = MLTrainingTaskRequest.builder()
            .algorithm("algo")
            .parameters(Collections.singletonList(MLParameterBuilder.parameter("k1", 1)))
            .inputDataset(DataFrameInputDataset.builder()
                .dataFrame(DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
                    put("key1", 2.0D);
                }})))
                .build())
            .build();
        assertSame(request, MLTrainingTaskRequest.fromActionRequest(request));
    }

    @Test
    public void fromActionRequest_WithNonMLTrainingTaskRequest() {
        MLTrainingTaskRequest request = MLTrainingTaskRequest.builder()
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
        MLTrainingTaskRequest result = MLTrainingTaskRequest.fromActionRequest(actionRequest);
        assertNotSame(request, result);
        assertEquals(request.getAlgorithm(), result.getAlgorithm());
        assertEquals(request.getParameters().size(), result.getParameters().size());
        assertEquals(request.getInputDataset().getInputDataType(), result.getInputDataset().getInputDataType());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_Exception() {
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
        MLTrainingTaskRequest.fromActionRequest(actionRequest);
    }
}