/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.memorycontainer.MLMemory;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetMemoryRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetMemoryResponse;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportGetMemoryActionTests extends OpenSearchTestCase {

    private static final String MEMORY_CONTAINER_ID = "test-memory-container-id";
    private static final String MEMORY_ID = "test-memory-id";
    private static final String MEMORY_INDEX_NAME = "ml-static-memory-test-container";
    private static final String USER_NAME = "test-user";
    private static final String OWNER_NAME = "owner-user";

    private TransportGetMemoryAction action;

    @Mock
    private Client client;
    @Mock
    private NamedXContentRegistry xContentRegistry;
    @Mock
    private MemoryContainerHelper memoryContainerHelper;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private TransportService transportService;
    @Mock
    private Task task;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private ActionListener<MLGetMemoryResponse> actionListener;

    private ActionRequest actionRequest;
    private User testUser;
    private User ownerUser;
    private User adminUser;
    private ThreadContext threadContext;
    private MLMemoryContainer testMemoryContainer;
    private MLMemory testMemory;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        // Setup test users using User.parse() with correct format: name|backend_roles|roles
        testUser = User.parse(USER_NAME + "||");  // No backend roles or roles
        ownerUser = User.parse(OWNER_NAME + "||");  // No backend roles or roles
        adminUser = User.parse("admin-user||all_access");  // Has all_access role

        // Setup thread context
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Setup action request
        actionRequest = mock(ActionRequest.class);

        // Setup test memory container
        MemoryStorageConfig storageConfig = MemoryStorageConfig
            .builder()
            .memoryIndexName(MEMORY_INDEX_NAME)
            .semanticStorageEnabled(false)
            .build();

        testMemoryContainer = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("Test memory container")
            .owner(ownerUser)
            .tenantId("test-tenant")
            .createdTime(Instant.now())
            .lastUpdatedTime(Instant.now())
            .memoryStorageConfig(storageConfig)
            .build();

        // Setup test memory
        testMemory = MLMemory
            .builder()
            .sessionId("test-session")
            .memory("Test memory content")
            .memoryType(MemoryType.RAW_MESSAGE)
            .userId("test-user")
            .createdTime(Instant.now())
            .lastUpdatedTime(Instant.now())
            .build();

        // Create action
        action = new TransportGetMemoryAction(
            transportService,
            actionFilters,
            client,
            xContentRegistry,
            memoryContainerHelper,
            mlFeatureEnabledSetting
        );

        // Setup feature flag to be enabled by default for tests
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        // Setup user context
        threadContext.putTransient(org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, OWNER_NAME + "||");

        when(memoryContainerHelper.checkMemoryContainerAccess(any(User.class), any(MLMemoryContainer.class))).thenReturn(true);

        // Setup memory index validation to pass
        when(memoryContainerHelper.validateMemoryIndexExists(any(MLMemoryContainer.class), any(String.class), any(ActionListener.class)))
            .thenReturn(true);

        // Setup memory index name
        when(memoryContainerHelper.getMemoryIndexName(testMemoryContainer)).thenReturn(MEMORY_INDEX_NAME);
    }

    public void testConstructor() {
        assertNotNull(action);
    }

    public void testDoExecuteSuccess() {
        // Setup request
        MLGetMemoryRequest getRequest = new MLGetMemoryRequest(MEMORY_CONTAINER_ID, MEMORY_ID);

        // Setup memory container helper to return container
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(testMemoryContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(String.class), any(ActionListener.class));

        // Setup client to return successful response
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.get.GetResponse> listener = invocation.getArgument(1);
            org.opensearch.action.get.GetResponse getResponse = mock(org.opensearch.action.get.GetResponse.class);
            when(getResponse.isExists()).thenReturn(true);
            when(getResponse.getSourceAsString()).thenReturn(createMemoryJson());
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(org.opensearch.action.get.GetRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, getRequest, actionListener);

        verify(memoryContainerHelper).getMemoryContainer(any(String.class), any(ActionListener.class));
        verify(memoryContainerHelper).checkMemoryContainerAccess(any(User.class), any(MLMemoryContainer.class));
        verify(memoryContainerHelper).validateMemoryIndexExists(any(MLMemoryContainer.class), any(String.class), any(ActionListener.class));
        verify(memoryContainerHelper).getMemoryIndexName(testMemoryContainer);
        verify(client).get(any(org.opensearch.action.get.GetRequest.class), any(ActionListener.class));

        // Capture and verify the success response content
        ArgumentCaptor<MLGetMemoryResponse> responseCaptor = forClass(MLGetMemoryResponse.class);
        verify(actionListener).onResponse(responseCaptor.capture());

        MLGetMemoryResponse capturedResponse = responseCaptor.getValue();
        assertNotNull(capturedResponse);

        // Verify the memory content in the response matches the JSON that was returned
        MLMemory returnedMemory = capturedResponse.getMlMemory();
        assertNotNull(returnedMemory);

        // Get the expected JSON content that was actually returned by the mock
        String expectedJson = createMemoryJson();

        // Verify the memory content matches what was in the JSON
        assertEquals("test-session", returnedMemory.getSessionId());
        assertEquals("Test memory content", returnedMemory.getMemory());
        assertEquals(MemoryType.RAW_MESSAGE, returnedMemory.getMemoryType());
        assertEquals("test-user", returnedMemory.getUserId());
        assertNotNull(returnedMemory.getCreatedTime());
        assertNotNull(returnedMemory.getLastUpdatedTime());
        assertTrue(expectedJson.contains("\"session_id\":\"test-session\""));
        assertTrue(expectedJson.contains("\"memory\":\"Test memory content\""));
        assertTrue(expectedJson.contains("\"memory_type\":\"RAW_MESSAGE\""));
        assertTrue(expectedJson.contains("\"user_id\":\"test-user\""));
    }

    public void testDoExecuteWithUnauthorizedUser() {
        // Setup request
        MLGetMemoryRequest getRequest = new MLGetMemoryRequest(MEMORY_CONTAINER_ID, MEMORY_ID);

        // Setup memory container helper to return container
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(testMemoryContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(String.class), any(ActionListener.class));

        // Setup access control to deny access
        when(memoryContainerHelper.checkMemoryContainerAccess(any(User.class), any(MLMemoryContainer.class))).thenReturn(false);

        // Execute
        action.doExecute(task, getRequest, actionListener);

        // Verify memory container helper was called
        verify(memoryContainerHelper).getMemoryContainer(any(String.class), any(ActionListener.class));

        // Verify access control was checked
        verify(memoryContainerHelper).checkMemoryContainerAccess(any(User.class), any(MLMemoryContainer.class));

        // Capture the actual exception that was passed to the action listener
        ArgumentCaptor<Exception> exceptionCaptor = forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());

        // Verify the exact error message and status
        Exception capturedException = exceptionCaptor.getValue();
        assertTrue(capturedException instanceof OpenSearchStatusException);
        OpenSearchStatusException statusException = (OpenSearchStatusException) capturedException;
        assertEquals("User doesn't have permissions to get memories in this container", statusException.getMessage());
        assertEquals(RestStatus.FORBIDDEN, statusException.status());
    }

    public void testDoExecuteWithParsingException() {
        // Setup request
        MLGetMemoryRequest getRequest = new MLGetMemoryRequest(MEMORY_CONTAINER_ID, MEMORY_ID);

        // Setup memory container helper to return container
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(testMemoryContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(String.class), any(ActionListener.class));

        when(memoryContainerHelper.checkMemoryContainerAccess(any(User.class), any(MLMemoryContainer.class))).thenReturn(true);

        // Setup memory index validation to pass
        when(memoryContainerHelper.validateMemoryIndexExists(any(MLMemoryContainer.class), any(String.class), any(ActionListener.class)))
            .thenReturn(true);

        // Setup memory index name
        when(memoryContainerHelper.getMemoryIndexName(testMemoryContainer)).thenReturn(MEMORY_INDEX_NAME);

        // Setup client to return response with invalid JSON
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.get.GetResponse> listener = invocation.getArgument(1);
            org.opensearch.action.get.GetResponse getResponse = mock(org.opensearch.action.get.GetResponse.class);
            when(getResponse.isExists()).thenReturn(true);
            when(getResponse.getSourceAsString()).thenReturn("invalid-json-content");
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(org.opensearch.action.get.GetRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, getRequest, actionListener);

        // Verify memory container helper was called
        verify(memoryContainerHelper).getMemoryContainer(any(String.class), any(ActionListener.class));

        // Verify access control was checked
        verify(memoryContainerHelper).checkMemoryContainerAccess(any(User.class), any(MLMemoryContainer.class));

        // Verify memory index validation was called
        verify(memoryContainerHelper).validateMemoryIndexExists(any(MLMemoryContainer.class), any(String.class), any(ActionListener.class));

        // Verify memory index name was retrieved
        verify(memoryContainerHelper).getMemoryIndexName(testMemoryContainer);

        // Verify client.get was called
        verify(client).get(any(org.opensearch.action.get.GetRequest.class), any(ActionListener.class));

        // Verify failure response due to parsing error
        verify(actionListener).onFailure(any(Exception.class));
    }

    @Test
    public void testDoExecuteWithNoResponse() {
        // Setup request
        MLGetMemoryRequest getRequest = new MLGetMemoryRequest(MEMORY_CONTAINER_ID, MEMORY_ID);

        // Setup memory container helper to return container
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(testMemoryContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(String.class), any(ActionListener.class));

        // Setup client to return response with isExists() as false
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.get.GetResponse> listener = invocation.getArgument(1);
            org.opensearch.action.get.GetResponse getResponse = mock(org.opensearch.action.get.GetResponse.class);
            when(getResponse.isExists()).thenReturn(false);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(org.opensearch.action.get.GetRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, getRequest, actionListener);

        verify(memoryContainerHelper).getMemoryContainer(any(String.class), any(ActionListener.class));
        verify(memoryContainerHelper).checkMemoryContainerAccess(any(User.class), any(MLMemoryContainer.class));
        verify(memoryContainerHelper).validateMemoryIndexExists(any(MLMemoryContainer.class), any(String.class), any(ActionListener.class));
        verify(memoryContainerHelper).getMemoryIndexName(testMemoryContainer);
        verify(client).get(any(org.opensearch.action.get.GetRequest.class), any(ActionListener.class));

        // Capture and verify the failure response
        ArgumentCaptor<Exception> exceptionCaptor = forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());

        // Verify the exact error message and status
        Exception capturedException = exceptionCaptor.getValue();
        assertTrue(capturedException instanceof OpenSearchStatusException);
        OpenSearchStatusException statusException = (OpenSearchStatusException) capturedException;
        assertEquals("Memory not found with id: " + MEMORY_ID, statusException.getMessage());
        assertEquals(RestStatus.NOT_FOUND, statusException.status());
    }

    @Test
    public void testDoExecuteWithClientGetFailure() {
        // Setup request
        MLGetMemoryRequest getRequest = new MLGetMemoryRequest(MEMORY_CONTAINER_ID, MEMORY_ID);

        // Setup memory container helper to return container
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(testMemoryContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(String.class), any(ActionListener.class));

        // Setup client to throw an exception when get() is called
        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.get.GetResponse> listener = invocation.getArgument(1);
            // Simulate a client failure by calling onFailure directly
            listener.onFailure(new RuntimeException("Client get operation failed"));
            return null;
        }).when(client).get(any(org.opensearch.action.get.GetRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, getRequest, actionListener);

        verify(memoryContainerHelper).getMemoryContainer(any(String.class), any(ActionListener.class));
        verify(memoryContainerHelper).checkMemoryContainerAccess(any(User.class), any(MLMemoryContainer.class));
        verify(memoryContainerHelper).validateMemoryIndexExists(any(MLMemoryContainer.class), any(String.class), any(ActionListener.class));
        verify(memoryContainerHelper).getMemoryIndexName(testMemoryContainer);
        verify(client).get(any(org.opensearch.action.get.GetRequest.class), any(ActionListener.class));

        ArgumentCaptor<Exception> exceptionCaptor = forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());

        // Verify the exact error message
        Exception capturedException = exceptionCaptor.getValue();
        assertTrue(capturedException instanceof RuntimeException);
        assertEquals("Client get operation failed", capturedException.getMessage());
    }

    @Test
    public void testDoExecuteWithProcessResponseException() {
        // Setup request
        MLGetMemoryRequest getRequest = new MLGetMemoryRequest(MEMORY_CONTAINER_ID, MEMORY_ID);

        // Setup memory container helper to return container
        doAnswer(invocation -> {
            ActionListener<MLMemoryContainer> listener = invocation.getArgument(1);
            listener.onResponse(testMemoryContainer);
            return null;
        }).when(memoryContainerHelper).getMemoryContainer(any(String.class), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.get.GetResponse> listener = invocation.getArgument(1);
            org.opensearch.action.get.GetResponse getResponse = mock(org.opensearch.action.get.GetResponse.class);
            when(getResponse.isExists()).thenThrow(new RuntimeException("Outer try block failure"));
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(org.opensearch.action.get.GetRequest.class), any(ActionListener.class));

        action.doExecute(task, getRequest, actionListener);

        verify(memoryContainerHelper).getMemoryContainer(any(String.class), any(ActionListener.class));
        verify(memoryContainerHelper).checkMemoryContainerAccess(any(User.class), any(MLMemoryContainer.class));
        verify(memoryContainerHelper).validateMemoryIndexExists(any(MLMemoryContainer.class), any(String.class), any(ActionListener.class));
        verify(memoryContainerHelper).getMemoryIndexName(testMemoryContainer);
        verify(client).get(any(org.opensearch.action.get.GetRequest.class), any(ActionListener.class));
        ArgumentCaptor<Exception> exceptionCaptor = forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());

        // Verify the exact error message
        Exception capturedException = exceptionCaptor.getValue();
        assertTrue(capturedException instanceof RuntimeException);
        assertEquals("Outer try block failure", capturedException.getMessage());
    }

    // ========== Helper Methods ==========

    private String createMemoryJson() {
        // Use epoch timestamps instead of ISO format to avoid parsing errors
        long currentTimeEpoch = System.currentTimeMillis();
        return "{"
            + "\"session_id\":\"test-session\","
            + "\"memory\":\"Test memory content\","
            + "\"memory_type\":\"RAW_MESSAGE\","
            + "\"user_id\":\"test-user\","
            + "\"created_time\":"
            + currentTimeEpoch
            + ","
            + "\"last_updated_time\":"
            + currentTimeEpoch
            + "}";
    }

    @Test
    public void testDoExecuteWithFeatureDisabled() {
        // Setup feature flag to be disabled
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);
        
        // Setup request
        MLGetMemoryRequest getRequest = new MLGetMemoryRequest(MEMORY_CONTAINER_ID, MEMORY_ID);
        
        // Execute
        action.doExecute(task, getRequest, actionListener);
        
        // Verify that the action listener received a failure with the expected message
        ArgumentCaptor<Exception> exceptionCaptor = forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());
        
        Exception capturedException = exceptionCaptor.getValue();
        assertTrue(capturedException instanceof OpenSearchStatusException);
        OpenSearchStatusException statusException = (OpenSearchStatusException) capturedException;
        assertEquals(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, statusException.getMessage());
        assertEquals(RestStatus.FORBIDDEN, statusException.status());
        
        // Verify that no other operations were attempted
        verify(memoryContainerHelper, never()).getMemoryContainer(any(String.class), any(ActionListener.class));
    }
}
