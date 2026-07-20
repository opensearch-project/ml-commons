/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.opensearch.ml.common.input.execute.agent.ModelProviderType;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

public class AgentModelServiceTest {
    @Test
    public void testCreateModelFromSpec_Success() {
        // Arrange
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_access_key");
        credential.put("secret_key", "test_secret_key");

        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("region", "us-west-2");

        MLAgentModelSpec modelSpec = MLAgentModelSpec
            .builder()
            .modelId("us.anthropic.claude-3-5-sonnet-20241022-v2:0")
            .modelProvider("bedrock/converse")
            .credential(credential)
            .modelParameters(modelParameters)
            .build();

        // Act
        MLRegisterModelInput result = AgentModelService.createModelFromSpec(modelSpec);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getConnector());
        assertFalse(result.isDeployModel());
    }

    @Test
    public void testCreateModelFromSpec_NullModelSpec() {
        // Arrange & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {

            // Act
            AgentModelService.createModelFromSpec(null);
        });
        assertEquals("Model specification not found", exception.getMessage());
    }

    @Test
    public void testCreateModelFromSpec_EmptyModelId() {
        // Arrange
        MLAgentModelSpec modelSpec = MLAgentModelSpec.builder().modelId("   ").modelProvider("bedrock/converse").build();

        // Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {

            // Act
            AgentModelService.createModelFromSpec(modelSpec);
        });
        assertEquals("model_id cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testCreateModelFromSpec_EmptyModelProvider() {
        // Arrange
        MLAgentModelSpec modelSpec = MLAgentModelSpec.builder().modelId("test-model-id").modelProvider("  ").build();

        // Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {

            // Act
            AgentModelService.createModelFromSpec(modelSpec);
        });
        assertEquals("model_provider cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testCreateModelFromSpec_UnsupportedModelProvider() {
        // Arrange
        MLAgentModelSpec modelSpec = MLAgentModelSpec.builder().modelId("test-model-id").modelProvider("unsupported/provider").build();

        // Build expected message dynamically from enum
        String supportedTypes = Stream.of(ModelProviderType.values()).map(ModelProviderType::getValue).collect(Collectors.joining(", "));
        String expectedMessage = "Unknown model provider type. Supported types: " + supportedTypes;

        // Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentModelService.createModelFromSpec(modelSpec)
        );
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    public void testCreateModelFromSpec_WithoutCredential() {
        // Arrange
        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("region", "us-east-1");

        MLAgentModelSpec modelSpec = MLAgentModelSpec
            .builder()
            .modelId("us.anthropic.claude-3-5-sonnet-20241022-v2:0")
            .modelProvider("bedrock/converse")
            .credential(null)
            .modelParameters(modelParameters)
            .build();

        // Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {

            AgentModelService.createModelFromSpec(modelSpec);
        });
        assertEquals("Missing credential", exception.getMessage());
    }

    @Test
    public void testCreateModelFromSpec_WithoutModelParameters() {
        // Arrange
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");

        MLAgentModelSpec modelSpec = MLAgentModelSpec
            .builder()
            .modelId("us.anthropic.claude-3-5-sonnet-20241022-v2:0")
            .modelProvider("bedrock/converse")
            .credential(credential)
            .modelParameters(null)
            .build();

        // Act
        MLRegisterModelInput result = AgentModelService.createModelFromSpec(modelSpec);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getConnector());
    }

    @Test
    public void testCreateModelFromSpec_MinimalConfiguration() {
        // Arrange - Test with only required fields
        MLAgentModelSpec modelSpec = MLAgentModelSpec
            .builder()
            .modelId("us.anthropic.claude-3-5-sonnet-20241022-v2:0")
            .modelProvider("bedrock/converse")
            .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {

            // Act
            AgentModelService.createModelFromSpec(modelSpec);
        });
        assertEquals("Missing credential", exception.getMessage());
    }

    @Test
    public void testInferLLMInterface_Success() {
        // Act
        String result = AgentModelService.inferLLMInterface("bedrock/converse");

        // Assert
        assertNotNull(result);
        assertEquals("bedrock/converse/claude", result);
    }

    @Test
    public void testInferLLMInterface_NullProvider() {
        // Act
        String result = AgentModelService.inferLLMInterface(null);

        // Assert
        assertNull(result);
    }

    @Test
    public void testInferLLMInterface_UnsupportedProvider() {
        // Act
        String result = AgentModelService.inferLLMInterface("unsupported/provider");

        // Assert
        assertNull(result);
    }

    @Test
    public void testInferLLMInterface_EmptyProvider() {
        // Act
        String result = AgentModelService.inferLLMInterface("");

        // Assert
        assertNull(result);
    }

    @Test
    public void testInferLLMInterface_InvalidProvider() {
        // Act
        String result = AgentModelService.inferLLMInterface("invalid-provider");

        // Assert
        assertNull(result);
    }

    @Test
    public void testCreateModelFromSpec_WithEmptyCredentialMap() {
        // Arrange
        Map<String, String> credential = new HashMap<>();
        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("region", "eu-west-1");

        MLAgentModelSpec modelSpec = MLAgentModelSpec
            .builder()
            .modelId("us.anthropic.claude-3-5-sonnet-20241022-v2:0")
            .modelProvider("bedrock/converse")
            .credential(credential)
            .modelParameters(modelParameters)
            .build();

        // Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {

            // Act
            AgentModelService.createModelFromSpec(modelSpec);
        });
        assertEquals("Missing credential", exception.getMessage());
    }

    @Test
    public void testCreateModelFromSpec_WithEmptyModelParametersMap() {
        // Arrange
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");
        Map<String, String> modelParameters = new HashMap<>();

        MLAgentModelSpec modelSpec = MLAgentModelSpec
            .builder()
            .modelId("us.anthropic.claude-3-5-sonnet-20241022-v2:0")
            .modelProvider("bedrock/converse")
            .credential(credential)
            .modelParameters(modelParameters)
            .build();

        // Act
        MLRegisterModelInput result = AgentModelService.createModelFromSpec(modelSpec);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getConnector());
    }
}
