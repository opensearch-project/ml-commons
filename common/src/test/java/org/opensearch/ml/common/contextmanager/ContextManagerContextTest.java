package org.opensearch.ml.common.contextmanager;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.Message;

public class ContextManagerContextTest {

    @Test
    public void testNoArgsConstructorInitializesCollections() {
        ContextManagerContext context = new ContextManagerContext();

        assertNotNull(context.getChatHistory());
        assertNotNull(context.getToolConfigs());
        assertNotNull(context.getToolInteractions());
        assertNotNull(context.getParameters());

        assertTrue(context.getChatHistory().isEmpty());
        assertTrue(context.getToolConfigs().isEmpty());
        assertTrue(context.getToolInteractions().isEmpty());
        assertTrue(context.getParameters().isEmpty());
    }

    @Test
    public void testBuilderInitializesCollections() {
        ContextManagerContext context = ContextManagerContext.builder().build();

        assertNotNull(context.getChatHistory());
        assertNotNull(context.getToolConfigs());
        assertNotNull(context.getToolInteractions());
        assertNotNull(context.getParameters());

        assertTrue(context.getChatHistory().isEmpty());
        assertTrue(context.getToolConfigs().isEmpty());
        assertTrue(context.getToolInteractions().isEmpty());
        assertTrue(context.getParameters().isEmpty());
    }

    @Test
    public void testGetEstimatedTokenCountWithNoArgsConstructor() {
        ContextManagerContext context = new ContextManagerContext();

        // Should not throw NPE
        int tokenCount = context.getEstimatedTokenCount();
        assertEquals(0, tokenCount);
    }

    @Test
    public void testGetEstimatedTokenCountWithBuilder() {
        ContextManagerContext context = ContextManagerContext.builder().build();

        // Should not throw NPE
        int tokenCount = context.getEstimatedTokenCount();
        assertEquals(0, tokenCount);
    }

    @Test
    public void testGetMessageCountWithNoArgsConstructor() {
        ContextManagerContext context = new ContextManagerContext();

        // Should not throw NPE
        int messageCount = context.getMessageCount();
        assertEquals(0, messageCount);
    }

    @Test
    public void testStructuredChatHistoryInitializedByBuilder() {
        ContextManagerContext context = ContextManagerContext.builder().build();

        assertNotNull(context.getStructuredChatHistory());
        assertTrue(context.getStructuredChatHistory().isEmpty());
    }

    @Test
    public void testStructuredChatHistoryInitializedByNoArgsConstructor() {
        ContextManagerContext context = new ContextManagerContext();

        assertNotNull(context.getStructuredChatHistory());
        assertTrue(context.getStructuredChatHistory().isEmpty());
    }

    @Test
    public void testAddStructuredMessage() {
        ContextManagerContext context = ContextManagerContext.builder().build();
        Message msg = new Message("user", null);

        context.addStructuredMessage(msg);

        assertEquals(1, context.getStructuredChatHistory().size());
        assertEquals("user", context.getStructuredChatHistory().get(0).getRole());
    }

    @Test
    public void testAddStructuredMessageWhenNull() {
        ContextManagerContext context = ContextManagerContext.builder().build();
        context.setStructuredChatHistory(null);

        Message msg = new Message("assistant", null);
        context.addStructuredMessage(msg);

        assertNotNull(context.getStructuredChatHistory());
        assertEquals(1, context.getStructuredChatHistory().size());
    }

    @Test
    public void testClearStructuredChatHistory() {
        ContextManagerContext context = ContextManagerContext.builder().build();
        context.addStructuredMessage(new Message("user", null));
        context.addStructuredMessage(new Message("assistant", null));
        assertEquals(2, context.getStructuredChatHistory().size());

        context.clearStructuredChatHistory();

        assertTrue(context.getStructuredChatHistory().isEmpty());
    }

    @Test
    public void testClearStructuredChatHistoryWhenNull() {
        ContextManagerContext context = ContextManagerContext.builder().build();
        context.setStructuredChatHistory(null);

        // Should not throw NPE
        context.clearStructuredChatHistory();
        assertNull(context.getStructuredChatHistory());
    }

    @Test
    public void testGetStructuredMessageCount() {
        ContextManagerContext context = ContextManagerContext.builder().build();
        assertEquals(0, context.getStructuredMessageCount());

        context.addStructuredMessage(new Message("user", null));
        context.addStructuredMessage(new Message("assistant", null));
        assertEquals(2, context.getStructuredMessageCount());
    }

    @Test
    public void testGetStructuredMessageCountWhenNull() {
        ContextManagerContext context = ContextManagerContext.builder().build();
        context.setStructuredChatHistory(null);

        assertEquals(0, context.getStructuredMessageCount());
    }

    @Test
    public void testIsStructuredModeWhenEmpty() {
        ContextManagerContext context = ContextManagerContext.builder().build();
        assertFalse(context.isStructuredMode());
    }

    @Test
    public void testIsStructuredModeWhenPopulated() {
        ContextManagerContext context = ContextManagerContext.builder().build();
        context.addStructuredMessage(new Message("user", null));
        assertTrue(context.isStructuredMode());
    }

    @Test
    public void testIsStructuredModeWhenNull() {
        ContextManagerContext context = ContextManagerContext.builder().build();
        context.setStructuredChatHistory(null);
        assertFalse(context.isStructuredMode());
    }

    @Test
    public void testGetMessageCountReturnsStructuredCountInStructuredMode() {
        ContextManagerContext context = ContextManagerContext.builder().build();
        context.addStructuredMessage(new Message("user", null));
        context.addStructuredMessage(new Message("assistant", null));
        context.addStructuredMessage(new Message("user", null));

        // In structured mode, getMessageCount() should return structured count
        assertEquals(3, context.getMessageCount());
    }

    @Test
    public void testGetEstimatedTokenCountIncludesStructuredMessages() {
        ContextManagerContext context = ContextManagerContext.builder().build();

        ContentBlock textBlock = new ContentBlock();
        textBlock.setText("Hello world"); // 11 chars => ceil(11/4.0) = 3 tokens

        List<ContentBlock> content = new ArrayList<>();
        content.add(textBlock);
        Message msg = new Message("user", content);
        context.addStructuredMessage(msg);

        int tokenCount = context.getEstimatedTokenCount();
        assertEquals(3, tokenCount);
    }

    @Test
    public void testBuilderWithStructuredChatHistory() {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("user", null));
        messages.add(new Message("assistant", null));

        ContextManagerContext context = ContextManagerContext.builder().structuredChatHistory(messages).build();

        assertEquals(2, context.getStructuredChatHistory().size());
        assertTrue(context.isStructuredMode());
    }
}
