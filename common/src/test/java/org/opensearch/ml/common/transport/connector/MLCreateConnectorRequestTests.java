/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;

public class MLCreateConnectorRequestTests {
    private MLCreateConnectorInput mlCreateConnectorInput;

    @Before
    public void setUp() {
        ConnectorAction.ActionType actionType = ConnectorAction.ActionType.PREDICT;
        String method = "POST";
        String url = "https://test.com";
        Map<String, String> headers = new HashMap<>();
        headers.put("api_key", "${credential.key}");
        String mlCreateConnectorRequestBody = "{\"input\": \"${parameters.input}\"}";
        String preProcessFunction = MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT;
        String postProcessFunction = MLPostProcessFunction.OPENAI_EMBEDDING;
        ConnectorAction action = new ConnectorAction(
            actionType,
            method,
            url,
            headers,
            mlCreateConnectorRequestBody,
            preProcessFunction,
            postProcessFunction
        );

        mlCreateConnectorInput = MLCreateConnectorInput
            .builder()
            .name("test_connector_name")
            .description("this is a test connector")
            .version("1")
            .protocol("http")
            .parameters(Map.of("input", "test input value"))
            .credential(Map.of("key", "test_key_value"))
            .actions(List.of(action))
            .access(AccessMode.PUBLIC)
            .backendRoles(Arrays.asList("role1", "role2"))
            .addAllBackendRoles(false)
            .build();
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLCreateConnectorRequest mlCreateConnectorRequest = MLCreateConnectorRequest
            .builder()
            .mlCreateConnectorInput(mlCreateConnectorInput)
            .build();
        BytesStreamOutput output = new BytesStreamOutput();
        mlCreateConnectorRequest.writeTo(output);
        MLCreateConnectorRequest parsedRequest = new MLCreateConnectorRequest(output.bytes().streamInput());
        assertEquals(mlCreateConnectorRequest.getMlCreateConnectorInput().getName(), parsedRequest.getMlCreateConnectorInput().getName());
        assertEquals(
            mlCreateConnectorRequest.getMlCreateConnectorInput().getAccess(),
            parsedRequest.getMlCreateConnectorInput().getAccess()
        );
        assertEquals(
            mlCreateConnectorRequest.getMlCreateConnectorInput().getProtocol(),
            parsedRequest.getMlCreateConnectorInput().getProtocol()
        );
        assertEquals(
            mlCreateConnectorRequest.getMlCreateConnectorInput().getBackendRoles(),
            parsedRequest.getMlCreateConnectorInput().getBackendRoles()
        );
        assertEquals(
            mlCreateConnectorRequest.getMlCreateConnectorInput().getActions(),
            parsedRequest.getMlCreateConnectorInput().getActions()
        );
        assertEquals(
            mlCreateConnectorRequest.getMlCreateConnectorInput().getParameters(),
            parsedRequest.getMlCreateConnectorInput().getParameters()
        );
    }

    @Test
    public void validate_Success() {
        MLCreateConnectorRequest mlCreateConnectorRequest = MLCreateConnectorRequest
            .builder()
            .mlCreateConnectorInput(mlCreateConnectorInput)
            .build();

        assertNull(mlCreateConnectorRequest.validate());
    }

    @Test
    public void validate_Exception_NullMLRegisterModelGroupInput() {
        MLCreateConnectorRequest mlCreateConnectorRequest = MLCreateConnectorRequest.builder().build();
        ActionRequestValidationException exception = mlCreateConnectorRequest.validate();
        assertEquals("Validation Failed: 1: ML Connector input can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequest_Success_WithMLRegisterModelRequest() {
        MLCreateConnectorRequest mlCreateConnectorRequest = MLCreateConnectorRequest
            .builder()
            .mlCreateConnectorInput(mlCreateConnectorInput)
            .build();
        assertSame(MLCreateConnectorRequest.fromActionRequest(mlCreateConnectorRequest), mlCreateConnectorRequest);
    }

    @Test
    public void fromActionRequest_Success_WithNonMLRegisterModelRequest() {
        MLCreateConnectorRequest mlCreateConnectorRequest = MLCreateConnectorRequest
            .builder()
            .mlCreateConnectorInput(mlCreateConnectorInput)
            .build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlCreateConnectorRequest.writeTo(out);
            }
        };
        MLCreateConnectorRequest result = MLCreateConnectorRequest.fromActionRequest(actionRequest);
        assertNotSame(result, mlCreateConnectorRequest);
        assertEquals(mlCreateConnectorRequest.getMlCreateConnectorInput().getName(), result.getMlCreateConnectorInput().getName());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() {
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
        MLCreateConnectorRequest.fromActionRequest(actionRequest);
    }
}
