/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.contextmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.execute.agent.ToolCall;

public class ContextManagerUtilsTest {

    @Test
    public void testFindSafePoint_EmptyList() {
        List<String> interactions = Collections.emptyList();
        assertEquals(0, ContextManagerUtils.findSafePoint(interactions, 0, true));
        assertEquals(0, ContextManagerUtils.findSafePoint(interactions, 0, false));
    }

    @Test
    public void testFindSafePoint_BoundaryConditions() {
        List<String> interactions = Arrays.asList("message1", "message2", "message3");

        // Start point boundary conditions
        assertEquals(0, ContextManagerUtils.findSafePoint(interactions, -1, true));
        assertEquals(0, ContextManagerUtils.findSafePoint(interactions, 0, true));
        assertEquals(3, ContextManagerUtils.findSafePoint(interactions, 5, true));

        // Cut point boundary conditions
        assertEquals(5, ContextManagerUtils.findSafePoint(interactions, 5, false));
    }

    @Test
    public void testFindSafePoint_RegularMessages() {
        List<String> interactions = Arrays.asList("user: hello", "assistant: hi", "user: how are you?");

        // Regular messages should be safe - should return the target point
        assertEquals(0, ContextManagerUtils.findSafePoint(interactions, 0, true));
        assertEquals(1, ContextManagerUtils.findSafePoint(interactions, 1, true));
        assertEquals(2, ContextManagerUtils.findSafePoint(interactions, 2, true));
    }

    @Test
    public void testFindSafePoint_ReturnsValidIndex() {
        List<String> interactions = Arrays
            .asList("toolResult: result1", "toolUse: call1", "user: hello", "toolUse: call2", "toolResult: result2");

        // Should return a valid index within bounds
        int result = ContextManagerUtils.findSafePoint(interactions, 0, true);
        assertTrue("Result should be >= 0", result >= 0);
        assertTrue("Result should be <= size", result <= interactions.size());

        // Test both start point and cut point modes
        int resultCut = ContextManagerUtils.findSafePoint(interactions, 0, false);
        assertTrue("Cut point result should be >= 0", resultCut >= 0);
        assertTrue("Cut point result should be <= size", resultCut <= interactions.size());
    }

    @Test
    public void testFindSafePoint_MethodExists() {
        // Basic test to ensure the method exists and can be called
        List<String> interactions = Arrays.asList("test message");

        // Should not throw exception
        int result1 = ContextManagerUtils.findSafePoint(interactions, 0, true);
        int result2 = ContextManagerUtils.findSafePoint(interactions, 0, false);

        // Results should be reasonable
        assertTrue("Start point result should be valid", result1 >= 0 && result1 <= interactions.size());
        assertTrue("Cut point result should be valid", result2 >= 0 && result2 <= interactions.size());
    }

    // --- Tests for findSafeCutPointForStructuredMessages ---

    @Test
    public void testStructuredSafePoint_BoundaryConditions() {
        List<Message> messages = List.of(makeTextMessage("user", "hello"), makeTextMessage("assistant", "hi"));

        assertEquals(0, ContextManagerUtils.findSafeCutPointForStructuredMessages(messages, 0));
        assertEquals(0, ContextManagerUtils.findSafeCutPointForStructuredMessages(messages, -1));
        assertEquals(2, ContextManagerUtils.findSafeCutPointForStructuredMessages(messages, 5));
    }

    @Test
    public void testStructuredSafePoint_RegularMessages() {
        // user → assistant → user → assistant: any cut point is safe
        List<Message> messages = List
            .of(
                makeTextMessage("user", "hello"),
                makeTextMessage("assistant", "hi"),
                makeTextMessage("user", "how are you"),
                makeTextMessage("assistant", "good")
            );

        assertEquals(1, ContextManagerUtils.findSafeCutPointForStructuredMessages(messages, 1));
        assertEquals(2, ContextManagerUtils.findSafeCutPointForStructuredMessages(messages, 2));
        assertEquals(3, ContextManagerUtils.findSafeCutPointForStructuredMessages(messages, 3));
    }

