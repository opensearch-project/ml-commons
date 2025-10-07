/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;

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
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.input.Constants;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerDeleteAction;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerDeleteRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLDeleteMemoryContainerActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLDeleteMemoryContainerAction restMLDeleteMemoryContainerAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isRemoteInferenceEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        restMLDeleteMemoryContainerAction = new RestMLDeleteMemoryContainerAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), any(), any());

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLDeleteMemoryContainerAction mlDeleteMemoryContainerAction = new RestMLDeleteMemoryContainerAction(mlFeatureEnabledSetting);
        assertNotNull(mlDeleteMemoryContainerAction);
    }

    public void testGetName() {
        String actionName = restMLDeleteMemoryContainerAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_delete_memory_container_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLDeleteMemoryContainerAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.DELETE, route.getMethod());
        assertEquals("/_plugins/_ml/memory_containers/{memory_container_id}", route.getPath());
    }

    public void testRoutePathConstant() {
        List<RestHandler.Route> routes = restMLDeleteMemoryContainerAction.routes();
        RestHandler.Route route = routes.get(0);

        // Verify the route path matches the expected pattern
        assertTrue(route.getPath().contains("/_plugins/_ml/memory_containers/"));
        assertTrue(route.getPath().contains("{memory_container_id}"));
        assertEquals(RestRequest.Method.DELETE, route.getMethod());
    }

    public void test_PrepareRequest() throws Exception {
        RestRequest request = getRestRequest("memory_container_id", null);
        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> argumentCaptor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), argumentCaptor.capture(), any());
        MLMemoryContainerDeleteRequest mlMemoryContainerDeleteRequest = argumentCaptor.getValue();
        assertNotNull(mlMemoryContainerDeleteRequest);
        assertEquals("memory_container_id", mlMemoryContainerDeleteRequest.getMemoryContainerId());
        assertNull(mlMemoryContainerDeleteRequest.getTenantId());
    }

    public void testPrepareRequest_MultiTenancyEnabled() throws Exception {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        RestRequest request = getRestRequest("memory_container_id", "tenant_id");
        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> argumentCaptor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), argumentCaptor.capture(), any());
        MLMemoryContainerDeleteRequest mlMemoryContainerDeleteRequest = argumentCaptor.getValue();
        assertNotNull(mlMemoryContainerDeleteRequest);
        assertEquals("memory_container_id", mlMemoryContainerDeleteRequest.getMemoryContainerId());
        assertEquals("tenant_id", mlMemoryContainerDeleteRequest.getTenantId());
    }

    public void testPrepareRequest_MultiTenancyDisabled() throws Exception {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        RestRequest request = getRestRequest("memory_container_id", "tenant_id");
        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> argumentCaptor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), argumentCaptor.capture(), any());
        MLMemoryContainerDeleteRequest mlMemoryContainerDeleteRequest = argumentCaptor.getValue();
        assertNotNull(mlMemoryContainerDeleteRequest);
        assertEquals("memory_container_id", mlMemoryContainerDeleteRequest.getMemoryContainerId());
        assertNull(mlMemoryContainerDeleteRequest.getTenantId());
    }

    public void testPrepareRequestWithAgenticMemoryDisabled() throws Exception {
        // Disable agentic memory feature
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        RestRequest request = getRestRequest("memory_container_id", "tenant_id");

        // Expect OpenSearchStatusException when feature is disabled
        thrown.expect(OpenSearchStatusException.class);
        thrown.expectMessage("The Agentic Memory APIs are not enabled. To enable, please update the setting plugins.ml_commons.agentic_memory_enabled");

        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);
    }

    public void testActionNameConstant() {
        // Test that the action name constant is correctly defined
        assertEquals("ml_delete_memory_container_action", restMLDeleteMemoryContainerAction.getName());
    }

    public void testPrepareRequest_WithDeleteMemoriesParameter() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test_id");
        params.put("delete_memories", "sessions,working");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_ml/memory_containers/test_id")
            .withParams(params)
            .build();

        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> captor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), captor.capture(), any());

        MLMemoryContainerDeleteRequest capturedRequest = captor.getValue();
        assertNotNull(capturedRequest.getDeleteMemories());
        assertEquals(2, capturedRequest.getDeleteMemories().size());
        assertTrue(capturedRequest.getDeleteMemories().contains(MemoryType.SESSIONS));
        assertTrue(capturedRequest.getDeleteMemories().contains(MemoryType.WORKING));
    }

    public void testPrepareRequest_WithDeleteAllMemoriesParameter() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test_id");
        params.put("delete_all_memories", "true");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_ml/memory_containers/test_id")
            .withParams(params)
            .build();

        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> captor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), captor.capture(), any());

        MLMemoryContainerDeleteRequest capturedRequest = captor.getValue();
        assertTrue(capturedRequest.isDeleteAllMemories());
    }

    public void testPrepareRequest_WithRequestBody() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        String requestContent = "{\"delete_memories\":[\"sessions\",\"long-term\"]}";

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test_id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_ml/memory_containers/test_id")
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();

        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> captor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), captor.capture(), any());

        MLMemoryContainerDeleteRequest capturedRequest = captor.getValue();
        assertNotNull(capturedRequest.getDeleteMemories());
        assertEquals(2, capturedRequest.getDeleteMemories().size());
        assertTrue(capturedRequest.getDeleteMemories().contains(MemoryType.SESSIONS));
        assertTrue(capturedRequest.getDeleteMemories().contains(MemoryType.LONG_TERM));
    }

    public void testPrepareRequest_WithRequestBodyAndUrlParams_UrlTakesPrecedence() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        String requestContent = "{\"delete_memories\":[\"session\"],\"delete_all_memories\":false}";

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test_id");
        params.put("delete_memories", "working,history");
        params.put("delete_all_memories", "true");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_ml/memory_containers/test_id")
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();

        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> captor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), captor.capture(), any());

        MLMemoryContainerDeleteRequest capturedRequest = captor.getValue();
        // URL params should take precedence
        assertTrue(capturedRequest.isDeleteAllMemories());
        assertNotNull(capturedRequest.getDeleteMemories());
        assertEquals(2, capturedRequest.getDeleteMemories().size());
        assertTrue(capturedRequest.getDeleteMemories().contains(MemoryType.WORKING));
        assertTrue(capturedRequest.getDeleteMemories().contains(MemoryType.HISTORY));
    }

    public void testPrepareRequest_WithEmptyDeleteMemoriesParameter() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test_id");
        params.put("delete_memories", "");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_ml/memory_containers/test_id")
            .withParams(params)
            .build();

        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> captor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), captor.capture(), any());

        MLMemoryContainerDeleteRequest capturedRequest = captor.getValue();
        assertTrue(capturedRequest.getDeleteMemories() == null || capturedRequest.getDeleteMemories().isEmpty());
    }

    public void testPrepareRequest_WithNullBodyMap() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Valid JSON but results in null values
        String requestContent = "{}";

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test_id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_ml/memory_containers/test_id")
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();

        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> captor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), captor.capture(), any());

        MLMemoryContainerDeleteRequest capturedRequest = captor.getValue();
        assertFalse(capturedRequest.isDeleteAllMemories());
        assertNull(capturedRequest.getDeleteMemories());
    }

    public void testPrepareRequest_WithDeleteMemoriesAsNonList() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // delete_memories is a string instead of list
        String requestContent = "{\"delete_memories\":\"session\"}";

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test_id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_ml/memory_containers/test_id")
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();

        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> captor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), captor.capture(), any());

        MLMemoryContainerDeleteRequest capturedRequest = captor.getValue();
        // Since it's not a list, it should be null
        assertNull(capturedRequest.getDeleteMemories());
    }

    public void testPrepareRequest_WithDeleteAllMemoriesAsNonBoolean() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // delete_all_memories is a string instead of boolean
        String requestContent = "{\"delete_all_memories\":\"yes\"}";

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test_id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_ml/memory_containers/test_id")
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();

        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> captor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), captor.capture(), any());

        MLMemoryContainerDeleteRequest capturedRequest = captor.getValue();
        // Since it's not a boolean, it should be false
        assertFalse(capturedRequest.isDeleteAllMemories());
    }

    public void testPrepareRequest_WithInvalidJsonInBody() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        String invalidJson = "{invalid json}";

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test_id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_ml/memory_containers/test_id")
            .withParams(params)
            .withContent(new BytesArray(invalidJson), MediaType.fromMediaType("application/json"))
            .build();

        expectThrows(Exception.class, () -> restMLDeleteMemoryContainerAction.handleRequest(request, channel, client));
    }

    public void testPrepareRequest_WithDuplicateMemoryTypes_AutomaticallyDeduplicates() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Test with duplicate memory types in request body
        String requestContent = "{\"delete_memories\":[\"sessions\",\"working\",\"sessions\",\"working\",\"long-term\"]}";

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test_id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_ml/memory_containers/test_id")
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();

        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> captor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), captor.capture(), any());

        MLMemoryContainerDeleteRequest capturedRequest = captor.getValue();
        assertNotNull(capturedRequest.getDeleteMemories());
        // Should have only 3 unique memory types despite 5 entries in the array
        assertEquals(3, capturedRequest.getDeleteMemories().size());
        assertTrue(capturedRequest.getDeleteMemories().contains(MemoryType.SESSIONS));
        assertTrue(capturedRequest.getDeleteMemories().contains(MemoryType.WORKING));
        assertTrue(capturedRequest.getDeleteMemories().contains(MemoryType.LONG_TERM));
    }

    public void testPrepareRequest_WithDuplicateMemoryTypesInUrlParams_AutomaticallyDeduplicates() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test_id");
        // Comma-separated string with duplicates
        params.put("delete_memories", "sessions,working,sessions,history,working");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_ml/memory_containers/test_id")
            .withParams(params)
            .build();

        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> captor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), captor.capture(), any());

        MLMemoryContainerDeleteRequest capturedRequest = captor.getValue();
        assertNotNull(capturedRequest.getDeleteMemories());
        // Should have only 3 unique memory types
        assertEquals(3, capturedRequest.getDeleteMemories().size());
        assertTrue(capturedRequest.getDeleteMemories().contains(MemoryType.SESSIONS));
        assertTrue(capturedRequest.getDeleteMemories().contains(MemoryType.WORKING));
        assertTrue(capturedRequest.getDeleteMemories().contains(MemoryType.HISTORY));
    }

    private RestRequest createRestRequestWithoutId() {
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath("/_plugins/_ml/memory_containers/")
            .build();
    }

    private RestRequest getRestRequest(String memoryContainerId, String tenantId) {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, memoryContainerId);

        Map<String, List<String>> headers = new HashMap<>();
        if (tenantId != null) {
            headers.put(Constants.TENANT_ID_HEADER, Collections.singletonList(tenantId));
        }

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).withHeaders(headers).build();
    }
}
