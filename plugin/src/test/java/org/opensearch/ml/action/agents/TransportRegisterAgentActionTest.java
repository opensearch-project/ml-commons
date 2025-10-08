/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.agent.MLAgentModelSpec;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentInput;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentRequest;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

public class TransportRegisterAgentActionTest {

    @Mock
    private Client client;
    
    @Mock
    private SdkClient sdkClient;
    
    @Mock
    private MLIndicesHandler mlIndicesHandler;
    
    @Mock
    private ClusterService clusterService;
    
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    
    @Mock
    private ActionListener<MLRegisterAgentResponse> listener;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testSimplifiedAgentRegistration() {
        // Test that the new simplified format is properly handled
        MLAgentModelSpec modelSpec = MLAgentModelSpec.builder()
            .modelId("us.anthropic.claude-3-7-sonnet-20250219-v1:0")
            .modelProvider("bedrock/converse")
            .credential(new HashMap<>())
            .modelParameters(new HashMap<>())
            .build();
            
        MLRegisterAgentInput agentInput = MLRegisterAgentInput.builder()
            .name("Test Agent")
            .type("conversational")
            .description("Test agent with simplified format")
            .model(modelSpec)
            .build();
            
        MLRegisterAgentRequest request = new MLRegisterAgentRequest(agentInput, "test-tenant");
        
        // Verify the request contains the agent input
        assert request.getAgentInput() != null;
        assert request.getAgentInput().getModel() != null;
        assert "bedrock/converse".equals(request.getAgentInput().getModel().getModelProvider());
    }
}