    @Test
    public void testStructuredSafePoint_SkipsToolResultMessage() {
        // user → assistant(toolCall) → tool(result) → assistant → user
        // Cutting at index 2 (tool result) should advance to index 3
        List<Message> messages = List
            .of(
                makeTextMessage("user", "search for X"),
                makeAssistantWithToolCall("tooluse_abc"),
                makeToolResult("tooluse_abc", "search results"),
                makeTextMessage("assistant", "here are the results"),
                makeTextMessage("user", "thanks")
            );

        // Target=2 is the tool result → must skip forward past it
        assertEquals(3, ContextManagerUtils.findSafeCutPointForStructuredMessages(messages, 2));
    }

    @Test
    public void testStructuredSafePoint_DoesNotSplitAfterAssistantToolCall() {
        // user → assistant(toolCall) → tool(result) → user
        // Cutting at index 2 would leave assistant toolCall without its result
        // because prev (index 1) is assistant with toolCalls
        List<Message> messages = List
            .of(
                makeTextMessage("user", "search for X"),
                makeAssistantWithToolCall("tooluse_abc"),
                makeToolResult("tooluse_abc", "search results"),
                makeTextMessage("user", "thanks")
            );

        // Target=2: is tool result AND prev has toolCalls → skip to 3
        assertEquals(3, ContextManagerUtils.findSafeCutPointForStructuredMessages(messages, 2));

        // Target=1: prev (index 0) is "user" with no toolCalls, and index 1 is NOT a tool result
        // but index 1 IS an assistant with toolCalls and index 2 is a tool result
        // However, the logic checks: is message[1] a tool result? No. Is prev[0] assistant with toolCalls? No.
        // So it returns 1. But this would separate the toolCall from its result!
        // Actually wait — the caller summarizes messages 0..cutPoint, and keeps cutPoint..end.
        // If cutPoint=1, we keep [assistantToolCall, toolResult, user] → that's fine, the pair stays together.
        assertEquals(1, ContextManagerUtils.findSafeCutPointForStructuredMessages(messages, 1));
    }

    @Test
    public void testStructuredSafePoint_ConsecutiveToolPairs() {
        // user → assistant(toolCall1) → tool(result1) → assistant(toolCall2) → tool(result2) → assistant
        List<Message> messages = List
            .of(
                makeTextMessage("user", "do two searches"),
                makeAssistantWithToolCall("tool1"),
                makeToolResult("tool1", "result1"),
                makeAssistantWithToolCall("tool2"),
                makeToolResult("tool2", "result2"),
                makeTextMessage("assistant", "done")
            );

        // Target=1: not tool result, prev not assistant-toolCall → safe
        assertEquals(1, ContextManagerUtils.findSafeCutPointForStructuredMessages(messages, 1));

        // Target=2: tool result → skip; index 3: not tool result, prev(2) not assistant with toolCalls → safe at 3
        assertEquals(3, ContextManagerUtils.findSafeCutPointForStructuredMessages(messages, 2));

        // Target=3: not tool result, prev(2) is tool result (not assistant with toolCalls) → safe
        assertEquals(3, ContextManagerUtils.findSafeCutPointForStructuredMessages(messages, 3));

        // Target=4: tool result → skip; index 5: not tool result, prev(4) is tool result → safe at 5
        assertEquals(5, ContextManagerUtils.findSafeCutPointForStructuredMessages(messages, 4));
    }

    @Test
    public void testStructuredSafePoint_EmptyList() {
        assertEquals(0, ContextManagerUtils.findSafeCutPointForStructuredMessages(Collections.emptyList(), 0));
    }

    // --- Helper methods ---

    private Message makeTextMessage(String role, String text) {
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText(text);
        return new Message(role, List.of(block));
    }

    private Message makeAssistantWithToolCall(String toolCallId) {
        Message msg = new Message();
        msg.setRole("assistant");
        msg.setContent(List.of());
        ToolCall toolCall = new ToolCall(toolCallId, "function", new ToolCall.ToolFunction("SearchIndexTool", "{\"query\":\"test\"}"));
        msg.setToolCalls(List.of(toolCall));
        return msg;
    }

    private Message makeToolResult(String toolCallId, String resultText) {
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText(resultText);
        Message msg = new Message("tool", List.of(block));
        msg.setToolCallId(toolCallId);
        return msg;
    }
}
