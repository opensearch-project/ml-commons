/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.utils.StringUtils.SAFE_INPUT_DESCRIPTION;

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

    private MLCreateConnectorRequest mlCreateConnectorRequest;

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
            null,
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
        mlCreateConnectorRequest = MLCreateConnectorRequest.builder().mlCreateConnectorInput(mlCreateConnectorInput).build();
    }

    @Test
    public void writeToSuccess() throws IOException {
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
    public void validateSuccess() {
        assertNull(mlCreateConnectorRequest.validate());
    }

    @Test
    public void validateWithNullMLCreateConnectorInputException() {
        MLCreateConnectorRequest mlCreateConnectorRequest = MLCreateConnectorRequest.builder().build();
        ActionRequestValidationException exception = mlCreateConnectorRequest.validate();
        assertEquals("Validation Failed: 1: ML Connector input can't be null;", exception.getMessage());
    }

    @Test
    public void fromActionRequestWithMLCreateConnectorRequestSuccess() {
        assertSame(MLCreateConnectorRequest.fromActionRequest(mlCreateConnectorRequest), mlCreateConnectorRequest);
    }

    @Test
    public void fromActionRequestWithNonMLCreateConnectorRequestSuccess() {
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
        MLCreateConnectorRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void validateWithUnsafeModelConnectorName() {
        MLCreateConnectorInput unsafeInput = MLCreateConnectorInput
            .builder()
            .name("<script>bad</script>")  // Unsafe name
            .description("safe description")
            .version("1")
            .protocol("http")
            .parameters(Map.of("input", "test"))
            .credential(Map.of("key", "value"))
            .actions(List.of())
            .access(AccessMode.PUBLIC)
            .backendRoles(Arrays.asList("role1"))
            .addAllBackendRoles(false)
            .build();

        MLCreateConnectorRequest request = MLCreateConnectorRequest.builder().mlCreateConnectorInput(unsafeInput).build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: Model connector name " + SAFE_INPUT_DESCRIPTION + ";", exception.getMessage());
    }

    @Test
    public void validateWithUnsafeModelConnectorDescription() {
        MLCreateConnectorInput unsafeInput = MLCreateConnectorInput
            .builder()
            .name("safeName")
            .description("<script>bad</script>")  // Unsafe description
            .version("1")
            .protocol("http")
            .parameters(Map.of("input", "test"))
            .credential(Map.of("key", "value"))
            .actions(List.of())
            .access(AccessMode.PUBLIC)
            .backendRoles(Arrays.asList("role1"))
            .addAllBackendRoles(false)
            .build();

        MLCreateConnectorRequest request = MLCreateConnectorRequest.builder().mlCreateConnectorInput(unsafeInput).build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: Model connector description " + SAFE_INPUT_DESCRIPTION + ";", exception.getMessage());
    }

    @Test
    public void validateWithEmptyAndInvalidModelConnectorNameAndDescription() {
        // Test empty name (should fail validation)
        MLCreateConnectorInput emptyNameInput = MLCreateConnectorInput
            .builder()
            .name("")  // Empty name
            .description("valid description")
            .version("1")
            .protocol("http")
            .parameters(Map.of("input", "test"))
            .credential(Map.of("key", "value"))
            .actions(List.of())
            .access(AccessMode.PUBLIC)
            .backendRoles(Arrays.asList("role1"))
            .addAllBackendRoles(false)
            .build();

        MLCreateConnectorRequest emptyNameRequest = MLCreateConnectorRequest.builder().mlCreateConnectorInput(emptyNameInput).build();
        ActionRequestValidationException emptyNameException = emptyNameRequest.validate();
        assertEquals(
            "Validation Failed: 1: Model connector name is required and cannot be null or blank;",
            emptyNameException.getMessage()
        );

        // Test empty description (should pass validation)
        MLCreateConnectorInput emptyDescriptionInput = MLCreateConnectorInput
            .builder()
            .name("valid name")
            .description("")  // Empty description
            .version("1")
            .protocol("http")
            .parameters(Map.of("input", "test"))
            .credential(Map.of("key", "value"))
            .actions(List.of())
            .access(AccessMode.PUBLIC)
            .backendRoles(Arrays.asList("role1"))
            .addAllBackendRoles(false)
            .build();

        MLCreateConnectorRequest emptyDescriptionRequest = MLCreateConnectorRequest
            .builder()
            .mlCreateConnectorInput(emptyDescriptionInput)
            .build();
        ActionRequestValidationException emptyDescriptionException = emptyDescriptionRequest.validate();
        assertNull("Empty description should pass validation", emptyDescriptionException);

        // Test invalid characters in name and description
        MLCreateConnectorInput invalidInput = MLCreateConnectorInput
            .builder()
            .name("invalid<name>")
            .description("invalid<description>")
            .version("1")
            .protocol("http")
            .parameters(Map.of("input", "test"))
            .credential(Map.of("key", "value"))
            .actions(List.of())
            .access(AccessMode.PUBLIC)
            .backendRoles(Arrays.asList("role1"))
            .addAllBackendRoles(false)
            .build();

        MLCreateConnectorRequest invalidRequest = MLCreateConnectorRequest.builder().mlCreateConnectorInput(invalidInput).build();
        ActionRequestValidationException invalidException = invalidRequest.validate();
        String exceptionMessage = invalidException.getMessage();
        assertTrue(
            "Error message should contain name validation failure",
            exceptionMessage.contains("Model connector name " + SAFE_INPUT_DESCRIPTION + ";")
        );

        assertTrue(
            "Error message should contain description validation failure",
            exceptionMessage.contains("Model connector description " + SAFE_INPUT_DESCRIPTION + ";")
        );
    }

    @Test
    public void validateWithDryRun() {
        // Test with dry run set to true
        MLCreateConnectorInput dryRunInput = MLCreateConnectorInput
            .builder()
            .name("")  // Empty name, which would normally fail validation
            .description("Test description")
            .version("1")
            .protocol("http")
            .parameters(Map.of("input", "test"))
            .credential(Map.of("key", "value"))
            .actions(List.of())
            .access(AccessMode.PUBLIC)
            .backendRoles(Arrays.asList("role1"))
            .addAllBackendRoles(false)
            .dryRun(true)  // Set dry run to true
            .build();

        MLCreateConnectorRequest dryRunRequest = MLCreateConnectorRequest.builder().mlCreateConnectorInput(dryRunInput).build();
        ActionRequestValidationException dryRunException = dryRunRequest.validate();
        assertNull("Validation should pass when dry run is true, even with empty name", dryRunException);

        // Test with dry run set to false (default behavior)
        MLCreateConnectorInput nonDryRunInput = MLCreateConnectorInput
            .builder()
            .name("")  // Empty name, which should fail validation
            .description("Test description")
            .version("1")
            .protocol("http")
            .parameters(Map.of("input", "test"))
            .credential(Map.of("key", "value"))
            .actions(List.of())
            .access(AccessMode.PUBLIC)
            .backendRoles(Arrays.asList("role1"))
            .addAllBackendRoles(false)
            .dryRun(false)  // Set dry run to false
            .build();

        MLCreateConnectorRequest nonDryRunRequest = MLCreateConnectorRequest.builder().mlCreateConnectorInput(nonDryRunInput).build();
        ActionRequestValidationException nonDryRunException = nonDryRunRequest.validate();
        assertNotNull("Validation should fail when dry run is false and name is empty", nonDryRunException);
        assertTrue(nonDryRunException.getMessage().contains("Model connector name is required"));
    }
}
