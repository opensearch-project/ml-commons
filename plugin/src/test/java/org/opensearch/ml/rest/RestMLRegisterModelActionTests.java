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
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ALLOW_MODEL_URL;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.Strings;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.settings.ClusterSettings;
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
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RestMLRegisterModelAction restMLRegisterModelAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    private ClusterService clusterService;

    private Settings settings;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().put(ML_COMMONS_ALLOW_MODEL_URL.getKey(), true).build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_ALLOW_MODEL_URL);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        restMLRegisterModelAction = new RestMLRegisterModelAction(clusterService, settings);
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
        assertEquals(RestRequest.Method.POST, route1.getMethod());
        assertEquals("/_plugins/_ml/models/_register", route1.getPath());
    }

    public void testRegisterModelRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLRegisterModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLRegisterModelRequest> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelRequest.class);
        verify(client, times(1)).execute(eq(MLRegisterModelAction.INSTANCE), argumentCaptor.capture(), any());
        MLRegisterModelInput registerModelInput = argumentCaptor.getValue().getRegisterModelInput();
        assertEquals("test_model", registerModelInput.getModelName());
        assertEquals("1", registerModelInput.getVersion());
        assertEquals("TORCH_SCRIPT", registerModelInput.getModelFormat().toString());
    }

    public void testRegisterModelUrlNotAllowed() throws Exception {
        settings = Settings.builder().put(ML_COMMONS_ALLOW_MODEL_URL.getKey(), false).build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_ALLOW_MODEL_URL);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        restMLRegisterModelAction = new RestMLRegisterModelAction(clusterService, settings);
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule
            .expectMessage(
                "To upload custom model user needs to enable allow_registering_model_via_url settings. Otherwise please use opensearch pre-trained models."
            );
        RestRequest request = getRestRequest();
        restMLRegisterModelAction.handleRequest(request, channel, client);
    }

    public void testRegisterModelRequestWithNullUrlAndUrlNotAllowed() throws Exception {
        settings = Settings.builder().put(ML_COMMONS_ALLOW_MODEL_URL.getKey(), false).build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_ALLOW_MODEL_URL);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        RestRequest request = getRestRequestWithNullUrl();
        restMLRegisterModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLRegisterModelRequest> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelRequest.class);
        verify(client, times(1)).execute(eq(MLRegisterModelAction.INSTANCE), argumentCaptor.capture(), any());
        MLRegisterModelInput registerModelInput = argumentCaptor.getValue().getRegisterModelInput();
        assertEquals("test_model", registerModelInput.getModelName());
        assertEquals("2", registerModelInput.getVersion());
        assertEquals("TORCH_SCRIPT", registerModelInput.getModelFormat().toString());
    }

    public void testRegisterModelRequestWithNullUrl() throws Exception {
        RestRequest request = getRestRequestWithNullUrl();
        restMLRegisterModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLRegisterModelRequest> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelRequest.class);
        verify(client, times(1)).execute(eq(MLRegisterModelAction.INSTANCE), argumentCaptor.capture(), any());
        MLRegisterModelInput registerModelInput = argumentCaptor.getValue().getRegisterModelInput();
        assertEquals("test_model", registerModelInput.getModelName());
        assertEquals("2", registerModelInput.getVersion());
        assertEquals("TORCH_SCRIPT", registerModelInput.getModelFormat().toString());
    }

    public void testRegisterModelRequestWithNullModelID() throws Exception {
        RestRequest request = getRestRequestWithNullModelId();
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
        final Map<String, Object> model = Map
            .of(
                "name",
                "test_model",
                "model_id",
                "test_model_with_modelId",
                "version",
                "1",
                "model_group_id",
                "modelGroupId",
                "url",
                "testUrl",
                "model_format",
                "TORCH_SCRIPT",
                "model_config",
                modelConfig
            );
        String requestContent = new Gson().toJson(model).toString();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/models/{model_id}/{version}/_register")
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    private RestRequest getRestRequestWithNullModelId() {
        RestRequest.Method method = RestRequest.Method.POST;
        final Map<String, Object> modelConfig = Map
            .of("model_type", "bert", "embedding_dimension", 384, "framework_type", "sentence_transformers", "all_config", "All Config");
        final Map<String, Object> model = Map
            .of(
                "name",
                "test_model",
                "version",
                "2",
                "model_group_id",
                "modelGroupId",
                "url",
                "testUrl",
                "model_format",
                "TORCH_SCRIPT",
                "model_config",
                modelConfig
            );
        String requestContent = new Gson().toJson(model).toString();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/models/_register")
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    private RestRequest getRestRequestWithNullUrl() {
        RestRequest.Method method = RestRequest.Method.POST;
        final Map<String, Object> modelConfig = Map
            .of("model_type", "bert", "embedding_dimension", 384, "framework_type", "sentence_transformers", "all_config", "All Config");
        final Map<String, Object> model = Map
            .of(
                "name",
                "test_model",
                "model_id",
                "test_model_with_modelId",
                "version",
                "2",
                "model_group_id",
                "modelGroupId",
                "model_format",
                "TORCH_SCRIPT",
                "model_config",
                modelConfig
            );
        String requestContent = new Gson().toJson(model).toString();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/models/{model_id}/{version}/_register")
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }
}
