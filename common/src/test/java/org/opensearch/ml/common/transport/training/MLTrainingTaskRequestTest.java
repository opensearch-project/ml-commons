/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.training;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;

public class MLTrainingTaskRequestTest {

    private MLInput mlInput;

    @Before
    public void setUp() {
        DataFrame dataFrame = DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {
            {
                put("key1", 2.0D);
            }
        }));
        mlInput = MLInput
            .builder()
            .algorithm(FunctionName.KMEANS)
            .parameters(KMeansParams.builder().centroids(1).build())
            .inputDataset(DataFrameInputDataset.builder().dataFrame(dataFrame).build())
            .build();
    }

    @Test
    public void validate_Success() {
        MLTrainingTaskRequest request = new MLTrainingTaskRequest(mlInput, true);
        assertNull(request.validate());
    }

    @Test
    public void validate_SuccessWithBuilder() {
        MLTrainingTaskRequest request = MLTrainingTaskRequest.builder().mlInput(mlInput).build();
        assertNull(request.validate());
    }

    @Test
    public void validate_Exception_NullMLInput() {
        MLTrainingTaskRequest request = MLTrainingTaskRequest.builder().build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: ML input can't be null;", exception.getMessage());
    }

    @Test
    public void validate_Exception_NullInputDataInMLInput() {
        mlInput.setInputDataset(null);
        MLTrainingTaskRequest request = MLTrainingTaskRequest.builder().mlInput(mlInput).build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: input data can't be null;", exception.getMessage());
    }

    @Test
    public void writeTo() throws IOException {
        MLTrainingTaskRequest request = MLTrainingTaskRequest.builder().mlInput(mlInput).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        request = new MLTrainingTaskRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(FunctionName.KMEANS, request.getMlInput().getAlgorithm());
        assertEquals(1, ((KMeansParams) request.getMlInput().getParameters()).getCentroids().intValue());
        assertEquals(MLInputDataType.DATA_FRAME, request.getMlInput().getInputDataset().getInputDataType());
    }

    @Test
    public void fromActionRequest_WithMLTrainingTaskRequest() {
        MLTrainingTaskRequest request = MLTrainingTaskRequest.builder().mlInput(mlInput).build();
        assertSame(request, MLTrainingTaskRequest.fromActionRequest(request));
    }

    @Test
    public void fromActionRequest_WithNonMLTrainingTaskRequest() {
        MLTrainingTaskRequest request = MLTrainingTaskRequest.builder().mlInput(mlInput).build();
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
        assertEquals(request.getMlInput().getAlgorithm(), result.getMlInput().getAlgorithm());
        assertEquals(request.getMlInput().getParameters(), result.getMlInput().getParameters());
        assertEquals(request.getMlInput().getInputDataset().getInputDataType(), result.getMlInput().getInputDataset().getInputDataType());
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
