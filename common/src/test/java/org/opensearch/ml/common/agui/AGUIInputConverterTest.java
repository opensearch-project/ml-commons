/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_CONTEXT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_MESSAGES;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_RUN_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_STATE;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_THREAD_ID;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_TOOLS;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.Message;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class AGUIInputConverterTest {

    private static final Gson gson = new Gson();

    @Test
    public void testIsAGUIInput_ValidInput() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertTrue(result);
    }

    @Test
    public void testIsAGUIInput_MissingThreadId() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_MissingRunId() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_MissingState() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_MissingMessages() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_MissingTools() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_MissingContext() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_MissingForwardedProps() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_MessagesNotArray() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.addProperty("messages", "not an array");
        aguiInput.add("tools", new JsonArray());
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_ToolsNotArray() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.addProperty("tools", "not an array");
        aguiInput.add("context", new JsonArray());
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_ContextNotArray() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("state", new JsonObject());
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());
        aguiInput.addProperty("context", "not an array");
        aguiInput.add("forwardedProps", new JsonObject());

        boolean result = AGUIInputConverter.isAGUIInput(gson.toJson(aguiInput));

        assertFalse(result);
    }

    @Test
    public void testIsAGUIInput_InvalidJson() {
        boolean result = AGUIInputConverter.isAGUIInput("invalid json");

        assertFalse(result);
    }

    @Test
    public void testConvertFromAGUIInput_BasicConversion() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", "tenant-id", false);

        assertNotNull(result);
        assertEquals("agent-id", result.getAgentId());
        assertEquals("tenant-id", result.getTenantId());
        assertEquals(FunctionName.AGENT, result.getFunctionName());
        assertFalse(result.getIsAsync());

        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) result.getInputDataset();
        assertNotNull(dataset);
        Map<String, String> params = dataset.getParameters();
        assertEquals("thread-123", params.get(AGUI_PARAM_THREAD_ID));
        assertEquals("run-456", params.get(AGUI_PARAM_RUN_ID));
    }

    @Test
    public void testConvertFromAGUIInput_WithState() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());

        JsonObject state = new JsonObject();
        state.addProperty("key", "value");
        aguiInput.add("state", state);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, true);

        assertNotNull(result);
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) result.getInputDataset();
        Map<String, String> params = dataset.getParameters();
        assertTrue(params.containsKey(AGUI_PARAM_STATE));
        assertEquals("{\"key\":\"value\"}", params.get(AGUI_PARAM_STATE));
    }

    @Test
    public void testConvertFromAGUIInput_WithContext() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());

        JsonArray context = new JsonArray();
        JsonObject contextItem = new JsonObject();
        contextItem.addProperty("description", "Location");
        contextItem.addProperty("value", "SF");
        context.add(contextItem);
        aguiInput.add("context", context);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) result.getInputDataset();
        Map<String, String> params = dataset.getParameters();
        assertTrue(params.containsKey(AGUI_PARAM_CONTEXT));
    }

    @Test
    public void testConvertFromAGUIInput_WithMessages_CreatesAgentInput() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("tools", new JsonArray());

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Hello");
        messages.add(userMsg);
        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        assertNotNull(result.getAgentInput());
        assertTrue(result.getAgentInput().getInput() instanceof List);
        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        assertNotNull(convertedMessages);
        assertEquals(1, convertedMessages.size());
        assertEquals("user", convertedMessages.get(0).getRole());
    }

    @Test
    public void testConvertFromAGUIInput_SkipsToolMessages() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("tools", new JsonArray());

        JsonArray messages = new JsonArray();

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Question");
        messages.add(userMsg);

        JsonObject toolMsg = new JsonObject();
        toolMsg.addProperty("role", "tool");
        toolMsg.addProperty("content", "Tool result");
        toolMsg.addProperty("toolCallId", "call-123");
        messages.add(toolMsg);

        JsonObject assistantMsg = new JsonObject();
        assistantMsg.addProperty("role", "assistant");
        assistantMsg.addProperty("content", "Answer");
        messages.add(assistantMsg);

        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        assertEquals(2, convertedMessages.size()); // user and assistant, tool skipped
        assertEquals("user", convertedMessages.get(0).getRole());
        assertEquals("assistant", convertedMessages.get(1).getRole());
    }

    @Test
    public void testConvertFromAGUIInput_SkipsAssistantWithOnlyToolCalls() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("tools", new JsonArray());

        JsonArray messages = new JsonArray();

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Question");
        messages.add(userMsg);

        // Assistant with toolCalls but no content
        JsonObject assistantWithToolCalls = new JsonObject();
        assistantWithToolCalls.addProperty("role", "assistant");
        assistantWithToolCalls.add("toolCalls", new JsonArray());
        messages.add(assistantWithToolCalls);

        // Final assistant with content
        JsonObject assistantWithContent = new JsonObject();
        assistantWithContent.addProperty("role", "assistant");
        assistantWithContent.addProperty("content", "Final answer");
        messages.add(assistantWithContent);

        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        assertEquals(2, convertedMessages.size()); // user and final assistant
        assertEquals("user", convertedMessages.get(0).getRole());
        assertEquals("assistant", convertedMessages.get(1).getRole());
        assertEquals("Final answer", convertedMessages.get(1).getContent().get(0).getText());
    }

    @Test
    public void testConvertFromAGUIInput_TextContent() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("tools", new JsonArray());

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Simple text");
        messages.add(userMsg);
        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        ContentBlock content = convertedMessages.get(0).getContent().get(0);
        assertEquals(ContentType.TEXT, content.getType());
        assertEquals("Simple text", content.getText());
    }

    @Test
    public void testConvertFromAGUIInput_MultimodalContent() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("tools", new JsonArray());

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");

        JsonArray contentArray = new JsonArray();

        // Text content
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", "Describe this image");
        contentArray.add(textContent);

        // Image content
        JsonObject imageContent = new JsonObject();
        imageContent.addProperty("type", "binary");
        imageContent.addProperty("mimeType", "image/png");
        imageContent.addProperty("data", "base64encodeddata");
        contentArray.add(imageContent);

        userMsg.add("content", contentArray);
        messages.add(userMsg);
        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        List<ContentBlock> content = convertedMessages.get(0).getContent();
        assertEquals(2, content.size());

        assertEquals(ContentType.TEXT, content.get(0).getType());
        assertEquals("Describe this image", content.get(0).getText());

        assertEquals(ContentType.IMAGE, content.get(1).getType());
        assertNotNull(content.get(1).getImage());
        assertEquals("png", content.get(1).getImage().getFormat());
        assertEquals("base64encodeddata", content.get(1).getImage().getData());
    }

    @Test
    public void testExtractToolResults_EmptyMessages() {
        JsonArray messages = new JsonArray();

        List<Map<String, String>> result = AGUIInputConverter.extractToolResults(messages);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testExtractToolResults_NoToolMessages() {
        JsonArray messages = new JsonArray();

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Hello");
        messages.add(userMsg);

        JsonObject assistantMsg = new JsonObject();
        assistantMsg.addProperty("role", "assistant");
        assistantMsg.addProperty("content", "Hi there");
        messages.add(assistantMsg);

        List<Map<String, String>> result = AGUIInputConverter.extractToolResults(messages);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testExtractToolResults_SingleToolResult() {
        JsonArray messages = new JsonArray();

        JsonObject toolMsg = new JsonObject();
        toolMsg.addProperty("role", "tool");
        toolMsg.addProperty("content", "Weather: 72F");
        toolMsg.addProperty("toolCallId", "call-123");
        messages.add(toolMsg);

        List<Map<String, String>> result = AGUIInputConverter.extractToolResults(messages);

        assertEquals(1, result.size());
        assertEquals("call-123", result.get(0).get("tool_call_id"));
        assertEquals("Weather: 72F", result.get(0).get("content"));
    }

    @Test
    public void testExtractToolResults_MultipleToolResults() {
        JsonArray messages = new JsonArray();

        JsonObject toolMsg1 = new JsonObject();
        toolMsg1.addProperty("role", "tool");
        toolMsg1.addProperty("content", "Result 1");
        toolMsg1.addProperty("toolCallId", "call-1");
        messages.add(toolMsg1);

        JsonObject toolMsg2 = new JsonObject();
        toolMsg2.addProperty("role", "tool");
        toolMsg2.addProperty("content", "Result 2");
        toolMsg2.addProperty("toolCallId", "call-2");
        messages.add(toolMsg2);

        List<Map<String, String>> result = AGUIInputConverter.extractToolResults(messages);

        assertEquals(2, result.size());
        assertEquals("call-1", result.get(0).get("tool_call_id"));
        assertEquals("Result 1", result.get(0).get("content"));
        assertEquals("call-2", result.get(1).get("tool_call_id"));
        assertEquals("Result 2", result.get(1).get("content"));
    }

    @Test
    public void testExtractToolCalls_EmptyMessages() {
        JsonArray messages = new JsonArray();

        List<String> result = AGUIInputConverter.extractToolCalls(messages);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testExtractToolCalls_NoToolCalls() {
        JsonArray messages = new JsonArray();

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "Hello");
        messages.add(userMsg);

        JsonObject assistantMsg = new JsonObject();
        assistantMsg.addProperty("role", "assistant");
        assistantMsg.addProperty("content", "Hi there");
        messages.add(assistantMsg);

        List<String> result = AGUIInputConverter.extractToolCalls(messages);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testExtractToolCalls_SingleAssistantWithToolCalls() {
        JsonArray messages = new JsonArray();

        JsonObject assistantMsg = new JsonObject();
        assistantMsg.addProperty("role", "assistant");

        JsonArray toolCalls = new JsonArray();
        JsonObject toolCall = new JsonObject();
        toolCall.addProperty("id", "call-123");
        toolCall.addProperty("type", "function");

        JsonObject function = new JsonObject();
        function.addProperty("name", "get_weather");
        function.addProperty("arguments", "{\"location\":\"NYC\"}");
        toolCall.add("function", function);

        toolCalls.add(toolCall);
        assistantMsg.add("toolCalls", toolCalls);
        messages.add(assistantMsg);

        List<String> result = AGUIInputConverter.extractToolCalls(messages);

        assertEquals(1, result.size());
        assertTrue(result.get(0).contains("call-123"));
        assertTrue(result.get(0).contains("get_weather"));
    }

    @Test
    public void testExtractToolCalls_MultipleAssistantsWithToolCalls() {
        JsonArray messages = new JsonArray();

        // First assistant with tool calls
        JsonObject assistantMsg1 = new JsonObject();
        assistantMsg1.addProperty("role", "assistant");
        JsonArray toolCalls1 = new JsonArray();
        JsonObject toolCall1 = new JsonObject();
        toolCall1.addProperty("id", "call-1");
        toolCalls1.add(toolCall1);
        assistantMsg1.add("toolCalls", toolCalls1);
        messages.add(assistantMsg1);

        // Second assistant with tool calls
        JsonObject assistantMsg2 = new JsonObject();
        assistantMsg2.addProperty("role", "assistant");
        JsonArray toolCalls2 = new JsonArray();
        JsonObject toolCall2 = new JsonObject();
        toolCall2.addProperty("id", "call-2");
        toolCalls2.add(toolCall2);
        assistantMsg2.add("toolCalls", toolCalls2);
        messages.add(assistantMsg2);

        List<String> result = AGUIInputConverter.extractToolCalls(messages);

        assertEquals(2, result.size());
        assertTrue(result.get(0).contains("call-1"));
        assertTrue(result.get(1).contains("call-2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvertFromAGUIInput_InvalidJson() {
        AGUIInputConverter.convertFromAGUIInput("invalid json", "agent-id", null, false);
    }

    @Test
    public void testConvertFromAGUIInput_PreservesParametersInDataset() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("messages", new JsonArray());
        aguiInput.add("tools", new JsonArray());

        JsonObject forwardedProps = new JsonObject();
        forwardedProps.addProperty("customParam", "customValue");
        aguiInput.add("forwardedProps", forwardedProps);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) result.getInputDataset();
        Map<String, String> params = dataset.getParameters();

        // All fields should be preserved
        assertTrue(params.containsKey(AGUI_PARAM_THREAD_ID));
        assertTrue(params.containsKey(AGUI_PARAM_RUN_ID));
        assertTrue(params.containsKey(AGUI_PARAM_MESSAGES));
        assertTrue(params.containsKey(AGUI_PARAM_TOOLS));
    }

    @Test
    public void testExtractToolResults_MissingToolCallId() {
        JsonArray messages = new JsonArray();

        JsonObject toolMsg = new JsonObject();
        toolMsg.addProperty("role", "tool");
        toolMsg.addProperty("content", "Result");
        // Missing toolCallId
        messages.add(toolMsg);

        List<Map<String, String>> result = AGUIInputConverter.extractToolResults(messages);

        // Should skip tool message without toolCallId
        assertTrue(result.isEmpty());
    }

    @Test
    public void testExtractToolResults_MissingContent() {
        JsonArray messages = new JsonArray();

        JsonObject toolMsg = new JsonObject();
        toolMsg.addProperty("role", "tool");
        toolMsg.addProperty("toolCallId", "call-123");
        // Missing content
        messages.add(toolMsg);

        List<Map<String, String>> result = AGUIInputConverter.extractToolResults(messages);

        // Should skip tool message without content
        assertTrue(result.isEmpty());
    }

    @Test
    public void testConvertFromAGUIInput_ImageContent_ExtractsFormat() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("tools", new JsonArray());

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");

        JsonArray contentArray = new JsonArray();
        JsonObject imageContent = new JsonObject();
        imageContent.addProperty("type", "binary");
        imageContent.addProperty("mimeType", "image/jpeg");
        imageContent.addProperty("data", "imagedata");
        contentArray.add(imageContent);

        userMsg.add("content", contentArray);
        messages.add(userMsg);
        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        ContentBlock imageBlock = convertedMessages.get(0).getContent().get(0);
        assertEquals(ContentType.IMAGE, imageBlock.getType());
        assertEquals("jpeg", imageBlock.getImage().getFormat());
    }

    @Test
    public void testConvertFromAGUIInput_NonImageBinaryContentSkipped() {
        JsonObject aguiInput = new JsonObject();
        aguiInput.addProperty("threadId", "thread-123");
        aguiInput.addProperty("runId", "run-456");
        aguiInput.add("tools", new JsonArray());

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");

        JsonArray contentArray = new JsonArray();
        JsonObject binaryContent = new JsonObject();
        binaryContent.addProperty("type", "binary");
        binaryContent.addProperty("mimeType", "application/pdf");
        binaryContent.addProperty("data", "pdfdata");
        contentArray.add(binaryContent);

        userMsg.add("content", contentArray);
        messages.add(userMsg);
        aguiInput.add("messages", messages);

        AgentMLInput result = AGUIInputConverter.convertFromAGUIInput(gson.toJson(aguiInput), "agent-id", null, false);

        @SuppressWarnings("unchecked")
        List<Message> convertedMessages = (List<Message>) result.getAgentInput().getInput();
        // Non-image binary content should be skipped
        assertTrue(convertedMessages.get(0).getContent().isEmpty());
    }
}
