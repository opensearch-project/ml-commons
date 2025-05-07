/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_ENABLED;
import static org.opensearch.ml.engine.algorithms.agent.MLAgentExecutor.MEMORY_ID;
import static org.opensearch.ml.engine.algorithms.agent.MLAgentExecutor.QUESTION;
import static org.opensearch.ml.engine.algorithms.agent.MLAgentExecutor.REGENERATE_INTERACTION_ID;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.Context;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.Version;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.MLTaskOutput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.MLMemoryManager;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.memory.action.conversation.GetInteractionAction;
import org.opensearch.ml.memory.action.conversation.GetInteractionResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import com.google.gson.Gson;

import software.amazon.awssdk.utils.ImmutableMap;

public class MLAgentExecutorTest {

    @Mock
    private Client client;
    SdkClient sdkClient;
    private Settings settings;
    @Mock
    private ClusterService clusterService;
    @Mock
    private ClusterState clusterState;
    @Mock
    private Metadata metadata;
    @Mock
    private NamedXContentRegistry xContentRegistry;
    @Mock
    private Map<String, Tool.Factory> toolFactories;
    @Mock
    private Map<String, Memory.Factory> memoryMap;
    @Mock
    private IndexResponse indexResponse;
    @Mock
    private ThreadPool threadPool;
    private ThreadContext threadContext;
    @Mock
    private Context context;
    @Mock
    private ConversationIndexMemory.Factory mockMemoryFactory;
    @Mock
    private ActionListener<Output> agentActionListener;
    @Mock
    private MLAgentRunner mlAgentRunner;

    @Mock
    private ConversationIndexMemory memory;
    @Mock
    private MLMemoryManager memoryManager;
    private MLAgentExecutor mlAgentExecutor;

    @Captor
    private ArgumentCaptor<Output> objectCaptor;

    @Captor
    private ArgumentCaptor<Exception> exceptionCaptor;

    private DiscoveryNode localNode = new DiscoveryNode(
        "mockClusterManagerNodeId",
        "mockClusterManagerNodeId",
        new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
        Collections.emptyMap(),
        Collections.singleton(CLUSTER_MANAGER_ROLE),
        Version.CURRENT
    );

    MLAgent mlAgent;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        threadContext = new ThreadContext(settings);
        memoryMap = ImmutableMap.of("memoryType", mockMemoryFactory);
        Mockito.doAnswer(invocation -> {
            MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type("memoryType").build();
            MLAgent mlAgent = MLAgent.builder().name("agent").memory(mlMemorySpec).type("flow").build();
            XContentBuilder content = mlAgent.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
            ActionListener<Object> listener = invocation.getArgument(1);
            GetResponse getResponse = Mockito.mock(GetResponse.class);
            Mockito.when(getResponse.isExists()).thenReturn(true);
            Mockito.when(getResponse.getSourceAsBytesRef()).thenReturn(BytesReference.bytes(content));
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(Mockito.any(), Mockito.any());
        Mockito.when(clusterService.state()).thenReturn(clusterState);
        Mockito.when(clusterState.metadata()).thenReturn(metadata);
        when(clusterService.localNode()).thenReturn(localNode);
        Mockito.when(metadata.hasIndex(Mockito.anyString())).thenReturn(true);
        Mockito.when(memory.getMemoryManager()).thenReturn(memoryManager);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, Set.of(ML_COMMONS_MCP_CONNECTOR_ENABLED)));

