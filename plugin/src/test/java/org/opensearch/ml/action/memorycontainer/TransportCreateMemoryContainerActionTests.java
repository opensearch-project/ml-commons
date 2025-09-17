/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerInput;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerRequest;
import org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.PutDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.IndicesAdminClient;

public class TransportCreateMemoryContainerActionTests extends OpenSearchTestCase {

    private static final String MEMORY_CONTAINER_ID = "test-memory-container-id";
    private static final String TENANT_ID = "test-tenant";
    private static final String USER_NAME = "test-user";

    private TransportCreateMemoryContainerAction action;

    @Mock
    private MLIndicesHandler mlIndicesHandler;
    @Mock
    private Client client;
    @Mock
    private SdkClient sdkClient;
    @Mock
    private ClusterService clusterService;
    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Mock
    private MLModelManager mlModelManager;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private TransportService transportService;
    @Mock
    private Task task;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private AdminClient adminClient;
    @Mock
    private IndicesAdminClient indicesAdminClient;

    @Mock
    private ActionListener<MLCreateMemoryContainerResponse> actionListener;

    @Captor
    private ArgumentCaptor<MLCreateMemoryContainerResponse> responseCaptor;
    @Captor
    private ArgumentCaptor<Exception> exceptionCaptor;

    private MLCreateMemoryContainerRequest request;
    private MLCreateMemoryContainerInput input;
    private MemoryStorageConfig memoryStorageConfig;
    private User testUser;
    private ThreadContext threadContext;
    private IndexResponse indexResponse;
    private PutDataObjectResponse putDataObjectResponse;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        // Setup test user
        testUser = new User(USER_NAME, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        // Setup thread context
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Setup admin client chain
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);

        // Setup memory storage config
        memoryStorageConfig = MemoryStorageConfig
            .builder()
            .memoryIndexName("test-memory-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("test-embedding-model")
            .llmModelId("test-llm-model")
            .dimension(768)
            .maxInferSize(5)
            .build();

        // Setup input
        input = MLCreateMemoryContainerInput
            .builder()
            .name("test-memory-container")
            .description("Test memory container description")
            .memoryStorageConfig(memoryStorageConfig)
            .tenantId(TENANT_ID)
            .build();

        // Setup request
        request = new MLCreateMemoryContainerRequest(input);

        // Setup index response (create real instance, don't mock methods)
        indexResponse = new IndexResponse(new ShardId(ML_MEMORY_CONTAINER_INDEX, "_na_", 0), MEMORY_CONTAINER_ID, 1, 0, 2, true);

        // Setup put data object response
        putDataObjectResponse = mock(PutDataObjectResponse.class);
        when(putDataObjectResponse.indexResponse()).thenReturn(indexResponse);

        // Setup ML feature settings
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true); // Enable by default for tests

