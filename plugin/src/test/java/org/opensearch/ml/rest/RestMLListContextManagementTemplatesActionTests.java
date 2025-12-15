/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.contextmanagement.MLListContextManagementTemplatesAction;
import org.opensearch.ml.common.transport.contextmanagement.MLListContextManagementTemplatesRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLListContextManagementTemplatesActionTests extends OpenSearchTestCase {
    private RestMLListContextManagementTemplatesAction restAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    private RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);
        restAction = new RestMLListContextManagementTemplatesAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            // Mock successful execution - actionListener not used in this test
            return null;
        }).when(client).execute(eq(MLListContextManagementTemplatesAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLListContextManagementTemplatesAction action = new RestMLListContextManagementTemplatesAction(mlFeatureEnabledSetting);
        assertNotNull(action);
    }

    public void testGetName() {
        String actionName = restAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_list_context_management_templates_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
        assertEquals("/_plugins/_ml/context_management", route.getPath());
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLListContextManagementTemplatesRequest> argumentCaptor = ArgumentCaptor
            .forClass(MLListContextManagementTemplatesRequest.class);
        verify(client, times(1)).execute(eq(MLListContextManagementTemplatesAction.INSTANCE), argumentCaptor.capture(), any());
        MLListContextManagementTemplatesRequest capturedRequest = argumentCaptor.getValue();
        assertEquals(0, capturedRequest.getFrom());
        assertEquals(10, capturedRequest.getSize());
    }

    public void testPrepareRequestWithCustomPagination() throws Exception {
        RestRequest request = getRestRequestWithPagination(5, 20);
        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLListContextManagementTemplatesRequest> argumentCaptor = ArgumentCaptor
            .forClass(MLListContextManagementTemplatesRequest.class);
        verify(client, times(1)).execute(eq(MLListContextManagementTemplatesAction.INSTANCE), argumentCaptor.capture(), any());
        MLListContextManagementTemplatesRequest capturedRequest = argumentCaptor.getValue();
        assertEquals(5, capturedRequest.getFrom());
        assertEquals(20, capturedRequest.getSize());
    }

    public void testPrepareRequestWithAgentFrameworkDisabled() {
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        RestRequest request = getRestRequest();
        
        assertThrows(IllegalStateException.class, () -> restAction.handleRequest(request, channel, client));
    }

    public void testGetRequestWithDefaultPagination() throws Exception {
        RestRequest request = getRestRequest();
        MLListContextManagementTemplatesRequest result = restAction.getRequest(request);

        assertNotNull(result);
        assertEquals(0, result.getFrom());
        assertEquals(10, result.getSize());
    }

    public void testGetRequestWithCustomPagination() throws Exception {
        RestRequest request = getRestRequestWithPagination(15, 25);
        MLListContextManagementTemplatesRequest result = restAction.getRequest(request);

        assertNotNull(result);
        assertEquals(15, result.getFrom());
        assertEquals(25, result.getSize());
    }

    public void testGetRequestWithAgentFrameworkDisabled() {
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        RestRequest request = getRestRequest();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> restAction.getRequest(request));
        assertEquals("Agent framework is disabled", exception.getMessage());
    }

    public void testGetRequestWithNegativeFrom() {
        RestRequest request = getRestRequestWithPagination(-1, 10);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(request));
        assertEquals("Parameter 'from' must be non-negative", exception.getMessage());
    }

    public void testGetRequestWithZeroSize() {
        RestRequest request = getRestRequestWithPagination(0, 0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(request));
        assertEquals("Parameter 'size' must be between 1 and 1000", exception.getMessage());
    }

    public void testGetRequestWithNegativeSize() {
        RestRequest request = getRestRequestWithPagination(0, -5);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(request));
        assertEquals("Parameter 'size' must be between 1 and 1000", exception.getMessage());
    }

    public void testGetRequestWithExcessiveSize() {
        RestRequest request = getRestRequestWithPagination(0, 1001);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> restAction.getRequest(request));
        assertEquals("Parameter 'size' must be between 1 and 1000", exception.getMessage());
    }

    public void testGetRequestWithMaxValidSize() throws Exception {
        RestRequest request = getRestRequestWithPagination(0, 1000);
        MLListContextManagementTemplatesRequest result = restAction.getRequest(request);

        assertNotNull(result);
        assertEquals(0, result.getFrom());
        assertEquals(1000, result.getSize());
    }

    public void testPrepareRequestReturnsRestChannelConsumer() throws Exception {
        RestRequest request = getRestRequest();
        Object consumer = restAction.prepareRequest(request, client);

        assertNotNull(consumer);

        // Execute the consumer to test the actual execution path using reflection
        java.lang.reflect.Method acceptMethod = consumer.getClass().getMethod("accept", Object.class);
        acceptMethod.invoke(consumer, channel);

        ArgumentCaptor<MLListContextManagementTemplatesRequest> argumentCaptor = ArgumentCaptor
            .forClass(MLListContextManagementTemplatesRequest.class);
        verify(client, times(1)).execute(eq(MLListContextManagementTemplatesAction.INSTANCE), argumentCaptor.capture(), any());
        MLListContextManagementTemplatesRequest capturedRequest = argumentCaptor.getValue();
        assertEquals(0, capturedRequest.getFrom());
        assertEquals(10, capturedRequest.getSize());
    }

    private RestRequest getRestRequest() {
        Map<String, String> params = new HashMap<>();
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
    }

    private RestRequest getRestRequestWithPagination(int from, int size) {
        Map<String, String> params = new HashMap<>();
        params.put("from", String.valueOf(from));
        params.put("size", String.valueOf(size));
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
    }
}
