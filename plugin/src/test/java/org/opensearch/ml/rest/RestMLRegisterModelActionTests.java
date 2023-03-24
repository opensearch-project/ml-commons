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
import org.junit.Ignore;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.Strings;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.register.MLRegisterModelAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.gson.Gson;

public class RestMLRegisterModelActionTests extends OpenSearchTestCase {

    private RestMLRegisterModelAction restMLRegisterModelAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        restMLRegisterModelAction = new RestMLRegisterModelAction();
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLRegisterModelAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLRegisterModelAction registerModelAction = new RestMLRegisterModelAction();
        assertNotNull(registerModelAction);
    }

    public void testGetName() {
        String actionName = restMLRegisterModelAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_register_model_action", actionName);
    }

    @Ignore
    public void testRoutes() {
        List<RestHandler.Route> routes = restMLRegisterModelAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route1 = routes.get(0);
        RestHandler.Route route2 = routes.get(1);
        assertEquals(RestRequest.Method.POST, route1.getMethod());
        assertEquals(RestRequest.Method.POST, route2.getMethod());
        assertEquals("/_plugins/_ml/models/_register", route1.getPath());
        assertEquals("/_plugins/_ml/models/{model_id}/{version}/_register", route2.getPath());
    }

    public void testReplacedRoutes() {
        List<RestHandler.ReplacedRoute> replacedRoutes = restMLRegisterModelAction.replacedRoutes();
        assertNotNull(replacedRoutes);
        assertFalse(replacedRoutes.isEmpty());
        RestHandler.Route route1 = replacedRoutes.get(0);
        RestHandler.Route route2 = replacedRoutes.get(1);
        assertEquals(RestRequest.Method.POST, route1.getMethod());
        assertEquals(RestRequest.Method.POST, route2.getMethod());
        assertEquals("/_plugins/_ml/models/_register", route1.getPath());
        assertEquals("/_plugins/_ml/models/{model_id}/{version}/_register", route2.getPath());
    }

    public void testRegisterModelRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLRegisterModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLRegisterModelRequest> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelRequest.class);
        verify(client, times(1)).execute(eq(MLRegisterModelAction.INSTANCE), argumentCaptor.capture(), any());
        MLRegisterModelInput registerModelInput = argumentCaptor.getValue().getRegisterModelInput();
        assertEquals("test_model_with_modelId", registerModelInput.getModelName());
        assertEquals("1", registerModelInput.getVersion());
        assertEquals("TORCH_SCRIPT", registerModelInput.getModelFormat().toString());
    }

    public void testRegisterModelRequest_NullModelID() throws Exception {
        RestRequest request = getRestRequest_NullModelId();
        restMLRegisterModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLRegisterModelRequest> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelRequest.class);
        verify(client, times(1)).execute(eq(MLRegisterModelAction.INSTANCE), argumentCaptor.capture(), any());
        MLRegisterModelInput registerModelInput = argumentCaptor.getValue().getRegisterModelInput();
        assertEquals("test_model", registerModelInput.getModelName());
        assertEquals("2", registerModelInput.getVersion());
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.POST;
        final Map<String, Object> modelConfig = Map
            .of("model_type", "bert", "embedding_dimension", 384, "framework_type", "sentence_transformers", "all_config", "All Config");
        final Map<String, Object> model = Map.of("url", "testUrl", "model_format", "TORCH_SCRIPT", "model_config", modelConfig);
        String requestContent = new Gson().toJson(model).toString();
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "test_model_with_modelId");
        params.put("version", "1");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/models/{model_id}/{version}/_register")
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    private RestRequest getRestRequest_NullModelId() {
        RestRequest.Method method = RestRequest.Method.POST;
        final Map<String, Object> modelConfig = Map
            .of("model_type", "bert", "embedding_dimension", 384, "framework_type", "sentence_transformers", "all_config", "All Config");
        final Map<String, Object> model = Map
            .of("name", "test_model", "version", "2", "url", "testUrl", "model_format", "TORCH_SCRIPT", "model_config", modelConfig);
        String requestContent = new Gson().toJson(model).toString();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/models/_register")
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }
}
