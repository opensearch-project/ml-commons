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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.contextmanager.ContextManagerContext;
import org.opensearch.transport.client.Client;

/**
 * Unit tests for SummarizationManager.
 */
public class SummarizationManagerTest {

    @Mock
    private Client client;

    private SummarizationManager manager;
    private ContextManagerContext context;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        manager = new SummarizationManager(client);
        context = ContextManagerContext.builder()
            .toolInteractions(new ArrayList<>())
            .parameters(new HashMap<>())
            .build();
    }

    @Test
    public void testGetType() {
        Assert.assertEquals("SummarizationManager", manager.getType());
    }

    @Test
    public void testInitializeWithDefaults() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        Assert.assertEquals(0.3, manager.summaryRatio, 0.001);
        Assert.assertEquals(10, manager.preserveRecentMessages);
    }

    @Test
    public void testInitializeWithCustomConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("summary_ratio", 0.5);
        config.put("preserve_recent_messages", 5);
        config.put("summarization_model_id", "test-model");
        config.put("summarization_system_prompt", "Custom prompt");

        manager.initialize(config);

        Assert.assertEquals(0.5, manager.summaryRatio, 0.001);
        Assert.assertEquals(5, manager.preserveRecentMessages);
        Assert.assertEquals("test-model", manager.summarizationModelId);
        Assert.assertEquals("Custom prompt", manager.summarizationSystemPrompt);
    }

    @Test
    public void testInitializeWithInvalidSummaryRatio() {
        Map<String, Object> config = new HashMap<>();
        config.put("summary_ratio", 0.9); // Invalid - too high

        manager.initialize(config);

        // Should use default value
        Assert.assertEquals(0.3, manager.summaryRatio, 0.001);
    }

    @Test
    public void testShouldActivateWithNoRules() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        Assert.assertTrue(manager.shouldActivate(context));
    }

    @Test
    public void testExecuteWithEmptyToolInteractions() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        manager.execute(context);

        Assert.assertTrue(context.getToolInteractions().isEmpty());
    }

    @Test
    public void testExecuteWithInsufficientMessages() {
        Map<String, Object> config = new HashMap<>();
        config.put("preserve_recent_messages", 10);
        manager.initialize(config);

        // Add only 5 interactions - not enough to summarize
        addToolInteractionsToContext(5);

        manager.execute(context);

        // Should remain unchanged
        Assert.assertEquals(5, context.getToolInteractions().size());
    }

    @Test
    public void testExecuteWithNoModelId() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        addToolInteractionsToContext(20);

        manager.execute(context);

        // Should remain unchanged due to missing model ID
        Assert.assertEquals(20, context.getToolInteractions().size());
    }

    @Test
    public void testExecuteWithNonStringOutputs() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Add tool interactions with non-string outputs
        Map<String, Object> interaction1 = new HashMap<>();
        interaction1.put("output", 123); // Integer output
        context.getToolInteractions().add(interaction1);

        Map<String, Object> interaction2 = new HashMap<>();
        interaction2.put("output", "String output"); // String output
        context.getToolInteractions().add(interaction2);

        manager.execute(context);

        // Should handle gracefully - only 1 string interaction, not enough to summarize
        Assert.assertEquals(2, context.getToolInteractions().size());
    }

    @Test
    public void testProcessSummarizationResult() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        addToolInteractionsToContext(10);
        List<String> remainingMessages = List.of("Message 6", "Message 7", "Message 8", "Message 9", "Message 10");

        manager.processSummarizationResult(
            context,
            "Test summary",
            5,
            remainingMessages,
            context.getToolInteractions()
        );

        // Should have 1 summary + 5 remaining = 6 total
        Assert.assertEquals(6, context.getToolInteractions().size());

        // First should be summary
        String firstOutput = (String) context.getToolInteractions().get(0).get("output");
        Assert.assertTrue(firstOutput.contains("Test summary"));
    }

    /**
     * Helper method to add tool interactions to the context.
     */
    private void addToolInteractionsToContext(int count) {
        for (int i = 1; i <= count; i++) {
            Map<String, Object> interaction = new HashMap<>();
            interaction.put("output", "Tool output " + i);
            context.getToolInteractions().add(interaction);
        }
    }
}
