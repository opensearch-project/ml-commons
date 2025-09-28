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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.ingest.PutPipelineRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
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
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
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
import org.opensearch.transport.client.ClusterAdminClient;
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
    private ClusterAdminClient clusterAdminClient;
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
    private MemoryConfiguration memoryStorageConfig;
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
        List<MemoryStrategy> strategies = new ArrayList<>();
        strategies
            .add(MemoryStrategy.builder().namespace(List.of(SESSION_ID_FIELD)).id("strategy-id1").enabled(true).type("semantic").build());
        memoryStorageConfig = spy(
            MemoryConfiguration
                .builder()
                .indexPrefix("test-memory-index")
                .embeddingModelType(FunctionName.TEXT_EMBEDDING)
                .embeddingModelId("test-embedding-model")
                .llmId("test-llm-model")
                .dimension(768)
                .maxInferSize(5)
                .strategies(strategies)
                .build()
        );

        // Setup input
        input = MLCreateMemoryContainerInput
            .builder()
            .name("test-memory-container")
            .description("Test memory container description")
            .configuration(memoryStorageConfig)
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
            mlIndicesHandler,
            connectorAccessControlHelper,
            mlFeatureEnabledSetting,
            mlModelManager
        );
    }

    public void testConstructor() {
        assertNotNull(action);
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

    public void testDoExecuteSuccess_LongTermMemory() throws InterruptedException {
        mockSuccessfulCreatePipeline();

        mockAndRunExecuteMethod(request);
    }

    private void mockSuccessfulCreatePipeline() {
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);

        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            // Create a real CreateIndexResponse that returns true for isAcknowledged
            AcknowledgedResponse response = new AcknowledgedResponse(true);
            listener.onResponse(response);
            return null;
        }).when(clusterAdminClient).putPipeline(any(PutPipelineRequest.class), any(ActionListener.class));
    }

    private void mockAndRunExecuteMethod(MLCreateMemoryContainerRequest request) {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        mockSuccessfulIndexCreation();

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

    public void testDoExecuteSuccess_WorkingMemoryOnly_NullLLMId() throws InterruptedException {
        when(memoryStorageConfig.getLlmId()).thenReturn(null);
        mockAndRunExecuteMethod(request);
    }

    public void testDoExecuteSuccess_WorkingMemoryOnly_EmptyStrategies() throws InterruptedException {
        when(memoryStorageConfig.getStrategies()).thenReturn(List.of());
        mockAndRunExecuteMethod(request);
    }

    public void testDoExecuteWithMinimalInput() throws InterruptedException {
        // Create minimal input (no memory storage config)
        MLCreateMemoryContainerInput minimalInput = MLCreateMemoryContainerInput
            .builder()
            .name("minimal-container")
            .tenantId(TENANT_ID)
            .configuration(MemoryConfiguration.builder().indexPrefix("test").build())
            .build();
        MLCreateMemoryContainerRequest minimalRequest = new MLCreateMemoryContainerRequest(minimalInput);
        mockAndRunExecuteMethod(minimalRequest);
    }

    public void testDoExecuteWithoutConfiguration() throws InterruptedException {
        // Create minimal input (no memory storage config)
        MLCreateMemoryContainerInput minimalInput = MLCreateMemoryContainerInput
            .builder()
            .name("minimal-container")
            .configuration(MemoryConfiguration.builder().build())
            .tenantId(TENANT_ID)
            .build();// If configuration is null, will create a default configuration with system index prefix
        MLCreateMemoryContainerRequest minimalRequest = new MLCreateMemoryContainerRequest(minimalInput);
        mockAndRunExecuteMethod(minimalRequest);
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
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("Index initialization failed"));
            return null;
        }).when(mlIndicesHandler).initMemoryContainerIndex(isA(ActionListener.class));

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
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMemoryContainerIndex(isA(ActionListener.class));

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

    public void testDoExecuteWithMemorySessionIndexCreationFailure() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMemoryContainerIndex(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Create session index failed"));
            return null;
        }).when(mlIndicesHandler).createSessionMemoryDataIndex(anyString(), any(MemoryConfiguration.class), isA(ActionListener.class));

        // Mock successful memory container indexing
        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Create session index failed"));
    }

    public void testDoExecuteWithSparseEncodingConfig() throws InterruptedException {
        // Create sparse encoding config
        MemoryConfiguration sparseConfig = MemoryConfiguration
            .builder()
            .indexPrefix("sparse-memory-index")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .embeddingModelId("sparse-embedding-model")
            .llmId("test-llm-model")
            .maxInferSize(5)
            .build();

        MLCreateMemoryContainerInput sparseInput = MLCreateMemoryContainerInput
            .builder()
            .name("sparse-memory-container")
            .description("Sparse encoding memory container")
            .configuration(sparseConfig)
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

        mockSuccessfulIndexCreation();

        // Execute
        action.doExecute(task, sparseRequest, actionListener);

        // Verify success response
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());
    }

    public void testDoExecuteWithIndexMemoryContainerThrowableFailure() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock successful index initialization
        mockSuccessfulIndexCreation();

        mockSuccessfulCreatePipeline();

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

    public void testDoExecuteWithIndexResponseExceptionInTryBlock() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        mockSuccessfulIndexCreation();

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

    public void testDoExecuteWithInitMemoryContainerIndexCatchException() throws InterruptedException {
        // Mock successful model validation
        mockSuccessfulModelValidation();

        // Mock exception in initMemoryContainerIndexIfAbsent catch block
        doThrow(new RuntimeException("InitMemoryContainerIndex catch exception"))
            .when(mlIndicesHandler)
            .initMemoryContainerIndex(isA(ActionListener.class));

        // Execute
        action.doExecute(task, request, actionListener);

        // Verify failure response
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("InitMemoryContainerIndex catch exception"));
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
        mockSuccessfulIndexCreation();
        mockSuccessfulCreatePipeline();
        // Create config with only embedding model (no LLM model)
        MemoryConfiguration embeddingOnlyConfig = MemoryConfiguration
            .builder()
            .indexPrefix("embedding-only-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("test-embedding-model")
            .dimension(768)
            .maxInferSize(5)
            .build();

        MLCreateMemoryContainerInput embeddingOnlyInput = MLCreateMemoryContainerInput
            .builder()
            .name("embedding-only-container")
            .description("Embedding only memory container")
            .configuration(embeddingOnlyConfig)
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

    public void testDoExecuteWithRemoteEmbeddingModel() throws InterruptedException {
        mockSuccessfulIndexCreation();
        mockSuccessfulCreatePipeline();
        // Create config with remote embedding model
        MemoryConfiguration remoteConfig = MemoryConfiguration
            .builder()
            .indexPrefix("remote-memory-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("remote-embedding-model")
            .llmId("test-llm-model")
            .dimension(768)
            .maxInferSize(5)
            .build();

        MLCreateMemoryContainerInput remoteInput = MLCreateMemoryContainerInput
            .builder()
            .name("remote-memory-container")
            .description("Remote embedding memory container")
            .configuration(remoteConfig)
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

    public void testDoExecuteWithTenantValidationFailure() throws InterruptedException {
        // Enable multi-tenancy and provide null tenant ID to trigger validation failure
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        MLCreateMemoryContainerInput invalidTenantInput = MLCreateMemoryContainerInput
                .builder()
                .name("invalid-tenant-container")
                .description("Container with invalid tenant")
                .configuration(memoryStorageConfig)
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
        MemoryConfiguration configWithoutIndexName = MemoryConfiguration
            .builder()
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("test-embedding-model")
            .llmId("test-llm-model")
            .dimension(768)
            .maxInferSize(5)
            .build(); // No memoryIndexName specified

        MLCreateMemoryContainerInput inputWithoutIndexName = MLCreateMemoryContainerInput
            .builder()
            .name("default-index-name-container")
            .description("Container with default index name generation")
            .configuration(configWithoutIndexName)
            .tenantId(TENANT_ID)
            .build();

        MLCreateMemoryContainerRequest requestWithoutIndexName = new MLCreateMemoryContainerRequest(inputWithoutIndexName);

        // Mock successful model validation
        mockSuccessfulModelValidation();
        mockSuccessfulIndexCreation();
        mockSuccessfulCreatePipeline();

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

    public void testDoExecuteWithNullUserContext() throws InterruptedException {
        // Setup null user context by clearing thread context
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, null);

        // Mock successful model validation
        mockSuccessfulModelValidation();
        mockSuccessfulIndexCreation();
        mockSuccessfulCreatePipeline();

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

    private void mockSuccessfulIndexCreation() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMemoryContainerIndex(isA(ActionListener.class));

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).createWorkingMemoryDataIndex(anyString(), any(MemoryConfiguration.class), isA(ActionListener.class));

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        })
            .when(mlIndicesHandler)
            .createLongTermMemoryIndex(anyString(), anyString(), any(MemoryConfiguration.class), isA(ActionListener.class));

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).createSessionMemoryDataIndex(anyString(), any(MemoryConfiguration.class), isA(ActionListener.class));

        // Mock successful index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).createLongTermMemoryHistoryIndex(anyString(), any(MemoryConfiguration.class), isA(ActionListener.class));
    }
}
