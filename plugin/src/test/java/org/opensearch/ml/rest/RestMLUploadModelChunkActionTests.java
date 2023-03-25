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
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkAction;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkInput;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLUploadModelChunkActionTests extends OpenSearchTestCase {

    private RestMLUploadModelChunkAction restChunkUploadAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        restChunkUploadAction = new RestMLUploadModelChunkAction();
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLUploadModelChunkAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLUploadModelChunkAction mlUploadChunk = new RestMLUploadModelChunkAction();
        assertNotNull(mlUploadChunk);
    }

    public void testGetName() {
        String actionName = restChunkUploadAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_upload_model_chunk_action", actionName);
    }

    @Ignore
    public void testRoutes() {
        List<RestHandler.Route> routes = restChunkUploadAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/models/{model_id}/upload_chunk/{chunk_number}", route.getPath());
    }

    public void testReplacedRoutes() {
        List<RestHandler.ReplacedRoute> replacedRoutes = restChunkUploadAction.replacedRoutes();
        assertNotNull(replacedRoutes);
        assertFalse(replacedRoutes.isEmpty());
        RestHandler.Route route = replacedRoutes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/models/{model_id}/upload_chunk/{chunk_number}", route.getPath());
    }

    public void testUploadChunkRequest() throws Exception {
        RestRequest request = getRestRequest();
        restChunkUploadAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLUploadModelChunkRequest> argumentCaptor = ArgumentCaptor.forClass(MLUploadModelChunkRequest.class);
        verify(client, times(1)).execute(eq(MLUploadModelChunkAction.INSTANCE), argumentCaptor.capture(), any());
        MLUploadModelChunkInput chunkRequest = argumentCaptor.getValue().getUploadModelChunkInput();
        assertNotNull(chunkRequest.getContent());
        assertEquals(Integer.valueOf(0), chunkRequest.getChunkNumber());
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.POST;
        BytesArray content = new BytesArray("12345678");
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "r50D4oMBAiM5tNuwVM4C");
        params.put("chunk_number", "0");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withParams(params)
            .withContent(content, null)
            .build();
        return request;
    }
}
