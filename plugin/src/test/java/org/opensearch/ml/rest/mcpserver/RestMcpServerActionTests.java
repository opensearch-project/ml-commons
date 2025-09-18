/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ERROR_CODE_FIELD;
import static org.opensearch.ml.common.CommonValue.ID_FIELD;
import static org.opensearch.ml.common.CommonValue.MESSAGE_FIELD;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpServerAction;
import org.opensearch.ml.common.transport.mcpserver.responses.server.MLMcpServerResponse;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMcpServerActionTests extends OpenSearchTestCase {

    private RestMcpServerAction restMCPStatelessStreamingAction;
    private ThreadPool threadPool;
    private NodeClient client;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private RestChannel channel;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        restMCPStatelessStreamingAction = new RestMcpServerAction(mlFeatureEnabledSetting);
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    @Test
    public void test_routes() {
        assertTrue(restMCPStatelessStreamingAction.routes().stream().anyMatch(route -> route.getMethod() == RestRequest.Method.POST));
        assertTrue(restMCPStatelessStreamingAction.routes().stream().anyMatch(route -> route.getMethod() == RestRequest.Method.GET));
        assertEquals(2, restMCPStatelessStreamingAction.routes().size());
    }

    @Test
    public void test_getName() {
        assertEquals("ml_mcp_server_action", restMCPStatelessStreamingAction.getName());
    }

    @Test
    public void test_statelessEndpoint() {
        assertEquals("/_plugins/_ml/mcp", RestMcpServerAction.MCP_SERVER_ENDPOINT);
    }

    @Test
    public void test_prepareRequest_getMethod() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.GET)
            .withPath(RestMcpServerAction.MCP_SERVER_ENDPOINT)
            .build();

        executeRestChannelConsumer(request);

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        BytesRestResponse response = responseCaptor.getValue();
        assertEquals(RestStatus.METHOD_NOT_ALLOWED, response.status());
        assertEquals(0, response.content().length());
    }

    @Test
    public void test_prepareRequest_featureFlagDisabled_post() throws Exception {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(false);
        RestMcpServerAction messageStreamingAction = new RestMcpServerAction(mlFeatureEnabledSetting);
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpServerAction.MCP_SERVER_ENDPOINT)
            .build();
        
        try {
            messageStreamingAction.prepareRequest(request, client);
            fail("Expected OpenSearchException to be thrown");
        } catch (OpenSearchException e) {
            assertTrue(e.getMessage().contains("The MCP server is not enabled"));
        }
    }

    @Test
    public void test_prepareRequest_featureFlagDisabled_get() throws Exception {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(false);
        RestMcpServerAction messageStreamingAction = new RestMcpServerAction(mlFeatureEnabledSetting);
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.GET)
            .withPath(RestMcpServerAction.MCP_SERVER_ENDPOINT)
            .build();
        
        try {
            messageStreamingAction.prepareRequest(request, client);
            fail("Expected OpenSearchException to be thrown");
        } catch (OpenSearchException e) {
            assertTrue(e.getMessage().contains("The MCP server is not enabled"));
        }
    }

    @Test
    public void test_prepareRequest_emptyBody() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpServerAction.MCP_SERVER_ENDPOINT)
            .withContent(new BytesArray(""), null)
            .build();

        executeRestChannelConsumer(request);
        verifyErrorResponse("Parse error: empty body");
    }

    @Test
    public void test_prepareRequest_nullContent() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpServerAction.MCP_SERVER_ENDPOINT)
            .withContent(null, null)
            .build();

        executeRestChannelConsumer(request);
        verifyErrorResponse("Parse error: empty body");
    }

    @Test
    public void test_prepareRequest_invalidJson() throws Exception {
        // Mock the transport action to return an error response
        doAnswer(invocation -> {
            ActionListener<MLMcpServerResponse> listener = invocation.getArgument(2);
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put(ID_FIELD, null);
            errorMap.put(ERROR_CODE_FIELD, -32700);
            errorMap.put(MESSAGE_FIELD, "Parse error: invalid json");
            listener.onResponse(new MLMcpServerResponse(false, null, errorMap));
            return null;
        }).when(client).execute(eq(MLMcpServerAction.INSTANCE), any(), any());

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpServerAction.MCP_SERVER_ENDPOINT)
            .withContent(new BytesArray("invalid json"), null)
            .build();

        executeRestChannelConsumer(request);
        verifyErrorResponse("Parse error: invalid json");
    }

    @Test
    public void test_prepareRequest_malformedJsonRpc() throws Exception {
        // Mock the transport action to return an error response
        doAnswer(invocation -> {
            ActionListener<MLMcpServerResponse> listener = invocation.getArgument(2);
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put(ID_FIELD, 1);
            errorMap.put(ERROR_CODE_FIELD, -32700);
            errorMap.put(MESSAGE_FIELD, "Parse error: malformed JSON-RPC");
            listener.onResponse(new MLMcpServerResponse(false, null, errorMap));
            return null;
        }).when(client).execute(eq(MLMcpServerAction.INSTANCE), any(), any());

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpServerAction.MCP_SERVER_ENDPOINT)
            .withContent(new BytesArray("{\"jsonrpc\":\"1.0\",\"id\":1}"), null)
            .build();

        executeRestChannelConsumer(request);
        verifyErrorResponse("Parse error: malformed JSON-RPC");
    }

    @Test
    public void test_prepareRequest_notificationMessage() throws Exception {
        // Mock the transport action to return a success response for notification
        doAnswer(invocation -> {
            ActionListener<MLMcpServerResponse> listener = invocation.getArgument(2);
            listener.onResponse(new MLMcpServerResponse(true, null, null));
            return null;
        }).when(client).execute(eq(MLMcpServerAction.INSTANCE), any(), any());

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpServerAction.MCP_SERVER_ENDPOINT)
            .withContent(new BytesArray("{\"jsonrpc\":\"2.0\",\"method\":\"test\",\"params\":{}}"), null)
            .build();

        executeRestChannelConsumer(request);
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        BytesRestResponse response = responseCaptor.getValue();
        assertEquals(RestStatus.ACCEPTED, response.status());
        assertEquals(0, response.content().length());
    }

    @Test
    public void test_prepareRequest_validRequest() throws Exception {
        // Mock the transport action to return a success response
        doAnswer(invocation -> {
            ActionListener<MLMcpServerResponse> listener = invocation.getArgument(2);
            listener.onResponse(new MLMcpServerResponse(true, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"success\"}", null));
            return null;
        }).when(client).execute(eq(MLMcpServerAction.INSTANCE), any(), any());

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpServerAction.MCP_SERVER_ENDPOINT)
            .withContent(new BytesArray("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\",\"params\":{}}"), null)
            .build();

        executeRestChannelConsumer(request);
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        BytesRestResponse response = responseCaptor.getValue();
        assertEquals(RestStatus.OK, response.status());
        assertTrue(response.content().utf8ToString().contains("success"));
    }

    @Test
    public void test_prepareRequest_transportProviderNotReady() throws Exception {
        // Mock the transport action to return an error response for transport provider not ready
        doAnswer(invocation -> {
            ActionListener<MLMcpServerResponse> listener = invocation.getArgument(2);
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put(ID_FIELD, 1);
            errorMap.put(ERROR_CODE_FIELD, -32000);
            errorMap.put(MESSAGE_FIELD, "MCP handler not ready - server initialization failed");
            listener.onResponse(new MLMcpServerResponse(false, null, errorMap));
            return null;
        }).when(client).execute(eq(MLMcpServerAction.INSTANCE), any(), any());

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpServerAction.MCP_SERVER_ENDPOINT)
            .withContent(new BytesArray("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\",\"params\":{}}"), null)
            .build();

        executeRestChannelConsumer(request);
        verifyErrorResponse("MCP handler not ready - server initialization failed");
    }

    @Test
    public void test_prepareRequest_transportActionFailure() throws Exception {
        // Mock the transport action to throw an exception
        doAnswer(invocation -> {
            ActionListener<MLMcpServerResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Transport action failed"));
            return null;
        }).when(client).execute(eq(MLMcpServerAction.INSTANCE), any(), any());

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpServerAction.MCP_SERVER_ENDPOINT)
            .withContent(new BytesArray("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\",\"params\":{}}"), null)
            .build();

        executeRestChannelConsumer(request);
        verifyErrorResponse("Internal server error: Transport action failed");
    }

    @Test
    public void test_sendErrorResponse() throws Exception {
        java.lang.reflect.Method sendErrorResponseMethod = RestMcpServerAction.class
            .getDeclaredMethod("sendErrorResponse", RestChannel.class, Object.class, int.class, String.class);
        sendErrorResponseMethod.setAccessible(true);

        sendErrorResponseMethod.invoke(restMCPStatelessStreamingAction, channel, 1, -32700, "Parse error");

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        BytesRestResponse response = responseCaptor.getValue();
        assertEquals(RestStatus.OK, response.status());
        assertTrue(response.content().utf8ToString().contains("Parse error"));
    }

    @Test
    public void test_sendErrorResponse_withNullId() throws Exception {
        java.lang.reflect.Method sendErrorResponseMethod = RestMcpServerAction.class
            .getDeclaredMethod("sendErrorResponse", RestChannel.class, Object.class, int.class, String.class);
        sendErrorResponseMethod.setAccessible(true);

        sendErrorResponseMethod.invoke(restMCPStatelessStreamingAction, channel, null, -32700, "Parse error");

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        BytesRestResponse response = responseCaptor.getValue();
        assertEquals(RestStatus.OK, response.status());
        assertTrue(response.content().utf8ToString().contains("Parse error"));
    }

    @Test
    public void test_sendErrorResponse_exceptionHandling() throws Exception {
        doThrow(new RuntimeException("Channel error")).when(channel).sendResponse(any());

        java.lang.reflect.Method sendErrorResponseMethod = RestMcpServerAction.class
            .getDeclaredMethod("sendErrorResponse", RestChannel.class, Object.class, int.class, String.class);
        sendErrorResponseMethod.setAccessible(true);

        sendErrorResponseMethod.invoke(restMCPStatelessStreamingAction, channel, 1, -32700, "Parse error");

        verify(channel, times(2)).sendResponse(any());
    }

    private void executeRestChannelConsumer(RestRequest request) throws Exception {
        Object consumer = restMCPStatelessStreamingAction.prepareRequest(request, client);
        java.lang.reflect.Method acceptMethod = consumer.getClass().getMethod("accept", Object.class);
        acceptMethod.invoke(consumer, channel);
    }

    private void verifyErrorResponse(String expectedMessage) {
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        BytesRestResponse response = responseCaptor.getValue();
        assertEquals(RestStatus.OK, response.status());
        assertTrue(response.content().utf8ToString().contains(expectedMessage));
    }
}
