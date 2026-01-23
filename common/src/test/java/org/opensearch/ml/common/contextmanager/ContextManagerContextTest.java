package org.opensearch.ml.common.contextmanager;

import static org.junit.Assert.*;

import org.junit.Test;

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
}
