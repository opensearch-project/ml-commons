/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.MLExceptionUtils.REMOTE_INFERENCE_DISABLED_ERR_MSG;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchParseException;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorRequest;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.gson.Gson;

public class RestMLUpdateConnectorActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RestMLUpdateConnectorAction restMLUpdateConnectorAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    RestChannel channel;

    private String REST_PATH = "/_plugins/_ml/connectors/{connector_id}";

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        when(mlFeatureEnabledSetting.isRemoteInferenceEnabled()).thenReturn(true);
        restMLUpdateConnectorAction = new RestMLUpdateConnectorAction(mlFeatureEnabledSetting);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLUpdateConnectorAction updateConnectorAction = new RestMLUpdateConnectorAction(mlFeatureEnabledSetting);
        assertNotNull(updateConnectorAction);
    }

    public void testGetName() {
        String actionName = restMLUpdateConnectorAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_update_connector_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLUpdateConnectorAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.PUT, route.getMethod());
        assertEquals(REST_PATH, route.getPath());
    }

    public void testUpdateConnectorRequest() throws Exception {
        doAnswer(invocation -> {
            invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLUpdateConnectorAction.INSTANCE), any(), any());

        RestRequest request = getRestRequest();
        restMLUpdateConnectorAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLUpdateConnectorRequest> argumentCaptor = ArgumentCaptor.forClass(MLUpdateConnectorRequest.class);
        verify(client, times(1)).execute(eq(MLUpdateConnectorAction.INSTANCE), argumentCaptor.capture(), any());
        MLUpdateConnectorRequest updateConnectorRequest = argumentCaptor.getValue();
        assertEquals("test_connectorId", updateConnectorRequest.getConnectorId());
        assertEquals("This is test description", updateConnectorRequest.getUpdateContent().getDescription());
        assertEquals("2", updateConnectorRequest.getUpdateContent().getVersion());

    }

    public void testUpdateConnectorRequestWithParsingException() throws Exception {
        exceptionRule.expect(OpenSearchParseException.class);
        exceptionRule.expectMessage("Can't get text on a VALUE_NULL");
        RestRequest request = getRestRequestWithNullValue();
        restMLUpdateConnectorAction.handleRequest(request, channel, client);
    }

    public void testUpdateConnectorRequestWithEmptyContent() throws Exception {
        exceptionRule.expect(OpenSearchParseException.class);
        exceptionRule.expectMessage("Failed to update connector: Request body is empty");
        RestRequest request = getRestRequestWithEmptyContent();
        restMLUpdateConnectorAction.handleRequest(request, channel, client);
    }

    public void testUpdateConnectorRequestWithNullConnectorId() throws Exception {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Request should contain connector_id");
        RestRequest request = getRestRequestWithNullConnectorId();
        restMLUpdateConnectorAction.handleRequest(request, channel, client);
    }

    public void testPrepareRequestFeatureDisabled() throws Exception {
        exceptionRule.expect(IllegalStateException.class);
        exceptionRule.expectMessage(REMOTE_INFERENCE_DISABLED_ERR_MSG);

        when(mlFeatureEnabledSetting.isRemoteInferenceEnabled()).thenReturn(false);
        RestRequest request = getRestRequest();
        restMLUpdateConnectorAction.handleRequest(request, channel, client);
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.PUT;
        final Map<String, Object> updateContent = Map.of("version", "2", "description", "This is test description");
        String requestContent = new Gson().toJson(updateContent).toString();
        Map<String, String> params = new HashMap<>();
        params.put("connector_id", "test_connectorId");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath(REST_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    private RestRequest getRestRequestWithNullValue() {
        RestRequest.Method method = RestRequest.Method.PUT;
        String requestContent = "{\"version\":\"2\",\"description\":null}";
        Map<String, String> params = new HashMap<>();
        params.put("connector_id", "test_connectorId");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath(REST_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    private RestRequest getRestRequestWithEmptyContent() {
        RestRequest.Method method = RestRequest.Method.PUT;
        Map<String, String> params = new HashMap<>();
        params.put("connector_id", "test_connectorId");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath(REST_PATH)
            .withParams(params)
            .withContent(new BytesArray(""), XContentType.JSON)
            .build();
        return request;
    }

    private RestRequest getRestRequestWithNullConnectorId() {
        RestRequest.Method method = RestRequest.Method.PUT;
        final Map<String, Object> updateContent = Map.of("version", "2", "description", "This is test description");
        String requestContent = new Gson().toJson(updateContent).toString();
        Map<String, String> params = new HashMap<>();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath(REST_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

}
