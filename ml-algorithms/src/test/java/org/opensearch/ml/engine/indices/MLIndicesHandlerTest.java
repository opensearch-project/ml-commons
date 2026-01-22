package org.opensearch.ml.engine.indices;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.META;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_JOBS_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_MESSAGE_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_META_INDEX;
import static org.opensearch.ml.common.CommonValue.SCHEMA_VERSION_FIELD;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchWrapperException;
import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.IndicesAdminClient;

public class MLIndicesHandlerTest {

    @Mock
    Client client;

    @Mock
    AdminClient adminClient;

    @Mock
    IndicesAdminClient indicesAdminClient;

    @Mock
    ClusterService clusterService;

    @Mock
    ClusterState clusterState;

    @Mock
    Metadata metadata;

    @Mock
    IndexMetadata agentindexMetadata;
    @Mock
    IndexMetadata memorymetaindexMetadata;

    @Mock
    MappingMetadata agentmappingMetadata;

    @Mock
    MappingMetadata memorymappingMetadata;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    Settings settings;
    ThreadContext threadContext;
    MLIndicesHandler indicesHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        doNothing().when(client).execute(any(), any(), any());
        doNothing().when(client).update(any(), any());
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        doNothing().when(indicesAdminClient).create(any(), any());
        doNothing().when(indicesAdminClient).refresh(any(), any());
        doNothing().when(indicesAdminClient).putMapping(any(), any());
        doNothing().when(indicesAdminClient).updateSettings(any(), any());
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(clusterState.getMetadata()).thenReturn(metadata);
        when(metadata.hasIndex(anyString())).thenReturn(true);
        when(metadata.indices()).thenReturn(Map.of(ML_AGENT_INDEX, agentindexMetadata, ML_MEMORY_META_INDEX, memorymetaindexMetadata));
        when(agentindexMetadata.mapping()).thenReturn(agentmappingMetadata);
        when(memorymetaindexMetadata.mapping()).thenReturn(memorymappingMetadata);
        when(agentmappingMetadata.getSourceAsMap()).thenReturn(Map.of(META, Map.of(SCHEMA_VERSION_FIELD, 3)));
        when(memorymappingMetadata.getSourceAsMap()).thenReturn(Map.of(META, Map.of(SCHEMA_VERSION_FIELD, 2)));
        settings = Settings.builder().put("test_key", 10).build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        indicesHandler = new MLIndicesHandler(clusterService, client, mlFeatureEnabledSetting);
    }

    @Test
    public void doesMultiTenantIndexExist_multiTenancyEnabled_returnsTrue() {
        assertTrue(MLIndicesHandler.doesMultiTenantIndexExist(null, true, null));
        MLIndicesHandler mlIndicesHandler = new MLIndicesHandler(clusterService, client, mlFeatureEnabledSetting);
        assertTrue(mlIndicesHandler.doesIndexExists(ML_CONFIG_INDEX));
    }

    @Test
    public void doesMultiTenantIndexExist_multiTenancyDisabledSearchesClusterService_returnsValidSearchResult() {
        assertFalse(MLIndicesHandler.doesMultiTenantIndexExist(clusterService, false, null));

        String sampleIndexName = "test-index";
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        MLIndicesHandler mlIndicesHandler = new MLIndicesHandler(clusterService, client, mlFeatureEnabledSetting);

        when(clusterService.state().metadata().hasIndex(sampleIndexName)).thenReturn(true);
        assertTrue(mlIndicesHandler.doesIndexExists(sampleIndexName));

        when(clusterService.state().metadata().hasIndex(sampleIndexName)).thenReturn(false);
        assertFalse(mlIndicesHandler.doesIndexExists(sampleIndexName));
    }

    @Test
    public void initMemoryMetaIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new AcknowledgedResponse(true));
            return null;
        }).when(indicesAdminClient).putMapping(any(), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        indicesHandler.initMemoryMetaIndex(listener);

        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void initMemoryMetaIndexNoIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, ML_MEMORY_META_INDEX));
            return null;
        }).when(indicesAdminClient).create(any(), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        indicesHandler.initMemoryMetaIndex(listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void initMemoryMessageIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new AcknowledgedResponse(true));
            return null;
        }).when(indicesAdminClient).putMapping(any(), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        indicesHandler.initMemoryMessageIndex(listener);

        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void initMemoryMessageIndexNoIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, ML_MEMORY_MESSAGE_INDEX));
            return null;
        }).when(indicesAdminClient).create(any(), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        indicesHandler.initMemoryMessageIndex(listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void initMLAgentIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(agentmappingMetadata.getSourceAsMap()).thenReturn(Map.of(META, Map.of(SCHEMA_VERSION_FIELD, 3)));
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new AcknowledgedResponse(true));
            return null;
        }).when(indicesAdminClient).putMapping(any(), any());
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new AcknowledgedResponse(true));
            return null;
        }).when(indicesAdminClient).updateSettings(any(), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        indicesHandler.initMLAgentIndex(listener);

        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void initMLAgentIndexNoIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, ML_AGENT_INDEX));
            return null;
        }).when(indicesAdminClient).create(any(), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        indicesHandler.initMLAgentIndex(listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void initMLConnectorIndex_ResourceAlreadyExistsException_RaceCondition() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new ResourceAlreadyExistsException("index [.plugins-ml-connector] already exists"));
            return null;
        }).when(indicesAdminClient).create(any(), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        indicesHandler.initMLConnectorIndex(listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void initMLJobsIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new AcknowledgedResponse(true));
            return null;
        }).when(indicesAdminClient).putMapping(any(), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        indicesHandler.initMLJobsIndex(listener);

        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void initMLJobsIndexNoIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, ML_JOBS_INDEX));
            return null;
        }).when(indicesAdminClient).create(any(), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        indicesHandler.initMLJobsIndex(listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void testInitIndexWithContext_Acknowledged() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.initIndexWithThreadContext("test-index", "{}", null, 1, listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void testInitIndexWithContext_NotAcknowledged() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(false, false, "test-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.initIndexWithThreadContext("test-index", "{}", null, 1, listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(false, argumentCaptor.getValue());
    }

    @Test
    public void testInitIndexWithContext_ResourceAlreadyExists() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new ResourceAlreadyExistsException("index [test-index] already exists"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.initIndexWithThreadContext("test-index", "{}", null, 1, listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void testInitIndexWithContext_WrappedResourceAlreadyExists() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            Exception wrappedException = new TestWrappedException(new ResourceAlreadyExistsException("index [test-index] already exists"));
            actionListener.onFailure(wrappedException);
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.initIndexWithThreadContext("test-index", "{}", null, 1, listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    // Helper class that implements OpenSearchWrapperException for testing
    public static class TestWrappedException extends Exception implements OpenSearchWrapperException {
        private final Throwable cause;

        public TestWrappedException(Throwable cause) {
            this.cause = cause;
        }

        @Override
        public synchronized Throwable getCause() {
            return cause;
        }
    }

    @Test
    public void testInitIndexWithContext_OtherException() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        RuntimeException testException = new RuntimeException("test failure");
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(testException);
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.initIndexWithThreadContext("test-index", "{}", null, 1, listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(testException, argumentCaptor.getValue());
    }

    @Test
    public void testInitIndexWithContext_WithCustomSettings() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        Map<String, Object> customSettings = new HashMap<>();
        customSettings.put("index.number_of_replicas", "1");
        indicesHandler.initIndexWithThreadContext("test-index", "{}", customSettings, 1, listener);

        ArgumentCaptor<CreateIndexRequest> requestCaptor = ArgumentCaptor.forClass(CreateIndexRequest.class);
        verify(indicesAdminClient).create(requestCaptor.capture(), any());
        assertEquals("test-index", requestCaptor.getValue().index());
    }

    @Test
    public void testCreateSessionMemoryDataIndex_NotSystemIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);

        // Create configuration with useSystemIndex = false
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(false).build();

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-session-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.createSessionMemoryDataIndex("test-session-index", config, listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void testCreateSessionMemoryDataIndex_WithNullIndexSettings() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);

        // Create configuration with useSystemIndex = true and null index settings
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).indexSettings(null).build();

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-session-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.createSessionMemoryDataIndex("test-session-index", config, listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
    }

    @Test
    public void testCreateSessionMemoryDataIndex_WithEmptyIndexSettings() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);

        // Create configuration with useSystemIndex = true and empty index settings
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).indexSettings(new HashMap<>()).build();

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-session-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.createSessionMemoryDataIndex("test-session-index", config, listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
    }

    @Test
    public void testCreateWorkingMemoryDataIndex_NotSystemIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);

        // Create configuration with useSystemIndex = false
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(false).build();

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-working-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.createWorkingMemoryDataIndex("test-working-index", config, listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void testCreateWorkingMemoryDataIndex_WithEmptyIndexSettings() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);

        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).indexSettings(new HashMap<>()).build();

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-working-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.createWorkingMemoryDataIndex("test-working-index", config, listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
    }

    @Test
    public void testCreateLongTermMemoryHistoryIndex_NotSystemIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);

        // Create configuration with useSystemIndex = false
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(false).build();

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-history-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.createLongTermMemoryHistoryIndex("test-history-index", config, listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void testCreateLongTermMemoryHistoryIndex_WithEmptyIndexSettings() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);

        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).indexSettings(new HashMap<>()).build();

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-history-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.createLongTermMemoryHistoryIndex("test-history-index", config, listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
    }

    @Test
    public void testCreateLongTermMemoryIndex_NotSystemIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);

        // Create configuration with useSystemIndex = false
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(false).build();

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-ltm-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.createLongTermMemoryIndex(null, "test-ltm-index", config, listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void testCreateLongTermMemoryIndex_WithEmptyIndexSettings() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);

        // Create configuration with useSystemIndex = true and empty index settings (tests default settings branch)
        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).indexSettings(new HashMap<>()).build();

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-ltm-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.createLongTermMemoryIndex(null, "test-ltm-index", config, listener);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
    }

    @Test
    public void testCreateLongTermMemoryIndex_WithPipeline() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);

        MemoryConfiguration config = MemoryConfiguration.builder().useSystemIndex(true).build();

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-ltm-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        indicesHandler.createLongTermMemoryIndex("my-pipeline", "test-ltm-index", config, listener);

        ArgumentCaptor<CreateIndexRequest> requestCaptor = ArgumentCaptor.forClass(CreateIndexRequest.class);
        verify(indicesAdminClient).create(requestCaptor.capture(), any());
        // Verify pipeline was set in the index settings
        assertTrue(requestCaptor.getValue().settings().get("default_pipeline") != null);
    }

    @Test
    public void testInitIndexIfAbsent_WithIsSystemIndexTrue() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        // When isSystemIndex=true, should use initIndexWithoutThreadContext (stash context)
        indicesHandler.initIndexIfAbsent("test-index", "{}", null, 1, listener, true);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void testInitIndexIfAbsent_WithIsSystemIndexFalse() {
        ActionListener<Boolean> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        // When isSystemIndex=false, should use initIndexWithThreadContext (keep context)
        indicesHandler.initIndexIfAbsent("test-index", "{}", null, 1, listener, false);

        verify(indicesAdminClient).create(isA(CreateIndexRequest.class), any());
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue());
    }

    @Test
    public void testInitIndexIfAbsent_WithIndexSettings_IsSystemIndexTrue() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        Map<String, Object> customSettings = new HashMap<>();
        customSettings.put("index.number_of_replicas", "1");

        // When isSystemIndex=true with custom settings
        indicesHandler.initIndexIfAbsent("test-index", "{}", customSettings, 1, listener, true);

        ArgumentCaptor<CreateIndexRequest> requestCaptor = ArgumentCaptor.forClass(CreateIndexRequest.class);
        verify(indicesAdminClient).create(requestCaptor.capture(), any());
        assertEquals("test-index", requestCaptor.getValue().index());
    }

    @Test
    public void testInitIndexIfAbsent_WithIndexSettings_IsSystemIndexFalse() {
        ActionListener<Boolean> listener = mock(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new CreateIndexResponse(true, true, "test-index"));
            return null;
        }).when(indicesAdminClient).create(any(), any());

        Map<String, Object> customSettings = new HashMap<>();
        customSettings.put("index.number_of_replicas", "1");

        // When isSystemIndex=false with custom settings
        indicesHandler.initIndexIfAbsent("test-index", "{}", customSettings, 1, listener, false);

        ArgumentCaptor<CreateIndexRequest> requestCaptor = ArgumentCaptor.forClass(CreateIndexRequest.class);
        verify(indicesAdminClient).create(requestCaptor.capture(), any());
        assertEquals("test-index", requestCaptor.getValue().index());
    }
}