        // Create action
        action = new TransportCreateMemoryContainerAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            clusterService,
            mlIndicesHandler,
            connectorAccessControlHelper,
            mlFeatureEnabledSetting,
            mlModelManager
        );
    }

    public void testConstructor() {
        assertNotNull(action);
    }

    public void testDoExecuteSuccess() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock successful memory container indexing
        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        // Mock successful memory data index creation - use doAnswer to avoid mocking final methods
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            // Create a real CreateIndexResponse that returns true for isAcknowledged
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify success response
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());
    }

    public void testDoExecuteWithMinimalInput() throws InterruptedException {
        // Create minimal input (no memory storage config)
        MLCreateMemoryContainerInput minimalInput = MLCreateMemoryContainerInput
            .builder()
            .name("minimal-container")
            .tenantId(TENANT_ID)
            .build();
        MLCreateMemoryContainerRequest minimalRequest = new MLCreateMemoryContainerRequest(minimalInput);

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock successful memory container indexing
        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        // Mock successful memory data index creation
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, minimalRequest, actionListener);

        // Verify success response
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());
    }

    public void testDoExecuteWithInvalidLLMModel() throws InterruptedException {
        // Mock invalid LLM model (not REMOTE)
        MLModel invalidLlmModel = mock(MLModel.class);
        when(invalidLlmModel.getAlgorithm()).thenReturn(FunctionName.KMEANS);

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(invalidLlmModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception instanceof IllegalArgumentException);
        assertTrue(exception.getMessage().contains("LLM model must be a REMOTE model"));
    }

    public void testDoExecuteWithInvalidEmbeddingModel() throws InterruptedException {
        // Mock valid LLM model
        MLModel llmModel = mock(MLModel.class);
        when(llmModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(llmModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        // Mock invalid embedding model (wrong type)
        MLModel invalidEmbeddingModel = mock(MLModel.class);
        when(invalidEmbeddingModel.getAlgorithm()).thenReturn(FunctionName.KMEANS);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(invalidEmbeddingModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-embedding-model"), any());

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception instanceof IllegalArgumentException);
        assertTrue(exception.getMessage().contains("Embedding model must be of type"));
    }

    public void testDoExecuteWithModelNotFound() throws InterruptedException {
        // Mock model not found
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Model not found"));
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception instanceof IllegalArgumentException);
        assertTrue(exception.getMessage().contains("LLM model with ID test-llm-model not found"));
    }

    public void testDoExecuteWithIndexInitializationFailure() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock failed index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Index initialization failed"));
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Index initialization failed"));
    }

    public void testDoExecuteWithMemoryContainerIndexingFailure() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock failed memory container indexing
        CompletableFuture<PutDataObjectResponse> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Indexing failed"));
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Indexing failed"));
    }

    public void testDoExecuteWithMemoryDataIndexCreationFailure() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock successful memory container indexing
        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        // Mock failed memory data index creation
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Data index creation failed"));
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Data index creation failed"));
    }

    public void testDoExecuteWithSparseEncodingConfig() throws InterruptedException {
        // Create sparse encoding config
        MemoryStorageConfig sparseConfig = MemoryStorageConfig
            .builder()
            .memoryIndexName("sparse-memory-index")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .embeddingModelId("sparse-embedding-model")
            .llmModelId("test-llm-model")
            .maxInferSize(5)
            .build();

        MLCreateMemoryContainerInput sparseInput = MLCreateMemoryContainerInput
            .builder()
            .name("sparse-memory-container")
            .description("Sparse encoding memory container")
            .memoryStorageConfig(sparseConfig)
            .tenantId(TENANT_ID)
            .build();

        MLCreateMemoryContainerRequest sparseRequest = new MLCreateMemoryContainerRequest(sparseInput);

        // Mock successful model validation for sparse encoding
        MLModel llmModel = mock(MLModel.class);
        when(llmModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(llmModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        MLModel sparseEmbeddingModel = mock(MLModel.class);
        when(sparseEmbeddingModel.getAlgorithm()).thenReturn(FunctionName.SPARSE_ENCODING);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(sparseEmbeddingModel);
            return null;
        }).when(mlModelManager).getModel(eq("sparse-embedding-model"), any());

        // Mock successful operations
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, sparseRequest, actionListener);

        // Verify success response
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());
    }

    public void testDoExecuteWithResourceAlreadyExistsException() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock successful memory container indexing
        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        // Mock ResourceAlreadyExistsException for memory data index creation (should be handled gracefully)
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(new ResourceAlreadyExistsException("Index already exists"));
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify success response (ResourceAlreadyExistsException should be handled gracefully)
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());
    }

    public void testDoExecuteWithCreateIndexNotAcknowledged() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock successful memory container indexing
        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        // Mock create index response not acknowledged
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(false, false, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception instanceof RuntimeException);
        assertTrue(exception.getMessage().contains("Failed to create memory data index"));
    }

    public void testDoExecuteWithUpdateMemoryContainerThrowableFailure() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock successful memory container indexing (first call)
        CompletableFuture<PutDataObjectResponse> successFuture = CompletableFuture.completedFuture(putDataObjectResponse);
        // Mock failed memory container update (second call) - using throwable branch
        CompletableFuture<PutDataObjectResponse> failureFuture = new CompletableFuture<>();
        failureFuture.completeExceptionally(new RuntimeException("Update throwable failure"));

        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(successFuture).thenReturn(failureFuture);

        // Mock successful memory data index creation
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Update throwable failure"));
    }

    public void testDoExecuteWithUpdateMemoryContainerCatchException() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock successful memory container indexing (first call)
        CompletableFuture<PutDataObjectResponse> successFuture = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(successFuture);

        // Mock successful memory data index creation
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Mock exception in updateMemoryContainer by making putDataObjectAsync throw on second call
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class)))
            .thenReturn(successFuture)
            .thenThrow(new RuntimeException("Update catch exception"));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Update catch exception"));
    }

    public void testDoExecuteWithIndexMemoryContainerThrowableFailure() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock failed memory container indexing with throwable branch
        CompletableFuture<PutDataObjectResponse> failureFuture = new CompletableFuture<>();
        failureFuture.completeExceptionally(new RuntimeException("Index throwable failure"));
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(failureFuture);

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Index throwable failure"));
    }

    public void testDoExecuteWithIndexMemoryContainerCatchException() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock exception in indexMemoryContainer catch block
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenThrow(new RuntimeException("Index catch exception"));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Index catch exception"));
    }

    public void testDoExecuteWithIndexResponseNotCreated() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock IndexResponse with result other than CREATED (e.g., UPDATED)
        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.UPDATED);
        when(indexResponse.getId()).thenReturn("test-id");

        PutDataObjectResponse putResponse = mock(PutDataObjectResponse.class);
        when(putResponse.indexResponse()).thenReturn(indexResponse);

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response due to non-CREATED result
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception instanceof RuntimeException);
        assertEquals("Failed to create memory container", exception.getMessage());
    }

    public void testDoExecuteWithIndexResponseExceptionInTryBlock() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock putDataObjectResponse that throws exception when accessing indexResponse
        PutDataObjectResponse faultyResponse = mock(PutDataObjectResponse.class);
        when(faultyResponse.indexResponse()).thenThrow(new RuntimeException("IndexResponse access exception"));

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(faultyResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("IndexResponse access exception"));
    }

    public void testDoExecuteWithUpdateMemoryContainerIndexResponseException() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock successful memory container indexing (first call)
        CompletableFuture<PutDataObjectResponse> successFuture = CompletableFuture.completedFuture(putDataObjectResponse);

        // Mock update response that throws exception when accessing indexResponse
        PutDataObjectResponse faultyUpdateResponse = mock(PutDataObjectResponse.class);
        when(faultyUpdateResponse.indexResponse()).thenThrow(new RuntimeException("Update IndexResponse exception"));
        CompletableFuture<PutDataObjectResponse> updateFuture = CompletableFuture.completedFuture(faultyUpdateResponse);

        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(successFuture).thenReturn(updateFuture);

        // Mock successful memory data index creation
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Update IndexResponse exception"));
    }

    public void testDoExecuteWithCreateMemoryDataIndexCatchException() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock successful memory container indexing
        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        // Mock exception in createMemoryDataIndex catch block
        doThrow(new RuntimeException("CreateMemoryDataIndex catch exception"))
            .when(indicesAdminClient)
            .create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("CreateMemoryDataIndex catch exception"));
    }

    public void testDoExecuteWithInitMemoryContainerIndexCatchException() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock exception in initMemoryContainerIndexIfAbsent catch block
        doThrow(new RuntimeException("InitMemoryContainerIndex catch exception"))
            .when(mlIndicesHandler)
            .initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("InitMemoryContainerIndex catch exception"));
    }

    public void testDoExecuteWithGeneralExceptionInDoExecute() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        // Mock exception during memory container creation by throwing in the try block
        doThrow(new RuntimeException("General doExecute exception")).when(sdkClient).putDataObjectAsync(any(PutDataObjectRequest.class));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("General doExecute exception"));
    }

    public void testDoExecuteWithEmbeddingModelValidationException() throws InterruptedException {
        // Mock valid LLM model
        MLModel llmModel = mock(MLModel.class);
        when(llmModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(llmModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        // Mock embedding model not found
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Embedding model not found"));
            return null;
        }).when(mlModelManager).getModel(eq("test-embedding-model"), any());

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception instanceof IllegalArgumentException);
        assertTrue(exception.getMessage().contains("Embedding model with ID test-embedding-model not found"));
    }

    public void testDoExecuteWithOnlyEmbeddingModelValidation() throws InterruptedException {
        // Create config with only embedding model (no LLM model)
        MemoryStorageConfig embeddingOnlyConfig = MemoryStorageConfig
            .builder()
            .memoryIndexName("embedding-only-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("test-embedding-model")
            .dimension(768)
            .maxInferSize(5)
            .build();

        MLCreateMemoryContainerInput embeddingOnlyInput = MLCreateMemoryContainerInput
            .builder()
            .name("embedding-only-container")
            .description("Embedding only memory container")
            .memoryStorageConfig(embeddingOnlyConfig)
            .tenantId(TENANT_ID)
            .build();

        MLCreateMemoryContainerRequest embeddingOnlyRequest = new MLCreateMemoryContainerRequest(embeddingOnlyInput);

        // Mock valid embedding model only
        MLModel embeddingModel = mock(MLModel.class);
        when(embeddingModel.getAlgorithm()).thenReturn(FunctionName.TEXT_EMBEDDING);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(embeddingModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-embedding-model"), any());

        // Mock successful operations
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, embeddingOnlyRequest, actionListener);

        // Verify success response
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());
    }

    public void testDoExecuteWithSemanticStorageDisabled() throws InterruptedException {
        // Create config with semantic storage disabled
        MemoryStorageConfig nonSemanticConfig = MemoryStorageConfig
            .builder()
            .memoryIndexName("non-semantic-index")
            .semanticStorageEnabled(false)
            .maxInferSize(5)
            .build();

        MLCreateMemoryContainerInput nonSemanticInput = MLCreateMemoryContainerInput
            .builder()
            .name("non-semantic-container")
            .description("Non-semantic memory container")
            .memoryStorageConfig(nonSemanticConfig)
            .tenantId(TENANT_ID)
            .build();

        MLCreateMemoryContainerRequest nonSemanticRequest = new MLCreateMemoryContainerRequest(nonSemanticInput);

        // Mock successful operations (no model validation needed)
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, nonSemanticRequest, actionListener);

        // Verify success response
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());

        // Verify that model validation was not called
        verify(mlModelManager, never()).getModel(anyString(), any());
    }

    public void testDoExecuteWithRemoteEmbeddingModel() throws InterruptedException {
        // Create config with remote embedding model
        MemoryStorageConfig remoteConfig = MemoryStorageConfig
            .builder()
            .memoryIndexName("remote-memory-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("remote-embedding-model")
            .llmModelId("test-llm-model")
            .dimension(768)
            .maxInferSize(5)
            .build();

        MLCreateMemoryContainerInput remoteInput = MLCreateMemoryContainerInput
            .builder()
            .name("remote-memory-container")
            .description("Remote embedding memory container")
            .memoryStorageConfig(remoteConfig)
            .tenantId(TENANT_ID)
            .build();

        MLCreateMemoryContainerRequest remoteRequest = new MLCreateMemoryContainerRequest(remoteInput);

        // Mock valid LLM model
        MLModel llmModel = mock(MLModel.class);
        when(llmModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(llmModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        // Mock remote embedding model (should be accepted)
        MLModel remoteEmbeddingModel = mock(MLModel.class);
        when(remoteEmbeddingModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(remoteEmbeddingModel);
            return null;
        }).when(mlModelManager).getModel(eq("remote-embedding-model"), any());

        // Mock successful operations
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, remoteRequest, actionListener);

        // Verify success response
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());
    }

    public void testDoExecuteWithNullMemoryStorageConfig() throws InterruptedException {
        // Create input with null memory storage config
        MLCreateMemoryContainerInput nullConfigInput = MLCreateMemoryContainerInput
            .builder()
            .name("null-config-container")
            .description("Null config memory container")
            .memoryStorageConfig(null)
            .tenantId(TENANT_ID)
            .build();

        MLCreateMemoryContainerRequest nullConfigRequest = new MLCreateMemoryContainerRequest(nullConfigInput);

        // Mock successful operations (no model validation needed for null config)
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, nullConfigRequest, actionListener);

        // Verify success response
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());

        // Verify that model validation was not called
        verify(mlModelManager, never()).getModel(anyString(), any());
    }

    public void testDoExecuteWithTenantValidationFailure() throws InterruptedException {
        // Enable multi-tenancy and provide null tenant ID to trigger validation failure
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        MLCreateMemoryContainerInput invalidTenantInput = MLCreateMemoryContainerInput
                .builder()
                .name("invalid-tenant-container")
                .description("Container with invalid tenant")
                .memoryStorageConfig(memoryStorageConfig)
                .tenantId(null) // This should trigger tenant validation failure
                .build();

        MLCreateMemoryContainerRequest invalidTenantRequest = new MLCreateMemoryContainerRequest(invalidTenantInput);

        // Execute
        action.doExecute(task, invalidTenantRequest, actionListener);

        // Verify failure response due to tenant validation
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("You don't have permission to access this resource"));
    }

    public void testDoExecuteWithDefaultIndexNameGeneration() throws InterruptedException {
        // Create config without specifying memory index name to test default generation
        MemoryStorageConfig configWithoutIndexName = MemoryStorageConfig
            .builder()
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("test-embedding-model")
            .llmModelId("test-llm-model")
            .dimension(768)
            .maxInferSize(5)
            .build(); // No memoryIndexName specified

        MLCreateMemoryContainerInput inputWithoutIndexName = MLCreateMemoryContainerInput
            .builder()
            .name("default-index-name-container")
            .description("Container with default index name generation")
            .memoryStorageConfig(configWithoutIndexName)
            .tenantId(TENANT_ID)
            .build();

        MLCreateMemoryContainerRequest requestWithoutIndexName = new MLCreateMemoryContainerRequest(inputWithoutIndexName);

        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful operations
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, requestWithoutIndexName, actionListener);

        // Verify success response
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());
    }

    public void testDoExecuteWithSparseEncodingDefaultIndexName() throws InterruptedException {
        // Create sparse encoding config without specifying memory index name
        MemoryStorageConfig sparseConfigWithoutIndexName = MemoryStorageConfig
            .builder()
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .embeddingModelId("sparse-embedding-model")
            .llmModelId("test-llm-model")
            .maxInferSize(5)
            .build(); // No memoryIndexName specified

        MLCreateMemoryContainerInput sparseInputWithoutIndexName = MLCreateMemoryContainerInput
            .builder()
            .name("sparse-default-index-container")
            .description("Sparse encoding container with default index name")
            .memoryStorageConfig(sparseConfigWithoutIndexName)
            .tenantId(TENANT_ID)
            .build();

        MLCreateMemoryContainerRequest sparseRequestWithoutIndexName = new MLCreateMemoryContainerRequest(sparseInputWithoutIndexName);

        // Mock successful model validation for sparse encoding
        MLModel llmModel = mock(MLModel.class);
        when(llmModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(llmModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        MLModel sparseEmbeddingModel = mock(MLModel.class);
        when(sparseEmbeddingModel.getAlgorithm()).thenReturn(FunctionName.SPARSE_ENCODING);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(sparseEmbeddingModel);
            return null;
        }).when(mlModelManager).getModel(eq("sparse-embedding-model"), any());

        // Mock successful operations
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, sparseRequestWithoutIndexName, actionListener);

        // Verify success response
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());
    }

    public void testDoExecuteWithStaticMemoryDefaultIndexName() throws InterruptedException {
        // Create config with semantic storage disabled to test static memory index generation
        MemoryStorageConfig staticConfigWithoutIndexName = MemoryStorageConfig
            .builder()
            .semanticStorageEnabled(false)
            .maxInferSize(5)
            .build(); // No memoryIndexName specified, semantic storage disabled

        MLCreateMemoryContainerInput staticInputWithoutIndexName = MLCreateMemoryContainerInput
            .builder()
            .name("static-default-index-container")
            .description("Static memory container with default index name")
            .memoryStorageConfig(staticConfigWithoutIndexName)
            .tenantId(TENANT_ID)
            .build();

        MLCreateMemoryContainerRequest staticRequestWithoutIndexName = new MLCreateMemoryContainerRequest(staticInputWithoutIndexName);

        // Mock successful operations (no model validation needed for static storage)
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, staticRequestWithoutIndexName, actionListener);

        // Verify success response
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());

        // Verify that model validation was not called
        verify(mlModelManager, never()).getModel(anyString(), any());
    }

    public void testDoExecuteWithSemanticStorageEnabledButNoEmbeddingModel() throws InterruptedException {
        // Create config with semantic storage enabled but no embedding model ID or type
        // Note: The constructor will auto-determine semanticStorageEnabled = false when embeddingModelId is null
        MemoryStorageConfig configWithoutEmbeddingModel = MemoryStorageConfig
            .builder()
            .memoryIndexName("semantic-no-embedding-index")
            .semanticStorageEnabled(true) // This will be overridden to false by constructor
            .embeddingModelId(null) // No embedding model ID
            .embeddingModelType(null) // No embedding model type
            .llmModelId("test-llm-model")
            .dimension(768)
            .maxInferSize(5)
            .build();

        MLCreateMemoryContainerInput inputWithoutEmbeddingModel = MLCreateMemoryContainerInput
            .builder()
            .name("semantic-no-embedding-container")
            .description("Semantic container without embedding model")
            .memoryStorageConfig(configWithoutEmbeddingModel)
            .tenantId(TENANT_ID)
            .build();

        MLCreateMemoryContainerRequest requestWithoutEmbeddingModel = new MLCreateMemoryContainerRequest(inputWithoutEmbeddingModel);

        // Mock valid LLM model only
        MLModel llmModel = mock(MLModel.class);
        when(llmModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(llmModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        // Mock successful operations
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, requestWithoutEmbeddingModel, actionListener);

        // Verify success response (should pass validation since embedding model ID is null)
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());

        // Verify that no model validation was called since semantic storage is effectively disabled
        // when embeddingModelId is null (constructor auto-determines semanticStorageEnabled = false)
        verify(mlModelManager, never()).getModel(anyString(), any());
    }

    public void testDoExecuteWithSemanticStorageEnabledButNullEmbeddingModelId() throws InterruptedException {
        // Create a config that simulates deserialization scenario where semanticStorageEnabled=true
        // but embeddingModelId=null (this can happen with StreamInput constructor)
        MemoryStorageConfig mockConfig = mock(MemoryStorageConfig.class);
        when(mockConfig.isSemanticStorageEnabled()).thenReturn(true);
        when(mockConfig.getLlmModelId()).thenReturn(null); // No LLM model
        when(mockConfig.getEmbeddingModelId()).thenReturn(null); // No embedding model ID
        when(mockConfig.getMemoryIndexName()).thenReturn("test-semantic-index");

        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput
            .builder()
            .name("semantic-container-null-embedding")
            .description("Semantic container with null embedding model ID")
            .memoryStorageConfig(mockConfig)
            .tenantId(TENANT_ID)
            .build();

        MLCreateMemoryContainerRequest request = new MLCreateMemoryContainerRequest(input);

        // Mock successful operations
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify success response
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());

        // Verify that no model validation was called since both LLM and embedding model IDs are null
        verify(mlModelManager, never()).getModel(anyString(), any());
    }

    public void testDoExecuteWithAgenticMemoryDisabled() throws InterruptedException {
        // Disable agentic memory feature
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response due to feature being disabled
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception instanceof OpenSearchStatusException);
        OpenSearchStatusException statusException = (OpenSearchStatusException) exception;
        assertEquals(RestStatus.FORBIDDEN, statusException.status());
        assertEquals("The Agentic Memory APIs are not enabled. To enable, please update the setting plugins.ml_commons.agentic_memory_enabled", exception.getMessage());
    }

    public void testDoExecuteWithSemanticStorageEnabledButNoLlmModel() throws InterruptedException {
        // Create config with semantic storage enabled but no LLM model ID
        MemoryStorageConfig configWithoutLlmModel = MemoryStorageConfig
            .builder()
            .memoryIndexName("semantic-no-llm-index")
            .semanticStorageEnabled(true)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("test-embedding-model")
            .llmModelId(null) // No LLM model ID
            .dimension(768)
            .maxInferSize(5)
            .build();

        MLCreateMemoryContainerInput inputWithoutLlmModel = MLCreateMemoryContainerInput
            .builder()
            .name("semantic-no-llm-container")
            .description("Semantic container without LLM model")
            .memoryStorageConfig(configWithoutLlmModel)
            .tenantId(TENANT_ID)
            .build();

        MLCreateMemoryContainerRequest requestWithoutLlmModel = new MLCreateMemoryContainerRequest(inputWithoutLlmModel);

        // Mock valid embedding model only
        MLModel embeddingModel = mock(MLModel.class);
        when(embeddingModel.getAlgorithm()).thenReturn(FunctionName.TEXT_EMBEDDING);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(embeddingModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-embedding-model"), any());

        // Mock successful operations
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, requestWithoutLlmModel, actionListener);

        // Verify success response
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());

        // Verify that only embedding model validation was called
        verify(mlModelManager).getModel(eq("test-embedding-model"), any());
        verify(mlModelManager, never()).getModel(eq("test-llm-model"), any());
    }

    public void testDoExecuteWithNullUserContext() throws InterruptedException {
        // Setup null user context by clearing thread context
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, null);

        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful operations
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(eq(MLIndex.MEMORY_CONTAINER), isA(ActionListener.class));

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify success response (should handle null user gracefully)
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());
    }

    // Helper method to mock successful model validation
    private void mockSuccessfulModelValidation() {
        // Mock valid LLM model
        MLModel llmModel = mock(MLModel.class);
        when(llmModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(llmModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        // Mock valid embedding model
        MLModel embeddingModel = mock(MLModel.class);
        when(embeddingModel.getAlgorithm()).thenReturn(FunctionName.TEXT_EMBEDDING);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(embeddingModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-embedding-model"), any());
    }
}
