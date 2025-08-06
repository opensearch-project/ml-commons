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
import org.opensearch.ml.common.input.Constants;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerGetAction;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerGetRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerGetResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLGetMemoryContainerActionTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLGetMemoryContainerAction restMLGetMemoryContainerAction;

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
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true); // Enable by default for tests
        restMLGetMemoryContainerAction = new RestMLGetMemoryContainerAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLMemoryContainerGetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLMemoryContainerGetAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLGetMemoryContainerAction action = new RestMLGetMemoryContainerAction(mlFeatureEnabledSetting);
        assertNotNull(action);
    }

    public void testGetName() {
        String actionName = restMLGetMemoryContainerAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_get_memory_container_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLGetMemoryContainerAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        assertEquals(1, routes.size());

        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
        assertEquals("/_plugins/_ml/memory_containers/{memory_container_id}", route.getPath());
    }

    public void testGetRequestWithValidMemoryContainerId() throws IOException {
        String memoryContainerId = "test-memory-container-123";
        RestRequest request = createRestRequest(memoryContainerId);

        MLMemoryContainerGetRequest mlMemoryContainerGetRequest = restMLGetMemoryContainerAction.getRequest(request);

        assertNotNull(mlMemoryContainerGetRequest);
        assertEquals(memoryContainerId, mlMemoryContainerGetRequest.getMemoryContainerId());
        assertNull(mlMemoryContainerGetRequest.getTenantId()); // Multi-tenancy disabled
    }

    public void testGetRequestWithLongMemoryContainerId() throws IOException {
        String longMemoryContainerId =
            "very-long-memory-container-id-with-multiple-segments-and-special-characters-!@#$%^&*()_+-=[]{}|;':\",./<>?-123456789";
        RestRequest request = createRestRequest(longMemoryContainerId);

        MLMemoryContainerGetRequest mlMemoryContainerGetRequest = restMLGetMemoryContainerAction.getRequest(request);

        assertNotNull(mlMemoryContainerGetRequest);
        assertEquals(longMemoryContainerId, mlMemoryContainerGetRequest.getMemoryContainerId());
        assertNull(mlMemoryContainerGetRequest.getTenantId());
    }

    public void testGetRequestWithSpecialCharacters() throws IOException {
        String memoryContainerIdWithSpecialChars = "container-with-special-chars-!@#$%^&*()_+-=[]{}|;':\",./<>?";
        RestRequest request = createRestRequest(memoryContainerIdWithSpecialChars);

        MLMemoryContainerGetRequest mlMemoryContainerGetRequest = restMLGetMemoryContainerAction.getRequest(request);

        assertNotNull(mlMemoryContainerGetRequest);
        assertEquals(memoryContainerIdWithSpecialChars, mlMemoryContainerGetRequest.getMemoryContainerId());
        assertNull(mlMemoryContainerGetRequest.getTenantId());
    }

    public void testGetRequestWithMultiTenancyEnabled() throws IOException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        
        String memoryContainerId = "tenant-container-123";
        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.TENANT_ID_HEADER, "test-tenant");
        
        RestRequest request = createRestRequestWithHeaders(memoryContainerId, headers);
        MLMemoryContainerGetRequest mlMemoryContainerGetRequest = restMLGetMemoryContainerAction.getRequest(request);

        assertNotNull(mlMemoryContainerGetRequest);
        assertEquals(memoryContainerId, mlMemoryContainerGetRequest.getMemoryContainerId());
        assertEquals("test-tenant", mlMemoryContainerGetRequest.getTenantId()); // Should be set from header
    }

    public void testGetRequestWithMultiTenancyDisabled() throws IOException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        
        String memoryContainerId = "no-tenant-container-123";
        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.TENANT_ID_HEADER, "should-be-ignored");
        
        RestRequest request = createRestRequestWithHeaders(memoryContainerId, headers);
        MLMemoryContainerGetRequest mlMemoryContainerGetRequest = restMLGetMemoryContainerAction.getRequest(request);

        assertNotNull(mlMemoryContainerGetRequest);
        assertEquals(memoryContainerId, mlMemoryContainerGetRequest.getMemoryContainerId());
        assertNull(mlMemoryContainerGetRequest.getTenantId()); // Should be null when multi-tenancy is disabled
    }

    public void testGetRequestWithMissingMemoryContainerId() throws IOException {
        RestRequest request = createRestRequestWithoutId();

        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> restMLGetMemoryContainerAction.getRequest(request)
        );
        assertEquals("Request should contain memory_container_id", exception.getMessage());
    }

    public void testGetRequestWithEmptyMemoryContainerId() throws IOException {
        RestRequest request = createRestRequestWithEmptyId();

        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> restMLGetMemoryContainerAction.getRequest(request)
        );
        assertEquals("Request should contain memory_container_id", exception.getMessage());
    }

    public void testGetRequestWithNullMemoryContainerId() throws IOException {
        RestRequest request = createRestRequestWithNullId();

        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> restMLGetMemoryContainerAction.getRequest(request)
        );
        assertEquals("Request should contain memory_container_id", exception.getMessage());
    }

    public void testPrepareRequest() throws Exception {
        String memoryContainerId = "test-container-456";
        RestRequest request = createRestRequest(memoryContainerId);

        restMLGetMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerGetRequest> argumentCaptor = ArgumentCaptor.forClass(MLMemoryContainerGetRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerGetAction.INSTANCE), argumentCaptor.capture(), any());

        MLMemoryContainerGetRequest capturedRequest = argumentCaptor.getValue();
        assertNotNull(capturedRequest);
        assertEquals(memoryContainerId, capturedRequest.getMemoryContainerId());
        assertNull(capturedRequest.getTenantId());
    }

    public void testPrepareRequestWithTenant() throws Exception {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        
        String memoryContainerId = "tenant-container-789";
        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.TENANT_ID_HEADER, "prepare-test-tenant");
        
        RestRequest request = createRestRequestWithHeaders(memoryContainerId, headers);
        restMLGetMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerGetRequest> argumentCaptor = ArgumentCaptor.forClass(MLMemoryContainerGetRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerGetAction.INSTANCE), argumentCaptor.capture(), any());
        
        MLMemoryContainerGetRequest capturedRequest = argumentCaptor.getValue();
        assertNotNull(capturedRequest);
        assertEquals(memoryContainerId, capturedRequest.getMemoryContainerId());
        assertEquals("prepare-test-tenant", capturedRequest.getTenantId());
    }

    public void testPrepareRequestWithMissingId() throws Exception {
        RestRequest request = createRestRequestWithoutId();

        expectThrows(IllegalArgumentException.class, () -> { restMLGetMemoryContainerAction.handleRequest(request, channel, client); });
    }

    public void testActionNameConstant() {
        // Test that the action name constant is correctly defined
        assertEquals("ml_get_memory_container_action", restMLGetMemoryContainerAction.getName());
    }

    public void testRoutePathConstant() {
        List<RestHandler.Route> routes = restMLGetMemoryContainerAction.routes();
        RestHandler.Route route = routes.get(0);

        // Verify the route path matches the expected pattern
        assertTrue(route.getPath().contains("/_plugins/_ml/memory_containers/"));
        assertTrue(route.getPath().contains("{memory_container_id}"));
        assertEquals(RestRequest.Method.GET, route.getMethod());
    }

    public void testGetRequestWithUUIDMemoryContainerId() throws IOException {
        String uuidMemoryContainerId = "550e8400-e29b-41d4-a716-446655440000";
        RestRequest request = createRestRequest(uuidMemoryContainerId);

        MLMemoryContainerGetRequest mlMemoryContainerGetRequest = restMLGetMemoryContainerAction.getRequest(request);

        assertNotNull(mlMemoryContainerGetRequest);
        assertEquals(uuidMemoryContainerId, mlMemoryContainerGetRequest.getMemoryContainerId());
        assertNull(mlMemoryContainerGetRequest.getTenantId());
    }

    public void testGetRequestWithNumericMemoryContainerId() throws IOException {
        String numericMemoryContainerId = "123456789";
        RestRequest request = createRestRequest(numericMemoryContainerId);

        MLMemoryContainerGetRequest mlMemoryContainerGetRequest = restMLGetMemoryContainerAction.getRequest(request);

        assertNotNull(mlMemoryContainerGetRequest);
        assertEquals(numericMemoryContainerId, mlMemoryContainerGetRequest.getMemoryContainerId());
        assertNull(mlMemoryContainerGetRequest.getTenantId());
    }

    public void testGetRequestWithAlphanumericMemoryContainerId() throws IOException {
        String alphanumericMemoryContainerId = "container123ABC456def";
        RestRequest request = createRestRequest(alphanumericMemoryContainerId);

        MLMemoryContainerGetRequest mlMemoryContainerGetRequest = restMLGetMemoryContainerAction.getRequest(request);

        assertNotNull(mlMemoryContainerGetRequest);
        assertEquals(alphanumericMemoryContainerId, mlMemoryContainerGetRequest.getMemoryContainerId());
        assertNull(mlMemoryContainerGetRequest.getTenantId());
    }

    public void testMultipleTenantHeaders() throws IOException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        
        String memoryContainerId = "multi-tenant-container";
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(Constants.TENANT_ID_HEADER, Collections.singletonList("first-tenant"));
        
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.GET)
            .withPath("/_plugins/_ml/memory_containers/" + memoryContainerId)
            .withParams(createParamsMap(memoryContainerId))
            .withHeaders(headers)
            .build();
        
        MLMemoryContainerGetRequest mlMemoryContainerGetRequest = restMLGetMemoryContainerAction.getRequest(request);

        assertNotNull(mlMemoryContainerGetRequest);
        assertEquals(memoryContainerId, mlMemoryContainerGetRequest.getMemoryContainerId());
        assertEquals("first-tenant", mlMemoryContainerGetRequest.getTenantId());
    }

    public void testRequestParameterExtraction() throws IOException {
        // Test that the parameter extraction works correctly with different path formats
        String memoryContainerId = "param-extraction-test";

        // Test with exact path match
        Map<String, String> params = new HashMap<>();
        params.put("memory_container_id", memoryContainerId);

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.GET)
            .withPath("/_plugins/_ml/memory_containers/" + memoryContainerId)
            .withParams(params)
            .build();

        MLMemoryContainerGetRequest mlMemoryContainerGetRequest = restMLGetMemoryContainerAction.getRequest(request);

        assertNotNull(mlMemoryContainerGetRequest);
        assertEquals(memoryContainerId, mlMemoryContainerGetRequest.getMemoryContainerId());
    }

    public void testGetRequestImmutability() throws IOException {
        String memoryContainerId = "immutable-test-container";
        RestRequest request = createRestRequest(memoryContainerId);

        MLMemoryContainerGetRequest mlMemoryContainerGetRequest = restMLGetMemoryContainerAction.getRequest(request);

        // Test that the request fields are immutable (final)
        assertNotNull(mlMemoryContainerGetRequest);
        assertEquals(memoryContainerId, mlMemoryContainerGetRequest.getMemoryContainerId());

        // Verify that there are no setter methods (fields should be final)
        try {
            mlMemoryContainerGetRequest.getClass().getMethod("setMemoryContainerId", String.class);
            fail("Should not have setter for final field");
        } catch (NoSuchMethodException e) {
            // Expected - no setter should exist
        }
    }

    // Helper methods for creating test requests

    private RestRequest createRestRequest(String memoryContainerId) {
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.GET)
            .withPath("/_plugins/_ml/memory_containers/" + memoryContainerId)
            .withParams(createParamsMap(memoryContainerId))
            .build();
    }

    private RestRequest createRestRequestWithHeaders(String memoryContainerId, Map<String, String> headers) {
        Map<String, List<String>> headerMap = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            headerMap.put(entry.getKey(), Collections.singletonList(entry.getValue()));
        }

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.GET)
            .withPath("/_plugins/_ml/memory_containers/" + memoryContainerId)
            .withParams(createParamsMap(memoryContainerId))
            .withHeaders(headerMap)
            .build();
    }

    private RestRequest createRestRequestWithoutId() {
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.GET)
            .withPath("/_plugins/_ml/memory_containers/")
            .build();
    }

    private RestRequest createRestRequestWithEmptyId() {
        Map<String, String> params = new HashMap<>();
        params.put("memory_container_id", "");

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.GET)
            .withPath("/_plugins/_ml/memory_containers/")
            .withParams(params)
            .build();
    }

    private RestRequest createRestRequestWithNullId() {
        Map<String, String> params = new HashMap<>();
        params.put("memory_container_id", null);

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.GET)
            .withPath("/_plugins/_ml/memory_containers/")
            .withParams(params)
            .build();
    }

    private Map<String, String> createParamsMap(String memoryContainerId) {
        Map<String, String> params = new HashMap<>();
        params.put("memory_container_id", memoryContainerId);
        return params;
    }

    public void testPrepareRequestWithAgenticMemoryDisabled() throws IOException {
        // Disable agentic memory feature
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        RestRequest request = createRestRequest("test-memory-container-id");

        // Expect OpenSearchStatusException when feature is disabled
        thrown.expect(OpenSearchStatusException.class);
        thrown.expectMessage("The Agentic Memory APIs are not enabled. To enable, please update the setting plugins.ml_commons.agentic_memory_enabled");

        restMLGetMemoryContainerAction.prepareRequest(request, client);
    }
}
