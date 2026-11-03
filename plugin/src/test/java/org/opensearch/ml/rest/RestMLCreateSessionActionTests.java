/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSIONS_PATH;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.session.MLCreateSessionAction;
import org.opensearch.ml.common.transport.session.MLCreateSessionRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler.Route;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLCreateSessionActionTests extends OpenSearchTestCase {

    private RestMLCreateSessionAction action;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private NodeClient client;
    private RestChannel channel;

    @Before
    public void setup() {
        mlFeatureEnabledSetting = mock(MLFeatureEnabledSetting.class);
        action = new RestMLCreateSessionAction(mlFeatureEnabledSetting);
        client = mock(NodeClient.class);
        channel = mock(RestChannel.class);
    }

    public void testGetName() {
        assertEquals("ml_create_session_action", action.getName());
    }

    public void testRoutes() {
        List<Route> routes = action.routes();
        assertEquals(1, routes.size());
        assertEquals(RestRequest.Method.POST, routes.get(0).getMethod());
        assertEquals(SESSIONS_PATH, routes.get(0).getPath());
    }

    public void testPrepareRequest_Success() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        String requestContent = "{\"summary\":\"test session\",\"owner_id\":\"user123\"}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "container123");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SESSIONS_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();

        action.handleRequest(request, channel, client);

        ArgumentCaptor<MLCreateSessionRequest> argCaptor = ArgumentCaptor.forClass(MLCreateSessionRequest.class);
        verify(client, times(1)).execute(eq(MLCreateSessionAction.INSTANCE), argCaptor.capture(), any());

        MLCreateSessionRequest capturedRequest = argCaptor.getValue();
        assertNotNull(capturedRequest);
        assertNotNull(capturedRequest.getMlCreateSessionInput());
        assertEquals("container123", capturedRequest.getMlCreateSessionInput().getMemoryContainerId());
        assertEquals("test session", capturedRequest.getMlCreateSessionInput().getSummary());
        assertEquals("user123", capturedRequest.getMlCreateSessionInput().getOwnerId());
    }

    public void testPrepareRequest_WithAllFields() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        String requestContent = "{"
            + "\"session_id\":\"session123\","
            + "\"owner_id\":\"user123\","
            + "\"summary\":\"test session\","
            + "\"metadata\":{\"key1\":\"value1\"},"
            + "\"agents\":{\"agent1\":\"config1\"},"
            + "\"additional_info\":{\"info1\":\"data1\"},"
            + "\"namespace\":{\"ns1\":\"val1\"}"
            + "}";

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "container123");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SESSIONS_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();

        action.handleRequest(request, channel, client);

        ArgumentCaptor<MLCreateSessionRequest> argCaptor = ArgumentCaptor.forClass(MLCreateSessionRequest.class);
        verify(client, times(1)).execute(eq(MLCreateSessionAction.INSTANCE), argCaptor.capture(), any());

        MLCreateSessionRequest capturedRequest = argCaptor.getValue();
        assertNotNull(capturedRequest.getMlCreateSessionInput());
        assertEquals("session123", capturedRequest.getMlCreateSessionInput().getSessionId());
        assertEquals("user123", capturedRequest.getMlCreateSessionInput().getOwnerId());
        assertEquals("test session", capturedRequest.getMlCreateSessionInput().getSummary());
        assertNotNull(capturedRequest.getMlCreateSessionInput().getMetadata());
        assertNotNull(capturedRequest.getMlCreateSessionInput().getAgents());
        assertNotNull(capturedRequest.getMlCreateSessionInput().getAdditionalInfo());
        assertNotNull(capturedRequest.getMlCreateSessionInput().getNamespace());
    }

    public void testPrepareRequest_AgenticMemoryDisabled() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        String requestContent = "{\"summary\":\"test session\"}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "container123");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SESSIONS_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();

        OpenSearchStatusException exception = expectThrows(
            OpenSearchStatusException.class,
            () -> action.prepareRequest(request, client)
        );

        assertEquals(RestStatus.FORBIDDEN, exception.status());
    }

    public void testPrepareRequest_MissingRequestBody() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "container123");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SESSIONS_PATH)
            .withParams(params)
            .build();

        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> action.handleRequest(request, channel, client)
        );

        assertEquals("Request body is required", exception.getMessage());
    }

    public void testPrepareRequest_MissingContainerId() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        String requestContent = "{\"summary\":\"test session\"}";

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SESSIONS_PATH)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();

        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> action.getRequest(request)
        );

        assertEquals("Request should contain memory_container_id", exception.getMessage());
    }

    public void testPrepareRequest_EmptyContainerId() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        String requestContent = "{\"summary\":\"test session\"}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SESSIONS_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();

        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> action.getRequest(request)
        );

        assertTrue(exception.getMessage().contains("memory_container_id"));
    }

    public void testPrepareRequest_WithMultiTenancy() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        String requestContent = "{\"summary\":\"test session\"}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "container123");

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-tenant-id", List.of("tenant123"));

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SESSIONS_PATH)
            .withParams(params)
            .withHeaders(headers)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();

        action.handleRequest(request, channel, client);

        ArgumentCaptor<MLCreateSessionRequest> argCaptor = ArgumentCaptor.forClass(MLCreateSessionRequest.class);
        verify(client, times(1)).execute(eq(MLCreateSessionAction.INSTANCE), argCaptor.capture(), any());

        MLCreateSessionRequest capturedRequest = argCaptor.getValue();
        assertNotNull(capturedRequest.getMlCreateSessionInput());
        assertEquals("tenant123", capturedRequest.getMlCreateSessionInput().getTenantId());
    }

    public void testGetRequest_DirectCall() throws Exception {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        String requestContent = "{\"summary\":\"test session\",\"owner_id\":\"user123\"}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "container123");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SESSIONS_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();

        MLCreateSessionRequest mlRequest = action.getRequest(request);

        assertNotNull(mlRequest);
        assertNotNull(mlRequest.getMlCreateSessionInput());
        assertEquals("container123", mlRequest.getMlCreateSessionInput().getMemoryContainerId());
        assertEquals("test session", mlRequest.getMlCreateSessionInput().getSummary());
        assertEquals("user123", mlRequest.getMlCreateSessionInput().getOwnerId());
    }

    public void testPrepareRequest_ContainerIdWithWhitespace() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        String requestContent = "{\"summary\":\"test session\"}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "   ");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SESSIONS_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();

        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> action.getRequest(request)
        );

        assertEquals("Memory container ID is required", exception.getMessage());
    }

    public void testPrepareRequest_MultiTenancyMissingHeader() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        String requestContent = "{\"summary\":\"test session\"}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "container123");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SESSIONS_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();

        OpenSearchStatusException exception = expectThrows(
            OpenSearchStatusException.class,
            () -> action.getRequest(request)
        );

        assertEquals(RestStatus.FORBIDDEN, exception.status());
        assertTrue(exception.getMessage().contains("Tenant ID"));
    }

    public void testPrepareRequest_MultiTenancyEmptyHeader() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        String requestContent = "{\"summary\":\"test session\"}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "container123");

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-tenant-id", List.of());

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SESSIONS_PATH)
            .withParams(params)
            .withHeaders(headers)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();

        OpenSearchStatusException exception = expectThrows(
            OpenSearchStatusException.class,
            () -> action.getRequest(request)
        );

        assertEquals(RestStatus.FORBIDDEN, exception.status());
    }

    public void testPrepareRequest_FullWorkflow() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        String requestContent = "{\"summary\":\"test session\",\"owner_id\":\"user123\"}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "container123");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SESSIONS_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();

        // Test prepareRequest returns a RestChannelConsumer
        var consumer = action.prepareRequest(request, client);
        assertNotNull(consumer);
    }

    public void testPrepareRequest_MinimalFields() throws Exception {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        String requestContent = "{}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "container123");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SESSIONS_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();

        action.handleRequest(request, channel, client);

        ArgumentCaptor<MLCreateSessionRequest> argCaptor = ArgumentCaptor.forClass(MLCreateSessionRequest.class);
        verify(client, times(1)).execute(eq(MLCreateSessionAction.INSTANCE), argCaptor.capture(), any());

        MLCreateSessionRequest capturedRequest = argCaptor.getValue();
        assertNotNull(capturedRequest);
        assertNotNull(capturedRequest.getMlCreateSessionInput());
        assertEquals("container123", capturedRequest.getMlCreateSessionInput().getMemoryContainerId());
    }
}
