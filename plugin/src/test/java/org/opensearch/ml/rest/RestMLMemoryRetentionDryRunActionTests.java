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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.input.Constants;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryRetentionDryRunAction;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryRetentionDryRunRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryRetentionDryRunResponse;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLMemoryRetentionDryRunActionTests extends OpenSearchTestCase {

    private RestMLMemoryRetentionDryRunAction action;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        action = new RestMLMemoryRetentionDryRunAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLMemoryRetentionDryRunResponse> listener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLMemoryRetentionDryRunAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testGetName() {
        assertFalse(Strings.isNullOrEmpty(action.getName()));
        assertEquals("ml_memory_retention_dry_run_action", action.getName());
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = action.routes();
        assertNotNull(routes);
        assertEquals(2, routes.size());
        assertEquals(RestRequest.Method.POST, routes.get(0).getMethod());
        assertEquals("/_plugins/_ml/memory_containers/{memory_container_id}/_retention/_dry_run", routes.get(0).getPath());
        assertEquals(RestRequest.Method.POST, routes.get(1).getMethod());
        assertEquals("/_plugins/_ml/memory_containers/_retention/_dry_run", routes.get(1).getPath());
    }

    public void testGetRequestSingleContainer() throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("memory_container_id", "container-abc");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath("/_plugins/_ml/memory_containers/container-abc/_retention/_dry_run")
            .withParams(params)
            .build();

        MLMemoryRetentionDryRunRequest dryRunRequest = action.getRequest(request);
        assertNotNull(dryRunRequest);
        assertEquals("container-abc", dryRunRequest.getMemoryContainerId());
        assertFalse(dryRunRequest.isClusterWide());
        assertNull(dryRunRequest.getTenantId());
    }

    public void testGetRequestClusterWide() throws IOException {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath("/_plugins/_ml/memory_containers/_retention/_dry_run")
            .build();

        MLMemoryRetentionDryRunRequest dryRunRequest = action.getRequest(request);
        assertNotNull(dryRunRequest);
        assertNull(dryRunRequest.getMemoryContainerId());
        assertTrue(dryRunRequest.isClusterWide());
    }

    public void testGetRequestWithMultiTenancy() throws IOException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        Map<String, String> params = new HashMap<>();
        params.put("memory_container_id", "c1");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(Constants.TENANT_ID_HEADER, List.of("tenant-7"));
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath("/_plugins/_ml/memory_containers/c1/_retention/_dry_run")
            .withParams(params)
            .withHeaders(headers)
            .build();

        MLMemoryRetentionDryRunRequest dryRunRequest = action.getRequest(request);
        assertEquals("c1", dryRunRequest.getMemoryContainerId());
        assertEquals("tenant-7", dryRunRequest.getTenantId());
    }

    public void testPrepareRequestDispatchesToTransport() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("memory_container_id", "container-xyz");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath("/_plugins/_ml/memory_containers/container-xyz/_retention/_dry_run")
            .withParams(params)
            .build();

        action.handleRequest(request, new FakeRestChannelHelper().channel(), client);

        ArgumentCaptor<MLMemoryRetentionDryRunRequest> captor = ArgumentCaptor.forClass(MLMemoryRetentionDryRunRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryRetentionDryRunAction.INSTANCE), captor.capture(), any());
        assertEquals("container-xyz", captor.getValue().getMemoryContainerId());
    }

    public void testPrepareRequestForbiddenWhenAgenticMemoryDisabled() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath("/_plugins/_ml/memory_containers/_retention/_dry_run")
            .build();

        expectThrows(OpenSearchStatusException.class, () -> action.handleRequest(request, new FakeRestChannelHelper().channel(), client));
    }

    /** Minimal RestChannel that is never written to (transport call is mocked to no-op). */
    private static class FakeRestChannelHelper {
        org.opensearch.rest.RestChannel channel() {
            return new org.opensearch.test.rest.FakeRestChannel(new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).build(), true, 1);
        }
    }
}
