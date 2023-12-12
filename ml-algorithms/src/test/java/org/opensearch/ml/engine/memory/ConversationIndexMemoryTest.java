package org.opensearch.ml.engine.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.engine.indices.MLIndicesHandler;

public class ConversationIndexMemoryTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    Client client;

    @Mock
    MLIndicesHandler indicesHandler;

    @Mock
    MLMemoryManager memoryManager;

    ConversationIndexMemory indexMemory;
    ConversationIndexMemory.Factory memoryFactory;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        indexMemory = new ConversationIndexMemory(client, indicesHandler, "test", "test", "test", memoryManager);
        doNothing().when(client).index(any(), any());
        doNothing().when(client).search(any(), any());
        doNothing().when(client).get(any(), any());
        doNothing().when(memoryManager).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
        doNothing().when(memoryManager).getFinalInteractions(any(), anyInt(), any());
        doNothing().when(memoryManager).createConversation(any(), any(), any());
        doNothing().when(indicesHandler).initMemoryMetaIndex(any());
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("test failure"));
            return null;
        }).when(indicesHandler).initMemoryMessageIndex(any());
        memoryFactory = new ConversationIndexMemory.Factory();
        memoryFactory.init(client, indicesHandler, memoryManager);
    }

    @Test
    public void save() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(indicesHandler).initMemoryMessageIndex(any());
        indexMemory.save("test_id", new ConversationIndexMessage("test", "123", "question", "response", false));
    }

    @Test
    public void save1() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(indicesHandler).initMemoryMessageIndex(any());
        indexMemory.save(new ConversationIndexMessage("test", "123", "question", "response", false), "parent_id", 0, "action");
    }

    @Test
    public void getMessages() {
        ActionListener<SearchResponse> listener = mock(ActionListener.class);
        indexMemory.getMessages("test_id", listener);
    }

    @Test
    public void getMessages1() {
        ActionListener<SearchResponse> listener = mock(ActionListener.class);
        indexMemory.getMessages(listener);
    }

    @Test
    public void clear() {
        indexMemory.clear();
    }

    @Test
    public void remove() {
        indexMemory.remove("test_id");
    }

    @Test
    public void factory_create_emptyMap() {
        ActionListener<ConversationIndexMemory> listener = mock(ActionListener.class);
        memoryFactory.create(Map.of(), listener);
    }

    @Test
    public void factory_create_session() {
        ActionListener<ConversationIndexMemory> listener = mock(ActionListener.class);
        memoryFactory.create(Map.of("memory_index_name", "test", "memory_message_index_name", "test", "session_id", "123"), listener);
    }

    @Test
    public void factory_create_question() {
        ActionListener<ConversationIndexMemory> listener = mock(ActionListener.class);
        memoryFactory.create(Map.of("memory_index_name", "test", "memory_message_index_name", "test", "question", "question"), listener);
    }

    @Test
    public void factory_create_no_question_no_session() {
        ActionListener<ConversationIndexMemory> listener = mock(ActionListener.class);
        memoryFactory.create(Map.of("memory_index_name", "test", "memory_message_index_name", "test"), listener);
    }

    @Test
    public void factory_create_with_memory() {
        ActionListener<ConversationIndexMemory> listener = mock(ActionListener.class);
        memoryFactory.create("test", "memory_id", "test", listener);
    }

    @Test
    public void factory_create_without_memory() {
        ActionListener<ConversationIndexMemory> listener = mock(ActionListener.class);
        memoryFactory.create("test", null, "test", listener);
    }
}
