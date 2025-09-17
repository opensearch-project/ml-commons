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
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.input.Constants;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerAction;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerInput;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLCreateMemoryContainerActionTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLCreateMemoryContainerAction restMLCreateMemoryContainerAction;

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
        restMLCreateMemoryContainerAction = new RestMLCreateMemoryContainerAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLCreateMemoryContainerResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLCreateMemoryContainerAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLCreateMemoryContainerAction action = new RestMLCreateMemoryContainerAction(mlFeatureEnabledSetting);
        assertNotNull(action);
    }

    public void testGetName() {
        String actionName = restMLCreateMemoryContainerAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_create_memory_container_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLCreateMemoryContainerAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        assertEquals(1, routes.size());

        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/memory_containers/_create", route.getPath());
    }

    public void testGetRequestWithValidInput() throws IOException {
        String requestBody = "{\n"
            + "  \"name\": \"test-memory-container\",\n"
            + "  \"description\": \"Test memory container description\",\n"
            + "  \"memory_storage_config\": {\n"
            + "    \"memory_index_name\": \"test-memory-index\",\n"
            + "    \"embedding_model_type\": \"TEXT_EMBEDDING\",\n"
            + "    \"embedding_model_id\": \"test-embedding-model\",\n"
            + "    \"llm_model_id\": \"test-llm-model\",\n"
            + "    \"dimension\": 768,\n"
            + "    \"max_infer_size\": 8\n"
            + "  }\n"
            + "}";

        RestRequest request = createRestRequest(requestBody);
        MLCreateMemoryContainerRequest mlCreateMemoryContainerRequest = restMLCreateMemoryContainerAction.getRequest(request);

        assertNotNull(mlCreateMemoryContainerRequest);
        MLCreateMemoryContainerInput input = mlCreateMemoryContainerRequest.getMlCreateMemoryContainerInput();
        assertNotNull(input);
        assertEquals("test-memory-container", input.getName());
        assertEquals("Test memory container description", input.getDescription());
        assertNotNull(input.getMemoryStorageConfig());
        assertEquals("test-memory-index", input.getMemoryStorageConfig().getMemoryIndexName());
        assertNull(input.getTenantId()); // Multi-tenancy disabled
    }

    public void testGetRequestWithMinimalInput() throws IOException {
        String requestBody = "{\n" + "  \"name\": \"minimal-container\"\n" + "}";

        RestRequest request = createRestRequest(requestBody);
        MLCreateMemoryContainerRequest mlCreateMemoryContainerRequest = restMLCreateMemoryContainerAction.getRequest(request);

        assertNotNull(mlCreateMemoryContainerRequest);
        MLCreateMemoryContainerInput input = mlCreateMemoryContainerRequest.getMlCreateMemoryContainerInput();
        assertNotNull(input);
        assertEquals("minimal-container", input.getName());
        assertNull(input.getDescription());
        assertNull(input.getMemoryStorageConfig());
        assertNull(input.getTenantId());
    }

    public void testGetRequestWithMultiTenancyEnabled() throws IOException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        String requestBody = "{\n" +
                "  \"name\": \"tenant-container\"\n" +
                "}";

        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.TENANT_ID_HEADER, "test-tenant");

        RestRequest request = createRestRequestWithHeaders(requestBody, headers);
        MLCreateMemoryContainerRequest mlCreateMemoryContainerRequest = restMLCreateMemoryContainerAction.getRequest(request);

        assertNotNull(mlCreateMemoryContainerRequest);
        MLCreateMemoryContainerInput input = mlCreateMemoryContainerRequest.getMlCreateMemoryContainerInput();
        assertNotNull(input);
        assertEquals("tenant-container", input.getName());
        assertEquals("test-tenant", input.getTenantId()); // Should be set from header
    }

    public void testGetRequestWithNoContent() throws IOException {
        RestRequest request = createRestRequestWithoutContent();

        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> restMLCreateMemoryContainerAction.getRequest(request)
        );
        assertEquals("Request body is required", exception.getMessage());
    }

    public void testGetRequestWithInvalidJson() throws IOException {
        String invalidJson = "{ invalid json }";
        RestRequest request = createRestRequest(invalidJson);

        expectThrows(IOException.class, () -> restMLCreateMemoryContainerAction.getRequest(request));
    }

    public void testGetRequestWithEmptyJson() throws IOException {
        String emptyJson = "{}";
        RestRequest request = createRestRequest(emptyJson);

        // Should throw IllegalArgumentException because name is required
        expectThrows(IllegalArgumentException.class, () -> restMLCreateMemoryContainerAction.getRequest(request));
    }

    public void testPrepareRequest() throws Exception {
        String requestBody = "{\n" + "  \"name\": \"test-container\",\n" + "  \"description\": \"Test description\"\n" + "}";

        RestRequest request = createRestRequest(requestBody);
        restMLCreateMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLCreateMemoryContainerRequest> argumentCaptor = ArgumentCaptor.forClass(MLCreateMemoryContainerRequest.class);
        verify(client, times(1)).execute(eq(MLCreateMemoryContainerAction.INSTANCE), argumentCaptor.capture(), any());

        MLCreateMemoryContainerInput input = argumentCaptor.getValue().getMlCreateMemoryContainerInput();
        assertNotNull(input);
        assertEquals("test-container", input.getName());
        assertEquals("Test description", input.getDescription());
    }

    public void testPrepareRequestWithComplexMemoryStorageConfig() throws Exception {
        String requestBody = "{\n"
            + "  \"name\": \"complex-container\",\n"
            + "  \"description\": \"Complex container with full config\",\n"
            + "  \"memory_storage_config\": {\n"
            + "    \"memory_index_name\": \"complex-memory-index\",\n"
            + "    \"embedding_model_type\": \"SPARSE_ENCODING\",\n"
            + "    \"embedding_model_id\": \"sparse-model\",\n"
            + "    \"llm_model_id\": \"complex-llm-model\",\n"
            + "    \"max_infer_size\": 10\n"
            + "  }\n"
            + "}";

        RestRequest request = createRestRequest(requestBody);
        restMLCreateMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLCreateMemoryContainerRequest> argumentCaptor = ArgumentCaptor.forClass(MLCreateMemoryContainerRequest.class);
        verify(client, times(1)).execute(eq(MLCreateMemoryContainerAction.INSTANCE), argumentCaptor.capture(), any());

        MLCreateMemoryContainerInput input = argumentCaptor.getValue().getMlCreateMemoryContainerInput();
        assertNotNull(input);
        assertEquals("complex-container", input.getName());
        assertEquals("Complex container with full config", input.getDescription());
        assertNotNull(input.getMemoryStorageConfig());
        assertEquals("complex-memory-index", input.getMemoryStorageConfig().getMemoryIndexName());
        assertEquals("sparse-model", input.getMemoryStorageConfig().getEmbeddingModelId());
        assertEquals("complex-llm-model", input.getMemoryStorageConfig().getLlmModelId());
        assertEquals(Integer.valueOf(10), input.getMemoryStorageConfig().getMaxInferSize());
    }

    public void testPrepareRequestWithInvalidContent() throws Exception {
        RestRequest request = createRestRequestWithoutContent();

        expectThrows(IllegalArgumentException.class, () -> { restMLCreateMemoryContainerAction.handleRequest(request, channel, client); });
    }

    public void testGetRequestWithSpecialCharacters() throws IOException {
        String requestBody = "{\n"
            + "  \"name\": \"container-with-special-chars-!@#$%\",\n"
            + "  \"description\": \"Description with\\nnewlines and\\ttabs and special chars: !@#$%^&*()\"\n"
            + "}";

        RestRequest request = createRestRequest(requestBody);
        MLCreateMemoryContainerRequest mlCreateMemoryContainerRequest = restMLCreateMemoryContainerAction.getRequest(request);

        assertNotNull(mlCreateMemoryContainerRequest);
        MLCreateMemoryContainerInput input = mlCreateMemoryContainerRequest.getMlCreateMemoryContainerInput();
        assertNotNull(input);
        assertEquals("container-with-special-chars-!@#$%", input.getName());
        assertEquals("Description with\nnewlines and\ttabs and special chars: !@#$%^&*()", input.getDescription());
    }

    public void testGetRequestWithLongValues() throws IOException {
        String longName = "very-long-container-name-that-exceeds-normal-length-expectations-and-contains-many-segments";
        String longDescription =
            "This is a very long description that contains multiple sentences and should test the handling of large text values in the memory container creation process. It includes various punctuation marks, numbers like 12345, and special characters like !@#$%^&*().";

        String requestBody = String
            .format("{\n" + "  \"name\": \"%s\",\n" + "  \"description\": \"%s\"\n" + "}", longName, longDescription);

        RestRequest request = createRestRequest(requestBody);
        MLCreateMemoryContainerRequest mlCreateMemoryContainerRequest = restMLCreateMemoryContainerAction.getRequest(request);

        assertNotNull(mlCreateMemoryContainerRequest);
        MLCreateMemoryContainerInput input = mlCreateMemoryContainerRequest.getMlCreateMemoryContainerInput();
        assertNotNull(input);
        assertEquals(longName, input.getName());
        assertEquals(longDescription, input.getDescription());
    }

    public void testGetRequestWithUnknownFields() throws IOException {
        String requestBody = "{\n"
            + "  \"name\": \"test-container\",\n"
            + "  \"description\": \"Test description\",\n"
            + "  \"unknown_field\": \"unknown_value\",\n"
            + "  \"another_unknown\": 123\n"
            + "}";

        RestRequest request = createRestRequest(requestBody);
        MLCreateMemoryContainerRequest mlCreateMemoryContainerRequest = restMLCreateMemoryContainerAction.getRequest(request);

        assertNotNull(mlCreateMemoryContainerRequest);
        MLCreateMemoryContainerInput input = mlCreateMemoryContainerRequest.getMlCreateMemoryContainerInput();
        assertNotNull(input);
        assertEquals("test-container", input.getName());
        assertEquals("Test description", input.getDescription());
        // Unknown fields should be ignored
    }

    public void testMultiTenancyDisabledTenantIdIsNull() throws IOException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        String requestBody = "{\n" +
                "  \"name\": \"no-tenant-container\"\n" +
                "}";

        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.TENANT_ID_HEADER, "should-be-ignored");

        RestRequest request = createRestRequestWithHeaders(requestBody, headers);
        MLCreateMemoryContainerRequest mlCreateMemoryContainerRequest = restMLCreateMemoryContainerAction.getRequest(request);

        assertNotNull(mlCreateMemoryContainerRequest);
        MLCreateMemoryContainerInput input = mlCreateMemoryContainerRequest.getMlCreateMemoryContainerInput();
        assertNotNull(input);
        assertEquals("no-tenant-container", input.getName());
        assertNull(input.getTenantId()); // Should be null when multi-tenancy is disabled
    }

    public void testActionNameConstant() {
        // Test that the action name constant is correctly defined
        assertEquals("ml_create_memory_container_action", restMLCreateMemoryContainerAction.getName());
    }

    public void testRoutePathConstant() {
        List<RestHandler.Route> routes = restMLCreateMemoryContainerAction.routes();
        RestHandler.Route route = routes.get(0);

        // Verify the route path matches the expected pattern
        assertTrue(route.getPath().contains("/_plugins/_ml/memory_containers/_create"));
        assertEquals(RestRequest.Method.POST, route.getMethod());
    }

    // Helper methods for creating test requests

    private RestRequest createRestRequest(String content) {
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath("/_plugins/_ml/memory_containers/_create")
            .withContent(new BytesArray(content), XContentType.JSON)
            .build();
    }

    private RestRequest createRestRequestWithHeaders(String content, Map<String, String> headers) {
        Map<String, List<String>> headerMap = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            headerMap.put(entry.getKey(), Collections.singletonList(entry.getValue()));
        }

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath("/_plugins/_ml/memory_containers/_create")
            .withHeaders(headerMap)
            .withContent(new BytesArray(content), XContentType.JSON)
            .build();
    }

    private RestRequest createRestRequestWithoutContent() {
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath("/_plugins/_ml/memory_containers/_create")
            .build();
    }

    public void testPrepareRequestWithAgenticMemoryDisabled() throws IOException {
        // Disable agentic memory feature
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        String requestContent = "{\n"
                + "  \"name\": \"test-memory-container\",\n"
                + "  \"description\": \"Test memory container description\"\n"
                + "}";

        RestRequest request = createRestRequest(requestContent);

        // Expect OpenSearchStatusException when feature is disabled
        thrown.expect(OpenSearchStatusException.class);
        thrown.expectMessage("The Agentic Memory APIs are not enabled. To enable, please update the setting plugins.ml_commons.agentic_memory_enabled");

        restMLCreateMemoryContainerAction.prepareRequest(request, client);
    }
}
