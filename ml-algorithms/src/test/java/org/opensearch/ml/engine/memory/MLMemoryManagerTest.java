package org.opensearch.ml.engine.memory;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.AdminClient;
import org.opensearch.client.Client;
import org.opensearch.client.IndicesAdminClient;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.memory.action.conversation.CreateConversationResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.memory.index.ConversationMetaIndex;
import org.opensearch.threadpool.ThreadPool;

public class MLMemoryManagerTest {

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
    ConversationMetaIndex conversationMetaIndex;

    @Mock
    private ThreadPool threadPool;

    MLMemoryManager memoryManager;
    Settings settings;
    ThreadContext threadContext;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        memoryManager = new MLMemoryManager(client, clusterService, conversationMetaIndex);
        doNothing().when(client).execute(any(), any(), any());
        doNothing().when(client).update(any(), any());
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        doNothing().when(indicesAdminClient).refresh(any(), any());
        doNothing().when(conversationMetaIndex).checkAccess(any(), any());
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex(anyString())).thenReturn(true);
        settings = Settings.builder().put("test_key", 10).build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void createConversation() {
        ActionListener<CreateConversationResponse> actionListener = mock(ActionListener.class);
        memoryManager.createConversation("test", "test", actionListener);
    }

    @Test
    public void createInteraction() {
        ActionListener<CreateInteractionResponse> actionListener = mock(ActionListener.class);
        memoryManager.createInteraction("test", "test", "test", "test", "test", Map.of("feedback", "1"), "test", 0, actionListener);
    }

    @Test
    public void createInteractionNullAdditionalInfo() {
        ActionListener<CreateInteractionResponse> actionListener = mock(ActionListener.class);
        memoryManager.createInteraction("test", "test", "test", "test", "test", null, "test", 0, actionListener);
    }

    @Test
    public void getFinalInteractions() {
        ActionListener<List<Interaction>> actionListener = mock(ActionListener.class);
        memoryManager.getFinalInteractions("test", 1, actionListener);
    }

    @Test
    public void innerGetFinalInteractions() {
        ActionListener<List<Interaction>> actionListener = mock(ActionListener.class);
        memoryManager.innerGetFinalInteractions("test", 1, actionListener);
    }

    @Test
    public void getTracesIndex() {
        ActionListener<List<Interaction>> actionListener = mock(ActionListener.class);
        memoryManager.getTraces("test", actionListener);
    }

    @Test
    public void getTracesNoIndex() {
        ActionListener<List<Interaction>> actionListener = mock(ActionListener.class);
        when(metadata.hasIndex(anyString())).thenReturn(false);
        memoryManager.getTraces("test", actionListener);
    }

    @Test
    public void updateInteraction() {
        ActionListener<UpdateResponse> actionListener = mock(ActionListener.class);
        memoryManager.updateInteraction("test", Map.of("feedback", "1"), actionListener);
    }
}
