/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;

public class MLExecuteConnectorRequestTests {
    private MLExecuteConnectorRequest mlExecuteConnectorRequest;
    private MLInput mlInput;
    private String connectorId;

    @Before
    public void setUp() {
        MLInputDataset inputDataSet = RemoteInferenceInputDataSet.builder().parameters(Map.of("input", "hello")).build();
        connectorId = "test_connector";
        mlInput = RemoteInferenceMLInput.builder().inputDataset(inputDataSet).algorithm(FunctionName.CONNECTOR).build();
        mlExecuteConnectorRequest = MLExecuteConnectorRequest.builder().connectorId(connectorId).mlInput(mlInput).build();
    }

    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mlExecuteConnectorRequest.writeTo(output);
        MLExecuteConnectorRequest parsedRequest = new MLExecuteConnectorRequest(output.bytes().streamInput());
        assertEquals(mlExecuteConnectorRequest.getConnectorId(), parsedRequest.getConnectorId());
        assertEquals(mlExecuteConnectorRequest.getMlInput().getAlgorithm(), parsedRequest.getMlInput().getAlgorithm());
        assertEquals(
            mlExecuteConnectorRequest.getMlInput().getInputDataset().getInputDataType(),
            parsedRequest.getMlInput().getInputDataset().getInputDataType()
        );
        assertEquals("hello", ((RemoteInferenceInputDataSet) parsedRequest.getMlInput().getInputDataset()).getParameters().get("input"));
    }

    @Test
    public void validateSuccess() {
        assertNull(mlExecuteConnectorRequest.validate());
    }

    @Test
    public void testConstructor() {
        MLExecuteConnectorRequest executeConnectorRequest = new MLExecuteConnectorRequest(connectorId, mlInput);
        assertTrue(executeConnectorRequest.isDispatchTask());
    }

    @Test
    public void validateWithNullMLInputException() {
        MLExecuteConnectorRequest executeConnectorRequest = MLExecuteConnectorRequest.builder().connectorId("test_connector").build();
        ActionRequestValidationException exception = executeConnectorRequest.validate();
        assertEquals("Validation Failed: 1: ML input can't be null;", exception.getMessage());
    }

    @Test
    public void validateWithNullMLInputDataSetException() {
        MLExecuteConnectorRequest executeConnectorRequest = MLExecuteConnectorRequest
            .builder()
            .connectorId("test_connector")
            .mlInput(new MLInput())
            .build();
        ActionRequestValidationException exception = executeConnectorRequest.validate();
        assertEquals("Validation Failed: 1: input data can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequestWithMLExecuteConnectorRequestSuccess() {
        assertSame(MLExecuteConnectorRequest.fromActionRequest(mlExecuteConnectorRequest), mlExecuteConnectorRequest);
    }

    @Test
    public void fromActionRequestWithNonMLExecuteConnectorRequestSuccess() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlExecuteConnectorRequest.writeTo(out);
            }
        };
        MLExecuteConnectorRequest result = MLExecuteConnectorRequest.fromActionRequest(actionRequest);
        assertNotSame(result, mlExecuteConnectorRequest);
        assertEquals(mlExecuteConnectorRequest.getConnectorId(), result.getConnectorId());
        assertEquals(mlExecuteConnectorRequest.getMlInput().getFunctionName(), result.getMlInput().getFunctionName());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequestIOException() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLExecuteConnectorRequest.fromActionRequest(actionRequest);
    }
}
