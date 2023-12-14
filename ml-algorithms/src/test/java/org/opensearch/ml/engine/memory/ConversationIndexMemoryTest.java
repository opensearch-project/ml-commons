package org.opensearch.ml.engine.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.APP_TYPE;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.MEMORY_ID;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.MEMORY_NAME;

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
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("clear method is not supported in ConversationIndexMemory");
        indexMemory.clear();
    }

    @Test
    public void remove() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("remove method is not supported in ConversationIndexMemory");
        indexMemory.remove("test_id");
    }

    @Test
    public void factory_create_emptyMap() {
        ActionListener<ConversationIndexMemory> listener = mock(ActionListener.class);
        memoryFactory.create(Map.of(), listener);

        verify(listener).onFailure(isA(IllegalArgumentException.class));
    }

    @Test
    public void factory_create() {
        ActionListener<ConversationIndexMemory> listener = mock(ActionListener.class);
        memoryFactory.create(Map.of(MEMORY_ID, "123", MEMORY_NAME, "name", APP_TYPE, "app"), listener);

        verify(listener).onResponse(isA(ConversationIndexMemory.class));
    }

    @Test
    public void factory_create_no_memory_id() {
        ActionListener<ConversationIndexMemory> listener = mock(ActionListener.class);
        memoryFactory.create(Map.of(MEMORY_NAME, "name", APP_TYPE, "app"), listener);

        verify(listener).onFailure(isA(IllegalArgumentException.class));
    }

    @Test
    public void factory_create_only_memory_id() {
        ActionListener<ConversationIndexMemory> listener = mock(ActionListener.class);
        memoryFactory.create(Map.of(MEMORY_ID, "123"), listener);

        verify(listener).onResponse(isA(ConversationIndexMemory.class));
    }
}
