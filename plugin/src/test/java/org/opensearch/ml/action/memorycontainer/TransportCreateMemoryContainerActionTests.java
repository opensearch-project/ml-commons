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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.ingest.GetPipelineRequest;
import org.opensearch.action.ingest.GetPipelineResponse;
import org.opensearch.action.ingest.PutPipelineRequest;
import org.opensearch.action.search.GetSearchPipelineAction;
import org.opensearch.action.search.GetSearchPipelineRequest;
import org.opensearch.action.search.PutSearchPipelineAction;
import org.opensearch.action.search.PutSearchPipelineRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;
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

        // Mock client.execute() for search pipeline creation (hybrid search pipeline) - scoped to search pipeline actions
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(2);
            listener.onFailure(new org.opensearch.OpenSearchStatusException("not found", RestStatus.NOT_FOUND));
            return null;
        }).when(client).execute(eq(GetSearchPipelineAction.INSTANCE), any(GetSearchPipelineRequest.class), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(2);
            listener.onResponse(new AcknowledgedResponse(true));
            return null;
        }).when(client).execute(eq(PutSearchPipelineAction.INSTANCE), any(PutSearchPipelineRequest.class), any(ActionListener.class));

        // Setup memory storage config
        List<MemoryStrategy> strategies = new ArrayList<>();
        strategies
            .add(
                MemoryStrategy
                    .builder()
                    .namespace(List.of(SESSION_ID_FIELD))
                    .id("strategy-id1")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .build()
            );
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
                .disableSession(false) // Enable session creation for tests
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

        // Mock GetPipelineRequest to simulate pipeline doesn't exist yet
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(1);
            // Return response with empty pipelines list (pipeline doesn't exist)
            org.opensearch.action.ingest.GetPipelineResponse getPipelineResponse = new org.opensearch.action.ingest.GetPipelineResponse(
                Collections.emptyList()
            );
            listener.onResponse(getPipelineResponse);
            return null;
        }).when(clusterAdminClient).getPipeline(any(), any(ActionListener.class));

        // Mock PutPipelineRequest to simulate successful pipeline creation
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
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
        when(memoryStorageConfig.getStrategies()).thenReturn(List.of()); // No strategies for working memory only
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
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) exception).status());
        assertTrue(exception.getMessage().contains("Internal server error"));
    }

    public void testAutoGeneratedPrefixPersistence() throws InterruptedException {
        // Create input without index prefix but with use_system_index=false
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(false).llmId("test-llm-model").build();

        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput
            .builder()
            .name("test-container")
            .configuration(config)
            .tenantId(TENANT_ID)
            .build();

        MLCreateMemoryContainerRequest request = new MLCreateMemoryContainerRequest(input);

        // Mock successful model validation
        mockSuccessfulModelValidation();
        mockSuccessfulIndexCreation();
        mockSuccessfulCreatePipeline();

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
        action.doExecute(task, request, actionListener);

        // Capture the PutDataObjectRequest to verify prefix was auto-generated
        ArgumentCaptor<PutDataObjectRequest> putRequestCaptor = ArgumentCaptor.forClass(PutDataObjectRequest.class);
        verify(sdkClient).putDataObjectAsync(putRequestCaptor.capture());

        // There should only be one call now (no update)
        PutDataObjectRequest putRequest = putRequestCaptor.getValue();
        assertNotNull(putRequest);

        // Verify that the configuration has the default prefix
        // Since no prefix was specified, it defaults to "default" from MemoryConfiguration
        assertNotNull(config.getIndexPrefix());
        assertNotNull(config.getIndexPrefix());

        // Verify success response
        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
        assertEquals("created", response.getStatus());
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
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) exception).status());
        assertTrue(exception.getMessage().contains("Internal server error"));
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
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) exception).status());
        assertTrue(exception.getMessage().contains("Internal server error"));
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
        assertTrue(exception instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) exception).status());
        assertTrue(exception.getMessage().contains("Internal server error"));
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

    // Note: Tests for invalid strategy type removed - type is now @NonNull enum,
    // so invalid types cannot be constructed via builder

    // Note: Test for missing namespace removed - namespace is now @NonNull,
    // so null namespace cannot be set via builder

    @Test
    public void testDoExecuteWithEmptyStrategyNamespace() throws InterruptedException {
        // Test behavior when strategy namespace is empty list
        // Our new validation will fail earlier because strategies require AI models
        MemoryStrategy strategyWithEmptyNamespace = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SEMANTIC)
            .enabled(true)
            .namespace(Arrays.asList())
            .build();

        MemoryConfiguration config = MemoryConfiguration.builder().strategies(Arrays.asList(strategyWithEmptyNamespace)).build();

        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput.builder().name("Test Container").configuration(config).build();
        MLCreateMemoryContainerRequest request = MLCreateMemoryContainerRequest.builder().mlCreateMemoryContainerInput(input).build();

        // Act
        action.doExecute(task, request, actionListener);

        // Assert - validates strategies require AI models before namespace validation
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());
        Exception exception = exceptionCaptor.getValue();
        assertTrue(exception instanceof IllegalArgumentException);
        assertTrue(exception.getMessage().contains("Strategies require both an LLM model and embedding model"));
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

        // Mock shared index validation (index doesn't exist - validation passes)
        mockSharedIndexValidation();
    }

    // Helper method to mock shared index validation - simulates index doesn't exist
    private void mockSharedIndexValidation() {
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);

        // Mock getMappings to simulate index doesn't exist (throw IndexNotFoundException)
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(1);
            // Simulate index not found error with proper exception type
            listener.onFailure(new IndexNotFoundException("test-index"));
            return null;
        }).when(indicesAdminClient).getMappings(any(), any(ActionListener.class));
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

    @Test
    public void testCreateContainer_StrategiesWithoutLlm_ShouldFail() {
        // Test that creating a container with strategies but no LLM fails validation
        MemoryStrategy strategy = MemoryStrategy.builder().type(MemoryStrategyType.SEMANTIC).namespace(Arrays.asList("user_id")).build();

        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .embeddingModelId("embedding-123")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(384)
            .strategies(Arrays.asList(strategy))
            .build();

        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput.builder().name("test-container").configuration(config).build();

        MLCreateMemoryContainerRequest request = new MLCreateMemoryContainerRequest(input);
        ActionListener<MLCreateMemoryContainerResponse> listener = mock(ActionListener.class);

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().contains("Strategies require both an LLM model and embedding model"));
        assertTrue(captor.getValue().getMessage().contains("Missing: LLM model"));
    }

    @Test
    public void testCreateContainer_StrategiesWithoutEmbedding_ShouldFail() {
        // Test that creating a container with strategies but no embedding model fails validation
        MemoryStrategy strategy = MemoryStrategy.builder().type(MemoryStrategyType.SEMANTIC).namespace(Arrays.asList("user_id")).build();

        MemoryConfiguration config = MemoryConfiguration
            .builder()
            .indexPrefix("test")
            .llmId("llm-123")
            .strategies(Arrays.asList(strategy))
            .build();

        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput.builder().name("test-container").configuration(config).build();

        MLCreateMemoryContainerRequest request = new MLCreateMemoryContainerRequest(input);
        ActionListener<MLCreateMemoryContainerResponse> listener = mock(ActionListener.class);

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().contains("Strategies require both an LLM model and embedding model"));
        assertTrue(captor.getValue().getMessage().contains("Missing: embedding model"));
    }

    @Test
    public void testCreateContainer_StrategiesWithoutBoth_ShouldFail() {
        // Test that creating a container with strategies but neither LLM nor embedding fails validation
        MemoryStrategy strategy = MemoryStrategy.builder().type(MemoryStrategyType.SEMANTIC).namespace(Arrays.asList("user_id")).build();

        MemoryConfiguration config = MemoryConfiguration.builder().indexPrefix("test").strategies(Arrays.asList(strategy)).build();

        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput.builder().name("test-container").configuration(config).build();

        MLCreateMemoryContainerRequest request = new MLCreateMemoryContainerRequest(input);
        ActionListener<MLCreateMemoryContainerResponse> listener = mock(ActionListener.class);

        action.doExecute(task, request, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().contains("Strategies require both an LLM model and embedding model"));
        assertTrue(captor.getValue().getMessage().contains("LLM model and embedding model"));
    }

    @Test
    public void testCreateContainer_WithStrategies_DisableSession() throws InterruptedException {
        // Test container with strategies but disable_session = true
        mockSuccessfulCreatePipeline();

        when(memoryStorageConfig.isDisableSession()).thenReturn(true);
        mockAndRunExecuteMethod(request);
    }

    @Test
    public void testCreateContainer_WithStrategies_DisableHistory() throws InterruptedException {
        // Test container with strategies but disable_history = true
        mockSuccessfulCreatePipeline();

        when(memoryStorageConfig.isDisableHistory()).thenReturn(true);
        mockAndRunExecuteMethod(request);
    }

    @Test
    public void testCreateContainer_WithoutStrategies_DisableSession() throws InterruptedException {
        // Test container without strategies and disable_session = true
        when(memoryStorageConfig.getStrategies()).thenReturn(List.of());
        when(memoryStorageConfig.isDisableSession()).thenReturn(true);
        mockAndRunExecuteMethod(request);
    }

    @Test
    public void testCreateContainer_PipelineAlreadyExists() throws InterruptedException {
        // Test shared index scenario where pipeline already exists
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);

        // Mock GetPipelineRequest to return existing pipeline
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(1);
            org.opensearch.ingest.PipelineConfiguration pipelineConfig = new org.opensearch.ingest.PipelineConfiguration(
                "test-pipeline",
                new BytesArray("{}"),
                org.opensearch.common.xcontent.XContentType.JSON
            );
            org.opensearch.action.ingest.GetPipelineResponse getPipelineResponse = new org.opensearch.action.ingest.GetPipelineResponse(
                Collections.singletonList(pipelineConfig)
            );
            listener.onResponse(getPipelineResponse);
            return null;
        }).when(clusterAdminClient).getPipeline(any(), any(ActionListener.class));

        mockSuccessfulModelValidation();
        mockSuccessfulIndexCreation();

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        action.doExecute(task, request, actionListener);

        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
    }

    @Test
    public void testCreateContainer_PutPipelineFailure() throws InterruptedException {
        // Test pipeline creation failure
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);

        // Mock GetPipelineRequest to simulate pipeline doesn't exist
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(1);
            org.opensearch.action.ingest.GetPipelineResponse getPipelineResponse = new org.opensearch.action.ingest.GetPipelineResponse(
                Collections.emptyList()
            );
            listener.onResponse(getPipelineResponse);
            return null;
        }).when(clusterAdminClient).getPipeline(any(), any(ActionListener.class));

        // Mock PutPipelineRequest to fail
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Pipeline creation failed"));
            return null;
        }).when(clusterAdminClient).putPipeline(any(PutPipelineRequest.class), any(ActionListener.class));

        mockSuccessfulModelValidation();
        mockSuccessfulIndexCreation();

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) captor.getValue()).status());
        assertTrue(captor.getValue().getMessage().contains("Internal server error"));
    }

    @Test
    public void testCreateContainer_GetPipelineError() throws InterruptedException {
        // Test getPipeline error path
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);

        // Mock GetPipelineRequest to fail (triggers error handler which should create pipeline)
        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Pipeline fetch error"));
            return null;
        }).when(clusterAdminClient).getPipeline(any(), any(ActionListener.class));

        // Mock successful putPipeline in error handler path
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            AcknowledgedResponse response = new AcknowledgedResponse(true);
            listener.onResponse(response);
            return null;
        }).when(clusterAdminClient).putPipeline(any(PutPipelineRequest.class), any(ActionListener.class));

        mockSuccessfulModelValidation();
        mockSuccessfulIndexCreation();

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        action.doExecute(task, request, actionListener);

        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
    }

    @Test
    public void testCreateContainer_IndexResponseNotCreated() throws InterruptedException {
        // Test when indexResponse result is not CREATED
        mockSuccessfulModelValidation();
        mockSuccessfulIndexCreation();

        IndexResponse mockIndexResponse = mock(IndexResponse.class);
        when(mockIndexResponse.getResult()).thenReturn(DocWriteResponse.Result.UPDATED); // Not CREATED

        PutDataObjectResponse mockPutResponse = mock(PutDataObjectResponse.class);
        when(mockPutResponse.indexResponse()).thenReturn(mockIndexResponse);

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(mockPutResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        mockSuccessfulCreatePipeline();

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) captor.getValue()).status());
        assertTrue(captor.getValue().getMessage().contains("Internal server error"));
    }

    @Test
    public void testCreateContainer_SparseEncodingProcessor() throws InterruptedException {
        // Test SPARSE_ENCODING processor type
        when(memoryStorageConfig.getEmbeddingModelType()).thenReturn(FunctionName.SPARSE_ENCODING);

        when(client.admin()).thenReturn(adminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);

        doAnswer(invocation -> {
            ActionListener listener = invocation.getArgument(1);
            org.opensearch.action.ingest.GetPipelineResponse getPipelineResponse = new org.opensearch.action.ingest.GetPipelineResponse(
                Collections.emptyList()
            );
            listener.onResponse(getPipelineResponse);
            return null;
        }).when(clusterAdminClient).getPipeline(any(), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            AcknowledgedResponse response = new AcknowledgedResponse(true);
            listener.onResponse(response);
            return null;
        }).when(clusterAdminClient).putPipeline(any(PutPipelineRequest.class), any(ActionListener.class));

        // Mock valid LLM model
        MLModel llmModel = mock(MLModel.class);
        when(llmModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(llmModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        // Mock valid SPARSE_ENCODING embedding model
        MLModel embeddingModel = mock(MLModel.class);
        when(embeddingModel.getAlgorithm()).thenReturn(FunctionName.SPARSE_ENCODING);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(embeddingModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-embedding-model"), any());

        mockSharedIndexValidation();
        mockSuccessfulIndexCreation();

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        action.doExecute(task, request, actionListener);

        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
    }

    @Test
    public void testCreateContainer_NoEmbeddingModelType() throws InterruptedException {
        // Test when embeddingModelType is null (no pipeline needed, no embedding validation)
        // Must have empty strategies because strategies require both LLM and embedding
        when(memoryStorageConfig.getEmbeddingModelType()).thenReturn(null);
        when(memoryStorageConfig.getEmbeddingModelId()).thenReturn(null);
        when(memoryStorageConfig.getStrategies()).thenReturn(List.of());

        // Mock valid LLM model only
        MLModel llmModel = mock(MLModel.class);
        when(llmModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(llmModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        mockSharedIndexValidation();
        mockSuccessfulIndexCreation();

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> listener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(true, true, "test-index");
            listener.onResponse(response);
            return null;
        }).when(indicesAdminClient).create(any(CreateIndexRequest.class), any(ActionListener.class));

        action.doExecute(task, request, actionListener);

        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
    }

    @Test
    public void testCreateContainer_LLMModelNotRemote() throws InterruptedException {
        // Test LLM model validation failure - not REMOTE type
        MLModel llmModel = mock(MLModel.class);
        when(llmModel.getAlgorithm()).thenReturn(FunctionName.TEXT_EMBEDDING); // Wrong type

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(llmModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        mockSharedIndexValidation();
        mockSuccessfulIndexCreation();
        mockSuccessfulCreatePipeline();

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().contains("must be a REMOTE model"));
    }

    @Test
    public void testCreateContainer_LLMModelNotFound() throws InterruptedException {
        // Test LLM model not found
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Model not found"));
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        mockSharedIndexValidation();
        mockSuccessfulIndexCreation();
        mockSuccessfulCreatePipeline();

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().contains("not found"));
    }

    @Test
    public void testCreateContainer_EmbeddingModelTypeMismatch() throws InterruptedException {
        // Test embedding model type mismatch
        MLModel llmModel = mock(MLModel.class);
        when(llmModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(llmModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        // Embedding model with wrong type
        MLModel embeddingModel = mock(MLModel.class);
        when(embeddingModel.getAlgorithm()).thenReturn(FunctionName.SPARSE_ENCODING); // Expected TEXT_EMBEDDING

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(embeddingModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-embedding-model"), any());

        mockSharedIndexValidation();
        mockSuccessfulIndexCreation();
        mockSuccessfulCreatePipeline();

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().contains("must be of type"));
    }

    @Test
    public void testCreateContainer_EmbeddingModelNotFound() throws InterruptedException {
        // Test embedding model not found
        MLModel llmModel = mock(MLModel.class);
        when(llmModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(llmModel);
            return null;
        }).when(mlModelManager).getModel(eq("test-llm-model"), any());

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Embedding model not found"));
            return null;
        }).when(mlModelManager).getModel(eq("test-embedding-model"), any());

        mockSharedIndexValidation();
        mockSuccessfulIndexCreation();
        mockSuccessfulCreatePipeline();

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().contains("not found"));
    }

    @Test
    public void testCreateContainer_IndexMemoryContainerException() throws InterruptedException {
        // Test exception in indexMemoryContainer outer catch block
        mockSuccessfulModelValidation();
        mockSuccessfulIndexCreation();
        mockSuccessfulCreatePipeline();

        // Make putDataObjectAsync throw exception
        CompletableFuture<PutDataObjectResponse> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("SDK client error"));
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    public void testCreateContainer_SharedIndexConfigMatch() throws InterruptedException {
        // Test successful validation when existing index config matches request
        mockSuccessfulLLMValidation();
        mockSuccessfulIndexCreation();
        mockSuccessfulCreatePipeline();

        // Get the actual index name from config
        String indexName = memoryStorageConfig.getLongMemoryIndexName();

        // Mock GetMappingsResponse with matching config
        GetMappingsResponse getMappingsResponse = mock(GetMappingsResponse.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> embeddingField = new HashMap<>();
        embeddingField.put("type", "knn_vector");
        embeddingField.put("dimension", 768);
        properties.put("memory_embedding", embeddingField);

        Map<String, Object> mappingMap = new HashMap<>();
        mappingMap.put("properties", properties);

        when(mappingMetadata.getSourceAsMap()).thenReturn(mappingMap);
        when(getMappingsResponse.getMappings()).thenReturn(Collections.singletonMap(indexName, mappingMetadata));

        // Mock GetPipelineResponse with matching model_id
        GetPipelineResponse getPipelineResponse = mock(GetPipelineResponse.class);

        // Create real PipelineConfiguration (final class, can't be mocked)
        String pipelineJson = "{" + "\"processors\": [" + "  {\"text_embedding\": {\"model_id\": \"test-embedding-model\"}}" + "]" + "}";
        org.opensearch.ingest.PipelineConfiguration pipelineConfig = new org.opensearch.ingest.PipelineConfiguration(
            indexName + "-embedding",
            new BytesArray(pipelineJson),
            org.opensearch.common.xcontent.XContentType.JSON
        );

        when(getPipelineResponse.pipelines()).thenReturn(Collections.singletonList(pipelineConfig));

        // Setup admin clients
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);

        // Mock getMappings call
        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);
            listener.onResponse(getMappingsResponse);
            return null;
        }).when(indicesAdminClient).getMappings(any(GetMappingsRequest.class), any(ActionListener.class));

        // Mock getPipeline call
        doAnswer(invocation -> {
            ActionListener<GetPipelineResponse> listener = invocation.getArgument(1);
            listener.onResponse(getPipelineResponse);
            return null;
        }).when(clusterAdminClient).getPipeline(any(GetPipelineRequest.class), any(ActionListener.class));

        CompletableFuture<PutDataObjectResponse> future = CompletableFuture.completedFuture(putDataObjectResponse);
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class))).thenReturn(future);

        action.doExecute(task, request, actionListener);

        verify(actionListener).onResponse(responseCaptor.capture());
        MLCreateMemoryContainerResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertEquals(MEMORY_CONTAINER_ID, response.getMemoryContainerId());
    }

    @Test
    public void testCreateContainer_SharedIndexConfigMismatch() throws InterruptedException {
        // Test failure when existing config doesn't match request
        mockSuccessfulLLMValidation();
        mockSuccessfulIndexCreation();
        mockSuccessfulCreatePipeline();

        // Get the actual index name from config
        String indexName = memoryStorageConfig.getLongMemoryIndexName();

        // Mock GetMappingsResponse with different dimension
        GetMappingsResponse getMappingsResponse = mock(GetMappingsResponse.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> embeddingField = new HashMap<>();
        embeddingField.put("type", "knn_vector");
        embeddingField.put("dimension", 384); // Different from requested 768
        properties.put("memory_embedding", embeddingField);

        Map<String, Object> mappingMap = new HashMap<>();
        mappingMap.put("properties", properties);

        when(mappingMetadata.getSourceAsMap()).thenReturn(mappingMap);
        when(getMappingsResponse.getMappings()).thenReturn(Collections.singletonMap(indexName, mappingMetadata));

        // Mock GetPipelineResponse with different model_id
        GetPipelineResponse getPipelineResponse = mock(GetPipelineResponse.class);

        // Create real PipelineConfiguration with different model_id (final class, can't be mocked)
        String pipelineJson = "{" + "\"processors\": [" + "  {\"text_embedding\": {\"model_id\": \"different-model\"}}" + "]" + "}";
        org.opensearch.ingest.PipelineConfiguration pipelineConfig = new org.opensearch.ingest.PipelineConfiguration(
            indexName + "-embedding",
            new BytesArray(pipelineJson),
            org.opensearch.common.xcontent.XContentType.JSON
        );

        when(getPipelineResponse.pipelines()).thenReturn(Collections.singletonList(pipelineConfig));

        // Setup admin clients
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);

        // Mock getMappings call
        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);
            listener.onResponse(getMappingsResponse);
            return null;
        }).when(indicesAdminClient).getMappings(any(GetMappingsRequest.class), any(ActionListener.class));

        // Mock getPipeline call
        doAnswer(invocation -> {
            ActionListener<GetPipelineResponse> listener = invocation.getArgument(1);
            listener.onResponse(getPipelineResponse);
            return null;
        }).when(clusterAdminClient).getPipeline(any(GetPipelineRequest.class), any(ActionListener.class));

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        // Validation errors for config mismatch should remain as IllegalArgumentException (4XX)
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().toLowerCase().contains("embedding configuration"));
    }

    @Test
    public void testCreateContainer_IndexExistsMappingNull() throws InterruptedException {
        // Test failure when mapping metadata is null
        mockSuccessfulLLMValidation();
        mockSuccessfulIndexCreation();

        // Get the actual index name from config
        String indexName = memoryStorageConfig.getLongMemoryIndexName();

        // Mock GetMappingsResponse with null MappingMetadata
        GetMappingsResponse getMappingsResponse = mock(GetMappingsResponse.class);
        when(getMappingsResponse.getMappings()).thenReturn(Collections.singletonMap(indexName, null));

        // Setup admin clients
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);

        // Mock getMappings call
        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);
            listener.onResponse(getMappingsResponse);
            return null;
        }).when(indicesAdminClient).getMappings(any(GetMappingsRequest.class), any(ActionListener.class));

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) captor.getValue()).status());
        assertTrue(captor.getValue().getMessage().contains("Internal server error"));
    }

    @Test
    public void testCreateContainer_IndexExistsNoEmbeddingField() throws InterruptedException {
        // Test failure when mapping has no embedding field
        mockSuccessfulLLMValidation();
        mockSuccessfulIndexCreation();

        // Get the actual index name from config
        String indexName = memoryStorageConfig.getLongMemoryIndexName();

        // Mock GetMappingsResponse with no embedding field
        GetMappingsResponse getMappingsResponse = mock(GetMappingsResponse.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);

        Map<String, Object> properties = new HashMap<>();
        // No memory_embedding field

        Map<String, Object> mappingMap = new HashMap<>();
        mappingMap.put("properties", properties);

        when(mappingMetadata.getSourceAsMap()).thenReturn(mappingMap);
        when(getMappingsResponse.getMappings()).thenReturn(Collections.singletonMap(indexName, mappingMetadata));

        // Setup admin clients
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);

        // Mock getMappings call
        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);
            listener.onResponse(getMappingsResponse);
            return null;
        }).when(indicesAdminClient).getMappings(any(GetMappingsRequest.class), any(ActionListener.class));

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().contains("malformed or missing embedding configuration"));
    }

    @Test
    public void testCreateContainer_IndexExistsPipelineNotFound() throws InterruptedException {
        // Test failure when pipeline doesn't exist
        mockSuccessfulLLMValidation();
        mockSuccessfulIndexCreation();

        // Get the actual index name from config
        String indexName = memoryStorageConfig.getLongMemoryIndexName();

        // Mock GetMappingsResponse with valid mapping
        GetMappingsResponse getMappingsResponse = mock(GetMappingsResponse.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> embeddingField = new HashMap<>();
        embeddingField.put("type", "knn_vector");
        embeddingField.put("dimension", 768);
        properties.put("memory_embedding", embeddingField);

        Map<String, Object> mappingMap = new HashMap<>();
        mappingMap.put("properties", properties);

        when(mappingMetadata.getSourceAsMap()).thenReturn(mappingMap);
        when(getMappingsResponse.getMappings()).thenReturn(Collections.singletonMap(indexName, mappingMetadata));

        // Mock GetPipelineResponse with empty list (pipeline not found)
        GetPipelineResponse getPipelineResponse = mock(GetPipelineResponse.class);
        when(getPipelineResponse.pipelines()).thenReturn(Collections.emptyList());

        // Setup admin clients
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);

        // Mock getMappings call
        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);
            listener.onResponse(getMappingsResponse);
            return null;
        }).when(indicesAdminClient).getMappings(any(GetMappingsRequest.class), any(ActionListener.class));

        // Mock getPipeline call
        doAnswer(invocation -> {
            ActionListener<GetPipelineResponse> listener = invocation.getArgument(1);
            listener.onResponse(getPipelineResponse);
            return null;
        }).when(clusterAdminClient).getPipeline(any(GetPipelineRequest.class), any(ActionListener.class));

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().contains("required ingest pipeline"));
    }

    @Test
    public void testCreateContainer_PipelineExistsNoModelId() throws InterruptedException {
        // Test failure when pipeline exists but has no model_id
        mockSuccessfulLLMValidation();
        mockSuccessfulIndexCreation();

        // Get the actual index name from config
        String indexName = memoryStorageConfig.getLongMemoryIndexName();

        // Mock GetMappingsResponse with valid mapping
        GetMappingsResponse getMappingsResponse = mock(GetMappingsResponse.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> embeddingField = new HashMap<>();
        embeddingField.put("type", "knn_vector");
        embeddingField.put("dimension", 768);
        properties.put("memory_embedding", embeddingField);

        Map<String, Object> mappingMap = new HashMap<>();
        mappingMap.put("properties", properties);

        when(mappingMetadata.getSourceAsMap()).thenReturn(mappingMap);
        when(getMappingsResponse.getMappings()).thenReturn(Collections.singletonMap(indexName, mappingMetadata));

        // Mock GetPipelineResponse with empty processors (no model_id)
        GetPipelineResponse getPipelineResponse = mock(GetPipelineResponse.class);

        // Create real PipelineConfiguration with empty processors (final class, can't be mocked)
        String pipelineJson = "{" + "\"processors\": []" + "}";
        org.opensearch.ingest.PipelineConfiguration pipelineConfig = new org.opensearch.ingest.PipelineConfiguration(
            indexName + "-embedding",
            new BytesArray(pipelineJson),
            org.opensearch.common.xcontent.XContentType.JSON
        );

        when(getPipelineResponse.pipelines()).thenReturn(Collections.singletonList(pipelineConfig));

        // Setup admin clients
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);

        // Mock getMappings call
        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);
            listener.onResponse(getMappingsResponse);
            return null;
        }).when(indicesAdminClient).getMappings(any(GetMappingsRequest.class), any(ActionListener.class));

        // Mock getPipeline call
        doAnswer(invocation -> {
            ActionListener<GetPipelineResponse> listener = invocation.getArgument(1);
            listener.onResponse(getPipelineResponse);
            return null;
        }).when(clusterAdminClient).getPipeline(any(GetPipelineRequest.class), any(ActionListener.class));

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().contains("not configured correctly"));
    }

    @Test
    public void testCreateContainer_GetPipelineValidationError() throws InterruptedException {
        // Test failure when getPipeline throws non-404 error
        mockSuccessfulLLMValidation();
        mockSuccessfulIndexCreation();

        // Get the actual index name from config
        String indexName = memoryStorageConfig.getLongMemoryIndexName();

        // Mock GetMappingsResponse with valid mapping
        GetMappingsResponse getMappingsResponse = mock(GetMappingsResponse.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> embeddingField = new HashMap<>();
        embeddingField.put("type", "knn_vector");
        embeddingField.put("dimension", 768);
        properties.put("memory_embedding", embeddingField);

        Map<String, Object> mappingMap = new HashMap<>();
        mappingMap.put("properties", properties);

        when(mappingMetadata.getSourceAsMap()).thenReturn(mappingMap);
        when(getMappingsResponse.getMappings()).thenReturn(Collections.singletonMap(indexName, mappingMetadata));

        // Setup admin clients
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);

        // Mock getMappings call
        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);
            listener.onResponse(getMappingsResponse);
            return null;
        }).when(indicesAdminClient).getMappings(any(GetMappingsRequest.class), any(ActionListener.class));

        // Mock getPipeline call to throw RuntimeException
        doAnswer(invocation -> {
            ActionListener<GetPipelineResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Pipeline validation error"));
            return null;
        }).when(clusterAdminClient).getPipeline(any(GetPipelineRequest.class), any(ActionListener.class));

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) captor.getValue()).status());
        assertTrue(captor.getValue().getMessage().contains("Internal server error"));
    }

    @Test
    public void testCreateContainer_GetMappingsValidationError() throws InterruptedException {
        // Test failure when getMappings throws non-IndexNotFoundException error
        mockSuccessfulLLMValidation();
        mockSuccessfulIndexCreation();

        // Setup admin clients
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);

        // Mock getMappings call to throw RuntimeException (not IndexNotFoundException)
        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Mapping retrieval error"));
            return null;
        }).when(indicesAdminClient).getMappings(any(GetMappingsRequest.class), any(ActionListener.class));

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, ((OpenSearchStatusException) captor.getValue()).status());
        assertTrue(captor.getValue().getMessage().contains("Internal server error"));
    }

    // Helper method to mock successful LLM validation (without embedding validation which will be tested)
    private void mockSuccessfulLLMValidation() {
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
