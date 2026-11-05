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
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.input.execute.agent.AgentInput;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.DocumentContent;
import org.opensearch.ml.common.input.execute.agent.ImageContent;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.execute.agent.SourceType;
import org.opensearch.ml.common.input.execute.agent.ToolCall;
import org.opensearch.ml.common.input.execute.agent.VideoContent;

public class GeminiV1BetaGenerateContentModelProviderTest {

    private GeminiV1BetaGenerateContentModelProvider provider;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        provider = new GeminiV1BetaGenerateContentModelProvider();
    }

    @Test
    public void testGetLLMInterface() {
        // Act
        String result = provider.getLLMInterface();

        // Assert
        assertEquals("gemini/v1beta/generatecontent", result);
    }

    @Test
    public void testCreateConnector_WithFullParameters() {
        // Arrange
        String modelId = "gemini-2.5-flash";
        Map<String, String> credential = new HashMap<>();
        credential.put("gemini_api_key", "test_api_key");

        Map<String, String> modelParameters = new HashMap<>();

        // Act
        Connector connector = provider.createConnector(modelId, credential, modelParameters);

        // Assert
        assertNotNull(connector);
        assertTrue(connector instanceof HttpConnector);
        HttpConnector httpConnector = (HttpConnector) connector;
        assertEquals("Auto-generated Gemini connector for Agent", httpConnector.getName());
        assertEquals("Auto-generated connector for Gemini generateContent API", httpConnector.getDescription());
        assertEquals("http", httpConnector.getProtocol());
        assertEquals(modelId, httpConnector.getParameters().get("model"));
        assertNotNull(httpConnector.getActions());
        assertEquals(1, httpConnector.getActions().size());
    }

    @Test
    public void testCreateConnector_WithDefaultParameters() {
        // Arrange
        String modelId = "gemini-2.5-flash";
        Map<String, String> credential = new HashMap<>();
        credential.put("gemini_api_key", "test_key");

        // Act
        Connector connector = provider.createConnector(modelId, credential, null);

        // Assert
        assertNotNull(connector);
        assertTrue(connector instanceof HttpConnector);
        HttpConnector httpConnector = (HttpConnector) connector;
        assertEquals(modelId, httpConnector.getParameters().get("model"));
    }

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
        assertTrue(body.contains("\"text\":\"Hello world\""));
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
        assertTrue(body.contains("\"inlineData\""));
        assertTrue(body.contains("\"mimeType\":\"image/png\""));
        assertTrue(body.contains("base64encodeddata"));
    }

    @Test
    public void testMapContentBlocks_ImageWithURL() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        ImageContent image = new ImageContent(SourceType.URL, "jpeg", "https://example.com/image.jpg");
        imageBlock.setImage(image);
        blocks.add(imageBlock);

        // Act
        Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"fileData\""));
        // URL is escaped in JSON, so check for parts that would be present
        assertTrue(body.contains("example.com") && body.contains("image.jpg"));
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
        assertTrue(body.contains("\"fileData\""));
        assertTrue(body.contains("\"mimeType\":\"pdf\""));
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
        assertTrue(body.contains("\"fileData\""));
        assertTrue(body.contains("\"mimeType\":\"video/mp4\""));
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
        assertTrue(body.contains("\"inlineData\""));
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
        assertTrue(body.contains("\"role\":\"model\""));
        assertTrue(result.containsKey("no_escape_params"));
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

    @Test
    public void testCreateConnector_VerifyRetryConfiguration() {
        // Arrange
        String modelId = "gemini-2.5-flash";
        Map<String, String> credential = new HashMap<>();
        credential.put("gemini_api_key", "test_key");

        // Act
        Connector connector = provider.createConnector(modelId, credential, null);

        // Assert
        assertTrue(connector instanceof HttpConnector);
        HttpConnector httpConnector = (HttpConnector) connector;
        assertNotNull(httpConnector.getConnectorClientConfig());
        assertEquals(Integer.valueOf(3), httpConnector.getConnectorClientConfig().getMaxRetryTimes());
    }

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
        try {
            provider.mapAgentInput(null, MLAgentType.CONVERSATIONAL);
            fail("Should throw IllegalArgumentException for null AgentInput");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("AgentInput and its input field cannot be null"));
        }
    }

    @Test
    public void testMapAgentInput_NullInputField() {
        // Arrange
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(null);

        // Act & Assert
        try {
            provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);
            fail("Should throw IllegalArgumentException for null input field");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("AgentInput and its input field cannot be null"));
        }
    }

    @Test
    public void testMapAgentInput_ContentBlocksWithMultipleTypes() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Describe this image:");
        blocks.add(textBlock);

        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        ImageContent image = new ImageContent(SourceType.BASE64, "png", "base64data");
        imageBlock.setImage(image);
        blocks.add(imageBlock);

        AgentInput agentInput = new AgentInput();
        agentInput.setInput(blocks);

        // Act
        Map<String, String> result = provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"text\":\"Describe this image:\""));
        assertTrue(body.contains("\"inlineData\""));
        assertTrue(body.contains("\"mimeType\":\"image/png\""));
    }

    @Test
    public void testMapAgentInput_MessagesWithMultipleRoles() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        List<ContentBlock> userContent = new ArrayList<>();
        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("What is AI?");
        userContent.add(userBlock);
        messages.add(new Message("user", userContent));

        List<ContentBlock> assistantContent = new ArrayList<>();
        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("AI stands for Artificial Intelligence.");
        assistantContent.add(assistantBlock);
        messages.add(new Message("assistant", assistantContent));

        List<ContentBlock> userContent2 = new ArrayList<>();
        ContentBlock userBlock2 = new ContentBlock();
        userBlock2.setType(ContentType.TEXT);
        userBlock2.setText("Tell me more.");
        userContent2.add(userBlock2);
        messages.add(new Message("user", userContent2));

        AgentInput agentInput = new AgentInput();
        agentInput.setInput(messages);

        // Act
        Map<String, String> result = provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"role\":\"model\""));
        assertTrue(body.contains("What is AI?"));
        assertTrue(body.contains("AI stands for Artificial Intelligence."));
        assertTrue(body.contains("Tell me more."));
    }

    @Test
    public void testMapAgentInput_TextInputWithPlanExecuteAndReflect() {
        // Arrange
        String text = "Test prompt";
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(text);

        // Act
        Map<String, String> result = provider.mapAgentInput(agentInput, MLAgentType.PLAN_EXECUTE_AND_REFLECT);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("${parameters.prompt}"));
    }

    @Test
    public void testMapAgentInput_ContentBlocksWithVideo() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock videoBlock = new ContentBlock();
        videoBlock.setType(ContentType.VIDEO);
        VideoContent video = new VideoContent(SourceType.BASE64, "mp4", "base64videodata");
        videoBlock.setVideo(video);
        blocks.add(videoBlock);

        AgentInput agentInput = new AgentInput();
        agentInput.setInput(blocks);

        // Act
        Map<String, String> result = provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"fileData\""));
        assertTrue(body.contains("\"mimeType\":\"video/mp4\""));
    }

    @Test
    public void testMapAgentInput_ContentBlocksWithDocument() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock docBlock = new ContentBlock();
        docBlock.setType(ContentType.DOCUMENT);
        DocumentContent doc = new DocumentContent(SourceType.BASE64, "pdf", "base64pdfdata");
        docBlock.setDocument(doc);
        blocks.add(docBlock);

        AgentInput agentInput = new AgentInput();
        agentInput.setInput(blocks);

        // Act
        Map<String, String> result = provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"fileData\""));
        assertTrue(body.contains("\"mimeType\":\"pdf\""));
    }

    @Test
    public void testMapAgentInput_EmptyContentBlocks() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(blocks);

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input type not supported. Expected String, List<ContentBlock>, or List<Message>");

        // Act
        provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);
    }

    @Test
    public void testMapAgentInput_EmptyMessages() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(messages);

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Input type not supported. Expected String, List<ContentBlock>, or List<Message>");

        // Act
        provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);
    }

    // ==================== Tests for extractMessageFromResponse ====================

    @Test
    public void testExtractMessageFromResponse_ValidGeminiResponse() {
        // Arrange
        Map<String, Object> part = new HashMap<>();
        part.put("text", "Hello world");

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(part);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", parts);
        content.put("role", "model");

        Map<String, Object> candidate = new HashMap<>();
        candidate.put("content", content);

        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(candidate);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("candidates", candidates);

        // Act
        String result = provider.extractMessageFromResponse(responseData);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("\"parts\""));
        assertTrue(result.contains("\"role\":\"model\""));
        assertTrue(result.contains("Hello world"));
    }

    @Test
    public void testExtractMessageFromResponse_NullResponseData() {
        // Act
        String result = provider.extractMessageFromResponse(null);

        // Assert
        assertNull(result);
    }

    @Test
    public void testExtractMessageFromResponse_MissingCandidates() {
        // Arrange
        Map<String, Object> responseData = new HashMap<>();

        // Act
        String result = provider.extractMessageFromResponse(responseData);

        // Assert
        assertNull(result);
    }

    @Test
    public void testExtractMessageFromResponse_EmptyCandidates() {
        // Arrange
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("candidates", new ArrayList<>());

        // Act
        String result = provider.extractMessageFromResponse(responseData);

        // Assert
        assertNull(result);
    }

    @Test
    public void testExtractMessageFromResponse_CandidatesNotList() {
        // Arrange
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("candidates", "not a list");

        // Act
        String result = provider.extractMessageFromResponse(responseData);

        // Assert
        assertNull(result);
    }

    @Test
    public void testExtractMessageFromResponse_FirstCandidateNotMap() {
        // Arrange
        List<Object> candidates = new ArrayList<>();
        candidates.add("not a map");

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("candidates", candidates);

        // Act
        String result = provider.extractMessageFromResponse(responseData);

        // Assert
        assertNull(result);
    }

    @Test
    public void testExtractMessageFromResponse_MissingContent() {
        // Arrange
        Map<String, Object> candidate = new HashMap<>();
        candidate.put("index", 0);

        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(candidate);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("candidates", candidates);

        // Act
        String result = provider.extractMessageFromResponse(responseData);

        // Assert
        assertNull(result);
    }

    @Test
    public void testExtractMessageFromResponse_WithMultipleParts() {
        // Arrange
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", "First part");

        Map<String, Object> textPart2 = new HashMap<>();
        textPart2.put("text", "Second part");

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(textPart);
        parts.add(textPart2);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", parts);
        content.put("role", "model");

        Map<String, Object> candidate = new HashMap<>();
        candidate.put("content", content);

        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(candidate);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("candidates", candidates);

        // Act
        String result = provider.extractMessageFromResponse(responseData);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("First part"));
        assertTrue(result.contains("Second part"));
    }

    // ==================== Tests for inference config parameters ====================

    @Test
    public void testCreateConnector_GenerationConfig_SubstitutesToValidJson() {
        // Arrange
        String modelId = "gemini-2.5-flash";
        Map<String, String> credential = new HashMap<>();
        credential.put("gemini_api_key", "test_key");

        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("max_tokens", "100");
        modelParameters.put("temperature", "0.5");

        // Act
        Connector connector = provider.createConnector(modelId, credential, modelParameters);

        // Assert — perform actual template substitution
        HttpConnector httpConnector = (HttpConnector) connector;
        String requestBody = httpConnector.getActions().get(0).getRequestBody();
        Map<String, String> params = new HashMap<>(httpConnector.getParameters());
        params.put("system_prompt", "You are helpful.");
        params.put("body", "{\"role\":\"user\",\"parts\":[{\"text\":\"hi\"}]}");
        org.apache.commons.text.StringSubstitutor sub = new org.apache.commons.text.StringSubstitutor(params, "${parameters.", "}");
        sub.setEnableUndefinedVariableException(false);
        String resolved = sub.replace(requestBody);

        assertTrue(resolved.contains("\"generationConfig\""));
        assertTrue(resolved.contains("\"maxOutputTokens\":100"));
        assertTrue(resolved.contains("\"temperature\":0.5"));
        assertFalse(resolved.contains("topP"));
    }

    @Test
    public void testCreateConnector_WithTopP_SubstitutesToValidJson() {
        // Arrange
        String modelId = "gemini-2.5-flash";
        Map<String, String> credential = new HashMap<>();
        credential.put("gemini_api_key", "test_key");

        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("max_tokens", "100");
        modelParameters.put("temperature", "0.8");
        modelParameters.put("top_p", "0.9");

        // Act
        Connector connector = provider.createConnector(modelId, credential, modelParameters);

        // Assert — substitution and parse
        HttpConnector httpConnector = (HttpConnector) connector;
        String requestBody = httpConnector.getActions().get(0).getRequestBody();
        Map<String, String> params = new HashMap<>(httpConnector.getParameters());
        params.put("system_prompt", "You are helpful.");
        params.put("body", "{\"role\":\"user\",\"parts\":[{\"text\":\"hi\"}]}");
        org.apache.commons.text.StringSubstitutor sub = new org.apache.commons.text.StringSubstitutor(params, "${parameters.", "}");
        sub.setEnableUndefinedVariableException(false);
        String resolved = sub.replace(requestBody);

        assertTrue(resolved.contains("\"maxOutputTokens\":100"));
        assertTrue(resolved.contains("\"temperature\":0.8"));
        assertTrue(resolved.contains("\"topP\": 0.9"));
    }

    @Test
    public void testCreateConnector_InvalidNumericParameter_ThrowsException() {
        // Arrange
        String modelId = "gemini-2.5-flash";
        Map<String, String> credential = new HashMap<>();
        credential.put("gemini_api_key", "test_key");

        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("top_p", "0.9\", \"malicious\": \"value");

        // Act & Assert
        try {
            provider.createConnector(modelId, credential, modelParameters);
            fail("Should throw IllegalArgumentException for invalid numeric parameter");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("top_p"));
            assertTrue(e.getMessage().contains("must be a valid number"));
        }
    }

    // ==================== parseToUnifiedMessage(s): Gemini replies and tool results ====================

    @Test
    public void testParse_TextReply() {
        Message message = provider.parseToUnifiedMessage("{\"role\":\"model\",\"parts\":[{\"text\":\"The cluster is green.\"}]}");
        assertEquals("assistant", message.getRole());
        assertEquals("The cluster is green.", message.getContent().get(0).getText());
        assertNull(message.getToolCalls());
    }

    @Test
    public void testParse_ThoughtSummaryPartsAreSkipped() {
        Message message = provider
            .parseToUnifiedMessage(
                "{\"role\":\"model\",\"parts\":[{\"text\":\"Let me think...\",\"thought\":true},{\"text\":\"Answer.\"}]}"
            );
        assertEquals(1, message.getContent().size());
        assertEquals("Answer.", message.getContent().get(0).getText());
    }

    @Test
    public void testParse_FunctionCallKeepsItsThoughtSignature() {
        Message message = provider
            .parseToUnifiedMessage(
                "{\"role\":\"model\",\"parts\":[{\"text\":\"Checking.\"},"
                    + "{\"functionCall\":{\"name\":\"ClusterHealthTool\",\"args\":{\"index\":\"orders\"}},\"thoughtSignature\":\"c2lnLTE=\"}]}"
            );
        assertEquals("assistant", message.getRole());
        assertEquals("Checking.", message.getContent().get(0).getText());
        ToolCall call = message.getToolCalls().get(0);
        assertEquals("ClusterHealthTool", call.getId());
        assertEquals("ClusterHealthTool", call.getFunction().getName());
        assertEquals("{\"index\":\"orders\"}", call.getFunction().getArguments());
        assertEquals("c2lnLTE=", call.getThoughtSignature());
    }

    @Test
    public void testParse_ParallelFunctionCallsOnlyTheFirstCarriesASignature() {
        Message message = provider
            .parseToUnifiedMessage(
                "{\"role\":\"model\",\"parts\":["
                    + "{\"functionCall\":{\"name\":\"a\",\"args\":{}},\"thoughtSignature\":\"sig\"},"
                    + "{\"functionCall\":{\"name\":\"b\"}}]}"
            );
        assertEquals(2, message.getToolCalls().size());
        assertEquals("sig", message.getToolCalls().get(0).getThoughtSignature());
        assertNull(message.getToolCalls().get(1).getThoughtSignature());
        assertEquals("{}", message.getToolCalls().get(1).getFunction().getArguments());
    }

    @Test
    public void testParse_FunctionResponseBecomesAToolMessage() {
        Message message = provider
            .parseToUnifiedMessage(
                "{\"role\":\"user\",\"parts\":[{\"functionResponse\":{\"name\":\"ClusterHealthTool\",\"response\":{\"text\":\"green\"}}}]}"
            );
        assertEquals("tool", message.getRole());
        assertEquals("ClusterHealthTool", message.getToolCallId());
        assertEquals("green", message.getContent().get(0).getText());
    }

    @Test
    public void testParseMessages_KeepsEveryResultOfParallelCalls() {
        List<Message> messages = provider
            .parseToUnifiedMessages(
                "{\"role\":\"user\",\"parts\":["
                    + "{\"functionResponse\":{\"name\":\"a\",\"response\":{\"text\":\"one\"}}},"
                    + "{\"functionResponse\":{\"name\":\"b\",\"response\":{\"error\":\"Tool not found: b\"}}}]}"
            );
        assertEquals(2, messages.size());
        assertEquals("a", messages.get(0).getToolCallId());
        assertEquals("one", messages.get(0).getContent().get(0).getText());
        assertEquals("b", messages.get(1).getToolCallId());
        // A non-text response is kept as JSON rather than dropped.
        assertEquals("{\"error\":\"Tool not found: b\"}", messages.get(1).getContent().get(0).getText());
    }

    @Test
    public void testParse_EmptyContentIsNull() {
        assertNull(provider.parseToUnifiedMessage("{\"role\":\"model\",\"parts\":[]}"));
        assertNull(provider.parseToUnifiedMessage("{\"role\":\"model\"}"));
        assertTrue(provider.parseToUnifiedMessages("{\"role\":\"model\",\"parts\":[{\"text\":\"\"}]}").isEmpty());
    }

    // ==================== mapMessages: sending tool calls and results back ====================

    private static Message text(String role, String text) {
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText(text);
        Message message = new Message();
        message.setRole(role);
        message.setContent(List.of(block));
        return message;
    }

    private static Message toolResult(String name, String text) {
        Message message = text("tool", text);
        message.setToolCallId(name);
        return message;
    }

    @Test
    public void testMapMessages_ToolCallsAndResultsRoundTrip() {
        Message assistant = text("assistant", "Checking.");
        assistant
            .setToolCalls(
                List
                    .of(
                        new ToolCall("a", "function", new ToolCall.ToolFunction("a", "{\"x\":1}"), "sig-a"),
                        new ToolCall("b", "function", new ToolCall.ToolFunction("b", null))
                    )
            );
        List<Message> messages = List.of(text("user", "Is it healthy?"), assistant, toolResult("a", "green"), toolResult("b", "ok"));

        String body = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL).get("body");

        assertEquals(
            "{\"role\":\"user\",\"parts\":[{\"text\":\"Is it healthy?\"}]},"
                + "{\"role\":\"model\",\"parts\":[{\"text\":\"Checking.\"},"
                + "{\"functionCall\":{\"name\":\"a\",\"args\":{\"x\":1}},\"thoughtSignature\":\"sig-a\"},"
                + "{\"functionCall\":{\"name\":\"b\",\"args\":{}}}]},"
                + "{\"role\":\"user\",\"parts\":[{\"functionResponse\":{\"name\":\"a\",\"response\":{\"text\":\"green\"}}},"
                + "{\"functionResponse\":{\"name\":\"b\",\"response\":{\"text\":\"ok\"}}}]}",
            body
        );
    }

    @Test
    public void testMapMessages_ToolResultTextIsEscaped() {
        String body = provider.mapMessages(List.of(toolResult("a", "say \"hi\"\nnow")), MLAgentType.CONVERSATIONAL).get("body");
        assertTrue(body.contains("\"response\":{\"text\":\"say \\\"hi\\\"\\nnow\"}"));
    }

    @Test
    public void testMapMessages_ParseThenMapPreservesTheSignature() {
        Message parsed = provider
            .parseToUnifiedMessage(
                "{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"t\",\"args\":{\"q\":\"x\"}},\"thoughtSignature\":\"opaque==\"}]}"
            );
        String body = provider.mapMessages(List.of(parsed), MLAgentType.CONVERSATIONAL).get("body");
        assertTrue(body.contains("\"thoughtSignature\":\"opaque==\""));
        assertTrue(body.contains("\"functionCall\":{\"name\":\"t\",\"args\":{\"q\":\"x\"}}"));
    }

    @Test
    public void testMapMessages_ResultFromAnotherProviderUsesTheCallsFunctionName() {
        Message assistant = text("assistant", "");
        assistant.setToolCalls(List.of(new ToolCall("call_abc123", "function", new ToolCall.ToolFunction("ClusterHealthTool", "{}"))));
        List<Message> messages = List.of(text("user", "health?"), assistant, toolResult("call_abc123", "green"));

        String body = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL).get("body");

        assertTrue(body.contains("{\"functionResponse\":{\"name\":\"ClusterHealthTool\",\"response\":{\"text\":\"green\"}}}"));
    }

    @Test
    public void testMapMessages_ResultWithoutAnIdIsMatchedByPosition() {
        Message assistant = text("assistant", "");
        assistant
            .setToolCalls(
                List
                    .of(
                        new ToolCall("1", "function", new ToolCall.ToolFunction("first", "{}")),
                        new ToolCall("2", "function", new ToolCall.ToolFunction("second", "{}"))
                    )
            );
        List<Message> messages = List.of(text("user", "go"), assistant, toolResult(null, "one"), toolResult(null, "two"));

        String body = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL).get("body");

        assertFalse(body.contains("\"name\":null"));
        assertTrue(body.contains("\"name\":\"first\",\"response\":{\"text\":\"one\"}"));
        assertTrue(body.contains("\"name\":\"second\",\"response\":{\"text\":\"two\"}"));
    }

    @Test
    public void testMapMessages_ResultWithNothingToMatchFailsClearly() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> provider.mapMessages(List.of(text("user", "go"), toolResult(null, "orphan")), MLAgentType.CONVERSATIONAL)
        );
        assertTrue(e.getMessage().contains("no tool_call_id"));
    }
}
