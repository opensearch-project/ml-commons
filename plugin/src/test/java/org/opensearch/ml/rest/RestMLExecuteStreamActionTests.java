/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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
import org.opensearch.action.get.GetResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.http.HttpChunk;
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

        assertNotNull(restAction.prepareRequest(request, client));
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

        assertNotNull(restAction.prepareRequest(request, client));
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

    @Test
    public void testCombineChunksWithSingleChunk() {
        String testContent = "{\"parameters\":{\"question\":\"test\"}}";
        BytesArray bytesArray = new BytesArray(testContent);

        HttpChunk mockChunk = mock(HttpChunk.class);
        when(mockChunk.content()).thenReturn(bytesArray);

        BytesReference result = restAction.combineChunks(List.of(mockChunk));

        assertNotNull(result);
        assertEquals(testContent, result.utf8ToString());
    }

    @Test
    public void testCombineChunksWithMultipleChunks() {
        String chunk1Content = "{\"parameters\":";
        String chunk2Content = "{\"question\":";
        String chunk3Content = "\"test\"}}";

        BytesArray bytes1 = new BytesArray(chunk1Content);
        BytesArray bytes2 = new BytesArray(chunk2Content);
        BytesArray bytes3 = new BytesArray(chunk3Content);

        HttpChunk mockChunk1 = mock(HttpChunk.class);
        HttpChunk mockChunk2 = mock(HttpChunk.class);
        HttpChunk mockChunk3 = mock(HttpChunk.class);

        when(mockChunk1.content()).thenReturn(bytes1);
        when(mockChunk2.content()).thenReturn(bytes2);
        when(mockChunk3.content()).thenReturn(bytes3);

        BytesReference result = restAction.combineChunks(List.of(mockChunk1, mockChunk2, mockChunk3));

        assertNotNull(result);
        String expectedContent = chunk1Content + chunk2Content + chunk3Content;
        assertEquals(expectedContent, result.utf8ToString());
    }

    @Test
    public void testCombineChunksWithEmptyList() {
        BytesReference result = restAction.combineChunks(List.of());

        assertNotNull(result);
        assertEquals(0, result.length());
    }

    @Test
    public void testCombineChunksWithLargeContent() {
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("chunk").append(i).append(",");
        }
        String content = largeContent.toString();

        BytesArray bytesArray = new BytesArray(content);

        HttpChunk mockChunk = mock(HttpChunk.class);
        when(mockChunk.content()).thenReturn(bytesArray);

        BytesReference result = restAction.combineChunks(List.of(mockChunk));

        assertNotNull(result);
        assertEquals(content.length(), result.length());
        assertEquals(content, result.utf8ToString());
    }

    @Test
    public void testPrepareRequestWithMcpHeadersFeatureDisabled() throws IOException {
        when(mlFeatureEnabledSetting.isStreamEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMcpHeaderPassthroughEnabled()).thenReturn(false);

        Map<String, String> params = new HashMap<>();
        params.put(org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID, "test_agent_id");
        final String requestContent = "{\"parameters\":{\"question\":\"test question\"}}";

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("aws-access-key-id", List.of("test-key"));
        headers.put("aws-region", List.of("us-west-2"));

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
                .withParams(params)
                .withHeaders(headers)
                .withContent(new BytesArray(requestContent), XContentType.JSON)
                .withPath("/_plugins/_ml/agents/test_agent_id/_execute/stream")
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> restAction.prepareRequest(request, client)
        );
        assertTrue(exception.getMessage().contains("MCP header passthrough"));
        assertTrue(exception.getMessage().contains("plugins.ml_commons.mcp_header_passthrough_enabled"));
    }

    @Test
    public void testPrepareRequestWithMcpHeadersFeatureEnabled() throws IOException {
        when(mlFeatureEnabledSetting.isStreamEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMcpHeaderPassthroughEnabled()).thenReturn(true);

        RestMLExecuteStreamAction spyAction = spy(restAction);
        Map<String, String> params = new HashMap<>();
        params.put(org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID, "test_agent_id");
        final String requestContent = "{\"parameters\":{\"question\":\"test question\"}}";

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("aws-access-key-id", List.of("test-key"));
        headers.put("aws-region", List.of("us-west-2"));

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
                .withParams(params)
                .withHeaders(headers)
                .withContent(new BytesArray(requestContent), XContentType.JSON)
                .withPath("/_plugins/_ml/agents/test_agent_id/_execute/stream")
                .build();

        assertNotNull(spyAction.prepareRequest(request, client));
        
        // Verify headers were put into ThreadContext
        assertEquals("test-key", client.threadPool().getThreadContext().getHeader("aws-access-key-id"));
        assertEquals("us-west-2", client.threadPool().getThreadContext().getHeader("aws-region"));
    }

    @Test
    public void testPrepareRequestWithoutMcpHeaders() throws IOException {
        when(mlFeatureEnabledSetting.isStreamEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);

        RestMLExecuteStreamAction spyAction = spy(restAction);
        RestRequest request = getExecuteAgentStreamRestRequest();

        // Should work without MCP headers when feature flag state doesn't matter
        assertNotNull(spyAction.prepareRequest(request, client));
    }

    @Test
    public void testPrepareRequestWithAllMcpHeaders() throws IOException {
        when(mlFeatureEnabledSetting.isStreamEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMcpHeaderPassthroughEnabled()).thenReturn(true);

        RestMLExecuteStreamAction spyAction = spy(restAction);
        Map<String, String> params = new HashMap<>();
        params.put(org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID, "test_agent_id");
        final String requestContent = "{\"parameters\":{\"question\":\"test question\"}}";

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("aws-access-key-id", List.of("test-access-key"));
        headers.put("aws-secret-access-key", List.of("test-secret-key"));
        headers.put("aws-session-token", List.of("test-session-token"));
        headers.put("aws-region", List.of("us-west-2"));
        headers.put("aws-service-name", List.of("bedrock"));
        headers.put("opensearch-url", List.of("https://localhost:9200"));

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
                .withParams(params)
                .withHeaders(headers)
                .withContent(new BytesArray(requestContent), XContentType.JSON)
                .withPath("/_plugins/_ml/agents/test_agent_id/_execute/stream")
                .build();

        assertNotNull(spyAction.prepareRequest(request, client));
        
        // Verify all MCP headers were put into ThreadContext
        assertEquals("test-access-key", client.threadPool().getThreadContext().getHeader("aws-access-key-id"));
        assertEquals("test-secret-key", client.threadPool().getThreadContext().getHeader("aws-secret-access-key"));
        assertEquals("test-session-token", client.threadPool().getThreadContext().getHeader("aws-session-token"));
        assertEquals("us-west-2", client.threadPool().getThreadContext().getHeader("aws-region"));
        assertEquals("bedrock", client.threadPool().getThreadContext().getHeader("aws-service-name"));
        assertEquals("https://localhost:9200", client.threadPool().getThreadContext().getHeader("opensearch-url"));
    }
}
