package org.opensearch.ml.engine.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.APP_TYPE;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.MEMORY_ID;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.MEMORY_NAME;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.execute.agent.ToolCall;
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

    @Test
    public void clear() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("clear method is not supported in ConversationIndexMemory");
        indexMemory.clear();
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

    // ==================== Tests for saveStructuredMessages ====================

    @Test
    public void saveStructuredMessages_nullMessages() {
        ActionListener<Void> listener = mock(ActionListener.class);
        indexMemory.saveStructuredMessages(null, listener);
        verify(listener).onResponse(null);
        verify(memoryManager, never()).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void saveStructuredMessages_emptyMessages() {
        ActionListener<Void> listener = mock(ActionListener.class);
        indexMemory.saveStructuredMessages(Collections.emptyList(), listener);
        verify(listener).onResponse(null);
    }

    @Test
    public void saveStructuredMessages_singlePair_success() {
        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("What is AI?");
        Message userMsg = new Message("user", Collections.singletonList(userBlock));

        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("AI is artificial intelligence.");
        Message assistantMsg = new Message("assistant", Collections.singletonList(assistantBlock));

        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> l = invocation.getArgument(8);
            l.onResponse(new CreateInteractionResponse("interaction_1"));
            return null;
        }).when(memoryManager).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());

        ActionListener<Void> listener = mock(ActionListener.class);
        indexMemory.saveStructuredMessages(Arrays.asList(userMsg, assistantMsg), listener);

        verify(listener).onResponse(null);
        verify(memoryManager, times(1)).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void saveStructuredMessages_continuesOnFailure_reportsError() {
        // Two user/assistant pairs
        ContentBlock u1 = new ContentBlock();
        u1.setType(ContentType.TEXT);
        u1.setText("Q1");
        ContentBlock a1 = new ContentBlock();
        a1.setType(ContentType.TEXT);
        a1.setText("A1");
        ContentBlock u2 = new ContentBlock();
        u2.setType(ContentType.TEXT);
        u2.setText("Q2");
        ContentBlock a2 = new ContentBlock();
        a2.setType(ContentType.TEXT);
        a2.setText("A2");

        List<Message> messages = Arrays
            .asList(
                new Message("user", Collections.singletonList(u1)),
                new Message("assistant", Collections.singletonList(a1)),
                new Message("user", Collections.singletonList(u2)),
                new Message("assistant", Collections.singletonList(a2))
            );

        // First save fails, second succeeds
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> l = invocation.getArgument(8);
            if (callCount.getAndIncrement() == 0) {
                l.onFailure(new RuntimeException("Save failed"));
            } else {
                l.onResponse(new CreateInteractionResponse("interaction_2"));
            }
            return null;
        }).when(memoryManager).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());

        ActionListener<Void> listener = mock(ActionListener.class);
        indexMemory.saveStructuredMessages(messages, listener);

        // Should report failure since one pair failed
        verify(listener).onFailure(isA(RuntimeException.class));
        // Both saves should have been attempted
        verify(memoryManager, times(2)).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void saveStructuredMessages_onlyUserMessage_savedAsIncompleteInteraction() {
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText("Just a question");
        Message userMsg = new Message("user", Collections.singletonList(block));

        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> l = invocation.getArgument(8);
            l.onResponse(new CreateInteractionResponse("trailing_interaction_1"));
            return null;
        }).when(memoryManager).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());

        ActionListener<Void> listener = mock(ActionListener.class);
        indexMemory.saveStructuredMessages(Collections.singletonList(userMsg), listener);

        // Trailing user message saved as incomplete interaction (question + empty response)
        verify(listener).onResponse(null);
        verify(memoryManager, times(1)).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
        Assert.assertEquals("trailing_interaction_1", indexMemory.getLastIncompleteInteractionId().get());
    }

    // ==================== Tests for getStructuredMessages ====================

    @Test
    public void getStructuredMessages_convertsInteractions() {
        List<org.opensearch.ml.common.conversation.Interaction> interactions = Arrays
            .asList(
                org.opensearch.ml.common.conversation.Interaction.builder().id("i1").input("Question 1").response("Answer 1").build(),
                org.opensearch.ml.common.conversation.Interaction.builder().id("i2").input("Question 2").response("Answer 2").build()
            );

        doAnswer(invocation -> {
            ActionListener<List<org.opensearch.ml.common.conversation.Interaction>> l = invocation.getArgument(2);
            l.onResponse(interactions);
            return null;
        }).when(memoryManager).getFinalInteractions(any(), anyInt(), any());

        ActionListener<List<Message>> listener = mock(ActionListener.class);
        indexMemory.getStructuredMessages(listener);

        org.mockito.ArgumentCaptor<List<Message>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(listener).onResponse(captor.capture());
        List<Message> result = captor.getValue();
        // 2 interactions * 2 (user + assistant) = 4 messages
        Assert.assertEquals(4, result.size());
        Assert.assertEquals("user", result.get(0).getRole());
        Assert.assertEquals("assistant", result.get(1).getRole());
    }

    @Test
    public void getStructuredMessages_failure() {
        doAnswer(invocation -> {
            ActionListener<?> l = invocation.getArgument(2);
            l.onFailure(new RuntimeException("Retrieval failed"));
            return null;
        }).when(memoryManager).getFinalInteractions(any(), anyInt(), any());

        ActionListener<List<Message>> listener = mock(ActionListener.class);
        indexMemory.getStructuredMessages(listener);

        verify(listener).onFailure(isA(RuntimeException.class));
    }

    // ==================== Tests for message filtering in saveStructuredMessages ====================

    @Test
    public void saveStructuredMessages_filtersToolRoleMessages() {
        // A user message, a tool-result message (role="tool"), and an assistant text message
        Message userMsg = createTextMessage("user", "What's the weather?");

        Message toolMsg = new Message();
        toolMsg.setRole("tool");
        ContentBlock toolBlock = new ContentBlock();
        toolBlock.setType(ContentType.TEXT);
        toolBlock.setText("72F and sunny");
        toolMsg.setContent(Collections.singletonList(toolBlock));
        toolMsg.setToolCallId("call-123");

        Message assistantMsg = createTextMessage("assistant", "The weather is 72F and sunny.");

        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> l = invocation.getArgument(8);
            l.onResponse(new CreateInteractionResponse("interaction_1"));
            return null;
        }).when(memoryManager).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());

        ActionListener<Void> listener = mock(ActionListener.class);
        indexMemory.saveStructuredMessages(Arrays.asList(userMsg, toolMsg, assistantMsg), listener);

        // Tool message should be filtered out; only the user/assistant pair is saved
        verify(listener).onResponse(null);
        verify(memoryManager, times(1)).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void saveStructuredMessages_filtersAssistantToolCallMessages() {
        // User message, assistant message with tool calls, tool result, assistant final answer
        Message userMsg = createTextMessage("user", "Calculate 2+2");

        Message assistantToolCallMsg = new Message();
        assistantToolCallMsg.setRole("assistant");
        ContentBlock tcBlock = new ContentBlock();
        tcBlock.setType(ContentType.TEXT);
        tcBlock.setText("Let me calculate that.");
        assistantToolCallMsg.setContent(Collections.singletonList(tcBlock));
        List<ToolCall> toolCalls = new ArrayList<>();
        toolCalls.add(new ToolCall("call-1", "function", new ToolCall.ToolFunction("calculator", "{\"expr\":\"2+2\"}")));
        assistantToolCallMsg.setToolCalls(toolCalls);

        Message toolResultMsg = new Message();
        toolResultMsg.setRole("tool");
        ContentBlock resultBlock = new ContentBlock();
        resultBlock.setType(ContentType.TEXT);
        resultBlock.setText("4");
        toolResultMsg.setContent(Collections.singletonList(resultBlock));
        toolResultMsg.setToolCallId("call-1");

        Message assistantFinalMsg = createTextMessage("assistant", "2+2 equals 4.");

        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> l = invocation.getArgument(8);
            l.onResponse(new CreateInteractionResponse("interaction_1"));
            return null;
        }).when(memoryManager).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());

        ActionListener<Void> listener = mock(ActionListener.class);
        indexMemory.saveStructuredMessages(Arrays.asList(userMsg, assistantToolCallMsg, toolResultMsg, assistantFinalMsg), listener);

        // Only user + final assistant text remain after filtering → 1 pair saved
        verify(listener).onResponse(null);
        verify(memoryManager, times(1)).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void saveStructuredMessages_filtersMultimodalMessages() {
        // A user message with an image block should be skipped
        Message userTextMsg = createTextMessage("user", "Hello");

        Message userImageMsg = new Message();
        userImageMsg.setRole("user");
        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        userImageMsg.setContent(Collections.singletonList(imageBlock));

        Message assistantMsg = createTextMessage("assistant", "Hi there!");

        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> l = invocation.getArgument(8);
            l.onResponse(new CreateInteractionResponse("interaction_1"));
            return null;
        }).when(memoryManager).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());

        ActionListener<Void> listener = mock(ActionListener.class);
        indexMemory.saveStructuredMessages(Arrays.asList(userTextMsg, userImageMsg, assistantMsg), listener);

        // Image message filtered; text user + assistant pair saved
        verify(listener).onResponse(null);
        verify(memoryManager, times(1)).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void saveStructuredMessages_allMessagesFilteredOut_returnsNull() {
        // Only tool and tool-call messages — all get filtered
        Message toolMsg = new Message();
        toolMsg.setRole("tool");
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText("result");
        toolMsg.setContent(Collections.singletonList(block));

        Message assistantToolCallMsg = new Message();
        assistantToolCallMsg.setRole("assistant");
        List<ToolCall> toolCalls = new ArrayList<>();
        toolCalls.add(new ToolCall("call-1", "function", new ToolCall.ToolFunction("search", "{}")));
        assistantToolCallMsg.setToolCalls(toolCalls);

        ActionListener<Void> listener = mock(ActionListener.class);
        indexMemory.saveStructuredMessages(Arrays.asList(toolMsg, assistantToolCallMsg), listener);

        // All messages filtered → early return with null
        verify(listener).onResponse(null);
        verify(memoryManager, never()).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void saveStructuredMessages_nullMessagesInList_skipped() {
        Message userMsg = createTextMessage("user", "Question");
        Message assistantMsg = createTextMessage("assistant", "Answer");

        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> l = invocation.getArgument(8);
            l.onResponse(new CreateInteractionResponse("interaction_1"));
            return null;
        }).when(memoryManager).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());

        ActionListener<Void> listener = mock(ActionListener.class);
        indexMemory.saveStructuredMessages(Arrays.asList(null, userMsg, null, assistantMsg), listener);

        // Null entries skipped; user/assistant pair saved
        verify(listener).onResponse(null);
        verify(memoryManager, times(1)).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void saveStructuredMessages_mixedToolConversation_onlyTextQAPairsSaved() {
        // Full tool-use conversation: user → assistant(tool_call) → tool → assistant(text)
        // repeated twice
        Message user1 = createTextMessage("user", "Q1");

        Message assistantTC1 = new Message();
        assistantTC1.setRole("assistant");
        List<ToolCall> tc1 = new ArrayList<>();
        tc1.add(new ToolCall("c1", "function", new ToolCall.ToolFunction("fn1", "{}")));
        assistantTC1.setToolCalls(tc1);

        Message tool1 = new Message();
        tool1.setRole("tool");
        ContentBlock t1Block = new ContentBlock();
        t1Block.setType(ContentType.TEXT);
        t1Block.setText("result1");
        tool1.setContent(Collections.singletonList(t1Block));

        Message assistantFinal1 = createTextMessage("assistant", "A1");

        Message user2 = createTextMessage("user", "Q2");

        Message assistantTC2 = new Message();
        assistantTC2.setRole("assistant");
        List<ToolCall> tc2 = new ArrayList<>();
        tc2.add(new ToolCall("c2", "function", new ToolCall.ToolFunction("fn2", "{}")));
        assistantTC2.setToolCalls(tc2);

        Message tool2 = new Message();
        tool2.setRole("tool");
        ContentBlock t2Block = new ContentBlock();
        t2Block.setType(ContentType.TEXT);
        t2Block.setText("result2");
        tool2.setContent(Collections.singletonList(t2Block));

        Message assistantFinal2 = createTextMessage("assistant", "A2");

        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> l = invocation.getArgument(8);
            l.onResponse(new CreateInteractionResponse("interaction_x"));
            return null;
        }).when(memoryManager).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());

        ActionListener<Void> listener = mock(ActionListener.class);
        indexMemory
            .saveStructuredMessages(
                Arrays.asList(user1, assistantTC1, tool1, assistantFinal1, user2, assistantTC2, tool2, assistantFinal2),
                listener
            );

        // After filtering: user1, assistantFinal1, user2, assistantFinal2 → 2 pairs
        verify(listener).onResponse(null);
        verify(memoryManager, times(2)).createInteraction(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void saveStructuredMessages_pendingInteraction_usesFilteredMessages() {
        // Set up a pending incomplete interaction
        indexMemory.getLastIncompleteInteractionId().set("pending_id_123");

        // Messages include a tool result (should be filtered) and an assistant text answer
        Message toolMsg = new Message();
        toolMsg.setRole("tool");
        ContentBlock tBlock = new ContentBlock();
        tBlock.setType(ContentType.TEXT);
        tBlock.setText("tool output");
        toolMsg.setContent(Collections.singletonList(tBlock));

        Message assistantMsg = createTextMessage("assistant", "Here is the answer.");

        doAnswer(invocation -> {
            ActionListener<org.opensearch.action.update.UpdateResponse> l = invocation.getArgument(2);
            l.onResponse(null);
            return null;
        }).when(memoryManager).updateInteraction(any(), any(), any());

        ActionListener<Void> listener = mock(ActionListener.class);
        indexMemory.saveStructuredMessages(Arrays.asList(toolMsg, assistantMsg), listener);

        // Should update the pending interaction with the assistant text (from filtered list)
        verify(memoryManager).updateInteraction(any(), any(), any());
        verify(listener).onResponse(null);
        // The pending ID should be consumed
        Assert.assertNull(indexMemory.getLastIncompleteInteractionId().get());
    }

    // ==================== Helper ====================

    private Message createTextMessage(String role, String text) {
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText(text);
        return new Message(role, Collections.singletonList(block));
    }
}
