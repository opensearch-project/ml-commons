package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.*;

import org.apache.lucene.search.TotalHits;
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
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;

import software.amazon.awssdk.utils.ImmutableMap;

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
    private ConversationIndexMemory.Factory mockMemoryFactory;

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

    @Mock
    ClusterState testClusterState;

    @Mock
    Metadata metadata;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);

        settings = Settings.builder().build();
        when(clusterService.state()).thenReturn(testClusterState);
        when(testClusterState.metadata()).thenReturn(metadata);

        toolFactories = ImmutableMap.of(FIRST_TOOL, firstToolFactory, SECOND_TOOL, secondToolFactory);
        mlReActAgentRunner = new MLReActAgentRunner(client, settings, clusterService, xContentRegistry, toolFactories, memoryMap);

        when(firstToolFactory.create(Mockito.anyMap())).thenReturn(firstTool);
        when(secondToolFactory.create(Mockito.anyMap())).thenReturn(secondTool);
        when(firstTool.getName()).thenReturn(FIRST_TOOL);
        when(firstTool.getDescription()).thenReturn("First tool description");
        when(secondTool.getDescription()).thenReturn("Second tool description");
        when(secondTool.getName()).thenReturn(SECOND_TOOL);
        when(firstTool.validate(Mockito.anyMap())).thenReturn(true);
        when(secondTool.validate(Mockito.anyMap())).thenReturn(true);
        Mockito
            .doAnswer(generateToolResponse("First tool response"))
            .when(firstTool)
            .run(Mockito.anyMap(), nextStepListenerCaptor.capture());
        Mockito
            .doAnswer(generateToolResponse("Second tool response"))
            .when(secondTool)
            .run(Mockito.anyMap(), nextStepListenerCaptor.capture());

        Mockito
            .doAnswer(getLLMAnswer(ImmutableMap.of("thought", "thought 1", "action", FIRST_TOOL)))
            .doAnswer(getLLMAnswer(ImmutableMap.of("thought", "thought 2", "action", SECOND_TOOL)))
            .doAnswer(getLLMAnswer(ImmutableMap.of("thought", "thought 3", "final_answer", "This is the final answer")))
            .when(client)
            .execute(any(ActionType.class), any(ActionRequest.class), isA(ActionListener.class));
    }

    private Answer getLLMAnswer(Map<String, String> llmResponse) {
        return invocation -> {
            ActionListener<Object> listener = invocation.getArgument(2);
            ModelTensor modelTensor = ModelTensor.builder().dataAsMap(llmResponse).build();
            ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
            ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
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
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .llm(llmSpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();
        mlReActAgentRunner.run(mlAgent, new HashMap<>(), agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors();
        assertEquals(1, agentOutput.size());
        // Respond with last tool output
        assertEquals("This is the final answer", agentOutput.get(0).getDataAsMap().get("response"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRunWithIncludeAgentMemoryIllegalArgumentException() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type("memoryType").build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .llm(llmSpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .memory(mlMemorySpec)
            .build();
        mlReActAgentRunner.run(mlAgent, new HashMap<>(), agentActionListener);
    }

    @Test
    public void testRunWithMemoryAndIndexExists() throws IOException {
        // Set up MLAgent with memory
        String memoryType = "conversationMemory";
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type(memoryType).build();
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec toolSpec = MLToolSpec
            .builder()
            .name(FIRST_TOOL)
            .type(FIRST_TOOL)
            .description("first tool description")
            .parameters(Map.of("index", "[\"foo\"]"))
            .build();

        Map<String, String> agentParams = new HashMap<>();
        agentParams.put("firstTool.", "[\"foo\"]");
        agentParams.put("verbose", "True");
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .llm(llmSpec)
            .tools(Arrays.asList(toolSpec))
            .memory(mlMemorySpec)
            .build();

        // Mocking Memory.Factory and Memory objects
        Memory.Factory memoryFactory = Mockito.mock(Memory.Factory.class);
        ConversationIndexMemory memory = Mockito.mock(ConversationIndexMemory.class);
        when(memoryMap.get(memoryType)).thenReturn(memoryFactory);
        when(memoryMap.containsKey(memoryType)).thenReturn(true);
        when(metadata.hasIndex((String) any())).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(1);
            listener.onResponse(memory);

            return null;
        }).when(memoryFactory).create(any(), any());
        SearchResponse searchResponse = createChatResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(memory).getMessages(any(), any());

        // Run the agent
        mlReActAgentRunner.run(mlAgent, agentParams, agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors();
        // Respond with all tool output
        assertEquals(7, modelTensorOutput.getMlModelOutputs().size());
        Assert.assertNotNull(agentOutput.get(0));
        assertNull("This is the final answer", agentOutput.get(0).getDataAsMap());
    }

    @Test
    public void testRunWithoutMemoryIndexExists() throws IOException {
        // Set up MLAgent with memory
        String memoryType = "conversationMemory";
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type(memoryType).build();
        MLToolSpec toolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .llm(llmSpec)
            .memory(mlMemorySpec)
            .tools(Arrays.asList(toolSpec))
            .build();

        // Mocking Memory.Factory and Memory objects
        Memory.Factory memoryFactory = Mockito.mock(Memory.Factory.class);
        ConversationIndexMemory memory = Mockito.mock(ConversationIndexMemory.class);
        when(memoryMap.get(memoryType)).thenReturn(memoryFactory);
        when(memoryMap.containsKey(memoryType)).thenReturn(true);
        when(metadata.hasIndex((String) any())).thenReturn(false);

        doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(1);
            listener.onResponse(memory);

            return null;
        }).when(memoryFactory).create(any(), any());
        SearchResponse searchResponse = createChatResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(memory).getMessages(any(), any());

        // Run the agent
        mlReActAgentRunner.run(mlAgent, new HashMap<>(), agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors();
        // Respond with all tool output
        assertEquals(1, modelTensorOutput.getMlModelOutputs().size());
        assertEquals(1, agentOutput.size());
        Assert.assertNull(agentOutput.get(0).getResult());
        assertEquals("This is the final answer", agentOutput.get(0).getDataAsMap().get("response"));
    }

    @Test
    public void testRunNullToolName() throws IOException {
        // Set up MLAgent with memory
        String memoryType = "conversationMemory";
        toolFactories = ImmutableMap.of(FIRST_TOOL, firstToolFactory);
        mlReActAgentRunner = new MLReActAgentRunner(client, settings, clusterService, xContentRegistry, toolFactories, memoryMap);
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type(memoryType).build();
        MLToolSpec toolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        when(firstTool.getName()).thenReturn(null);
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .llm(llmSpec)
            .memory(mlMemorySpec)
            .tools(Arrays.asList(toolSpec))
            .build();

        // Mocking Memory.Factory and Memory objects
        Memory.Factory memoryFactory = Mockito.mock(Memory.Factory.class);
        ConversationIndexMemory memory = Mockito.mock(ConversationIndexMemory.class);
        when(memoryMap.get(memoryType)).thenReturn(memoryFactory);
        when(memoryMap.containsKey(memoryType)).thenReturn(true);
        when(metadata.hasIndex((String) any())).thenReturn(false);

        doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(1);
            listener.onResponse(memory);

            return null;
        }).when(memoryFactory).create(any(), any());
        SearchResponse searchResponse = createChatResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(memory).getMessages(any(), any());

        // Run the agent
        mlReActAgentRunner.run(mlAgent, new HashMap<>(), agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors();
        // Respond with all tool output
        assertEquals(1, agentOutput.size());
        Assert.assertNull(agentOutput.get(0).getResult());
        assertEquals("This is the final answer", agentOutput.get(0).getDataAsMap().get("response"));
    }

    @Test
    public void testRunWithIncludeOutputSet() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).includeOutputInAgentResponse(true).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).includeOutputInAgentResponse(true).build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .llm(llmSpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();
        mlReActAgentRunner.run(mlAgent, new HashMap<>(), agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors();
        // Respond with all tool output
        assertEquals(3, agentOutput.size());
        assertEquals("First tool response", agentOutput.get(0).getResult());
        assertEquals("Second tool response", agentOutput.get(1).getResult());
        assertEquals("This is the final answer", agentOutput.get(2).getDataAsMap().get("response"));
    }

    @Test
    public void testRunWithIncludeOutputSetToSomeTools() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).includeOutputInAgentResponse(true).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).includeOutputInAgentResponse(false).build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .llm(llmSpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();
        mlReActAgentRunner.run(mlAgent, new HashMap<>(), agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors();
        // Respond with all tool output
        assertEquals(2, agentOutput.size());
        assertEquals("First tool response", agentOutput.get(0).getResult());
        assertEquals("This is the final answer", agentOutput.get(1).getDataAsMap().get("response"));
    }

    public static XContentBuilder builder() throws IOException {
        return XContentBuilder.builder(XContentType.JSON.xContent());
    }

    private SearchResponse createChatResponse() throws IOException {
        XContentBuilder content = builder();
        content.startObject();
        content.field("question", "Test Question");
        content.field("response", "test response");
        content.endObject();

        SearchHit[] hits = new SearchHit[1];
        hits[0] = new SearchHit(0, "modelId", null, null).sourceRef(BytesReference.bytes(content));

        return new SearchResponse(
            new InternalSearchResponse(
                new SearchHits(hits, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f),
                InternalAggregations.EMPTY,
                new Suggest(Collections.emptyList()),
                new SearchProfileShardResults(Collections.emptyMap()),
                false,
                false,
                1
            ),
            "",
            5,
            5,
            0,
            100,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
    }

}
