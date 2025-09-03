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
import static org.opensearch.ml.common.CommonValue.MCP_CONNECTORS_FIELD;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_SEARCH_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_SEARCH_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;
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
import org.opensearch.OpenSearchException;
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
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
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

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

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
        when(this.clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(settings, Set.of(ML_COMMONS_MCP_CONNECTOR_ENABLED, ML_COMMONS_AGENTIC_SEARCH_ENABLED)));

        // Mock MLFeatureEnabledSetting
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isMcpConnectorEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isAgenticSearchEnabled()).thenReturn(true);

        settings = Settings.builder().build();
        mlAgentExecutor = Mockito
            .spy(
                new MLAgentExecutor(
                    client,
                    sdkClient,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryMap,
                    mlFeatureEnabledSetting,
                    null
                )
            );

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

    @Test
    public void test_query_planning_agentic_search_disabled() throws IOException {
        // Create an MLAgent with QueryPlanningTool
        MLAgent mlAgentWithQueryPlanning = new MLAgent(
            "test",
            MLAgentType.FLOW.name(),
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List
                .of(
                    new MLToolSpec(
                        "QueryPlanningTool",
                        "QueryPlanningTool",
                        "QueryPlanningTool",
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
            false,
            null
        );

        // Create GetResponse with the MLAgent that has QueryPlanningTool
        XContentBuilder content = mlAgentWithQueryPlanning.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "test-agent-id", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse agentGetResponse = new GetResponse(getResult);

        // Create a new MLAgentExecutor with agentic search disabled
        MLFeatureEnabledSetting disabledSearchSetting = Mockito.mock(MLFeatureEnabledSetting.class);
        when(disabledSearchSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(disabledSearchSetting.isMcpConnectorEnabled()).thenReturn(true);
        when(disabledSearchSetting.isAgenticSearchEnabled()).thenReturn(false);

        MLAgentExecutor mlAgentExecutorWithDisabledSearch = Mockito
            .spy(
                new MLAgentExecutor(
                    client,
                    sdkClient,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryMap,
                    disabledSearchSetting,
                    null
                )
            );

        // Mock the agent get response
        Mockito.doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));

        // Mock the agent runner
        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutorWithDisabledSearch).getAgentRunner(Mockito.any());

        // Execute the agent
        mlAgentExecutorWithDisabledSearch.execute(getAgentMLInput(), agentActionListener);

        // Verify that the execution fails with the correct error message
        Mockito.verify(agentActionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        Assert.assertTrue(exception instanceof OpenSearchException);
        Assert.assertEquals(exception.getMessage(), ML_COMMONS_AGENTIC_SEARCH_DISABLED_MESSAGE);
    }

    @Test
    public void test_mcp_connector_requires_mcp_connector_enabled() throws IOException {
        // Create an MLAgent with MCP connectors in parameters
        Map<String, String> parameters = new HashMap<>();
        parameters.put(MCP_CONNECTORS_FIELD, "[{\"connector_id\": \"test-connector\"}]");

        MLAgent mlAgentWithMcpConnectors = new MLAgent(
            "test",
            MLAgentType.FLOW.name(),
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            Collections.emptyList(),
            parameters,
            new MLMemorySpec("memoryType", "123", 0),
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            false,
            null
        );

        // Create GetResponse with the MLAgent that has MCP connectors
        XContentBuilder content = mlAgentWithMcpConnectors.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "test-agent-id", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse agentGetResponse = new GetResponse(getResult);

        // Create a new MLAgentExecutor with MCP connector disabled
        MLFeatureEnabledSetting disabledMcpSetting = Mockito.mock(MLFeatureEnabledSetting.class);
        when(disabledMcpSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(disabledMcpSetting.isMcpConnectorEnabled()).thenReturn(false);
        when(disabledMcpSetting.isAgenticSearchEnabled()).thenReturn(true);

        MLAgentExecutor mlAgentExecutorWithDisabledMcp = Mockito
            .spy(
                new MLAgentExecutor(
                    client,
                    sdkClient,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryMap,
                    disabledMcpSetting,
                    null
                )
            );

        // Mock the agent get response
        Mockito.doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));

        // Mock the agent runner
        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutorWithDisabledMcp).getAgentRunner(Mockito.any());

        // Execute the agent
        mlAgentExecutorWithDisabledMcp.execute(getAgentMLInput(), agentActionListener);

        // Verify that the execution fails with the correct error message
        Mockito.verify(agentActionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        Assert.assertTrue(exception instanceof OpenSearchException);
        Assert.assertEquals(exception.getMessage(), ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE);
    }

    @Test
    public void test_query_planning_agentic_search_enabled() throws IOException {
        // Create an MLAgent with QueryPlanningTool
        MLAgent mlAgentWithQueryPlanning = new MLAgent(
            "test",
            MLAgentType.FLOW.name(),
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List
                .of(
                    new MLToolSpec(
                        "QueryPlanningTool",
                        "QueryPlanningTool",
                        "QueryPlanningTool",
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
            false,
            null
        );

        // Create GetResponse with the MLAgent that has QueryPlanningTool
        XContentBuilder content = mlAgentWithQueryPlanning.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "test-agent-id", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse agentGetResponse = new GetResponse(getResult);

        // Create a new MLAgentExecutor with agentic search enabled
        MLFeatureEnabledSetting enabledSearchSetting = Mockito.mock(MLFeatureEnabledSetting.class);
        when(enabledSearchSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(enabledSearchSetting.isMcpConnectorEnabled()).thenReturn(true);
        when(enabledSearchSetting.isAgenticSearchEnabled()).thenReturn(true);

        MLAgentExecutor mlAgentExecutorWithEnabledSearch = Mockito
            .spy(
                new MLAgentExecutor(
                    client,
                    sdkClient,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryMap,
                    enabledSearchSetting,
                    null
                )
            );

        // Mock the agent get response
        Mockito.doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));

        // Mock the agent runner
        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutorWithEnabledSearch).getAgentRunner(Mockito.any());

        // Mock successful execution
        ModelTensor modelTensor = ModelTensor.builder().name("response").dataAsMap(ImmutableMap.of("test_key", "test_value")).build();
        Mockito.doAnswer(invocation -> {
            ActionListener<ModelTensor> listener = invocation.getArgument(2);
            listener.onResponse(modelTensor);
            return null;
        }).when(mlAgentRunner).run(Mockito.any(), Mockito.any(), Mockito.any());

        // Execute the agent
        mlAgentExecutorWithEnabledSearch.execute(getAgentMLInput(), agentActionListener);

        // Verify that the execution succeeds
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput output = (ModelTensorOutput) objectCaptor.getValue();
        Assert.assertEquals(1, output.getMlModelOutputs().size());
        Assert.assertEquals(1, output.getMlModelOutputs().get(0).getMlModelTensors().size());
        Assert.assertEquals(modelTensor, output.getMlModelOutputs().get(0).getMlModelTensors().get(0));
    }

    private AgentMLInput getAgentMLInput() {
        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.PARENT_INTERACTION_ID, "parentInteractionId");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        return new AgentMLInput("test", null, FunctionName.AGENT, dataset);
    }

    @Test
    public void test_handleMemoryCreation_noMemorySpec() throws IOException {
        // Test execution when no memory spec is provided
        ModelTensor modelTensor = ModelTensor.builder().name("response").dataAsMap(ImmutableMap.of("test_key", "test_value")).build();
        Mockito.doAnswer(invocation -> {
            ActionListener<ModelTensor> listener = invocation.getArgument(2);
            listener.onResponse(modelTensor);
            return null;
        }).when(mlAgentRunner).run(Mockito.any(), Mockito.any(), Mockito.any());

        GetResponse agentGetResponse = prepareMLAgent("test-agent-id", false, null);
        Mockito.doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));

        Mockito.doReturn(mlAgentRunner).when(mlAgentExecutor).getAgentRunner(Mockito.any());

        // Test with no memory spec (null memory in agent)
        Map<String, String> params = new HashMap<>();
        params.put(MEMORY_ID, "test-memory-id");
        params.put(MLAgentExecutor.PARENT_INTERACTION_ID, "test-parent-id");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, dataset);

        mlAgentExecutor.execute(agentMLInput, agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput output = (ModelTensorOutput) objectCaptor.getValue();
        Assert.assertEquals(1, output.getMlModelOutputs().size());
    }

    @Test
    public void test_handleMemoryCreation_unsupportedMemoryFactory() throws IOException {
        // Test handling of unsupported memory factory type
        Memory.Factory unsupportedFactory = Mockito.mock(Memory.Factory.class);
        Map<String, Memory.Factory> memoryFactoryMap = ImmutableMap.of("unsupported_type", unsupportedFactory);

        MLAgentExecutor executor = Mockito
            .spy(
                new MLAgentExecutor(
                    client,
                    sdkClient,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryFactoryMap,
                    mlFeatureEnabledSetting,
                    null
                )
            );

        // Create an agent with unsupported memory type
        MLMemorySpec unsupportedMemorySpec = MLMemorySpec.builder().type("unsupported_type").build();
        MLAgent mlAgentWithUnsupportedMemory = new MLAgent(
            "test",
            MLAgentType.CONVERSATIONAL.name(),
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            Collections.emptyList(),
            Map.of("test", "test"),
            unsupportedMemorySpec,
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            false,
            null
        );

        // Create GetResponse with the MLAgent that has unsupported memory
        XContentBuilder content = mlAgentWithUnsupportedMemory.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "test-agent-id", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse agentGetResponse = new GetResponse(getResult);

        Mockito.doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(agentGetResponse);
            return null;
        }).when(client).get(Mockito.any(GetRequest.class), Mockito.any(ActionListener.class));

        Mockito.doReturn(mlAgentRunner).when(executor).getAgentRunner(Mockito.any());

        // Test with unsupported memory factory
        Map<String, String> params = new HashMap<>();
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, dataset);

        executor.execute(agentMLInput, agentActionListener);

        Mockito.verify(agentActionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        Assert.assertTrue(exception instanceof IllegalArgumentException);
        Assert.assertTrue(exception.getMessage().contains("Unsupported memory factory type"));
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

    @Test
    public void testConfigureMemorySpecBranches() {
        // Test with null memory in AgentMLInput
        MLAgent agent = MLAgent.builder().name("test").type("flow").build();
        AgentMLInput input = new AgentMLInput("test", null, FunctionName.AGENT, null);
        Map<String, String> params = new HashMap<>();
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();

        MLMemorySpec result = mlAgentExecutor.configureMemorySpec(agent, input, dataset);
        Assert.assertNull(result);

        // Test with bedrock_agentcore_memory in parameters
        params.put("memory_type", "bedrock_agentcore_memory");
        result = mlAgentExecutor.configureMemorySpec(agent, input, dataset);
        Assert.assertNotNull(result);
        Assert.assertEquals("bedrock_agentcore_memory", result.getType());

        // Test with memory in AgentMLInput
        Map<String, Object> memoryMap = new HashMap<>();
        memoryMap.put("type", "test_memory");
        input = new AgentMLInput("test", null, FunctionName.AGENT, dataset);
        input.setMemory(memoryMap);
        result = mlAgentExecutor.configureMemorySpec(agent, input, dataset);
        Assert.assertNotNull(result);
        Assert.assertEquals("test_memory", result.getType());
    }

    @Test
    public void testConfigureMemoryFromInputBranches() {
        Map<String, String> params = new HashMap<>();
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();

        // Test with null memory type
        Map<String, Object> memoryMap = new HashMap<>();
        MLMemorySpec result = mlAgentExecutor.configureMemoryFromInput(memoryMap, dataset);
        Assert.assertNull(result);

        // Test with valid memory type and all parameters
        memoryMap.put("type", "bedrock_agentcore_memory");
        memoryMap.put("memory_arn", "test-arn");
        memoryMap.put("region", "us-west-2");

        Map<String, Object> credentials = new HashMap<>();
        credentials.put("access_key", "test-access");
        credentials.put("secret_key", "test-secret");
        credentials.put("session_token", "test-token");
        memoryMap.put("credentials", credentials);

        result = mlAgentExecutor.configureMemoryFromInput(memoryMap, dataset);
        Assert.assertNotNull(result);
        Assert.assertEquals("bedrock_agentcore_memory", result.getType());
        Assert.assertEquals("bedrock_agentcore_memory", params.get("memory_type"));
        Assert.assertEquals("test-arn", params.get("memory_arn"));
        Assert.assertEquals("us-west-2", params.get("memory_region"));
        Assert.assertEquals("test-access", params.get("memory_access_key"));
        Assert.assertEquals("test-secret", params.get("memory_secret_key"));
        Assert.assertEquals("test-token", params.get("memory_session_token"));

        // Test without credentials
        memoryMap.remove("credentials");
        params.clear();
        result = mlAgentExecutor.configureMemoryFromInput(memoryMap, dataset);
        Assert.assertNotNull(result);
        Assert.assertNull(params.get("memory_access_key"));
    }

    @Test
    public void testHandleBedrockMemoryBranches() throws IOException {
        // Mock BedrockAgentCoreMemory.Factory
        org.opensearch.ml.engine.memory.bedrockagentcore.BedrockAgentCoreMemory.Factory bedrockFactory = Mockito
            .mock(org.opensearch.ml.engine.memory.bedrockagentcore.BedrockAgentCoreMemory.Factory.class);
        org.opensearch.ml.engine.memory.bedrockagentcore.BedrockAgentCoreMemory bedrockMemory = Mockito
            .mock(org.opensearch.ml.engine.memory.bedrockagentcore.BedrockAgentCoreMemory.class);

        // Test without regenerate interaction
        Map<String, Object> memoryMap = new HashMap<>();
        memoryMap.put("memory_arn", "test-arn");
        memoryMap.put("region", "us-west-2");

        AgentMLInput input = new AgentMLInput("test", null, FunctionName.AGENT, null);
        input.setMemory(memoryMap);

        Map<String, String> params = new HashMap<>();
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        org.opensearch.ml.common.MLTask task = org.opensearch.ml.common.MLTask
            .builder()
            .taskType(org.opensearch.ml.common.MLTaskType.AGENT_EXECUTION)
            .build();
        MLAgent agent = MLAgent.builder().name("test").type("flow").build();

        Mockito.when(bedrockMemory.getConversationId()).thenReturn("bedrock-conversation-id");
        Mockito.doAnswer(invocation -> {
            ActionListener<org.opensearch.ml.engine.memory.bedrockagentcore.BedrockAgentCoreMemory> listener = invocation.getArgument(1);
            listener.onResponse(bedrockMemory);
            return null;
        }).when(bedrockFactory).create(Mockito.any(), Mockito.any());

        mlAgentExecutor
            .handleBedrockMemory(
                bedrockFactory,
                input,
                "agent-id",
                dataset,
                task,
                false,
                Arrays.asList(),
                Arrays.asList(),
                agent,
                agentActionListener
            );

        Assert.assertEquals("bedrock-conversation-id", params.get(MEMORY_ID));

        // Test with regenerate interaction
        params.put(REGENERATE_INTERACTION_ID, "regen-id");
        Interaction mockInteraction = Mockito.mock(Interaction.class);
        Mockito.when(mockInteraction.getInput()).thenReturn("regenerate question");
        GetInteractionResponse interactionResponse = Mockito.mock(GetInteractionResponse.class);
        Mockito.when(interactionResponse.getInteraction()).thenReturn(mockInteraction);

        Mockito.doAnswer(invocation -> {
            ActionListener<GetInteractionResponse> listener = invocation.getArgument(2);
            listener.onResponse(interactionResponse);
            return null;
        }).when(client).execute(Mockito.eq(GetInteractionAction.INSTANCE), Mockito.any(), Mockito.any());

        mlAgentExecutor
            .handleBedrockMemory(
                bedrockFactory,
                input,
                "agent-id",
                dataset,
                task,
                false,
                Arrays.asList(),
                Arrays.asList(),
                agent,
                agentActionListener
            );

        Assert.assertEquals("regenerate question", params.get(QUESTION));
    }

    @Test
    public void testHandleAgentRetrievalErrorBranches() {
        ActionListener<MLAgent> listener = Mockito.mock(ActionListener.class);

        // Test with IndexNotFoundException
        org.opensearch.index.IndexNotFoundException indexException = new org.opensearch.index.IndexNotFoundException("test-index");
        mlAgentExecutor.handleAgentRetrievalError(indexException, "agent-id", listener);

        Mockito.verify(listener).onFailure(Mockito.any(org.opensearch.OpenSearchStatusException.class));

        // Test with other exception
        RuntimeException otherException = new RuntimeException("other error");
        mlAgentExecutor.handleAgentRetrievalError(otherException, "agent-id", listener);

        Mockito.verify(listener, times(2)).onFailure(Mockito.any());
    }

    @Test
    public void testParseAgentResponseBranches() throws IOException {
        ActionListener<MLAgent> listener = Mockito.mock(ActionListener.class);

        // Test with null parser response
        org.opensearch.remote.metadata.client.GetDataObjectResponse mockResponse = Mockito
            .mock(org.opensearch.remote.metadata.client.GetDataObjectResponse.class);
        Mockito.when(mockResponse.parser()).thenReturn(null);

        mlAgentExecutor.parseAgentResponse(mockResponse, "agent-id", null, listener);

        Mockito.verify(listener).onFailure(Mockito.any(org.opensearch.OpenSearchStatusException.class));
    }

    @Test
    public void testMultiTenancyEnabledScenarios() {
        // Test onMultiTenancyEnabledChanged
        mlAgentExecutor.onMultiTenancyEnabledChanged(true);
        Assert.assertTrue(mlAgentExecutor.getIsMultiTenancyEnabled());

        // Test execute with multi-tenancy enabled but no tenant ID
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        mlAgentExecutor.setIsMultiTenancyEnabled(true);

        Map<String, String> params = new HashMap<>();
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput input = new AgentMLInput("test", null, FunctionName.AGENT, dataset);
        input.setTenantId(null);

        try {
            mlAgentExecutor.execute(input, agentActionListener);
        } catch (org.opensearch.OpenSearchStatusException e) {
            // Expected exception for multi-tenancy violation
            Assert.assertTrue(e.getMessage().contains("You don't have permission to access this resource"));
        }
    }

    @Test
    public void testAsyncTaskUpdaterBranches() {
        org.opensearch.ml.common.MLTask task = org.opensearch.ml.common.MLTask.builder().taskId("test-task").build();
        ActionListener<Object> updater = mlAgentExecutor.createAsyncTaskUpdater(task, Arrays.asList(), Arrays.asList());

        // Test with null output
        updater.onResponse(null);

        // Test with exception
        updater.onFailure(new RuntimeException("test error"));

        // Verify task state changes
        Assert.assertNotNull(task.getResponse());
    }

    @Test
    public void testCreateAgentActionListenerBranches() {
        ActionListener<Object> actionListener = mlAgentExecutor
            .createAgentActionListener(agentActionListener, Arrays.asList(), Arrays.asList(), "test-agent");

        // Test with null output
        actionListener.onResponse(null);
        Mockito.verify(agentActionListener).onResponse(null);

        // Test with exception
        actionListener.onFailure(new RuntimeException("test error"));
        Mockito.verify(agentActionListener).onFailure(Mockito.any());
    }

}
