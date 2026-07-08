/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.input.execute.agent.AgentInput;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.DocumentContent;
import org.opensearch.ml.common.input.execute.agent.ImageContent;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.execute.agent.SourceType;
import org.opensearch.ml.common.input.execute.agent.ToolCall;
import org.opensearch.ml.common.input.execute.agent.VideoContent;

public class BedrockInvokeModelProviderTest {

    private BedrockInvokeModelProvider provider;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        provider = new BedrockInvokeModelProvider();
    }

    @Test
    public void testGetLLMInterface() {
        // Act
        String result = provider.getLLMInterface();

        // Assert
        assertEquals("bedrock/invoke/claude", result);
    }

    // ==================== createConnector Tests ====================

    @Test
    public void testCreateConnector_WithFullParameters() {
        // Arrange
        String modelId = "us.anthropic.claude-sonnet-4-6-20250131-v1:0";
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_access_key");
        credential.put("secret_key", "test_secret_key");

        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("region", "us-west-2");

        // Act
        Connector connector = provider.createConnector(modelId, credential, modelParameters);

        // Assert
        assertNotNull(connector);
        assertTrue(connector instanceof AwsConnector);
        AwsConnector awsConnector = (AwsConnector) connector;
        assertEquals("Auto-generated Bedrock InvokeModel connector for Agent", awsConnector.getName());
        assertEquals("Auto-generated connector for Bedrock InvokeModel API with Claude Messages format", awsConnector.getDescription());
        assertEquals("aws_sigv4", awsConnector.getProtocol());
        assertEquals("us-west-2", awsConnector.getParameters().get("region"));
        assertEquals("bedrock", awsConnector.getParameters().get("service_name"));
        assertEquals(modelId, awsConnector.getParameters().get("model"));
        assertEquals("65536", awsConnector.getParameters().get("max_tokens"));
        assertNotNull(awsConnector.getActions());
        assertEquals(1, awsConnector.getActions().size());
    }

    @Test
    public void testCreateConnector_WithDefaultRegion() {
        // Arrange
        String modelId = "us.anthropic.claude-sonnet-4-6-20250131-v1:0";
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");

        // Act
        Connector connector = provider.createConnector(modelId, credential, null);

        // Assert
        assertNotNull(connector);
        assertTrue(connector instanceof AwsConnector);
        AwsConnector awsConnector = (AwsConnector) connector;
        assertEquals("us-east-1", awsConnector.getParameters().get("region"));
    }

    @Test
    public void testCreateConnector_WithCompactionEnabled() {
        // Arrange
        String modelId = "us.anthropic.claude-sonnet-4-6-20250131-v1:0";
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");

        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("compaction", "true");

        // Act
        Connector connector = provider.createConnector(modelId, credential, modelParameters);

        // Assert
        assertNotNull(connector);
        assertTrue(connector instanceof AwsConnector);
        AwsConnector awsConnector = (AwsConnector) connector;
        String betaConfig = awsConnector.getParameters().get("anthropic_beta_config");
        String contextConfig = awsConnector.getParameters().get("context_management_config");

        assertNotNull(betaConfig);
        assertTrue(betaConfig.contains("anthropic_beta"));
        assertTrue(betaConfig.contains("compact-2026-01-12"));

        assertNotNull(contextConfig);
        assertTrue(contextConfig.contains("context_management"));
        assertTrue(contextConfig.contains("compact_20260112"));
        assertTrue(contextConfig.contains("100000")); // default trigger tokens
    }

    @Test
    public void testCreateConnector_WithCompactionCustomConfig() {
        // Arrange
        String modelId = "us.anthropic.claude-opus-4-6-20250131-v1:0";
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");

        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("compaction", "true");
        modelParameters.put("compaction_config", "{\"trigger_tokens\": 50000}");

        // Act
        Connector connector = provider.createConnector(modelId, credential, modelParameters);

        // Assert
        assertNotNull(connector);
        AwsConnector awsConnector = (AwsConnector) connector;
        String contextConfig = awsConnector.getParameters().get("context_management_config");

        assertNotNull(contextConfig);
        assertTrue(contextConfig.contains("50000")); // custom trigger tokens
    }

    @Test
    public void testCreateConnector_WithCompactionOnUnsupportedModel() {
        // Arrange - Claude 3.5 does not support compaction (only 4.6+)
        String modelId = "us.anthropic.claude-3-5-sonnet-20241022-v2:0";
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");

        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("compaction", "true");

        // Act
        Connector connector = provider.createConnector(modelId, credential, modelParameters);

        // Assert - compaction config should be empty strings
        assertNotNull(connector);
        AwsConnector awsConnector = (AwsConnector) connector;
        assertEquals("", awsConnector.getParameters().get("anthropic_beta_config"));
        assertEquals("", awsConnector.getParameters().get("context_management_config"));
    }

    @Test
    public void testCreateConnector_VerifyRetryConfiguration() {
        // Arrange
        String modelId = "us.anthropic.claude-sonnet-4-6-20250131-v1:0";
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");

        // Act
        Connector connector = provider.createConnector(modelId, credential, null);

        // Assert
        assertTrue(connector instanceof AwsConnector);
        AwsConnector awsConnector = (AwsConnector) connector;
        assertNotNull(awsConnector.getConnectorClientConfig());
        assertEquals(Integer.valueOf(3), awsConnector.getConnectorClientConfig().getMaxRetryTimes());
    }

    // ==================== mapTextInput Tests ====================

    @Test
    public void testMapTextInput_ConversationalAgent() {
        // Arrange
        String text = "Hello, how are you?";

        // Act
        Map<String, String> result = provider.mapTextInput(text, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"type\":\"text\""));
        assertTrue(body.contains("\"text\":\"Hello, how are you?\""));
    }

    @Test
    public void testMapTextInput_PlanExecuteAndReflect() {
        // Arrange
        String text = "Test prompt";

        // Act
        Map<String, String> result = provider.mapTextInput(text, MLAgentType.PLAN_EXECUTE_AND_REFLECT);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("${parameters.prompt}"));
    }

    @Test
    public void testMapTextInput_WithSpecialCharacters() {
        // Arrange
        String text = "Text with \"quotes\" and \n newlines";

        // Act
        Map<String, String> result = provider.mapTextInput(text, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\\\""));
        assertTrue(body.contains("\\n"));
    }

    // ==================== mapContentBlocks Tests ====================

    @Test
    public void testMapContentBlocks_TextOnly() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello world");
        blocks.add(textBlock);

        // Act
        Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"type\":\"text\""));
        assertTrue(body.contains("\"text\":\"Hello world\""));
    }

    @Test
    public void testMapContentBlocks_CompactionBlock() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock compactionBlock = new ContentBlock();
        compactionBlock.setType(ContentType.COMPACTION);
        compactionBlock.setContent("Summary of previous conversation...");
        blocks.add(compactionBlock);

        // Act
        Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"type\":\"compaction\""));
        assertTrue(body.contains("\"content\":\"Summary of previous conversation...\""));
    }

    @Test
    public void testMapContentBlocks_CompactionAndText() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();

        ContentBlock compactionBlock = new ContentBlock();
        compactionBlock.setType(ContentType.COMPACTION);
        compactionBlock.setContent("Summary");
        blocks.add(compactionBlock);

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Current message");
        blocks.add(textBlock);

        // Act
        Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        String body = result.get("body");
        assertTrue(body.contains("\"type\":\"compaction\""));
        assertTrue(body.contains("\"type\":\"text\""));
        assertTrue(body.contains("Summary"));
        assertTrue(body.contains("Current message"));
    }

    @Test
    public void testMapContentBlocks_ImageWithBase64() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        ImageContent image = new ImageContent(SourceType.BASE64, "png", "base64encodeddata");
        imageBlock.setImage(image);
        blocks.add(imageBlock);

        // Act
        Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"type\":\"image\""));
        assertTrue(body.contains("\"source\""));
        assertTrue(body.contains("\"type\":\"base64\""));
        assertTrue(body.contains("\"media_type\":\"image/png\""));
        assertTrue(body.contains("base64encodeddata"));
    }

    @Test
    public void testMapContentBlocks_ImageWithURL_ThrowsException() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        ImageContent image = new ImageContent(SourceType.URL, "jpeg", "https://example.com/image.jpg");
        imageBlock.setImage(image);
        blocks.add(imageBlock);

        // Act & Assert
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("URL-based content is not supported");
        provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);
    }

    @Test
    public void testMapContentBlocks_DocumentWithBase64() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock docBlock = new ContentBlock();
        docBlock.setType(ContentType.DOCUMENT);
        DocumentContent doc = new DocumentContent(SourceType.BASE64, "pdf", "base64pdfdata");
        docBlock.setDocument(doc);
        blocks.add(docBlock);

        // Act
        Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"type\":\"document\""));
        assertTrue(body.contains("\"media_type\":\"application/pdf\""));
        assertTrue(body.contains("\"type\":\"base64\""));
    }

    @Test
    public void testMapContentBlocks_VideoWithBase64() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock videoBlock = new ContentBlock();
        videoBlock.setType(ContentType.VIDEO);
        VideoContent video = new VideoContent(SourceType.BASE64, "mp4", "base64videodata");
        videoBlock.setVideo(video);
        blocks.add(videoBlock);

        // Act
        Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"type\":\"video\""));
        assertTrue(body.contains("\"media_type\":\"video/mp4\""));
        assertTrue(body.contains("\"type\":\"base64\""));
    }

    @Test
    public void testMapContentBlocks_MultipleBlocks() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Describe this image:");
        blocks.add(textBlock);

        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        ImageContent image = new ImageContent(SourceType.BASE64, "png", "imagedata");
        imageBlock.setImage(image);
        blocks.add(imageBlock);

        // Act
        Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"text\":\"Describe this image:\""));
        assertTrue(body.contains("\"type\":\"image\""));
    }

    @Test
    public void testMapContentBlocks_EmptyList() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();

        // Act
        Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
    }

    @Test
    public void testMapContentBlocks_NullList() {
        // Act
        Map<String, String> result = provider.mapContentBlocks(null, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
    }

    // ==================== mapMessages Tests ====================

    @Test
    public void testMapMessages_SingleMessage() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        List<ContentBlock> content = new ArrayList<>();
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello");
        content.add(textBlock);

        Message message = new Message("user", content);
        messages.add(message);

        // Act
        Map<String, String> result = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"type\":\"text\""));
        assertTrue(body.contains("\"text\":\"Hello\""));
    }

    @Test
    public void testMapMessages_MultipleMessages() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        List<ContentBlock> userContent = new ArrayList<>();
        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("Hello");
        userContent.add(userBlock);
        messages.add(new Message("user", userContent));

        List<ContentBlock> assistantContent = new ArrayList<>();
        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("Hi there!");
        assistantContent.add(assistantBlock);
        messages.add(new Message("assistant", assistantContent));

        // Act
        Map<String, String> result = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"role\":\"assistant\""));
        assertTrue(result.containsKey("no_escape_params"));
    }

    @Test
    public void testMapMessages_WithCompactionInHistory() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        // Assistant message with compaction
        List<ContentBlock> assistantContent = new ArrayList<>();
        ContentBlock compactionBlock = new ContentBlock();
        compactionBlock.setType(ContentType.COMPACTION);
        compactionBlock.setContent("Summary of earlier conversation");
        assistantContent.add(compactionBlock);

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Continuing our discussion");
        assistantContent.add(textBlock);

        messages.add(new Message("assistant", assistantContent));

        // Act
        Map<String, String> result = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        String body = result.get("body");
        assertTrue(body.contains("\"type\":\"compaction\""));
        assertTrue(body.contains("Summary of earlier conversation"));
        assertTrue(body.contains("\"type\":\"text\""));
        assertTrue(body.contains("Continuing our discussion"));
    }

    @Test
    public void testMapMessages_EmptyList() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        // Act
        Map<String, String> result = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
    }

    @Test
    public void testMapMessages_NullList() {
        // Act
        Map<String, String> result = provider.mapMessages(null, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
    }

    @Test
    public void testMapMessages_WithAssistantToolCalls() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        // User message
        List<ContentBlock> userContent = new ArrayList<>();
        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("What's the weather?");
        userContent.add(userBlock);
        messages.add(new Message("user", userContent));

        // Assistant message with tool calls
        List<ContentBlock> assistantContent = new ArrayList<>();
        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("Let me check the weather");
        assistantContent.add(assistantBlock);

        Message assistantMsg = new Message("assistant", assistantContent);
        List<ToolCall> toolCalls = new ArrayList<>();
        ToolCall.ToolFunction function = new ToolCall.ToolFunction("get_weather", "{\"location\":\"NYC\"}");
        ToolCall toolCall = new ToolCall("call-123", "function", function);
        toolCalls.add(toolCall);
        assistantMsg.setToolCalls(toolCalls);
        messages.add(assistantMsg);

        // Act
        Map<String, String> result = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"type\":\"tool_use\""));
        assertTrue(body.contains("call-123"));
        assertTrue(body.contains("get_weather"));
    }

    @Test
    public void testMapMessages_WithToolResultMessages() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        // Tool result message
        List<ContentBlock> toolContent = new ArrayList<>();
        ContentBlock toolBlock = new ContentBlock();
        toolBlock.setType(ContentType.TEXT);
        toolBlock.setText("72F and sunny");
        toolContent.add(toolBlock);

        Message toolMsg = new Message("tool", toolContent);
        toolMsg.setToolCallId("call-123");
        messages.add(toolMsg);

        // Act
        Map<String, String> result = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"type\":\"tool_result\""));
        assertTrue(body.contains("call-123"));
        assertTrue(body.contains("72F and sunny"));
        // Tool role should be converted to user
        assertTrue(body.contains("\"role\":\"user\""));
    }

    @Test
    public void testMapMessages_WithMultipleConsecutiveToolResults() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        // First tool result
        List<ContentBlock> tool1Content = new ArrayList<>();
        ContentBlock tool1Block = new ContentBlock();
        tool1Block.setType(ContentType.TEXT);
        tool1Block.setText("72F and sunny");
        tool1Content.add(tool1Block);
        Message toolMsg1 = new Message("tool", tool1Content);
        toolMsg1.setToolCallId("call-1");
        messages.add(toolMsg1);

        // Second tool result
        List<ContentBlock> tool2Content = new ArrayList<>();
        ContentBlock tool2Block = new ContentBlock();
        tool2Block.setType(ContentType.TEXT);
        tool2Block.setText("3:00 PM");
        tool2Content.add(tool2Block);
        Message toolMsg2 = new Message("tool", tool2Content);
        toolMsg2.setToolCallId("call-2");
        messages.add(toolMsg2);

        // Act
        Map<String, String> result = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        // Both tool results should be in the same user message
        assertTrue(body.contains("call-1"));
        assertTrue(body.contains("call-2"));
        assertTrue(body.contains("72F and sunny"));
        assertTrue(body.contains("3:00 PM"));
    }

    @Test
    public void testMapMessages_WithNoEscapeParams() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        List<ContentBlock> content = new ArrayList<>();
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Test");
        content.add(textBlock);
        messages.add(new Message("user", content));

        // Act
        Map<String, String> result = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("no_escape_params"));
        String noEscapeParams = result.get("no_escape_params");
        assertTrue(noEscapeParams.contains("_chat_history"));
        assertTrue(noEscapeParams.contains("_tools"));
        assertTrue(noEscapeParams.contains("_interactions"));
        assertTrue(noEscapeParams.contains("tool_configs"));
        assertTrue(noEscapeParams.contains("body"));
    }

    // ==================== parseToUnifiedMessage Tests ====================

    @Test
    public void testParseToUnifiedMessage_TextContent() {
        // Arrange
        String json = "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"Hello world\"}]}";

        // Act
        Message result = provider.parseToUnifiedMessage(json);

        // Assert
        assertNotNull(result);
        assertEquals("assistant", result.getRole());
        assertNotNull(result.getContent());
        assertEquals(1, result.getContent().size());
        assertEquals(ContentType.TEXT, result.getContent().get(0).getType());
        assertEquals("Hello world", result.getContent().get(0).getText());
        assertNull(result.getToolCalls());
    }

    @Test
    public void testParseToUnifiedMessage_CompactionContent() {
        // Arrange
        String json = "{\"role\":\"assistant\",\"content\":[{\"type\":\"compaction\",\"content\":\"Summary of conversation\"}]}";

        // Act
        Message result = provider.parseToUnifiedMessage(json);

        // Assert
        assertNotNull(result);
        assertEquals("assistant", result.getRole());
        assertNotNull(result.getContent());
        assertEquals(1, result.getContent().size());
        assertEquals(ContentType.COMPACTION, result.getContent().get(0).getType());
        assertEquals("Summary of conversation", result.getContent().get(0).getContent());
    }

    @Test
    public void testParseToUnifiedMessage_CompactionAndText() {
        // Arrange
        String json = "{\"role\":\"assistant\",\"content\":["
            + "{\"type\":\"compaction\",\"content\":\"Earlier context summary\"},"
            + "{\"type\":\"text\",\"text\":\"Current response\"}"
            + "]}";

        // Act
        Message result = provider.parseToUnifiedMessage(json);

        // Assert
        assertNotNull(result);
        assertEquals("assistant", result.getRole());
        assertNotNull(result.getContent());
        assertEquals(2, result.getContent().size());
        assertEquals(ContentType.COMPACTION, result.getContent().get(0).getType());
        assertEquals("Earlier context summary", result.getContent().get(0).getContent());
        assertEquals(ContentType.TEXT, result.getContent().get(1).getType());
        assertEquals("Current response", result.getContent().get(1).getText());
    }

    @Test
    public void testParseToUnifiedMessage_ToolUse() {
        // Arrange
        String json = "{\"role\":\"assistant\",\"content\":["
            + "{\"type\":\"tool_use\",\"id\":\"tc_1\",\"name\":\"search\",\"input\":{\"query\":\"test\"}}"
            + "]}";

        // Act
        Message result = provider.parseToUnifiedMessage(json);

        // Assert
        assertNotNull(result);
        assertEquals("assistant", result.getRole());
        assertNull(result.getContent()); // no text blocks
        assertNotNull(result.getToolCalls());
        assertEquals(1, result.getToolCalls().size());
        assertEquals("tc_1", result.getToolCalls().get(0).getId());
        assertEquals("search", result.getToolCalls().get(0).getFunction().getName());
        assertTrue(result.getToolCalls().get(0).getFunction().getArguments().contains("test"));
    }

    @Test
    public void testParseToUnifiedMessage_TextAndToolUse() {
        // Arrange
        String json = "{\"role\":\"assistant\",\"content\":["
            + "{\"type\":\"text\",\"text\":\"Let me search\"},"
            + "{\"type\":\"tool_use\",\"id\":\"tc_2\",\"name\":\"lookup\",\"input\":{}}"
            + "]}";

        // Act
        Message result = provider.parseToUnifiedMessage(json);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Let me search", result.getContent().get(0).getText());
        assertEquals(1, result.getToolCalls().size());
        assertEquals("lookup", result.getToolCalls().get(0).getFunction().getName());
    }

    @Test
    public void testParseToUnifiedMessage_ToolResult() {
        // Arrange
        String json = "{\"role\":\"user\",\"content\":["
            + "{\"type\":\"tool_result\",\"tool_use_id\":\"tc_1\",\"content\":\"Result data\"}"
            + "]}";

        // Act
        Message result = provider.parseToUnifiedMessage(json);

        // Assert
        assertNotNull(result);
        assertEquals("tool", result.getRole()); // user+tool_result mapped to "tool"
        assertEquals("tc_1", result.getToolCallId());
        assertEquals(1, result.getContent().size());
        assertEquals("Result data", result.getContent().get(0).getText());
    }

    @Test
    public void testParseToUnifiedMessage_WrappedFormat() {
        // Arrange - wrapped format from streaming
        String json = "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":["
            + "{\"type\":\"text\",\"text\":\"Response text\"}"
            + "]}}}";

        // Act
        Message result = provider.parseToUnifiedMessage(json);

        // Assert
        assertNotNull(result);
        assertEquals("assistant", result.getRole());
        assertEquals("Response text", result.getContent().get(0).getText());
    }

    @Test
    public void testParseToUnifiedMessage_WrappedFormatWithCompaction() {
        // Arrange - wrapped format with compaction from streaming
        String json = "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":["
            + "{\"type\":\"compaction\",\"content\":\"Summary\"},"
            + "{\"type\":\"text\",\"text\":\"New response\"}"
            + "]}}}";

        // Act
        Message result = provider.parseToUnifiedMessage(json);

        // Assert
        assertNotNull(result);
        assertEquals("assistant", result.getRole());
        assertEquals(2, result.getContent().size());
        assertEquals(ContentType.COMPACTION, result.getContent().get(0).getType());
        assertEquals(ContentType.TEXT, result.getContent().get(1).getType());
    }

    @Test
    public void testParseToUnifiedMessage_EmptyContent() {
        // Arrange
        String json = "{\"role\":\"assistant\",\"content\":[]}";

        // Act
        Message result = provider.parseToUnifiedMessage(json);

        // Assert
        assertNull(result);
    }

    @Test
    public void testParseToUnifiedMessage_NoTextOrTools() {
        // Arrange - Content with only unknown keys
        String json = "{\"role\":\"assistant\",\"content\":[{\"unknown\":\"value\"}]}";

        // Act
        Message result = provider.parseToUnifiedMessage(json);

        // Assert
        assertNull(result);
    }

    @Test
    public void testParseToUnifiedMessage_NullJson() {
        // Act & Assert
        exceptionRule.expect(IllegalArgumentException.class);
        provider.parseToUnifiedMessage(null);
    }

    // ==================== mapAgentInput Tests ====================

    @Test
    public void testMapAgentInput_TextInput() {
        // Arrange
        String text = "Hello, how are you?";
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(text);

        // Act
        Map<String, String> result = provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"type\":\"text\""));
        assertTrue(body.contains("\"text\":\"Hello, how are you?\""));
    }

    @Test
    public void testMapAgentInput_ContentBlocksInput() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Test content");
        blocks.add(textBlock);

        AgentInput agentInput = new AgentInput();
        agentInput.setInput(blocks);

        // Act
        Map<String, String> result = provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"text\":\"Test content\""));
    }

    @Test
    public void testMapAgentInput_MessagesInput() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        List<ContentBlock> content = new ArrayList<>();
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello");
        content.add(textBlock);

        Message message = new Message("user", content);
        messages.add(message);

        AgentInput agentInput = new AgentInput();
        agentInput.setInput(messages);

        // Act
        Map<String, String> result = provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"text\":\"Hello\""));
        assertTrue(result.containsKey("no_escape_params"));
    }

    @Test
    public void testMapAgentInput_NullAgentInput() {
        // Act & Assert
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("AgentInput and its input field cannot be null");
        provider.mapAgentInput(null, MLAgentType.CONVERSATIONAL);
    }

    @Test
    public void testMapAgentInput_NullInputField() {
        // Arrange
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(null);

        // Act & Assert
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("AgentInput and its input field cannot be null");
        provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);
    }
}
