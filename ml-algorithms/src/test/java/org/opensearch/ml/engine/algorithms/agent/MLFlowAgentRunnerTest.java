package org.opensearch.ml.engine.algorithms.agent;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.opensearch.action.StepListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.tools.ToolsFactory;
import software.amazon.awssdk.utils.ImmutableMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

public class MLFlowAgentRunnerTest {

    public static final String FIRST_TOOL = "firstTool";
    public static final String SECOND_TOOL = "secondTool";

    @Mock
    private Client client;

    private Settings settings;

    @Mock
    private ClusterService clusterService;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private Map<String, Memory.Factory> memoryMap;

    private MLFlowAgentRunner mlFlowAgentRunner;

    @Mock
    private ToolsFactory toolsFactory;

    @Mock
    private Tool firstTool;

    @Mock
    private Tool secondTool;

    @Mock
    private ActionListener<Object> agentActionListener;

    @Captor
    private ArgumentCaptor<Object> objectCaptor;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        mlFlowAgentRunner = new MLFlowAgentRunner(client, settings, clusterService, xContentRegistry, toolsFactory, memoryMap);
        when(toolsFactory.getTool(Mockito.anyString())).thenReturn(firstTool).thenReturn(secondTool);
        Mockito.doAnswer(generateToolResponse("First tool response")).when(firstTool).run(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doAnswer(generateToolResponse("Second tool response")).when(secondTool).run(Mockito.any(), Mockito.any(), Mockito.any());
    }

    private Answer generateToolResponse(String response) {
        return invocation -> {
            ActionListener<Object> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        };
    }

    @Test
    public void testRunWithIncludeOutputNotSet() {
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();
        final MLAgent mlAgent = MLAgent.builder().name("TestAgent")
                .tools(Arrays.asList(firstToolSpec, secondToolSpec)).build();
        mlFlowAgentRunner.run(mlAgent, new HashMap<>(), agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        List<ModelTensor> agentOutput = (List<ModelTensor>) objectCaptor.getValue();
        Assert.assertEquals(1, agentOutput.size());
        // Respond with last tool output
        Assert.assertEquals(SECOND_TOOL, agentOutput.get(0).getName());
        Assert.assertEquals("Second tool response", agentOutput.get(0).getResult());
    }

    @Test
    public void testRunWithIncludeOutputSet() {
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL)
                .includeOutputInAgentResponse(true).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL)
                .includeOutputInAgentResponse(true).build();
        final MLAgent mlAgent = MLAgent.builder().name("TestAgent")
                .tools(Arrays.asList(firstToolSpec, secondToolSpec)).build();
        mlFlowAgentRunner.run(mlAgent, new HashMap<>(), agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        List<ModelTensor> agentOutput = (List<ModelTensor>) objectCaptor.getValue();
        // Respond with all tool output
        Assert.assertEquals(2, agentOutput.size());
        Assert.assertEquals(FIRST_TOOL, agentOutput.get(0).getName());
        Assert.assertEquals(SECOND_TOOL, agentOutput.get(1).getName());
        Assert.assertEquals("First tool response", agentOutput.get(0).getResult());
        Assert.assertEquals("Second tool response", agentOutput.get(1).getResult());
    }
}
