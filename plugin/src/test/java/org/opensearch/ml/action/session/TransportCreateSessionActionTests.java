/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.session.MLCreateSessionInput;
import org.opensearch.ml.common.transport.session.MLCreateSessionRequest;
import org.opensearch.ml.common.transport.session.MLCreateSessionResponse;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportCreateSessionActionTests extends OpenSearchTestCase {

    @Mock
    private Client client;

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private MemoryContainerHelper memoryContainerHelper;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Task task;

    @Mock
    private ActionListener<MLCreateSessionResponse> actionListener;

    private TransportCreateSessionAction transportCreateSessionAction;
    private MLCreateSessionRequest request;
    private MLCreateSessionInput input;
    private MLMemoryContainer memoryContainer;
    private User user;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        // Setup thread context
        Settings settings = Settings.builder().build();
        ThreadContext threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Create test input
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "1.0");
        metadata.put("type", "conversation");

        Map<String, Object> agents = new HashMap<>();
        agents.put("agent1", "assistant");

        input = MLCreateSessionInput
            .builder()
            .sessionId("session-123")
            .ownerId("owner-456")
            .tenantId("tenant-123")
            .summary("Test session summary")
            .metadata(metadata)
            .agents(agents)
            .tenantId("tenant-789")
            .memoryContainerId("memory-container-abc")
            .build();

        request = MLCreateSessionRequest.builder().mlCreateSessionInput(input).build();

        // Create test memory container
        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .indexPrefix("test-memory")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("test-embedding-model")
            .dimension(768)
            .build();

        memoryContainer = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("Test memory container")
            .configuration(config)
            .build();

        // Create test user
        user = new User(
            "test-user",
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList(),
            java.util.Collections.emptyMap()
        );

        transportCreateSessionAction = new TransportCreateSessionAction(
            transportService,
            actionFilters,
            client,
            mlFeatureEnabledSetting,
            memoryContainerHelper
        );
    }

    @Test
    public void testDoExecute_Success() {
        // Setup mocks
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Mock memory container helper
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(memoryContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("memory-container-abc"), anyString(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        when(memoryContainerHelper.getOwnerId(any())).thenReturn("owner-456");

        // Mock index response
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("generated-session-id");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(), any(), any());

        // Execute
        transportCreateSessionAction.doExecute(task, request, actionListener);

        // Verify
        ArgumentCaptor<MLCreateSessionResponse> responseCaptor = ArgumentCaptor.forClass(MLCreateSessionResponse.class);
        verify(actionListener).onResponse(responseCaptor.capture());

        MLCreateSessionResponse response = responseCaptor.getValue();
        assertEquals("generated-session-id", response.getSessionId());
        assertEquals("created", response.getStatus());
    }

    @Test
    public void testDoExecute_AgenticMemoryDisabled() {
        // Setup mocks
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        // Execute
        transportCreateSessionAction.doExecute(task, request, actionListener);

        // Verify
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) exception).status());
    }

    @Test
    public void testDoExecute_TenantValidation() {
        // Setup mocks
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        // Create input with tenant ID
        MLCreateSessionInput tenantInput = MLCreateSessionInput.builder()
            .sessionId("session-123")
            .tenantId("test-tenant")
            .memoryContainerId("memory-container-abc")
            .build();

        MLCreateSessionRequest tenantRequest = MLCreateSessionRequest.builder()
            .mlCreateSessionInput(tenantInput)
            .build();

        // Mock memory container helper to simulate successful flow
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(memoryContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("memory-container-abc"), anyString(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        when(memoryContainerHelper.getOwnerId(any())).thenReturn("owner-456");

        // Mock index response
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("session-123");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(), any(), any());

        // Execute
        transportCreateSessionAction.doExecute(task, tenantRequest, actionListener);

        // Verify that the flow continues (tenant validation passes in this case)
        // The exact behavior depends on TenantAwareHelper implementation
        verify(memoryContainerHelper).getMemoryContainer(eq("memory-container-abc"), anyString(), any());
    }

    @Test
    public void testDoExecute_BlankMemoryContainerId() {
        // Setup mocks
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Create input with blank memory container ID
        MLCreateSessionInput blankInput = MLCreateSessionInput.builder()
            .sessionId("session-123")
                .tenantId("tenant-123")
            .memoryContainerId("")
            .build();

        MLCreateSessionRequest blankRequest = MLCreateSessionRequest.builder()
            .mlCreateSessionInput(blankInput)
            .build();

        // Execute
        transportCreateSessionAction.doExecute(task, blankRequest, actionListener);

        // Verify
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof IllegalArgumentException);
        assertEquals("Memory container ID is required", exception.getMessage());
    }

    @Test
    public void testDoExecute_NullMemoryContainerId() {
        // Setup mocks
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Create input with null memory container ID
        MLCreateSessionInput nullInput = MLCreateSessionInput.builder()
            .sessionId("session-123")
                .tenantId("tenant-123")
            .memoryContainerId(null)
            .build();

        MLCreateSessionRequest nullRequest = MLCreateSessionRequest.builder()
            .mlCreateSessionInput(nullInput)
            .build();

        // Execute
        transportCreateSessionAction.doExecute(task, nullRequest, actionListener);

        // Verify
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof IllegalArgumentException);
        assertEquals("Memory container ID is required", exception.getMessage());
    }

    @Test
    public void testDoExecute_MemoryContainerNotFound() {
        // Setup mocks
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Mock memory container helper to return error
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onFailure(new OpenSearchStatusException("Memory container not found", RestStatus.NOT_FOUND));
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("memory-container-abc"), anyString(), any());

        // Execute
        transportCreateSessionAction.doExecute(task, request, actionListener);

        // Verify
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals("Memory container not found", exception.getMessage());
    }

    @Test
    public void testDoExecute_AccessDenied() {
        // Setup mocks
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Mock memory container helper
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(memoryContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("memory-container-abc"), anyString(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(false);

        // Execute
        transportCreateSessionAction.doExecute(task, request, actionListener);

        // Verify
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals("User doesn't have permissions to add memory to this container", exception.getMessage());
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) exception).status());
    }

    @Test
    public void testDoExecute_IndexingFailure() {
        // Setup mocks
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Mock memory container helper
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(memoryContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("memory-container-abc"), anyString(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        when(memoryContainerHelper.getOwnerId(any())).thenReturn("owner-456");

        // Mock indexing failure
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Indexing failed"));
            return null;
        }).when(memoryContainerHelper).indexData(any(), any(), any());

        // Execute
        transportCreateSessionAction.doExecute(task, request, actionListener);

        // Verify
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) exception).status());
        assertTrue(exception.getMessage().contains("Internal server error"));
    }

    @Test
    public void testDoExecute_WithCustomSessionId() {
        // Setup mocks
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Create input with custom session ID
        MLCreateSessionInput customInput = MLCreateSessionInput.builder()
            .sessionId("custom-session-id")
            .ownerId("owner-456")
            .tenantId("tenant-123")
            .summary("Test session with custom ID")
            .memoryContainerId("memory-container-abc")
            .build();

        MLCreateSessionRequest customRequest = MLCreateSessionRequest.builder()
            .mlCreateSessionInput(customInput)
            .build();

        // Mock memory container helper
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(memoryContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("memory-container-abc"), anyString(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        when(memoryContainerHelper.getOwnerId(any())).thenReturn("owner-456");

        // Mock index response with custom ID
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("custom-session-id");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(), any(), any());

        // Execute
        transportCreateSessionAction.doExecute(task, customRequest, actionListener);

        // Verify
        ArgumentCaptor<MLCreateSessionResponse> responseCaptor = ArgumentCaptor.forClass(MLCreateSessionResponse.class);
        verify(actionListener).onResponse(responseCaptor.capture());

        MLCreateSessionResponse response = responseCaptor.getValue();
        assertEquals("custom-session-id", response.getSessionId());
        assertEquals("created", response.getStatus());

        // Verify that IndexRequest was created with the custom ID
        ArgumentCaptor<IndexRequest> indexRequestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(memoryContainerHelper).indexData(any(), indexRequestCaptor.capture(), any());

        IndexRequest indexRequest = indexRequestCaptor.getValue();
        assertEquals("custom-session-id", indexRequest.id());
    }

    @Test
    public void testDoExecute_WithoutCustomSessionId() {
        // Setup mocks
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Create input without session ID
        MLCreateSessionInput noIdInput = MLCreateSessionInput.builder()
            .ownerId("owner-456")
                .tenantId("tenant-123")
            .summary("Test session without ID")
            .memoryContainerId("memory-container-abc")
            .build();

        MLCreateSessionRequest noIdRequest = MLCreateSessionRequest.builder()
            .mlCreateSessionInput(noIdInput)
            .build();

        // Mock memory container helper
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(memoryContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("memory-container-abc"), anyString(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        when(memoryContainerHelper.getOwnerId(any())).thenReturn("owner-456");

        // Mock index response with generated ID
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("auto-generated-id");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(), any(), any());

        // Execute
        transportCreateSessionAction.doExecute(task, noIdRequest, actionListener);

        // Verify
        ArgumentCaptor<MLCreateSessionResponse> responseCaptor = ArgumentCaptor.forClass(MLCreateSessionResponse.class);
        verify(actionListener).onResponse(responseCaptor.capture());

        MLCreateSessionResponse response = responseCaptor.getValue();
        assertEquals("auto-generated-id", response.getSessionId());
        assertEquals("created", response.getStatus());

        // Verify that IndexRequest was created without ID (for auto-generation)
        ArgumentCaptor<IndexRequest> indexRequestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(memoryContainerHelper).indexData(any(), indexRequestCaptor.capture(), any());

        IndexRequest indexRequest = indexRequestCaptor.getValue();
        assertNull(indexRequest.id());
    }

    @Test
    public void testDoExecute_WithBlankSessionId() {
        // Setup mocks
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Create input with blank session ID
        MLCreateSessionInput blankIdInput = MLCreateSessionInput.builder()
            .sessionId("   ") // Blank session ID
            .ownerId("owner-456")
                .tenantId("tenant-123")
            .summary("Test session with blank ID")
            .memoryContainerId("memory-container-abc")
            .build();

        MLCreateSessionRequest blankIdRequest = MLCreateSessionRequest.builder()
            .mlCreateSessionInput(blankIdInput)
            .build();

        // Mock memory container helper
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(memoryContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("memory-container-abc"), anyString(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        when(memoryContainerHelper.getOwnerId(any())).thenReturn("owner-456");

        // Mock index response
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("auto-generated-id");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(), any(), any());

        // Execute
        transportCreateSessionAction.doExecute(task, blankIdRequest, actionListener);

        // Verify
        ArgumentCaptor<MLCreateSessionResponse> responseCaptor = ArgumentCaptor.forClass(MLCreateSessionResponse.class);
        verify(actionListener).onResponse(responseCaptor.capture());

        MLCreateSessionResponse response = responseCaptor.getValue();
        assertEquals("auto-generated-id", response.getSessionId());
        assertEquals("created", response.getStatus());

        // Verify that IndexRequest was created without ID (blank is treated as no ID)
        ArgumentCaptor<IndexRequest> indexRequestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(memoryContainerHelper).indexData(any(), indexRequestCaptor.capture(), any());

        IndexRequest indexRequest = indexRequestCaptor.getValue();
        assertNull(indexRequest.id());
    }

    @Test
    public void testConstructor() {
        // Test that constructor properly initializes the action
        assertNotNull(transportCreateSessionAction);

        // Verify that the action name is set correctly through the parent constructor
        // This is implicitly tested through the successful creation of the action
    }

    @Test
    public void testDoExecute_ComplexSessionData() {
        // Setup mocks
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Create input with complex data
        Map<String, Object> complexMetadata = new HashMap<>();
        complexMetadata.put("nested", Map.of("key1", "value1", "key2", 42));
        complexMetadata.put("array", java.util.Arrays.asList("item1", "item2", "item3"));

        Map<String, Object> complexAgents = new HashMap<>();
        complexAgents.put("agent_config", Map.of("timeout", 30, "retries", 3));

        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("sessionLength", 120);
        additionalInfo.put("language", "en");

        MLCreateSessionInput complexInput = MLCreateSessionInput.builder()
            .sessionId("complex-session")
            .ownerId("owner-456")
                .tenantId("tenant-123")
            .summary("Complex session with nested data")
            .metadata(complexMetadata)
            .agents(complexAgents)
            .additionalInfo(additionalInfo)
            .memoryContainerId("memory-container-abc")
            .build();

        MLCreateSessionRequest complexRequest = MLCreateSessionRequest.builder()
            .mlCreateSessionInput(complexInput)
            .build();

        // Mock memory container helper
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(2);
            listener.onResponse(memoryContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(eq("memory-container-abc"), anyString(), any());

        when(memoryContainerHelper.checkMemoryContainerAccess(any(), any())).thenReturn(true);
        when(memoryContainerHelper.getOwnerId(any())).thenReturn("owner-456");

        // Mock index response
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("complex-session");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(2);
            listener.onResponse(indexResponse);
            return null;
        }).when(memoryContainerHelper).indexData(any(), any(), any());

        // Execute
        transportCreateSessionAction.doExecute(task, complexRequest, actionListener);

        // Verify
        ArgumentCaptor<MLCreateSessionResponse> responseCaptor = ArgumentCaptor.forClass(MLCreateSessionResponse.class);
        verify(actionListener).onResponse(responseCaptor.capture());

        MLCreateSessionResponse response = responseCaptor.getValue();
        assertEquals("complex-session", response.getSessionId());
        assertEquals("created", response.getStatus());
    }
}