        settings = Settings.builder().build();
        mlAgentExecutor = Mockito
            .spy(new MLAgentExecutor(client, sdkClient, settings, clusterService, xContentRegistry, toolFactories, memoryMap, false, null));

    }

    @Test
    public void test_NoAgentIndex() {
        ModelTensor modelTensor = ModelTensor.builder().name("response").dataAsMap(ImmutableMap.of("test_key", "test_value")).build();
        Mockito.doAnswer(invocation -> {
            ActionListener<ModelTensor> listener = invocation.getArgument(2);
            listener.onResponse(modelTensor);
            return null;
        }).when(mlAgentRunner).run(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        Mockito.when(metadata.hasIndex(Mockito.anyString())).thenReturn(false);

        mlAgentExecutor.execute(getAgentMLInput(), agentActionListener);

        Mockito.verify(agentActionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        Assert.assertTrue(exception instanceof ResourceNotFoundException);
        Assert.assertEquals(exception.getMessage(), "Agent index not found");
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NullInput_ThrowsException() {
        mlAgentExecutor.execute(null, agentActionListener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NonAgentInput_ThrowsException() {
        Input input = new Input() {
            @Override
            public FunctionName getFunctionName() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {

            }

            @Override
            public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
                return null;
            }
        };
        mlAgentExecutor.execute(input, agentActionListener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NonInputData_ThrowsException() {
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, null);
        mlAgentExecutor.execute(agentMLInput, agentActionListener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_NonInputParas_ThrowsException() {
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(null).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, inputDataSet);
        mlAgentExecutor.execute(agentMLInput, agentActionListener);
    }

    @Test
    public void test_HappyCase_ReturnsResult() throws IOException {
        ModelTensor modelTensor = ModelTensor.builder().name("response").dataAsMap(ImmutableMap.of("test_key", "test_value")).build();
        Mockito.doAnswer(invocation -> {
            ActionListener<ModelTensor> listener = invocation.getArgument(2);
            listener.onResponse(modelTensor);
            return null;
        }).when(mlAgentRunner).run(Mockito.any(), Mockito.any(), Mockito.any());

        GetResponse agentGetResponse = prepareMLAgent("test-agent-id", false, null);
        Mockito.doAnswer(invocation -> {
            // Extract the ActionListener argument from the method invocation
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            // Trigger the onResponse method of the ActionListener with the mock response
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));

        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        mlAgentExecutor.execute(getAgentMLInput(), agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput output = (ModelTensorOutput) objectCaptor.getValue();
        Assert.assertEquals(1, output.getMlModelOutputs().size());
        Assert.assertEquals(1, output.getMlModelOutputs().get(0).getMlModelTensors().size());
        Assert.assertEquals(modelTensor, output.getMlModelOutputs().get(0).getMlModelTensors().get(0));
    }

    @Test
    public void test_AgentRunnerReturnsListOfModelTensor_ReturnsResult() throws IOException {
        ModelTensor modelTensor1 = ModelTensor.builder().name("response1").dataAsMap(ImmutableMap.of("test_key", "test_value")).build();
        ModelTensor modelTensor2 = ModelTensor.builder().name("response2").dataAsMap(ImmutableMap.of("test_key", "test_value")).build();
        List<ModelTensor> response = Arrays.asList(modelTensor1, modelTensor2);
        Mockito.doAnswer(invocation -> {
            ActionListener<List<ModelTensor>> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(mlAgentRunner).run(Mockito.any(), Mockito.any(), Mockito.any());

        GetResponse agentGetResponse = prepareMLAgent("test-agent-id", false, null);
        Mockito.doAnswer(invocation -> {
            // Extract the ActionListener argument from the method invocation
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            // Trigger the onResponse method of the ActionListener with the mock response
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));

        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        mlAgentExecutor.execute(getAgentMLInput(), agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput output = (ModelTensorOutput) objectCaptor.getValue();
        Assert.assertEquals(1, output.getMlModelOutputs().size());
        Assert.assertEquals(2, output.getMlModelOutputs().get(0).getMlModelTensors().size());
        Assert.assertEquals(response, output.getMlModelOutputs().get(0).getMlModelTensors());
    }

    @Test
    public void test_AgentRunnerReturnsListOfModelTensors_ReturnsResult() throws IOException {
        ModelTensor modelTensor1 = ModelTensor.builder().name("response1").dataAsMap(ImmutableMap.of("test_key", "test_value")).build();
        ModelTensors modelTensors1 = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor1)).build();
        ModelTensor modelTensor2 = ModelTensor.builder().name("response2").dataAsMap(ImmutableMap.of("test_key", "test_value")).build();
        ModelTensors modelTensors2 = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor2)).build();
        List<ModelTensors> response = Arrays.asList(modelTensors1, modelTensors2);
        Mockito.doAnswer(invocation -> {
            ActionListener<List<ModelTensors>> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(mlAgentRunner).run(Mockito.any(), Mockito.any(), Mockito.any());

        GetResponse agentGetResponse = prepareMLAgent("test-agent-id", false, null);
        Mockito.doAnswer(invocation -> {
            // Extract the ActionListener argument from the method invocation
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            // Trigger the onResponse method of the ActionListener with the mock response
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));

        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        mlAgentExecutor.execute(getAgentMLInput(), agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput output = (ModelTensorOutput) objectCaptor.getValue();
        Assert.assertEquals(1, output.getMlModelOutputs().size());
        Assert.assertEquals(2, output.getMlModelOutputs().get(0).getMlModelTensors().size());
        Assert.assertEquals(Arrays.asList(modelTensor1, modelTensor2), output.getMlModelOutputs().get(0).getMlModelTensors());
    }

    @Test
    public void test_AgentRunnerReturnsListOfString_ReturnsResult() throws IOException {
        List<String> response = Arrays.asList("response1", "response2");
        Mockito.doAnswer(invocation -> {
            ActionListener<List<String>> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(mlAgentRunner).run(Mockito.any(), Mockito.any(), Mockito.any());

        GetResponse agentGetResponse = prepareMLAgent("test-agent-id", false, null);
        Mockito.doAnswer(invocation -> {
            // Extract the ActionListener argument from the method invocation
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            // Trigger the onResponse method of the ActionListener with the mock response
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));

        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        mlAgentExecutor.execute(getAgentMLInput(), agentActionListener);

        Gson gson = new Gson();
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput output = (ModelTensorOutput) objectCaptor.getValue();
        Assert.assertEquals(1, output.getMlModelOutputs().size());
        Assert.assertEquals(1, output.getMlModelOutputs().get(0).getMlModelTensors().size());
        Assert.assertEquals(gson.toJson(response), output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getResult());
    }

    @Test
    public void test_AgentRunnerReturnsString_ReturnsResult() throws IOException {
        Mockito.doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(2);
            listener.onResponse("response");
            return null;
        }).when(mlAgentRunner).run(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        GetResponse agentGetResponse = prepareMLAgent("test-agent-id", false, null);
        Mockito.doAnswer(invocation -> {
            // Extract the ActionListener argument from the method invocation
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            // Trigger the onResponse method of the ActionListener with the mock response
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));

        mlAgentExecutor.execute(getAgentMLInput(), agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput output = (ModelTensorOutput) objectCaptor.getValue();
        Assert.assertEquals(1, output.getMlModelOutputs().size());
        Assert.assertEquals(1, output.getMlModelOutputs().get(0).getMlModelTensors().size());
        Assert.assertEquals("response", output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getResult());
    }

    @Test
    public void test_AgentRunnerReturnsModelTensorOutput_ReturnsResult() throws IOException {
        ModelTensor modelTensor1 = ModelTensor.builder().name("response1").dataAsMap(ImmutableMap.of("test_key", "test_value")).build();
        ModelTensors modelTensors1 = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor1)).build();
        ModelTensor modelTensor2 = ModelTensor.builder().name("response2").dataAsMap(ImmutableMap.of("test_key", "test_value")).build();
        ModelTensors modelTensors2 = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor2)).build();
        List<ModelTensors> modelTensorsList = Arrays.asList(modelTensors1, modelTensors2);
        ModelTensorOutput modelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(modelTensorsList).build();
        Mockito.doAnswer(invocation -> {
            ActionListener<ModelTensorOutput> listener = invocation.getArgument(2);
            listener.onResponse(modelTensorOutput);
            return null;
        }).when(mlAgentRunner).run(Mockito.any(), Mockito.any(), Mockito.any());
        GetResponse agentGetResponse = prepareMLAgent("test-agent-id", false, null);
        Mockito.doAnswer(invocation -> {
            // Extract the ActionListener argument from the method invocation
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            // Trigger the onResponse method of the ActionListener with the mock response
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));

        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        mlAgentExecutor.execute(getAgentMLInput(), agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput output = (ModelTensorOutput) objectCaptor.getValue();
        Assert.assertEquals(1, output.getMlModelOutputs().size());
        Assert.assertEquals(2, output.getMlModelOutputs().get(0).getMlModelTensors().size());
        Assert.assertEquals(Arrays.asList(modelTensor1, modelTensor2), output.getMlModelOutputs().get(0).getMlModelTensors());
    }

    @Test
    public void test_CreateConversation_ReturnsResult() throws IOException {
        ModelTensor modelTensor = ModelTensor.builder().name("response").dataAsMap(ImmutableMap.of("test_key", "test_value")).build();
        ConversationIndexMemory memory = Mockito.mock(ConversationIndexMemory.class);
        Mockito.doAnswer(invocation -> {
            ActionListener<ModelTensor> listener = invocation.getArgument(2);
            listener.onResponse(modelTensor);
            return null;
        }).when(mlAgentRunner).run(Mockito.any(), Mockito.any(), Mockito.any());

        GetResponse agentGetResponse = prepareMLAgent("test-agent-id", false, null);
        Mockito.doAnswer(invocation -> {
            // Extract the ActionListener argument from the method invocation
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            // Trigger the onResponse method of the ActionListener with the mock response
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));

        CreateInteractionResponse interaction = Mockito.mock(CreateInteractionResponse.class);
        Mockito.when(interaction.getId()).thenReturn("interaction_id");
        Mockito.doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> responseActionListener = invocation.getArgument(4);
            responseActionListener.onResponse(interaction);
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Mockito.doAnswer(invocation -> {
            Mockito.when(memory.getConversationId()).thenReturn("conversation_id");
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onResponse(memory);
            return null;
        }).when(mockMemoryFactory).create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Map<String, String> params = new HashMap<>();
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, dataset);
        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        mlAgentExecutor.execute(agentMLInput, agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput output = (ModelTensorOutput) objectCaptor.getValue();
        Assert.assertEquals(1, output.getMlModelOutputs().size());
        Assert.assertEquals(1, output.getMlModelOutputs().get(0).getMlModelTensors().size());
        Assert.assertEquals(modelTensor, output.getMlModelOutputs().get(0).getMlModelTensors().get(0));
    }

    @Test
    public void test_Regenerate_Validation() throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put(REGENERATE_INTERACTION_ID, "foo");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, dataset);
        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());

        GetResponse agentGetResponse = prepareMLAgent("test-agent-id", false, null);
        Mockito.doAnswer(invocation -> {
            // Extract the ActionListener argument from the method invocation
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            // Trigger the onResponse method of the ActionListener with the mock response
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));
        mlAgentExecutor.execute(agentMLInput, agentActionListener);

        Mockito.verify(agentActionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        Assert.assertTrue(exception instanceof IllegalArgumentException);
        Assert.assertEquals(exception.getMessage(), "A memory ID must be provided to regenerate.");
    }

    @Test
    public void test_Regenerate_GetOriginalInteraction() throws IOException {
        ModelTensor modelTensor = ModelTensor.builder().name("response").dataAsMap(ImmutableMap.of("test_key", "test_value")).build();
        Mockito.doAnswer(invocation -> {
            ActionListener<ModelTensor> listener = invocation.getArgument(2);
            listener.onResponse(modelTensor);
            return null;
        }).when(mlAgentRunner).run(Mockito.any(), Mockito.any(), Mockito.any());

        GetResponse agentGetResponse = prepareMLAgent("test-agent-id", false, null);
        Mockito.doAnswer(invocation -> {
            // Extract the ActionListener argument from the method invocation
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            // Trigger the onResponse method of the ActionListener with the mock response
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));

        CreateInteractionResponse interaction = Mockito.mock(CreateInteractionResponse.class);
        Mockito.when(interaction.getId()).thenReturn("interaction_id");
        Mockito.doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> responseActionListener = invocation.getArgument(4);
            responseActionListener.onResponse(interaction);
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Mockito.doAnswer(invocation -> {
            Mockito.when(memory.getConversationId()).thenReturn("conversation_id");
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onResponse(memory);
            return null;
        }).when(mockMemoryFactory).create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Mockito.doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(Boolean.TRUE);
            return null;
        }).when(memoryManager).deleteInteractionAndTrace(Mockito.anyString(), Mockito.any());

        Mockito.doAnswer(invocation -> {
            ActionListener<GetInteractionResponse> listener = invocation.getArgument(2);
            GetInteractionResponse interactionResponse = Mockito.mock(GetInteractionResponse.class);
            Interaction mockInteraction = Mockito.mock(Interaction.class);
            Mockito.when(mockInteraction.getInput()).thenReturn("regenerate question");
            Mockito.when(interactionResponse.getInteraction()).thenReturn(mockInteraction);
            listener.onResponse(interactionResponse);
            return null;
        }).when(client).execute(Mockito.eq(GetInteractionAction.INSTANCE), Mockito.any(), Mockito.any());

        String interactionId = "bar-interaction";
        Map<String, String> params = new HashMap<>();
        params.put(MEMORY_ID, "foo-memory");
        params.put(REGENERATE_INTERACTION_ID, interactionId);
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, dataset);
        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        mlAgentExecutor.execute(agentMLInput, agentActionListener);

        Mockito.verify(client, times(1)).execute(Mockito.eq(GetInteractionAction.INSTANCE), Mockito.any(), Mockito.any());
        Assert.assertEquals(params.get(QUESTION), "regenerate question");
        // original interaction got deleted
        Mockito.verify(memoryManager, times(1)).deleteInteractionAndTrace(Mockito.eq(interactionId), Mockito.any());
    }

    @Test
    public void test_Regenerate_OriginalInteraction_NotExist() throws IOException {
        ModelTensor modelTensor = ModelTensor.builder().name("response").dataAsMap(ImmutableMap.of("test_key", "test_value")).build();
        ConversationIndexMemory memory = Mockito.mock(ConversationIndexMemory.class);
        Mockito.doAnswer(invocation -> {
            ActionListener<ModelTensor> listener = invocation.getArgument(2);
            listener.onResponse(modelTensor);
            return null;
        }).when(mlAgentRunner).run(Mockito.any(), Mockito.any(), Mockito.any());

        GetResponse agentGetResponse = prepareMLAgent("test-agent-id", false, null);
        Mockito.doAnswer(invocation -> {
            // Extract the ActionListener argument from the method invocation
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            // Trigger the onResponse method of the ActionListener with the mock response
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));

        CreateInteractionResponse interaction = Mockito.mock(CreateInteractionResponse.class);
        Mockito.when(interaction.getId()).thenReturn("interaction_id");
        Mockito.doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> responseActionListener = invocation.getArgument(4);
            responseActionListener.onResponse(interaction);
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Mockito.doAnswer(invocation -> {
            Mockito.when(memory.getConversationId()).thenReturn("conversation_id");
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onResponse(memory);
            return null;
        }).when(mockMemoryFactory).create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Mockito.doAnswer(invocation -> {
            ActionListener<GetInteractionResponse> listener = invocation.getArgument(2);
            listener.onFailure(new ResourceNotFoundException("Interaction bar-interaction not found"));
            return null;
        }).when(client).execute(Mockito.eq(GetInteractionAction.INSTANCE), Mockito.any(), Mockito.any());

        Map<String, String> params = new HashMap<>();
        params.put(MEMORY_ID, "foo-memory");
        params.put(REGENERATE_INTERACTION_ID, "bar-interaction");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, dataset);
        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        mlAgentExecutor.execute(agentMLInput, agentActionListener);

        Mockito.verify(client, times(1)).execute(Mockito.eq(GetInteractionAction.INSTANCE), Mockito.any(), Mockito.any());
        Assert.assertNull(params.get(QUESTION));

        Mockito.verify(agentActionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        Assert.assertTrue(exception instanceof ResourceNotFoundException);
        Assert.assertEquals(exception.getMessage(), "Interaction bar-interaction not found");
    }

    @Test
    public void test_CreateFlowAgent() {
        MLAgent mlAgent = MLAgent.builder().name("test_agent").type("flow").build();
        MLAgentRunner mlAgentRunner = mlAgentExecutor.getAgentRunner(mlAgent);
        Assert.assertTrue(mlAgentRunner instanceof MLFlowAgentRunner);
    }

    @Test
    public void test_CreateChatAgent() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLAgent mlAgent = MLAgent.builder().name("test_agent").type(MLAgentType.CONVERSATIONAL.name()).llm(llmSpec).build();
        MLAgentRunner mlAgentRunner = mlAgentExecutor.getAgentRunner(mlAgent);
        Assert.assertTrue(mlAgentRunner instanceof MLChatAgentRunner);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_InvalidAgent_ThrowsException() {
        MLAgent mlAgent = MLAgent.builder().name("test_agent").type("illegal").build();
        mlAgentExecutor.getAgentRunner(mlAgent);
    }

    @Test
    public void test_GetModel_ThrowsException() {
        Mockito.doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException());
            return null;
        }).when(client).get(Mockito.any(), Mockito.any());
        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        mlAgentExecutor.execute(getAgentMLInput(), agentActionListener);

        Mockito.verify(agentActionListener).onFailure(exceptionCaptor.capture());
        Assert.assertNotNull(exceptionCaptor.getValue());
    }

    @Test
    public void test_GetModelDoesNotExist_ThrowsException() {
        Mockito.doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            GetResponse getResponse = Mockito.mock(GetResponse.class);
            Mockito.when(getResponse.isExists()).thenReturn(false);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(Mockito.any(), Mockito.any());
        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        mlAgentExecutor.execute(getAgentMLInput(), agentActionListener);

        Mockito.verify(agentActionListener).onFailure(exceptionCaptor.capture());
        Assert.assertNotNull(exceptionCaptor.getValue());
    }

    @Test
    public void test_CreateConversationFailure_ThrowsException() {
        Mockito.doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException());
            return null;
        }).when(mockMemoryFactory).create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        Map<String, String> params = new HashMap<>();
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, dataset);
        mlAgentExecutor.execute(agentMLInput, agentActionListener);

        Mockito.verify(agentActionListener).onFailure(exceptionCaptor.capture());
        Assert.assertNotNull(exceptionCaptor.getValue());
    }

    @Test
    public void test_CreateInteractionFailure_ThrowsException() {
        ConversationIndexMemory memory = Mockito.mock(ConversationIndexMemory.class);
        Mockito.doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> responseActionListener = invocation.getArgument(4);
            responseActionListener.onFailure(new RuntimeException());
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Mockito.doAnswer(invocation -> {
            Mockito.when(memory.getConversationId()).thenReturn("conversation_id");
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onResponse(memory);
            return null;
        }).when(mockMemoryFactory).create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        Map<String, String> params = new HashMap<>();
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, dataset);
        mlAgentExecutor.execute(agentMLInput, agentActionListener);

        Mockito.verify(agentActionListener).onFailure(exceptionCaptor.capture());
        Assert.assertNotNull(exceptionCaptor.getValue());
    }

    @Test
    public void test_AgentRunnerFailure_ReturnsResult() {
        ModelTensor modelTensor = ModelTensor.builder().name("response").dataAsMap(ImmutableMap.of("test_key", "test_value")).build();
        Mockito.doAnswer(invocation -> {
            ActionListener<ModelTensor> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException());
            return null;
        }).when(mlAgentRunner).run(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        mlAgentExecutor.execute(getAgentMLInput(), agentActionListener);

        Mockito.verify(agentActionListener).onFailure(exceptionCaptor.capture());
        Assert.assertNotNull(exceptionCaptor.getValue());
    }

    @Test
    public void test_AsyncMode_ReturnsTaskId() throws IOException {
        ModelTensor modelTensor = ModelTensor.builder().name("response").result("test").build();
        GetResponse agentGetResponse = prepareMLAgent("test-agent-id", false, null);
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());

        Mockito.doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(), Mockito.any());

        indexResponse = new IndexResponse(new ShardId(ML_TASK_INDEX, "_na_", 0), "task_id", 1, 0, 2, true);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(client).index(any(), any());

        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        AgentMLInput input = getAgentMLInput();
        input.setIsAsync(true);

        mlAgentExecutor.execute(input, agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        MLTaskOutput result = (MLTaskOutput) objectCaptor.getValue();

        Assert.assertEquals("task_id", result.getTaskId());
        Assert.assertEquals("RUNNING", result.getStatus());
    }

    @Test
    public void test_AsyncMode_IndexTask_failure() throws IOException {
        ModelTensor modelTensor = ModelTensor.builder().name("response").result("test").build();
        GetResponse agentGetResponse = prepareMLAgent("test-agent-id", false, null);
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());

        Mockito.doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(), Mockito.any());

        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new Exception("Index Not Found"));
            return null;
        }).when(client).index(any(), any());

        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());
        AgentMLInput input = getAgentMLInput();
        input.setIsAsync(true);

        mlAgentExecutor.execute(input, agentActionListener);

        Mockito.verify(agentActionListener).onFailure(exceptionCaptor.capture());
        Assert.assertNotNull(exceptionCaptor.getValue());
    }

    private AgentMLInput getAgentMLInput() {
        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.PARENT_INTERACTION_ID, "parentInteractionId");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        return new AgentMLInput("test", null, FunctionName.AGENT, dataset);
    }

    public GetResponse prepareMLAgent(String agentId, boolean isHidden, String tenantId) throws IOException {

        mlAgent = new MLAgent(
            "test",
            MLAgentType.CONVERSATIONAL.name(),
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List
                .of(
                    new MLToolSpec(
                        "memoryType",
                        "test",
                        "test",
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        false,
                        Collections.emptyMap(),
                        null,
                        null
                    )
                ),
            Map.of("test", "test"),
            new MLMemorySpec("memoryType", "123", 0),
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            isHidden,
            tenantId
        );

        XContentBuilder content = mlAgent.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", agentId, 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }

}
