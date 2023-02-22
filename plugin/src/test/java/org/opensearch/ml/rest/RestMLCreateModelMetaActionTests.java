/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.Strings;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaAction;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaInput;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.gson.Gson;

public class RestMLCreateModelMetaActionTests extends OpenSearchTestCase {

    private RestMLCreateModelMetaAction restMLCreateModelMetaAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setup() {
        restMLCreateModelMetaAction = new RestMLCreateModelMetaAction();
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLCreateModelMetaAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLCreateModelMetaAction mlUploadModel = new RestMLCreateModelMetaAction();
        assertNotNull(mlUploadModel);
    }

    public void testGetName() {
        String actionName = restMLCreateModelMetaAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_create_model_meta_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLCreateModelMetaAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/models/meta", route.getPath());
    }

    public void testCreateModelMetaRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLCreateModelMetaAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLCreateModelMetaRequest> argumentCaptor = ArgumentCaptor.forClass(MLCreateModelMetaRequest.class);
        verify(client, times(1)).execute(eq(MLCreateModelMetaAction.INSTANCE), argumentCaptor.capture(), any());
        MLCreateModelMetaInput metaModelRequest = argumentCaptor.getValue().getMlCreateModelMetaInput();
        assertEquals("all-MiniLM-L6-v3", metaModelRequest.getName());
        assertEquals("1", metaModelRequest.getVersion());
        assertEquals(Integer.valueOf(2), metaModelRequest.getTotalChunks());
    }

    public void testCreateModelMeta_NoContent() throws Exception {
        RestRequest.Method method = RestRequest.Method.POST;
        Map<String, String> params = new HashMap<>();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withMethod(method).withParams(params).build();
        expectedEx.expect(IOException.class);
        expectedEx.expectMessage("Model meta request has empty body");
        restMLCreateModelMetaAction.handleRequest(request, channel, client);
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
