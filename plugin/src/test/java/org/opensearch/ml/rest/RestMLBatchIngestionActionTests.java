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
import static org.opensearch.ml.utils.TestHelper.getBatchIngestionRestRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionAction;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionInput;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionRequest;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLBatchIngestionActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLBatchIngestAction restMLBatchIngestAction;
    private ThreadPool threadPool;
    NodeClient client;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        restMLBatchIngestAction = new RestMLBatchIngestAction();
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        doAnswer(invocation -> {
            ActionListener<MLBatchIngestionResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLBatchIngestionAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLBatchIngestAction mlBatchIngestAction = new RestMLBatchIngestAction();
        assertNotNull(mlBatchIngestAction);
    }

    public void testGetName() {
        String actionName = restMLBatchIngestAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_batch_ingestion_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLBatchIngestAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/_batch_ingestion", route.getPath());
    }

    public void testGetRequest() throws IOException {
        RestRequest request = getBatchIngestionRestRequest();
        MLBatchIngestionRequest mlBatchIngestionRequest = restMLBatchIngestAction.getRequest(request);

        MLBatchIngestionInput mlBatchIngestionInput = mlBatchIngestionRequest.getMlBatchIngestionInput();
        assertEquals("test batch index", mlBatchIngestionInput.getIndexName());
        assertEquals("$.content[0]", mlBatchIngestionInput.getFieldMapping().get("chapter"));
        assertNotNull(mlBatchIngestionInput.getDataSources().get("source"));
        assertNotNull(mlBatchIngestionInput.getCredential());
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getBatchIngestionRestRequest();
        restMLBatchIngestAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLBatchIngestionRequest> argumentCaptor = ArgumentCaptor.forClass(MLBatchIngestionRequest.class);
        verify(client, times(1)).execute(eq(MLBatchIngestionAction.INSTANCE), argumentCaptor.capture(), any());
        MLBatchIngestionInput mlBatchIngestionInput = argumentCaptor.getValue().getMlBatchIngestionInput();
        assertEquals("test batch index", mlBatchIngestionInput.getIndexName());
        assertEquals("$.content[0]", mlBatchIngestionInput.getFieldMapping().get("chapter"));
        assertNotNull(mlBatchIngestionInput.getDataSources().get("source"));
        assertNotNull(mlBatchIngestionInput.getCredential());
    }

    public void testPrepareRequest_EmptyContent() throws Exception {
        thrown.expect(IOException.class);
        thrown.expectMessage("Batch Ingestion request has empty body");
        Map<String, String> params = new HashMap<>();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();

        restMLBatchIngestAction.handleRequest(request, channel, client);
    }
}
