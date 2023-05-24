/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ALLOW_LOCAL_FILE_UPLOAD;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.util.HashMap;
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
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaAction;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaInput;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.gson.Gson;

public class RestMLRegisterModelMetaActionTests extends OpenSearchTestCase {

    private RestMLRegisterModelMetaAction restMLRegisterModelMetaAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    private ClusterService clusterService;

    private Settings settings;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().put(ML_COMMONS_ALLOW_LOCAL_FILE_UPLOAD.getKey(), true).build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_ALLOW_LOCAL_FILE_UPLOAD);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        restMLRegisterModelMetaAction = new RestMLRegisterModelMetaAction(clusterService, settings);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLRegisterModelMetaAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLRegisterModelMetaAction mlUploadModel = new RestMLRegisterModelMetaAction(clusterService, settings);
        assertNotNull(mlUploadModel);
    }

    public void testGetName() {
        String actionName = restMLRegisterModelMetaAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_register_model_meta_action", actionName);
    }

    @Ignore
    public void testRoutes() {
        List<RestHandler.Route> routes = restMLRegisterModelMetaAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/models/_register_meta", route.getPath());
    }

    public void testReplacedRoutes() {
        List<RestHandler.ReplacedRoute> replacedRoutes = restMLRegisterModelMetaAction.replacedRoutes();
        assertNotNull(replacedRoutes);
        assertFalse(replacedRoutes.isEmpty());
        RestHandler.Route route = replacedRoutes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/models/_register_meta", route.getPath());
    }

    public void testRegisterModelMetaRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLRegisterModelMetaAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLRegisterModelMetaRequest> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelMetaRequest.class);
        verify(client, times(1)).execute(eq(MLRegisterModelMetaAction.INSTANCE), argumentCaptor.capture(), any());
        MLRegisterModelMetaInput metaModelRequest = argumentCaptor.getValue().getMlRegisterModelMetaInput();
        assertEquals("all-MiniLM-L6-v3", metaModelRequest.getName());
        assertEquals("1", metaModelRequest.getModelGroupId());
        assertEquals(Integer.valueOf(2), metaModelRequest.getTotalChunks());
    }

    public void testRegisterModelFileUploadNotAllowed() throws Exception {
        settings = Settings.builder().put(ML_COMMONS_ALLOW_LOCAL_FILE_UPLOAD.getKey(), false).build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_ALLOW_LOCAL_FILE_UPLOAD);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        restMLRegisterModelMetaAction = new RestMLRegisterModelMetaAction(clusterService, settings);
        expectedException.expect(IllegalArgumentException.class);
        expectedException
            .expectMessage(
                "To upload custom model from local file, user needs to enable allow_registering_model_via_local_file settings. Otherwise please use opensearch pre-trained models"
            );
        RestRequest request = getRestRequest();
        restMLRegisterModelMetaAction.handleRequest(request, channel, client);
    }

    public void testRegisterModelMeta_NoContent() throws Exception {
        RestRequest.Method method = RestRequest.Method.POST;
        Map<String, String> params = new HashMap<>();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withMethod(method).withParams(params).build();
        expectedException.expect(IOException.class);
        expectedException.expectMessage("Model meta request has empty body");
        restMLRegisterModelMetaAction.handleRequest(request, channel, client);
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.POST;
        Map<String, String> params = new HashMap<>();
        final var requestContent = prepareCustomModel();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    private String prepareCustomModel() {
        final Map<String, Object> modelConfig = Map
            .of("model_type", "bert", "embedding_dimension", 384, "framework_type", "sentence_transformers", "all_config", "All Config");
        final Map<String, Object> model = Map
            .of(
                "name",
                "all-MiniLM-L6-v3",
                "version",
                "1",
                "model_group_id",
                "1",
                "model_format",
                "TORCH_SCRIPT",
                "model_task_type",
                "TEXT_EMBEDDING",
                "model_content_hash_value",
                "123456677555433",
                "model_content_size_in_bytes",
                12345,
                "total_chunks",
                2,
                "model_config",
                modelConfig
            );
        return new Gson().toJson(model).toString();
    }
}
