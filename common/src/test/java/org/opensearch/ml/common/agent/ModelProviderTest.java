/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.junit.Assert.*;
import static org.opensearch.ml.common.connector.AbstractConnector.ACCESS_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SECRET_KEY_FIELD;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

public class ModelProviderTest {

    @Test
    public void testBedrockConverseProvider() {
        BedrockConverseModelProvider provider = new BedrockConverseModelProvider();

        String modelName = "us.anthropic.claude-3-7-sonnet-20250219-v1:0";
        Map<String, String> credential = new HashMap<>();
        credential.put(ACCESS_KEY_FIELD, "test_key");
        credential.put(SECRET_KEY_FIELD, "test_secret");

        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("region", "us-west-2");

        // Test connector creation
        Connector connector = provider.createConnector(modelName, credential, modelParameters);
        assertNotNull(connector);
        assertEquals("Auto-generated Bedrock Converse connector", connector.getName());
        assertEquals("aws_sigv4", connector.getProtocol());
        assertEquals("us-west-2", connector.getParameters().get("region"));
        assertEquals(modelName, connector.getParameters().get("model"));
        assertTrue(connector instanceof org.opensearch.ml.common.connector.AwsConnector);

        // Test model input creation
        MLRegisterModelInput modelInput = provider.createModelInput(modelName, connector, modelParameters);
        assertNotNull(modelInput);
        assertEquals(FunctionName.REMOTE, modelInput.getFunctionName());
        assertEquals("Auto-generated model for " + modelName, modelInput.getModelName());
        assertEquals(connector, modelInput.getConnector());
        assertFalse(modelInput.isDeployModel());
    }

    @Test
    public void testModelProviderFactory() {
        // Test Bedrock Converse provider
        ModelProvider provider = ModelProviderFactory.getProvider("bedrock/converse");
        assertNotNull(provider);
        assertTrue(provider instanceof BedrockConverseModelProvider);

        // Test unsupported provider
        try {
            ModelProviderFactory.getProvider("unsupported/provider");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unknown model provider type"));
        }
    }

    @Test
    public void testAgentModelService() {
        MLAgentModelSpec modelSpec = MLAgentModelSpec
            .builder()
            .modelId("us.anthropic.claude-3-7-sonnet-20250219-v1:0")
            .modelProvider("bedrock/converse")
            .credential(new HashMap<>())
            .modelParameters(new HashMap<>())
            .build();

        // Test model creation
        MLRegisterModelInput modelInput = AgentModelService.createModelFromSpec(modelSpec);
        assertNotNull(modelInput);
        assertEquals(FunctionName.REMOTE, modelInput.getFunctionName());
        assertNotNull(modelInput.getConnector());
    }
}
