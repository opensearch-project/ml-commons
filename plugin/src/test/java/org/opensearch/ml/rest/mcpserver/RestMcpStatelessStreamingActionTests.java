/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMcpStatelessStreamingActionTests extends OpenSearchTestCase {

    private RestMcpStatelessStreamingAction restMCPStatelessStreamingAction;
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
        restMCPStatelessStreamingAction = new RestMcpStatelessStreamingAction(mlFeatureEnabledSetting);
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
        assertEquals(1, restMCPStatelessStreamingAction.routes().size());
    }

    @Test
    public void test_getName() {
        assertEquals("ml_stateless_mcp_action", restMCPStatelessStreamingAction.getName());
    }

    @Test
    public void test_statelessEndpoint() {
        assertEquals("/_plugins/_ml/mcp/stream", RestMcpStatelessStreamingAction.STATELESS_ENDPOINT);
    }

    @Test
    public void test_prepareRequest_featureFlagDisabled() throws Exception {
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(false);
        RestMcpStatelessStreamingAction messageStreamingAction = new RestMcpStatelessStreamingAction(mlFeatureEnabledSetting);
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpStatelessStreamingAction.STATELESS_ENDPOINT)
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
            .withPath(RestMcpStatelessStreamingAction.STATELESS_ENDPOINT)
            .withContent(new BytesArray(""), null)
            .build();

        executeRestChannelConsumer(request);
        verifyErrorResponse("Parse error: empty body");
    }

    @Test
    public void test_prepareRequest_nullBody() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpStatelessStreamingAction.STATELESS_ENDPOINT)
            .withContent(null, null)
            .build();

        executeRestChannelConsumer(request);
        verifyErrorResponse("Parse error: empty body");
    }

    @Test
    public void test_prepareRequest_blankBody() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpStatelessStreamingAction.STATELESS_ENDPOINT)
            .withContent(new BytesArray("   "), null)
            .build();

        executeRestChannelConsumer(request);
        verifyErrorResponse("Parse error: empty body");
    }

    @Test
    public void test_prepareRequest_invalidJson() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpStatelessStreamingAction.STATELESS_ENDPOINT)
            .withContent(new BytesArray("invalid json"), null)
            .build();

        executeRestChannelConsumer(request);
        verifyErrorResponse("Parse error");
    }

    @Test
    public void test_prepareRequest_malformedJsonRpc() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpStatelessStreamingAction.STATELESS_ENDPOINT)
            .withContent(new BytesArray("{\"jsonrpc\":\"1.0\",\"id\":1}"), null)
            .build();

        try {
            executeRestChannelConsumer(request);
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("MCP handler not ready") || e.getMessage().contains("server initialization failed"));
        }
    }

    @Test
    public void test_prepareRequest_notificationMessage() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpStatelessStreamingAction.STATELESS_ENDPOINT)
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
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(RestMcpStatelessStreamingAction.STATELESS_ENDPOINT)
            .withContent(new BytesArray("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\",\"params\":{}}"), null)
            .build();

        try {
            executeRestChannelConsumer(request);
        } catch (Exception e) {
            assertTrue(
                e.getMessage().contains("MCP handler not ready")
                    || e.getMessage().contains("server initialization failed")
                    || e.getMessage().contains("Missing handler")
            );
        }
    }

    @Test
    public void test_sendErrorResponse() throws Exception {
        java.lang.reflect.Method sendErrorResponseMethod = RestMcpStatelessStreamingAction.class
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
        java.lang.reflect.Method sendErrorResponseMethod = RestMcpStatelessStreamingAction.class
            .getDeclaredMethod("sendErrorResponse", RestChannel.class, Object.class, int.class, String.class);
        sendErrorResponseMethod.setAccessible(true);

        sendErrorResponseMethod.invoke(restMCPStatelessStreamingAction, channel, null, -32700, "Parse error");

        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());
        BytesRestResponse response = responseCaptor.getValue();
        assertEquals(RestStatus.OK, response.status());
        assertTrue(response.content().utf8ToString().contains("Parse error"));
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
