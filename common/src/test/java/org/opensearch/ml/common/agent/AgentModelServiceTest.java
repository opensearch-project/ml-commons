/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

public class AgentModelServiceTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

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
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Model specification not found");

        // Act
        AgentModelService.createModelFromSpec(null);
    }

    @Test
    public void testCreateModelFromSpec_EmptyModelId() {
        // Arrange
        MLAgentModelSpec modelSpec = MLAgentModelSpec.builder().modelId("   ").modelProvider("bedrock/converse").build();

        // Assert
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model_id cannot be null or empty");

        // Act
        AgentModelService.createModelFromSpec(modelSpec);
    }

    @Test
    public void testCreateModelFromSpec_EmptyModelProvider() {
        // Arrange
        MLAgentModelSpec modelSpec = MLAgentModelSpec.builder().modelId("test-model-id").modelProvider("  ").build();

        // Assert
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model_provider cannot be null or empty");

        // Act
        AgentModelService.createModelFromSpec(modelSpec);
    }

    @Test
    public void testCreateModelFromSpec_UnsupportedModelProvider() {
        // Arrange
        MLAgentModelSpec modelSpec = MLAgentModelSpec.builder().modelId("test-model-id").modelProvider("unsupported/provider").build();

        // Assert
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unknown model provider type. Supported types: bedrock/converse, gemini/v1beta/generatecontent");

        // Act
        AgentModelService.createModelFromSpec(modelSpec);
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
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");

        AgentModelService.createModelFromSpec(modelSpec);
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

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");

        // Act
        AgentModelService.createModelFromSpec(modelSpec);
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
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");

        // Act
        AgentModelService.createModelFromSpec(modelSpec);
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
