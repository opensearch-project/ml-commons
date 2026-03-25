/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.contextmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.contextmanager.ContextManagerContext;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.input.execute.agent.Message;

/**
 * Unit tests for SlidingWindowManager.
 */
public class SlidingWindowManagerTest {

    private SlidingWindowManager manager;
    private ContextManagerContext context;

    @Before
    public void setUp() {
        manager = new SlidingWindowManager();
        context = ContextManagerContext.builder().toolInteractions(new ArrayList<>()).parameters(new HashMap<>()).build();
    }

    @Test
    public void testGetType() {
        Assert.assertEquals("SlidingWindowManager", manager.getType());
    }

    @Test
    public void testInitializeWithDefaults() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Should initialize with default values without throwing exceptions
        Assert.assertNotNull(manager);
    }

    @Test
    public void testInitializeWithCustomConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 10);

        manager.initialize(config);

        // Should initialize without throwing exceptions
        Assert.assertNotNull(manager);
    }

    @Test
    public void testShouldActivateWithNoRules() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Should always activate when no rules are defined
        Assert.assertTrue(manager.shouldActivate(context));
    }

    @Test
    public void testExecuteWithEmptyToolInteractions() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Should handle empty tool interactions gracefully
        manager.execute(context);

        Assert.assertTrue(context.getToolInteractions().isEmpty());
    }

    @Test
    public void testExecuteWithSmallToolInteractions() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 10);
        manager.initialize(config);

        // Add fewer interactions than the limit
        addToolInteractionsToContext(5);
        int originalSize = context.getToolInteractions().size();

        manager.execute(context);

        // Tool interactions should remain unchanged
        Assert.assertEquals(originalSize, context.getToolInteractions().size());
    }

    @Test
    public void testExecuteWithLargeToolInteractions() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 5);
        manager.initialize(config);

        // Add more interactions than the limit
        addToolInteractionsToContext(10);

        manager.execute(context);

        // Tool interactions should be truncated to the limit
        Assert.assertEquals(5, context.getToolInteractions().size());

        // Parameters should be updated with truncated interactions
        String interactionsParam = (String) context.getParameters().get("_interactions");
        Assert.assertNotNull(interactionsParam);

        // Should contain only the last 5 interactions
        String[] interactions = interactionsParam.substring(2).split(", "); // Remove ", " prefix
        Assert.assertEquals(5, interactions.length);

        // Should keep the most recent interactions (6-10)
        for (int i = 0; i < interactions.length; i++) {
            String expected = "Tool output " + (6 + i);
            Assert.assertEquals(expected, interactions[i]);
        }

        // Verify toolInteractions also contain the most recent ones
        for (int i = 0; i < context.getToolInteractions().size(); i++) {
            String expected = "Tool output " + (6 + i);
            String actual = context.getToolInteractions().get(i);
            Assert.assertEquals(expected, actual);
        }
    }

    @Test
    public void testExecuteKeepsMostRecentInteractions() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 3);
        manager.initialize(config);

        // Add interactions with identifiable content
        addToolInteractionsToContext(7);

        manager.execute(context);

        // Should keep the last 3 interactions (5, 6, 7)
        String interactionsParam = (String) context.getParameters().get("_interactions");
        String[] interactions = interactionsParam.substring(2).split(", ");
        Assert.assertEquals(3, interactions.length);
        Assert.assertEquals("Tool output 5", interactions[0]);
        Assert.assertEquals("Tool output 6", interactions[1]);
        Assert.assertEquals("Tool output 7", interactions[2]);
    }

    @Test
    public void testExecuteWithExactLimit() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 5);
        manager.initialize(config);

        // Add exactly the limit number of interactions
        addToolInteractionsToContext(5);

        manager.execute(context);

        // Parameters should not be updated since no truncation needed
        Assert.assertNull(context.getParameters().get("_interactions"));
    }

    @Test
    public void testExecuteWithNullToolInteractions() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        context.setToolInteractions(null);

        // Should handle null tool interactions gracefully
        manager.execute(context);

        // Should not throw exception
        Assert.assertNull(context.getToolInteractions());
    }

    @Test
    public void testExecuteWithNonStringOutputs() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 1); // Set to 1 to force truncation
        manager.initialize(config);

        // Add tool interactions as strings
        context.getToolInteractions().add("123"); // Integer as string
        context.getToolInteractions().add("String output"); // String output

        manager.execute(context);

        // Should process all string interactions and set _interactions parameter
        Assert.assertNotNull(context.getParameters().get("_interactions"));
        Assert.assertEquals(1, context.getToolInteractions().size()); // Should keep only 1
    }

    @Test
    public void testInvalidMaxMessagesConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", "invalid_number");

        // Should handle invalid config gracefully and use default
        manager.initialize(config);

        Assert.assertNotNull(manager);
    }

    @Test
    public void testExecuteWithNullParameters() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 3);
        manager.initialize(config);

        // Set parameters to null
        context.setParameters(null);
        addToolInteractionsToContext(5);

        manager.execute(context);

        // Should create new parameters map and update it
        Assert.assertNotNull(context.getParameters());
        String interactionsParam = (String) context.getParameters().get("_interactions");
        Assert.assertNotNull(interactionsParam);

        String[] interactions = interactionsParam.substring(2).split(", ");
        Assert.assertEquals(3, interactions.length);
    }

    @Test
    public void testExecuteSlideChatHistoryWithSizeOfTwo() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 2);
        manager.initialize(config);

        // Add 5 chat history interactions, expect only the last 2 to remain
        List<Interaction> chatHistory = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            chatHistory.add(Interaction.builder().id("id-" + i).input("question-" + i).response("answer-" + i).build());
        }
        context.setChatHistory(chatHistory);

        manager.execute(context);

        // Should keep only the most recent 2 interactions
        Assert.assertEquals(2, context.getChatHistory().size());
        Assert.assertEquals("question-4", context.getChatHistory().get(0).getInput());
        Assert.assertEquals("answer-4", context.getChatHistory().get(0).getResponse());
        Assert.assertEquals("question-5", context.getChatHistory().get(1).getInput());
        Assert.assertEquals("answer-5", context.getChatHistory().get(1).getResponse());
    }

    @Test
    public void testExecuteChatHistoryWithinLimit() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 5);
        manager.initialize(config);

        // Add 3 interactions, fewer than the limit
        List<Interaction> chatHistory = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            chatHistory.add(Interaction.builder().id("id-" + i).input("q-" + i).response("r-" + i).build());
        }
        context.setChatHistory(chatHistory);

        manager.execute(context);

        // All interactions should remain unchanged
        Assert.assertEquals(3, context.getChatHistory().size());
        Assert.assertEquals("q-1", context.getChatHistory().get(0).getInput());
        Assert.assertEquals("q-3", context.getChatHistory().get(2).getInput());
    }

    @Test
    public void testExecuteChatHistoryEmpty() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 2);
        manager.initialize(config);

        context.setChatHistory(new ArrayList<>());

        manager.execute(context);

        Assert.assertTrue(context.getChatHistory().isEmpty());
    }

    @Test
    public void testExecuteChatHistoryNull() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 2);
        manager.initialize(config);

        context.setChatHistory(null);

        manager.execute(context);

        Assert.assertNull(context.getChatHistory());
    }

    @Test
    public void testExecuteChatHistoryExactLimit() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 3);
        manager.initialize(config);

        List<Interaction> chatHistory = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            chatHistory.add(Interaction.builder().id("id-" + i).input("q-" + i).response("r-" + i).build());
        }
        context.setChatHistory(chatHistory);

        manager.execute(context);

        // No truncation needed, all should remain
        Assert.assertEquals(3, context.getChatHistory().size());
    }

    @Test
    public void testExecuteSlidesBothToolInteractionsAndChatHistory() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 2);
        manager.initialize(config);

        // Add tool interactions
        addToolInteractionsToContext(4);

        // Add chat history
        List<Interaction> chatHistory = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            chatHistory.add(Interaction.builder().id("id-" + i).input("q-" + i).response("r-" + i).build());
        }
        context.setChatHistory(chatHistory);

        manager.execute(context);

        // Both should be slid to keep the most recent 2
        Assert.assertEquals(2, context.getToolInteractions().size());
        Assert.assertEquals("Tool output 3", context.getToolInteractions().get(0));
        Assert.assertEquals("Tool output 4", context.getToolInteractions().get(1));

        Assert.assertEquals(2, context.getChatHistory().size());
        Assert.assertEquals("q-3", context.getChatHistory().get(0).getInput());
        Assert.assertEquals("q-4", context.getChatHistory().get(1).getInput());
    }

    @Test
    public void testExecuteSlideStructuredChatHistoryWithSizeOfTwo() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 2);
        manager.initialize(config);

        List<Message> structuredHistory = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            structuredHistory.add(new Message("user", null));
        }
        // Set identifiable roles to verify ordering
        structuredHistory.get(0).setRole("msg-1");
        structuredHistory.get(1).setRole("msg-2");
        structuredHistory.get(2).setRole("msg-3");
        structuredHistory.get(3).setRole("msg-4");
        structuredHistory.get(4).setRole("msg-5");
        context.setStructuredChatHistory(structuredHistory);

        manager.execute(context);

        // Should keep only the most recent 2 messages
        Assert.assertEquals(2, context.getStructuredChatHistory().size());
        Assert.assertEquals("msg-4", context.getStructuredChatHistory().get(0).getRole());
        Assert.assertEquals("msg-5", context.getStructuredChatHistory().get(1).getRole());
    }

    @Test
    public void testExecuteStructuredChatHistoryWithinLimit() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 5);
        manager.initialize(config);

        List<Message> structuredHistory = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            structuredHistory.add(new Message("user", null));
        }
        context.setStructuredChatHistory(structuredHistory);

        manager.execute(context);

        // All messages should remain unchanged
        Assert.assertEquals(3, context.getStructuredChatHistory().size());
    }

    @Test
    public void testExecuteStructuredChatHistoryEmpty() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 2);
        manager.initialize(config);

        context.setStructuredChatHistory(new ArrayList<>());

        manager.execute(context);

        Assert.assertTrue(context.getStructuredChatHistory().isEmpty());
    }

    @Test
    public void testExecuteStructuredChatHistoryNull() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_messages", 2);
        manager.initialize(config);

        context.setStructuredChatHistory(null);

        manager.execute(context);

        Assert.assertNull(context.getStructuredChatHistory());
    }

    /**
     * Helper method to add tool interactions to the context.
     */
    private void addToolInteractionsToContext(int count) {
        for (int i = 1; i <= count; i++) {
            context.getToolInteractions().add("Tool output " + i);
        }
    }
}
