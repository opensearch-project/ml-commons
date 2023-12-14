package org.opensearch.ml.engine.indices;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.META;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.CommonValue.SCHEMA_VERSION_FIELD;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.AdminClient;
import org.opensearch.client.Client;
import org.opensearch.client.IndicesAdminClient;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.threadpool.ThreadPool;

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
    IndexMetadata indexMetadata;

    @Mock
    MappingMetadata mappingMetadata;

    @Mock
    private ThreadPool threadPool;

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
        when(metadata.indices()).thenReturn(Map.of(ML_AGENT_INDEX, indexMetadata));
        when(indexMetadata.mapping()).thenReturn(mappingMetadata);
        when(mappingMetadata.getSourceAsMap()).thenReturn(Map.of(META, Map.of(SCHEMA_VERSION_FIELD, Integer.valueOf(1))));
        settings = Settings.builder().put("test_key", 10).build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        indicesHandler = new MLIndicesHandler(clusterService, client);
    }

    @Test
    public void initMemoryMetaIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        indicesHandler.initMemoryMetaIndex(listener);
    }

    @Test
    public void initMemoryMetaIndexNoIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);
        indicesHandler.initMemoryMetaIndex(listener);
    }

    @Test
    public void initMemoryMessageIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        indicesHandler.initMemoryMessageIndex(listener);
    }

    @Test
    public void initMemoryMessageIndexNoIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);
        indicesHandler.initMemoryMessageIndex(listener);
    }

    @Test
    public void initMLAgentIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        indicesHandler.initMLAgentIndex(listener);
    }

    @Test
    public void initMLAgentIndexNoIndex() {
        ActionListener<Boolean> listener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);
        indicesHandler.initMLAgentIndex(listener);
    }
}
