/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.contextmanager;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.contextmanager.ContextManagerContext;

/**
 * Unit tests for ToolsOutputTruncateManager.
 */
public class ToolsOutputTruncateManagerTest {

    private ToolsOutputTruncateManager manager;
    private ContextManagerContext context;

    @Before
    public void setUp() {
        manager = new ToolsOutputTruncateManager();
        context = ContextManagerContext.builder().parameters(new HashMap<>()).build();
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
        config.put("max_output_length", 20000);

        manager.initialize(config);

        // Should initialize without throwing exceptions
        Assert.assertNotNull(manager);
    }

    @Test
    public void testInitializeWithTokensExceedThrowsException() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_output_length", 10000);

        Map<String, Object> activation = new HashMap<>();
        activation.put("tokens_exceed", 50000);
        config.put("activation", activation);

        // Should throw IllegalArgumentException when tokens_exceed is used
        IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class, () -> manager.initialize(config));

        Assert.assertTrue(exception.getMessage().contains("does not support 'tokens_exceed' activation rule"));
    }

    @Test
    public void testInitializeWithMessageCountExceedThrowsException() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_output_length", 10000);

        Map<String, Object> activation = new HashMap<>();
        activation.put("message_count_exceed", 10);
        config.put("activation", activation);

        // Should throw IllegalArgumentException when message_count_exceed is used
        IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class, () -> manager.initialize(config));

        Assert.assertTrue(exception.getMessage().contains("does not support 'message_count_exceed' activation rule"));
    }

    @Test
    public void testInitializeWithOtherActivationRulesSucceeds() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_output_length", 10000);

        Map<String, Object> activation = new HashMap<>();
        // Other activation rules should work fine (though currently none are defined)
        activation.put("some_other_rule", 100);
        config.put("activation", activation);

        // Should not throw exception for other activation rules
        manager.initialize(config);
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
    public void testExecuteWithNoToolOutput() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Should handle missing tool output gracefully
        manager.execute(context);

        // No exception should be thrown
        Assert.assertNull(context.getParameters().get("_current_tool_output"));
    }

    @Test
    public void testExecuteWithNullParameters() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        context.setParameters(null);

        // Should handle null parameters gracefully
        manager.execute(context);

        // No exception should be thrown
        Assert.assertNull(context.getParameters());
    }

    @Test
    public void testExecuteWithSmallToolOutput() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_output_length", 100);
        manager.initialize(config);

        String smallOutput = "This is a small output";
        context.getParameters().put("_current_tool_output", smallOutput);

        manager.execute(context);

        // Output should remain unchanged
        String result = context.getParameters().get("_current_tool_output");
        Assert.assertEquals(smallOutput, result);
        Assert.assertFalse(result.contains("truncated"));
    }

    @Test
    public void testExecuteWithLargeToolOutput() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_output_length", 50);
        manager.initialize(config);

        // Create output longer than 50 characters
        String largeOutput = "This is a very long output that exceeds the maximum allowed length and should be truncated";
        context.getParameters().put("_current_tool_output", largeOutput);

        manager.execute(context);

        // Output should be truncated
        String result = context.getParameters().get("_current_tool_output");
        Assert.assertNotEquals(largeOutput, result);
        Assert.assertTrue(result.contains("truncated"));
        Assert.assertTrue(result.contains("original length: " + largeOutput.length()));

        // Should contain the first 50 characters of original output
        Assert.assertTrue(result.startsWith(largeOutput.substring(0, 50)));
    }

    @Test
    public void testExecuteWithExactLimit() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_output_length", 20);
        manager.initialize(config);

        // Create output exactly 20 characters
        String exactOutput = "12345678901234567890"; // exactly 20 chars
        context.getParameters().put("_current_tool_output", exactOutput);

        manager.execute(context);

        // Output should remain unchanged (not truncated)
        String result = context.getParameters().get("_current_tool_output");
        Assert.assertEquals(exactOutput, result);
        Assert.assertFalse(result.contains("truncated"));
    }

    @Test
    public void testExecuteWithDefaultMaxLength() {
        Map<String, Object> config = new HashMap<>();
        manager.initialize(config);

        // Create output with 40001 characters (exceeds default 40000)
        StringBuilder largeOutput = new StringBuilder();
        for (int i = 0; i < 40001; i++) {
            largeOutput.append("x");
        }
        String output = largeOutput.toString();
        context.getParameters().put("_current_tool_output", output);

        manager.execute(context);

        // Output should be truncated to 40000 characters
        String result = context.getParameters().get("_current_tool_output");
        Assert.assertTrue(result.contains("truncated"));
        Assert.assertTrue(result.contains("original length: 40001"));
        Assert.assertTrue(result.startsWith("xxxxxxxxxxxx")); // Should start with x's
    }

    @Test
    public void testExecuteWithInvalidMaxLength() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_output_length", -100); // Invalid negative value
        manager.initialize(config);

        // Should use default value (40000) when invalid config is provided
        StringBuilder largeOutput = new StringBuilder();
        for (int i = 0; i < 40001; i++) {
            largeOutput.append("y");
        }
        String output = largeOutput.toString();
        context.getParameters().put("_current_tool_output", output);

        manager.execute(context);

        // Should still truncate using default value
        String result = context.getParameters().get("_current_tool_output");
        Assert.assertTrue(result.contains("truncated"));
    }

    @Test
    public void testExecuteWithZeroMaxLength() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_output_length", 0); // Invalid zero value
        manager.initialize(config);

        String output = "Some output";
        context.getParameters().put("_current_tool_output", output);

        manager.execute(context);

        // Should use default value (40000) when zero is provided
        String result = context.getParameters().get("_current_tool_output");
        Assert.assertEquals(output, result); // Should not be truncated since it's way below 40000
    }

    @Test
    public void testExecuteWithStringConfigValue() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_output_length", "100"); // String value
        manager.initialize(config);

        // Create output longer than 100 characters
        String largeOutput =
            "This is a very long output that exceeds one hundred characters and should definitely be truncated by the manager";
        context.getParameters().put("_current_tool_output", largeOutput);

        manager.execute(context);

        // Should parse string value and truncate accordingly
        String result = context.getParameters().get("_current_tool_output");
        Assert.assertTrue(result.contains("truncated"));
    }

    @Test
    public void testExecutePreservesOriginalLengthInMessage() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_output_length", 10);
        manager.initialize(config);

        String output = "This is a 30 character output!"; // 30 characters
        context.getParameters().put("_current_tool_output", output);

        manager.execute(context);

        String result = context.getParameters().get("_current_tool_output");
        Assert.assertTrue(result.contains("original length: 30"));
    }

    @Test
    public void testExecuteWithEmptyToolOutput() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_output_length", 100);
        manager.initialize(config);

        context.getParameters().put("_current_tool_output", "");

        manager.execute(context);

        // Empty output should remain empty
        String result = context.getParameters().get("_current_tool_output");
        Assert.assertEquals("", result);
    }

    @Test
    public void testExecuteMultipleTimes() {
        Map<String, Object> config = new HashMap<>();
        config.put("max_output_length", 20);
        manager.initialize(config);

        // First execution
        String output1 = "This is the first very long output that should be truncated";
        context.getParameters().put("_current_tool_output", output1);
        manager.execute(context);
        String result1 = context.getParameters().get("_current_tool_output");
        Assert.assertTrue(result1.contains("truncated"));

        // Second execution with different output
        String output2 = "This is the second very long output that should also be truncated";
        context.getParameters().put("_current_tool_output", output2);
        manager.execute(context);
        String result2 = context.getParameters().get("_current_tool_output");
        Assert.assertTrue(result2.contains("truncated"));

        // Results should be different
        Assert.assertNotEquals(result1, result2);
    }
}
