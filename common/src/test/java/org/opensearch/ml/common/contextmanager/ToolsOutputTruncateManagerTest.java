/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.contextmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for ToolsOutputTruncateManager.
 */
public class ToolsOutputTruncateManagerTest {

    private ToolsOutputTruncateManager manager;
    private ContextManagerContext context;

    @Before
    public void setUp() {
        manager = new ToolsOutputTruncateManager();
        context = ContextManagerContext.builder().toolInteractions(new ArrayList<>()).build();
    }

    @Test
    public void testGetType() {
        Assert.assertEquals("ToolsOutputTruncateManager", manager.getType());
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
        config.put("max_tokens", 1000);
        config.put("truncation_strategy", "preserve_end");
        config.put("truncation_marker", "... [TRUNCATED]");

        manager.initialize(config);

        // Should initialize without throwing exceptions
        Assert.assertNotNull(manager);
    }

    @Test
    public void testInitializeWithActivationRules() {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> activation = new HashMap<>();
        activation.put("tokens_exceed", 5000);
        config.put("activation", activation);

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
    public void testShouldActivateWithTokensExceedRule() {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> activation = new HashMap<>();
        activation.put("tokens_exceed", 100);
        config.put("activation", activation);

        manager.initialize(config);

        // Create context with small tool output (should not activate)
        Map<String, Object> interaction = new HashMap<>();
        interaction.put("output", "Small output");
        context.getToolInteractions().add(interaction);

        Assert.assertFalse(manager.shouldActivate(context));

        // Create context with large tool output (should activate)
        String largeOutput = "This is a very long output that should exceed the token limit. ".repeat(50);
        interaction.put("output", largeOutput);

        Assert.assertTrue(manager.shouldActivate(context));
    }

    @Test
    public void testExecuteWithNoToolInteractions() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Should handle empty tool interactions gracefully
        manager.execute(context);

        Assert.assertTrue(context.getToolInteractions().isEmpty());
    }

    @Test
    public void testExecuteWithSmallToolOutput() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_tokens", 1000);
        manager.initialize(config);

        // Add small tool output
        Map<String, Object> interaction = new HashMap<>();
        interaction.put("output", "Small output that should not be truncated");
        context.getToolInteractions().add(interaction);

        String originalOutput = (String) interaction.get("output");
        manager.execute(context);

        // Output should remain unchanged
        Assert.assertEquals(originalOutput, interaction.get("output"));
    }

    @Test
    public void testExecuteWithLargeToolOutput() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_tokens", 50);
        config.put("truncation_strategy", "preserve_beginning");
        config.put("truncation_marker", "... [TRUNCATED]");
        manager.initialize(config);

        // Add large tool output
        String largeOutput = "This is a very long output that should definitely be truncated because it exceeds the token limit. "
            .repeat(10);
        Map<String, Object> interaction = new HashMap<>();
        interaction.put("output", largeOutput);
        context.getToolInteractions().add(interaction);

        manager.execute(context);

        String truncatedOutput = (String) interaction.get("output");

        // Output should be truncated and contain the marker
        Assert.assertNotEquals(largeOutput, truncatedOutput);
        Assert.assertTrue(truncatedOutput.contains("... [TRUNCATED]"));
        Assert.assertTrue(truncatedOutput.length() < largeOutput.length());
    }

    @Test
    public void testExecuteWithMultipleToolOutputs() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_tokens", 50);
        config.put("truncation_marker", "... [TRUNCATED]");
        manager.initialize(config);

        // Add multiple tool outputs - some large, some small
        String smallOutput = "Small output";
        String largeOutput = "This is a very long output that should be truncated. ".repeat(10);

        Map<String, Object> interaction1 = new HashMap<>();
        interaction1.put("output", smallOutput);
        context.getToolInteractions().add(interaction1);

        Map<String, Object> interaction2 = new HashMap<>();
        interaction2.put("output", largeOutput);
        context.getToolInteractions().add(interaction2);

        Map<String, Object> interaction3 = new HashMap<>();
        interaction3.put("output", smallOutput);
        context.getToolInteractions().add(interaction3);

        manager.execute(context);

        // First and third outputs should remain unchanged
        Assert.assertEquals(smallOutput, interaction1.get("output"));
        Assert.assertEquals(smallOutput, interaction3.get("output"));

        // Second output should be truncated
        String truncatedOutput = (String) interaction2.get("output");
        Assert.assertNotEquals(largeOutput, truncatedOutput);
        Assert.assertTrue(truncatedOutput.contains("... [TRUNCATED]"));
    }

    @Test
    public void testExecuteWithNonStringOutput() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Add non-string tool output
        Map<String, Object> interaction = new HashMap<>();
        interaction.put("output", 12345);
        context.getToolInteractions().add(interaction);

        // Should handle non-string outputs gracefully
        manager.execute(context);

        // Output should remain unchanged
        Assert.assertEquals(12345, interaction.get("output"));
    }

    @Test
    public void testTruncationStrategies() {
        // Test preserve_beginning strategy
        testTruncationStrategy("preserve_beginning");

        // Test preserve_end strategy
        testTruncationStrategy("preserve_end");

        // Test preserve_middle strategy
        testTruncationStrategy("preserve_middle");
    }

    private void testTruncationStrategy(String strategy) {
        ToolsOutputTruncateManager testManager = new ToolsOutputTruncateManager();
        Map<String, Object> config = new HashMap<>();
        config.put("max_tokens", 50);
        config.put("truncation_strategy", strategy);
        config.put("truncation_marker", "... [TRUNCATED]");
        testManager.initialize(config);

        ContextManagerContext testContext = ContextManagerContext.builder().toolInteractions(new ArrayList<>()).build();

        String largeOutput = "This is a very long output that should be truncated according to the specified strategy. ".repeat(10);
        Map<String, Object> interaction = new HashMap<>();
        interaction.put("output", largeOutput);
        testContext.getToolInteractions().add(interaction);

        testManager.execute(testContext);

        String truncatedOutput = (String) interaction.get("output");

        // Output should be truncated and contain the marker
        Assert.assertNotEquals(largeOutput, truncatedOutput);
        Assert.assertTrue(truncatedOutput.contains("... [TRUNCATED]"));
        Assert.assertTrue(truncatedOutput.length() < largeOutput.length());
    }

    @Test
    public void testInvalidTruncationStrategy() {
        Map<String, Object> config = new HashMap<>();
        config.put("truncation_strategy", "invalid_strategy");

        // Should handle invalid strategy gracefully and use default
        manager.initialize(config);

        Assert.assertNotNull(manager);
    }

    @Test
    public void testInvalidMaxTokensConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_tokens", "invalid_number");

        // Should handle invalid config gracefully and use default
        manager.initialize(config);

        Assert.assertNotNull(manager);
    }
}
