/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.MLExceptionUtils.STREAM_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID;
import static org.opensearch.ml.utils.TestHelper.getExecuteAgentStreamRestRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLExecuteStreamActionTests extends OpenSearchTestCase {

    NodeClient client;
    private ThreadPool threadPool;
    private MLAgent mlAgent;

    @Mock
    RestChannel channel;

    private RestMLExecuteStreamAction restAction;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private ClusterService clusterService;
    private MLModelManager mlModelManager;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mlFeatureEnabledSetting = mock(MLFeatureEnabledSetting.class);
        clusterService = mock(ClusterService.class);
        mlModelManager = mock(MLModelManager.class);
        mlAgent = mock(MLAgent.class);
        restAction = new RestMLExecuteStreamAction(mlModelManager, mlFeatureEnabledSetting, clusterService);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    @Test
    public void testGetName() {
        assertEquals("ml_execute_stream_action", restAction.getName());
    }

    @Test
    public void testRoutes() {
        List<RestMLExecuteStreamAction.Route> routes = restAction.routes();
        assertEquals(1, routes.size());

        assertEquals(RestRequest.Method.POST, routes.get(0).getMethod());
        assertTrue(routes.get(0).getPath().contains("/agents/"));
        assertTrue(routes.get(0).getPath().contains("/_execute/stream"));
    }

    @Test
    public void testConstructor() {
        assertNotNull(restAction);
        RestMLExecuteStreamAction newAction = new RestMLExecuteStreamAction(mlModelManager, mlFeatureEnabledSetting, clusterService);
        assertNotNull(newAction);
        assertEquals("ml_execute_stream_action", newAction.getName());
    }

    @Test
    public void testSupportsContentStream() {
        assertTrue(restAction.supportsContentStream());
    }

    @Test
    public void testSupportsStreaming() {
        assertTrue(restAction.supportsStreaming());
    }

    @Test
    public void testAllowsUnsafeBuffers() {
        assertTrue(restAction.allowsUnsafeBuffers());
    }

    @Test
    public void testPrepareRequestWhenStreamEnabled() throws IOException {
        when(mlFeatureEnabledSetting.isStreamEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);

        RestMLExecuteStreamAction spyAction = spy(restAction);
        doReturn(mlAgent).when(spyAction).validateAndGetAgent(anyString(), any());
        doReturn(true).when(spyAction).isModelValid(anyString(), any(), any());

        RestRequest request = getExecuteAgentStreamRestRequest();
        assertNotNull(spyAction.prepareRequest(request, client));
    }

    @Test
    public void testPrepareRequestWhenStreamDisabled() throws IOException {
        when(mlFeatureEnabledSetting.isStreamEnabled()).thenReturn(false);
        RestRequest request = getExecuteAgentStreamRestRequest();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> restAction.prepareRequest(request, null)
        );
        assertEquals(STREAM_DISABLED_ERR_MSG, exception.getMessage());
    }

    @Test
    public void testPrepareRequestValidAgentId() throws IOException {
        when(mlFeatureEnabledSetting.isStreamEnabled()).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            GetResponse mockResponse = mock(GetResponse.class);
            when(mockResponse.isExists()).thenReturn(true);
            when(mockResponse.getSourceAsString()).thenReturn(
                    "{\"name\":\"test_agent\",\"type\":\"flow\"}"
            );
            listener.onResponse(mockResponse);
            return null;
        }).when(client).get(any(), any());

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_AGENT_ID, "valid_agent_id");
        final String requestContent = "{\"parameters\":{\"question\":\"test question\"}}";

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
                .withParams(params)
                .withContent(new BytesArray(requestContent), XContentType.JSON)
                .withPath("/_plugins/_ml/agents/valid_agent_id/_execute/stream")
                .build();

        assertNotNull(restAction.prepareRequest(request, client));
    }

    @Test
    public void testPrepareRequestInvalidAgentId() throws IOException {
        when(mlFeatureEnabledSetting.isStreamEnabled()).thenReturn(true);

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_AGENT_ID, "invalid_agent_id");
        final String requestContent = "{\"parameters\":{\"question\":\"test question\"}}";

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
                .withParams(params)
                .withContent(new BytesArray(requestContent), XContentType.JSON)
                .withPath("/_plugins/_ml/agents/invalid_agent_id/_execute/stream")
                .build();

        OpenSearchStatusException exception = assertThrows(
                OpenSearchStatusException.class,
                () -> restAction.prepareRequest(request, client)
        );
        assertTrue(exception.getMessage().contains("Failed to find agent"));
        assertEquals(RestStatus.NOT_FOUND, exception.status());
    }

    @Test
    public void testPrepareRequestValidModelId() throws IOException {
        when(mlFeatureEnabledSetting.isStreamEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        RestMLExecuteStreamAction spyAction = spy(restAction);
        MLAgent mockAgent = mock(MLAgent.class);
        LLMSpec mockLLM = mock(LLMSpec.class);
        when(mockLLM.getModelId()).thenReturn("valid_model_id");
        when(mockAgent.getLlm()).thenReturn(mockLLM);

        doReturn(mockAgent).when(spyAction).validateAndGetAgent(anyString(), any());
        doReturn(true).when(spyAction).isModelValid(anyString(), any(), any());

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_AGENT_ID, "test_agent_id");
        final String requestContent = "{\"parameters\":{\"question\":\"test question\"}}";

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
                .withParams(params)
                .withContent(new BytesArray(requestContent), XContentType.JSON)
                .withPath("/_plugins/_ml/agents/test_agent_id/_execute/stream")
                .build();

        assertNotNull(spyAction.prepareRequest(request, client));
    }

    @Test
    public void testPrepareRequestInvalidModelId() throws IOException {
        when(mlFeatureEnabledSetting.isStreamEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            GetResponse mockResponse = mock(GetResponse.class);
            when(mockResponse.isExists()).thenReturn(true);
            when(mockResponse.getSourceAsString()).thenReturn(
                    "{\"name\":\"test_agent\",\"type\":\"flow\",\"llm\":{\"model_id\":\"invalid_model_id\"}}"
            );
            listener.onResponse(mockResponse);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(2);
            listener.onFailure(new Exception("Model not found"));
            return null;
        }).when(mlModelManager).getModel(anyString(), anyString(), any());

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_AGENT_ID, "test_agent_id");
        final String requestContent = "{\"parameters\":{\"question\":\"test question\"}}";

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
                .withParams(params)
                .withContent(new BytesArray(requestContent), XContentType.JSON)
                .withPath("/_plugins/_ml/agents/test_agent_id/_execute/stream")
                .build();

        OpenSearchStatusException exception = assertThrows(
                OpenSearchStatusException.class,
                () -> restAction.prepareRequest(request, client)
        );
        assertTrue(exception.getMessage().contains("Failed to find model"));
        assertEquals(RestStatus.NOT_FOUND, exception.status());
    }

    @Test
    public void testGetRequestAgent() throws IOException {
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_AGENT_ID, "test_agent_id");
        final String requestContent = "{\"parameters\":{\"question\":\"test question\"}}";

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
                .withParams(params)
                .withContent(new BytesArray(requestContent), XContentType.JSON)
                .withPath("/_plugins/_ml/agents/test_agent_id/_execute/stream")
                .build();

        String agentId = "test_agent_id";

        MLExecuteTaskRequest executeTaskRequest = restAction.getRequest(agentId, request, request.content());

        Input input = executeTaskRequest.getInput();
        assertNotNull(input);
        assertEquals(FunctionName.AGENT, input.getFunctionName());

        AgentMLInput agentInput = (AgentMLInput) input;
        assertEquals(agentId, agentInput.getAgentId());

        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) agentInput.getInputDataset();
        assertNotNull(inputDataSet);
        assertEquals("true", inputDataSet.getParameters().get("stream"));
    }

    @Test
    public void testGetRequestAgentFrameworkDisabled() {
        RestRequest request = getExecuteAgentStreamRestRequest();

        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> restAction.handleRequest(request, channel, client));
    }
}
