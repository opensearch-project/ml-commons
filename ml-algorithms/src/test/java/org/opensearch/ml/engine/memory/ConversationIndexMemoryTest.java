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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.memory.action.conversation.CreateConversationResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.transport.client.Client;

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
    public void getType() {
        Assert.assertEquals(indexMemory.getType(), ConversationIndexMemory.TYPE);
    }

    // @Test
    // public void save() {
    // doAnswer(invocation -> {
    // ActionListener<Boolean> listener = invocation.getArgument(0);
    // listener.onResponse(true);
    // return null;
    // }).when(indicesHandler).initMemoryMessageIndex(any());
    // indexMemory.save("test_id", new ConversationIndexMessage("test", "123", "question", "response", false));
    //
    // verify(indicesHandler).initMemoryMessageIndex(any());
    // }

    // @Test
    // public void save4() {
    // doAnswer(invocation -> {
    // ActionListener<Boolean> listener = invocation.getArgument(0);
    // listener.onFailure(new RuntimeException());
    // return null;
    // }).when(indicesHandler).initMemoryMessageIndex(any());
    // indexMemory.save("test_id", new ConversationIndexMessage("test", "123", "question", "response", false));
    //
    // verify(indicesHandler).initMemoryMessageIndex(any());
    // }

    @Test
    public void save1() {
        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> listener = invocation.getArgument(8);
            listener.onResponse(new CreateInteractionResponse("interaction_id"));
            return null;
        }).when(memoryManager).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
        indexMemory.save(new ConversationIndexMessage("test", "123", "question", "response", false), "parent_id", 0, "action");

        verify(memoryManager).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void save6() {
        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> listener = invocation.getArgument(8);
            listener.onFailure(new RuntimeException());
            return null;
        }).when(memoryManager).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
        indexMemory.save(new ConversationIndexMessage("test", "123", "question", "response", false), "parent_id", 0, "action");

        verify(memoryManager).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // @Test
    // public void save2() {
    // doAnswer(invocation -> {
    // ActionListener<Boolean> listener = invocation.getArgument(0);
    // listener.onResponse(Boolean.TRUE);
    // return null;
    // }).when(indicesHandler).initMemoryMessageIndex(any());
    // doAnswer(invocation -> {
    // ActionListener<IndexResponse> listener = invocation.getArgument(1);
    // listener.onResponse(new IndexResponse(new ShardId("test", "test", 1), "test", 1l, 1l, 1l, true));
    // return null;
    // }).when(client).index(any(), any());
    // ActionListener<IndexResponse> actionListener = mock(ActionListener.class);
    // indexMemory.save("test_id", new ConversationIndexMessage("test", "123", "question", "response", false), actionListener);
    //
    // verify(actionListener).onResponse(isA(IndexResponse.class));
    // }

    // @Test
    // public void save3() {
    // doAnswer(invocation -> {
    // ActionListener<Boolean> listener = invocation.getArgument(0);
    // listener.onFailure(new RuntimeException());
    // return null;
    // }).when(indicesHandler).initMemoryMessageIndex(any());
    // ActionListener<Boolean> actionListener = mock(ActionListener.class);
    // indexMemory.save("test_id", new ConversationIndexMessage("test", "123", "question", "response", false), actionListener);
    //
    // verify(actionListener).onFailure(isA(RuntimeException.class));
    // }

    // @Test
    // public void save5() {
    // doAnswer(invocation -> {
    // ActionListener<Boolean> listener = invocation.getArgument(0);
    // listener.onResponse(Boolean.FALSE);
    // return null;
    // }).when(indicesHandler).initMemoryMessageIndex(any());
    // doAnswer(invocation -> {
    // ActionListener<IndexResponse> listener = invocation.getArgument(1);
    // listener.onResponse(new IndexResponse(new ShardId("test", "test", 1), "test", 1l, 1l, 1l, true));
    // return null;
    // }).when(client).index(any(), any());
    // ActionListener<IndexResponse> actionListener = mock(ActionListener.class);
    // indexMemory.save("test_id", new ConversationIndexMessage("test", "123", "question", "response", false), actionListener);
    //
    // verify(actionListener).onFailure(isA(RuntimeException.class));
    // }

    @Test
    public void clear() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("clear method is not supported in ConversationIndexMemory");
        indexMemory.clear();
    }

    // @Test
    // public void remove() {
    // exceptionRule.expect(RuntimeException.class);
    // exceptionRule.expectMessage("remove method is not supported in ConversationIndexMemory");
    // indexMemory.remove("test_id");
    // }

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
    public void factory_create_only_memory_id() {
        ActionListener<ConversationIndexMemory> listener = mock(ActionListener.class);
        memoryFactory.create(Map.of(MEMORY_ID, "123"), listener);

        verify(listener).onResponse(isA(ConversationIndexMemory.class));
    }

    @Test
    public void factory_create_empty_memory_id() {
        doAnswer(invocation -> {
            ActionListener<CreateConversationResponse> listener = invocation.getArgument(2);
            listener.onResponse(new CreateConversationResponse("interaction_id"));
            return null;
        }).when(memoryManager).createConversation(any(), any(), any());
        ActionListener<ConversationIndexMemory> listener = mock(ActionListener.class);
        memoryFactory.create(Map.of(MEMORY_NAME, "name", APP_TYPE, "app"), listener);

        verify(listener).onResponse(isA(ConversationIndexMemory.class));
        verify(memoryManager).createConversation(any(), any(), any());
    }

    @Test
    public void factory_create_empty_memory_id_failure() {
        doAnswer(invocation -> {
            ActionListener<CreateConversationResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException());
            return null;
        }).when(memoryManager).createConversation(any(), any(), any());
        ActionListener<ConversationIndexMemory> listener = mock(ActionListener.class);
        memoryFactory.create(Map.of(MEMORY_NAME, "name", APP_TYPE, "app"), listener);

        verify(listener).onFailure(isA(RuntimeException.class));
        verify(memoryManager).createConversation(any(), any(), any());
    }
}
