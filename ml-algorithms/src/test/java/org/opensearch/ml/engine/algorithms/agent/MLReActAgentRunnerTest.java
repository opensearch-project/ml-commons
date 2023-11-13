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
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionType;
import org.opensearch.action.StepListener;
import org.opensearch.client.Client;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.MLTaskResponse;
import software.amazon.awssdk.utils.ImmutableMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

public class MLReActAgentRunnerTest {
    public static final String FIRST_TOOL = "firstTool";
    public static final String SECOND_TOOL = "secondTool";

    @Mock
    private Client client;

    private Settings settings;

    @Mock
    private ClusterService clusterService;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    private Map<String, Tool.Factory> toolFactories;

    @Mock
    private Map<String, Memory.Factory> memoryMap;

    private MLReActAgentRunner mlReActAgentRunner;

    @Mock
    private Tool.Factory firstToolFactory;

    @Mock
    private Tool.Factory secondToolFactory;
    @Mock
    private Tool firstTool;

    @Mock
    private Tool secondTool;

    @Mock
    private ActionListener<Object> agentActionListener;

    @Captor
    private ArgumentCaptor<Object> objectCaptor;

    @Captor
    private ArgumentCaptor<StepListener<Object>> nextStepListenerCaptor;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        toolFactories = ImmutableMap.of(FIRST_TOOL, firstToolFactory, SECOND_TOOL, secondToolFactory);
        mlReActAgentRunner = new MLReActAgentRunner(client, settings, clusterService, xContentRegistry, toolFactories, memoryMap);
        when(firstToolFactory.create(Mockito.anyMap())).thenReturn(firstTool);
        when(secondToolFactory.create(Mockito.anyMap())).thenReturn(secondTool);
        when(firstTool.getName()).thenReturn(FIRST_TOOL);
        when(secondTool.getName()).thenReturn(SECOND_TOOL);
        when(firstTool.validate(Mockito.anyMap())).thenReturn(true);
        when(secondTool.validate(Mockito.anyMap())).thenReturn(true);
        Mockito.doAnswer(generateToolResponse("First tool response")).when(firstTool).run(Mockito.anyMap(), nextStepListenerCaptor.capture());
        Mockito.doAnswer(generateToolResponse("Second tool response")).when(secondTool).run(Mockito.anyMap(), nextStepListenerCaptor.capture());

        Mockito.doAnswer(getLLMAnswer(ImmutableMap.of("thought", "thought 1", "action", FIRST_TOOL)))
                .doAnswer(getLLMAnswer(ImmutableMap.of("thought", "thought 2", "action", SECOND_TOOL)))
                .doAnswer(getLLMAnswer(ImmutableMap.of("thought", "thought 3", "final_answer", "This is the final answer")))
                .when(client).execute(any(ActionType.class), any(ActionRequest.class), isA(ActionListener.class));
    }

    private Answer getLLMAnswer(Map<String, String> llmResponse) {
        return invocation -> {
            ActionListener<Object> listener = invocation.getArgument(2);
            ModelTensor modelTensor = ModelTensor.builder().dataAsMap(llmResponse).build();
            ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
            ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder()
                    .mlModelOutputs(Arrays.asList(modelTensors)).build();
            MLTaskResponse mlTaskResponse = MLTaskResponse.builder().output(mlModelTensorOutput).build();
            listener.onResponse(mlTaskResponse);
            return null;
        };
    }

    private Answer generateToolResponse(String response) {
        return invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        };
    }

    @Test
    public void testRunWithIncludeOutputNotSet() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();
        final MLAgent mlAgent = MLAgent.builder().name("TestAgent").llm(llmSpec)
                .tools(Arrays.asList(firstToolSpec, secondToolSpec)).build();
        mlReActAgentRunner.run(mlAgent, new HashMap<>(), agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors();
        Assert.assertEquals(1, agentOutput.size());
        // Respond with last tool output
        Assert.assertEquals("This is the final answer", agentOutput.get(0).getDataAsMap().get("response"));
    }

    @Test
    public void testRunWithIncludeOutputSet() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL)
                .includeOutputInAgentResponse(true).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL)
                .includeOutputInAgentResponse(true).build();
        final MLAgent mlAgent = MLAgent.builder().name("TestAgent").llm(llmSpec)
                .tools(Arrays.asList(firstToolSpec, secondToolSpec)).build();
        mlReActAgentRunner.run(mlAgent, new HashMap<>(), agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors();
        // Respond with all tool output
        Assert.assertEquals(3, agentOutput.size());
        Assert.assertEquals("First tool response", agentOutput.get(0).getResult());
        Assert.assertEquals("Second tool response", agentOutput.get(1).getResult());
        Assert.assertEquals("This is the final answer", agentOutput.get(2).getDataAsMap().get("response"));
    }

    @Test
    public void testRunWithIncludeOutputSetToSomeTools() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL)
                .includeOutputInAgentResponse(true).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL)
                .includeOutputInAgentResponse(false).build();
        final MLAgent mlAgent = MLAgent.builder().name("TestAgent").llm(llmSpec)
                .tools(Arrays.asList(firstToolSpec, secondToolSpec)).build();
        mlReActAgentRunner.run(mlAgent, new HashMap<>(), agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors();
        // Respond with all tool output
        Assert.assertEquals(2, agentOutput.size());
        Assert.assertEquals("First tool response", agentOutput.get(0).getResult());
        Assert.assertEquals("This is the final answer", agentOutput.get(1).getDataAsMap().get("response"));
    }
}
