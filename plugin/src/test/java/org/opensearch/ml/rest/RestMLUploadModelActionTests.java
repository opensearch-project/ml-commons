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
import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.Strings;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.upload.MLUploadInput;
import org.opensearch.ml.common.transport.upload.MLUploadModelAction;
import org.opensearch.ml.common.transport.upload.MLUploadModelRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.gson.Gson;

public class RestMLUploadModelActionTests extends OpenSearchTestCase {

    private RestMLUploadModelAction restMLUploadModelAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        restMLUploadModelAction = new RestMLUploadModelAction();
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLUploadModelAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLUploadModelAction mlUploadModel = new RestMLUploadModelAction();
        assertNotNull(mlUploadModel);
    }

    public void testGetName() {
        String actionName = restMLUploadModelAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_upload_model_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLUploadModelAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route1 = routes.get(0);
        RestHandler.Route route2 = routes.get(1);
        assertEquals(RestRequest.Method.POST, route1.getMethod());
        assertEquals(RestRequest.Method.POST, route2.getMethod());
        assertEquals("/_plugins/_ml/models/_upload", route1.getPath());
        assertEquals("/_plugins/_ml/models/{model_id}/{version}/_upload", route2.getPath());
    }

    public void testUploadModelRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLUploadModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLUploadModelRequest> argumentCaptor = ArgumentCaptor.forClass(MLUploadModelRequest.class);
        verify(client, times(1)).execute(eq(MLUploadModelAction.INSTANCE), argumentCaptor.capture(), any());
        MLUploadInput mlUploadInput = argumentCaptor.getValue().getMlUploadInput();
        assertEquals("test_model_with_modelId", mlUploadInput.getModelName());
        assertEquals("1", mlUploadInput.getVersion());
        assertEquals("TORCH_SCRIPT", mlUploadInput.getModelFormat().toString());
    }

    public void testUploadModelRequest_NullModelID() throws Exception {
        RestRequest request = getRestRequest_NullModelId();
        restMLUploadModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLUploadModelRequest> argumentCaptor = ArgumentCaptor.forClass(MLUploadModelRequest.class);
        verify(client, times(1)).execute(eq(MLUploadModelAction.INSTANCE), argumentCaptor.capture(), any());
        MLUploadInput mlUploadInput = argumentCaptor.getValue().getMlUploadInput();
        assertEquals("test_model", mlUploadInput.getModelName());
        assertEquals("2", mlUploadInput.getVersion());
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
            .withPath("/_plugins/_ml/models/{model_id}/{version}/_upload")
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
            .withPath("/_plugins/_ml/models/_upload")
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }
}
