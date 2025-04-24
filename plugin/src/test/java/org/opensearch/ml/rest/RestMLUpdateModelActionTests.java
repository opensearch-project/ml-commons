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
import static org.opensearch.ml.utils.TestHelper.toJsonString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchParseException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.model.MLUpdateModelAction;
import org.opensearch.ml.common.transport.model.MLUpdateModelInput;
import org.opensearch.ml.common.transport.model.MLUpdateModelRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.gson.Gson;

public class RestMLUpdateModelActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RestMLUpdateModelAction restMLUpdateModelAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        when(mlFeatureEnabledSetting.isRemoteInferenceEnabled()).thenReturn(true);
        restMLUpdateModelAction = new RestMLUpdateModelAction(mlFeatureEnabledSetting);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLUpdateModelAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    @Test
    public void testConstructor() {
        RestMLUpdateModelAction UpdateModelAction = new RestMLUpdateModelAction(mlFeatureEnabledSetting);
        assertNotNull(UpdateModelAction);
    }

    @Test
    public void testGetName() {
        String actionName = restMLUpdateModelAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_update_model_action", actionName);
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = restMLUpdateModelAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.PUT, route.getMethod());
        assertEquals("/_plugins/_ml/models/{model_id}", route.getPath());
    }

    @Test
    public void testUpdateModelRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLUpdateModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLUpdateModelRequest> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelRequest.class);
        verify(client, times(1)).execute(eq(MLUpdateModelAction.INSTANCE), argumentCaptor.capture(), any());
        MLUpdateModelInput updateModelInput = argumentCaptor.getValue().getUpdateModelInput();
        assertEquals("testModelName", updateModelInput.getName());
        assertEquals("This is test description", updateModelInput.getDescription());
    }

    @Test
    public void testUpdateModelRequestWithEmptyContent() throws Exception {
        exceptionRule.expect(OpenSearchParseException.class);
        exceptionRule.expectMessage("Update model request has empty body");
        RestRequest request = getRestRequestWithEmptyContent();
        restMLUpdateModelAction.handleRequest(request, channel, client);
    }

    @Test
    public void testUpdateModelRequestWithNullModelId() throws Exception {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Request should contain model_id");
        RestRequest request = getRestRequestWithNullModelId();
        restMLUpdateModelAction.handleRequest(request, channel, client);
    }

    @Test
    public void testUpdateModelRequestWithNullField() throws Exception {
        exceptionRule.expect(OpenSearchParseException.class);
        exceptionRule.expectMessage("Can't get text on a VALUE_NULL");
        RestRequest request = getRestRequestWithNullField();
        restMLUpdateModelAction.handleRequest(request, channel, client);
    }

    @Test
    public void testUpdateModelRequestWithConnectorIDAndConnectorUpdateContent() throws Exception {
        exceptionRule.expect(OpenSearchStatusException.class);
        exceptionRule
            .expectMessage("Model cannot have both stand-alone connector and internal connector. Please check your update input body.");
        RestRequest request = getRestRequestWithConnectorIDAndConnectorUpdateContent();
        restMLUpdateModelAction.handleRequest(request, channel, client);
    }

    @Test
    public void testUpdateModelRequestWithConnectorID() throws Exception {
        RestRequest request = getRestRequestWithConnectorID();
        restMLUpdateModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLUpdateModelRequest> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelRequest.class);
        verify(client, times(1)).execute(eq(MLUpdateModelAction.INSTANCE), argumentCaptor.capture(), any());
        MLUpdateModelInput updateModelInput = argumentCaptor.getValue().getUpdateModelInput();
        assertEquals("testModelName", updateModelInput.getName());
        assertEquals("testConnectorID", updateModelInput.getConnectorId());
    }

    @Test
    public void testUpdateModelRequestWithConnectorUpdateContent() throws Exception {
        RestRequest request = getRestRequestWithConnectorUpdateContent();
        restMLUpdateModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLUpdateModelRequest> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelRequest.class);
        verify(client, times(1)).execute(eq(MLUpdateModelAction.INSTANCE), argumentCaptor.capture(), any());
        MLUpdateModelInput updateModelInput = argumentCaptor.getValue().getUpdateModelInput();
        assertEquals("testModelName", updateModelInput.getName());
        assertEquals(
            "{\"description\":\"updated description\",\"version\":\"1\",\"parameters\":{},\"credential\":{}}",
            toJsonString(updateModelInput.getConnector())
        );
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.PUT;
        final Map<String, Object> modelContent = Map.of("name", "testModelName", "description", "This is test description");
        String requestContent = new Gson().toJson(modelContent);
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "test_modelId");
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/models/{model_id}")
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
    }

    private RestRequest getRestRequestWithEmptyContent() {
        RestRequest.Method method = RestRequest.Method.PUT;
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "test_modelId");
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/models/{model_id}")
            .withParams(params)
            .withContent(new BytesArray(""), XContentType.JSON)
            .build();
    }

    private RestRequest getRestRequestWithNullModelId() {
        RestRequest.Method method = RestRequest.Method.PUT;
        final Map<String, Object> modelContent = Map.of("name", "testModelName", "description", "This is test description");
        String requestContent = new Gson().toJson(modelContent);
        Map<String, String> params = new HashMap<>();
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/models/{model_id}")
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
    }

    private RestRequest getRestRequestWithNullField() {
        RestRequest.Method method = RestRequest.Method.PUT;
        String requestContent = "{\"name\":\"testModelName\",\"description\":null}";
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "test_modelId");
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/models/{model_id}")
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
    }

    private RestRequest getRestRequestWithConnectorIDAndConnectorUpdateContent() {
        RestRequest.Method method = RestRequest.Method.PUT;
        MLCreateConnectorInput updateContent = MLCreateConnectorInput
            .builder()
            .updateConnector(true)
            .version("1")
            .description("updated description")
            .build();
        final Map<String, Object> modelContent = Map
            .of(
                "name",
                "testModelName",
                "description",
                "This is test description",
                "connector_id",
                "testConnectorID",
                "connector",
                updateContent
            );
        String requestContent = new Gson().toJson(modelContent);
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "test_modelId");
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/models/{model_id}")
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
    }

    private RestRequest getRestRequestWithConnectorID() {
        RestRequest.Method method = RestRequest.Method.PUT;
        final Map<String, Object> modelContent = Map
            .of("name", "testModelName", "description", "This is test description", "connector_id", "testConnectorID");
        String requestContent = new Gson().toJson(modelContent);
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "test_modelId");
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/models/{model_id}")
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
    }

    private RestRequest getRestRequestWithConnectorUpdateContent() {
        RestRequest.Method method = RestRequest.Method.PUT;
        MLCreateConnectorInput updateContent = MLCreateConnectorInput
            .builder()
            .updateConnector(true)
            .version("1")
            .description("updated description")
            .build();
        final Map<String, Object> modelContent = Map
            .of("name", "testModelName", "description", "This is test description", "connector", updateContent);
        String requestContent = new Gson().toJson(modelContent);
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "test_modelId");
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/models/{model_id}")
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
    }
}
