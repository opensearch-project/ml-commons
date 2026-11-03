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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionAction;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLExecuteMemoryRetentionResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLExecuteMemoryRetentionActionTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLExecuteMemoryRetentionAction restAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        restAction = new RestMLExecuteMemoryRetentionAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLExecuteMemoryRetentionResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLExecuteMemoryRetentionAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        assertNotNull(new RestMLExecuteMemoryRetentionAction(mlFeatureEnabledSetting));
    }

    public void testGetName() {
        String name = restAction.getName();
        assertFalse(Strings.isNullOrEmpty(name));
        assertEquals("ml_execute_memory_retention_action", name);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restAction.routes();
        assertNotNull(routes);
        assertEquals(1, routes.size());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/memory_containers/_retention/_execute", route.getPath());
    }

    public void testGetRequest() throws IOException {
        RestRequest request = createRestRequest();
        MLExecuteMemoryRetentionRequest executeRequest = restAction.getRequest(request);
        assertNotNull(executeRequest);
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = createRestRequest();
        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLExecuteMemoryRetentionRequest> captor = ArgumentCaptor.forClass(MLExecuteMemoryRetentionRequest.class);
        verify(client, times(1)).execute(eq(MLExecuteMemoryRetentionAction.INSTANCE), captor.capture(), any());
        assertNotNull(captor.getValue());
    }

    public void testPrepareRequestRejectsRequestBody() throws IOException {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath("/_plugins/_ml/memory_containers/_retention/_execute")
            .withHeaders(headers)
            .withContent(
                new org.opensearch.core.common.bytes.BytesArray("{\"foo\":\"bar\"}"),
                org.opensearch.common.xcontent.XContentType.JSON
            )
            .build();

        thrown.expect(OpenSearchStatusException.class);
        thrown.expectMessage("_execute does not accept a request body");

        restAction.prepareRequest(request, client);
    }

    public void testPrepareRequestAgenticMemoryDisabled() throws IOException {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);
        RestRequest request = createRestRequest();

        thrown.expect(OpenSearchStatusException.class);
        thrown
            .expectMessage(
                "The Agentic Memory APIs are not enabled. To enable, please update the setting plugins.ml_commons.agentic_memory_enabled"
            );

        restAction.prepareRequest(request, client);
    }

    private RestRequest createRestRequest() {
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath("/_plugins/_ml/memory_containers/_retention/_execute")
            .build();
    }
}
