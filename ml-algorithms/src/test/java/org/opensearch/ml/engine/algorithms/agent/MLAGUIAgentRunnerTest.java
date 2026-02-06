/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLAgentModelSpec;
import org.opensearch.ml.common.hooks.HookRegistry;
import org.opensearch.ml.common.hooks.PreLLMEvent;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

public class MLAGUIAgentRunnerTest {

    @Mock
    private Client client;

    @Mock
    private ClusterService clusterService;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    @SuppressWarnings("rawtypes")
    private Map<String, Memory.Factory> memoryFactoryMap;

    @Mock
    private SdkClient sdkClient;

    @Mock
    private Encryptor encryptor;

    @Mock
    private ActionListener<Object> agentActionListener;

    @Mock
    private TransportChannel channel;

    private Settings settings;
    @SuppressWarnings("rawtypes")
    private Map<String, Tool.Factory> toolFactories;
    private MLAGUIAgentRunner aguiAgentRunner;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        toolFactories = new HashMap<>();

        aguiAgentRunner = new MLAGUIAgentRunner(
            client,
            settings,
            clusterService,
            xContentRegistry,
            toolFactories,
            memoryFactoryMap,
            sdkClient,
            encryptor
        );
    }

    @Test
    public void testRun_WithLegacyLLMInterface() {
        // Arrange - agent uses legacy LLM interface
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAGUIAgent")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("test-model-id").build())
            .build();

        Map<String, String> params = new HashMap<>();

        // Act
        aguiAgentRunner.run(mlAgent, params, agentActionListener, channel);

        // Assert - verify agent_type parameter is set
        assertEquals("ag_ui", params.get("agent_type"));
    }

    @Test
    public void testRun_WithRevampedModelInterface() {
        // Arrange - agent uses revamped model interface
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAGUIAgent")
            .type(MLAgentType.AG_UI.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        Map<String, String> params = new HashMap<>();

        // Act
        aguiAgentRunner.run(mlAgent, params, agentActionListener, channel);

        // Assert - verify agent_type parameter is set with revamped interface
        assertEquals("ag_ui", params.get("agent_type"));
    }

    @Test
    public void testRun_WithLLMInterfaceInAgentParameters() {
        // Arrange - agent has llm_interface in its parameters
        Map<String, String> agentParams = new HashMap<>();
        agentParams.put("llm_interface", "bedrock/converse/claude");

        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAGUIAgent")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("test-model-id").build())
            .parameters(agentParams)
            .build();

        Map<String, String> params = new HashMap<>();

        // Act
        aguiAgentRunner.run(mlAgent, params, agentActionListener, channel);

        // Assert - agent_type is set even when llm_interface comes from agent
        assertEquals("ag_ui", params.get("agent_type"));
    }

    @Test
    public void testConstructor_WithHookRegistry() {
        // Arrange
        HookRegistry hookRegistry = new HookRegistry();

        // Add a callback to the hook registry to verify it's functional
        final boolean[] callbackInvoked = { false };
        hookRegistry.addCallback(PreLLMEvent.class, event -> { callbackInvoked[0] = true; });

        // Act
        MLAGUIAgentRunner runnerWithHooks = new MLAGUIAgentRunner(
            client,
            settings,
            clusterService,
            xContentRegistry,
            toolFactories,
            memoryFactoryMap,
            sdkClient,
            encryptor,
            hookRegistry
        );

        // Assert - runner is created with hook registry
        assertNotNull(runnerWithHooks);

        // Verify hook registry has the callback registered
        assertEquals(1, hookRegistry.getCallbackCount(PreLLMEvent.class));

        // Verify runner can execute (hook registry is passed to MLChatAgentRunner)
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAGUIAgent")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("test-model-id").build())
            .build();

        Map<String, String> params = new HashMap<>();
        runnerWithHooks.run(mlAgent, params, agentActionListener, channel);
        assertEquals("ag_ui", params.get("agent_type"));
    }
}
