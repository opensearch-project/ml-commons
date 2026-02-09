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

public class OpenaiV1ChatCompletionsModelProviderTest {

    private OpenaiV1ChatCompletionsModelProvider provider;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        provider = new OpenaiV1ChatCompletionsModelProvider();
    }

    @Test
    public void testGetLLMInterface() {
        // Act
        String result = provider.getLLMInterface();

        // Assert
        assertEquals("openai/v1/chat/completions", result);
    }

    @Test
    public void testCreateConnector_WithFullParameters() {
        // Arrange
        String modelId = "gpt-4o";
        Map<String, String> credential = new HashMap<>();
        credential.put("openai_api_key", "test_api_key");

        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("temperature", "0.7");

        // Act
        Connector connector = provider.createConnector(modelId, credential, modelParameters);

        // Assert
        assertNotNull(connector);
        assertTrue(connector instanceof HttpConnector);
        HttpConnector httpConnector = (HttpConnector) connector;
        assertEquals("Auto-generated OpenAI connector for Agent", httpConnector.getName());
        assertEquals("Auto-generated connector for OpenAI Chat Completions API", httpConnector.getDescription());
        assertEquals("http", httpConnector.getProtocol());
        assertEquals(modelId, httpConnector.getParameters().get("model"));
        assertEquals("0.7", httpConnector.getParameters().get("temperature"));
        assertNotNull(httpConnector.getActions());
        assertEquals(1, httpConnector.getActions().size());
    }

    @Test
    public void testCreateConnector_WithDefaultParameters() {
        // Arrange
        String modelId = "gpt-4o";
        Map<String, String> credential = new HashMap<>();
        credential.put("openai_api_key", "test_key");

        // Act
        Connector connector = provider.createConnector(modelId, credential, null);

        // Assert
        assertNotNull(connector);
        assertTrue(connector instanceof HttpConnector);
        HttpConnector httpConnector = (HttpConnector) connector;
        assertEquals(modelId, httpConnector.getParameters().get("model"));
    }

    @Test
    public void testCreateConnector_WithNullCredential() {
        // Arrange
        String modelId = "gpt-4o";
        Map<String, String> modelParameters = new HashMap<>();

        // Act
        Connector connector = provider.createConnector(modelId, null, modelParameters);

        // Assert
        assertNotNull(connector);
        assertTrue(connector instanceof HttpConnector);
        HttpConnector httpConnector = (HttpConnector) connector;
        assertNotNull(httpConnector.getCredential());
    }

    @Test
    public void testCreateConnector_WithEmptyCredential() {
        // Arrange
        String modelId = "gpt-4o";
        Map<String, String> credential = new HashMap<>();
        Map<String, String> modelParameters = new HashMap<>();

        // Act
        Connector connector = provider.createConnector(modelId, credential, modelParameters);

        // Assert
        assertNotNull(connector);
        assertTrue(connector instanceof HttpConnector);
    }

    @Test
    public void testCreateModelInput() {
        // Arrange
        String modelName = "gpt-4o";
        Map<String, String> credential = new HashMap<>();
        credential.put("openai_api_key", "test_key");
        Connector connector = provider.createConnector(modelName, credential, null);

        // Act
        var modelInput = provider.createModelInput(modelName, connector, null);

        // Assert
        assertNotNull(modelInput);
        assertEquals("Auto-generated model for " + modelName, modelInput.getModelName());
        assertEquals(connector, modelInput.getConnector());
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
        assertTrue(body.contains("\"content\":\"Hello, how are you?\""));
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
        assertTrue(body.contains("\"type\":\"text\""));
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
        assertTrue(body.contains("\"type\":\"image_url\""));
        assertTrue(body.contains("\"image_url\""));
        assertTrue(body.contains("data:image/png;base64,"));
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
        assertTrue(body.contains("\"type\":\"image_url\""));
        assertTrue(body.contains("\"image_url\""));
        // URL is escaped in JSON, so check for parts that would be present
        assertTrue(body.contains("example.com") || body.contains("image.jpg"));
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

        // Act & Assert
        try {
            provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);
            fail("Should throw IllegalArgumentException for VIDEO content type");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Video content is not supported"));
        }
    }

    @Test
    public void testMapContentBlocks_VideoWithURL() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock videoBlock = new ContentBlock();
        videoBlock.setType(ContentType.VIDEO);
        VideoContent video = new VideoContent(SourceType.URL, "mp4", "https://example.com/video.mp4");
        videoBlock.setVideo(video);
        blocks.add(videoBlock);

        // Act & Assert
        try {
            provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);
            fail("Should throw IllegalArgumentException for VIDEO content type");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Video content is not supported"));
        }
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

        // Act & Assert
        try {
            provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);
            fail("Should throw IllegalArgumentException for DOCUMENT content type");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Document content is not supported"));
        }
    }

    @Test
    public void testMapContentBlocks_DocumentWithURL() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock docBlock = new ContentBlock();
        docBlock.setType(ContentType.DOCUMENT);
        DocumentContent doc = new DocumentContent(SourceType.URL, "pdf", "https://example.com/doc.pdf");
        docBlock.setDocument(doc);
        blocks.add(docBlock);

        // Act & Assert
        try {
            provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);
            fail("Should throw IllegalArgumentException for DOCUMENT content type");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Document content is not supported"));
        }
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
        assertTrue(body.contains("\"type\":\"image_url\""));
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
        String modelId = "gpt-4o";
        Map<String, String> credential = new HashMap<>();
        credential.put("openai_api_key", "test_key");

        // Act
        Connector connector = provider.createConnector(modelId, credential, null);

        // Assert
        assertTrue(connector instanceof HttpConnector);
        HttpConnector httpConnector = (HttpConnector) connector;
        assertNotNull(httpConnector.getConnectorClientConfig());
        assertEquals(Integer.valueOf(3), httpConnector.getConnectorClientConfig().getMaxRetryTimes());
    }

    @Test
    public void testCreateConnector_VerifyHeaders() {
        // Arrange
        String modelId = "gpt-4o";
        Map<String, String> credential = new HashMap<>();
        credential.put("openai_api_key", "test_key");

        // Act
        Connector connector = provider.createConnector(modelId, credential, null);

        // Assert
        assertTrue(connector instanceof HttpConnector);
        HttpConnector httpConnector = (HttpConnector) connector;
        assertNotNull(httpConnector.getActions());
        assertEquals(1, httpConnector.getActions().size());
        var action = httpConnector.getActions().get(0);
        assertNotNull(action.getHeaders());
        assertTrue(action.getHeaders().containsKey("Content-Type"));
        assertEquals("application/json", action.getHeaders().get("Content-Type"));
        assertTrue(action.getHeaders().containsKey("Authorization"));
        assertTrue(action.getHeaders().get("Authorization").contains("${credential.openai_api_key}"));
    }

    @Test
    public void testCreateConnector_VerifyURL() {
        // Arrange
        String modelId = "gpt-4o";
        Map<String, String> credential = new HashMap<>();
        credential.put("openai_api_key", "test_key");

        // Act
        Connector connector = provider.createConnector(modelId, credential, null);

        // Assert
        assertTrue(connector instanceof HttpConnector);
        HttpConnector httpConnector = (HttpConnector) connector;
        assertNotNull(httpConnector.getActions());
        assertEquals(1, httpConnector.getActions().size());
        var action = httpConnector.getActions().get(0);
        assertEquals("https://api.openai.com/v1/chat/completions", action.getUrl());
        assertEquals("POST", action.getMethod());
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
        assertTrue(body.contains("\"content\":\"Hello, how are you?\""));
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
        assertTrue(body.contains("\"type\":\"image_url\""));
        assertTrue(body.contains("data:image/png;base64,"));
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
        assertTrue(body.contains("\"role\":\"assistant\""));
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

        // Act & Assert
        try {
            provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);
            fail("Should throw IllegalArgumentException for VIDEO content type");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Video content is not supported"));
        }
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

        // Act & Assert
        try {
            provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);
            fail("Should throw IllegalArgumentException for DOCUMENT content type");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Document content is not supported"));
        }
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

    @Test
    public void testMapContentBlocks_ImageWithDifferentFormats() {
        // Test different image formats
        String[] formats = { "png", "jpeg", "jpg", "gif", "webp" };
        for (String format : formats) {
            // Arrange
            List<ContentBlock> blocks = new ArrayList<>();
            ContentBlock imageBlock = new ContentBlock();
            imageBlock.setType(ContentType.IMAGE);
            ImageContent image = new ImageContent(SourceType.BASE64, format, "base64data");
            imageBlock.setImage(image);
            blocks.add(imageBlock);

            // Act
            Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

            // Assert
            assertNotNull(result);
            String body = result.get("body");
            assertTrue("Format " + format + " should be supported", body.contains("data:image/" + format + ";base64,"));
        }
    }

    @Test
    public void testMapMessages_WithImageContent() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        List<ContentBlock> content = new ArrayList<>();
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("What's in this image?");
        content.add(textBlock);

        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        ImageContent image = new ImageContent(SourceType.BASE64, "png", "imagedata");
        imageBlock.setImage(image);
        content.add(imageBlock);

        Message message = new Message("user", content);
        messages.add(message);

        // Act
        Map<String, String> result = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"text\":\"What's in this image?\""));
        assertTrue(body.contains("\"type\":\"image_url\""));
    }

    @Test
    public void testMapMessages_WithToolCalls() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        // User message
        List<ContentBlock> userContent = new ArrayList<>();
        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("What's the weather in Seattle?");
        userContent.add(userBlock);
        messages.add(new Message("user", userContent));

        // Assistant message with tool call
        List<ContentBlock> assistantContent = new ArrayList<>();
        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("Let me check the weather for you.");
        assistantContent.add(assistantBlock);

        Message assistantMessage = new Message("assistant", assistantContent);
        List<ToolCall> toolCalls = new ArrayList<>();
        ToolCall.ToolFunction function = new ToolCall.ToolFunction("get_weather", "{\"location\":\"Seattle, WA\"}");
        ToolCall toolCall = new ToolCall("call_123", "function", function);
        toolCalls.add(toolCall);
        assistantMessage.setToolCalls(toolCalls);
        messages.add(assistantMessage);

        // Act
        Map<String, String> result = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"role\":\"assistant\""));
        assertTrue(body.contains("\"tool_calls\""));
        assertTrue(body.contains("\"id\":\"call_123\""));
        assertTrue(body.contains("\"type\":\"function\""));
        assertTrue(body.contains("\"name\":\"get_weather\""));
        assertTrue(body.contains("\"arguments\""));
        assertTrue(body.contains("location"));
        assertTrue(body.contains("Seattle"));
    }

    @Test
    public void testMapMessages_WithToolResult() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        // User message
        List<ContentBlock> userContent = new ArrayList<>();
        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("What's the weather?");
        userContent.add(userBlock);
        messages.add(new Message("user", userContent));

        // Assistant message with tool call
        List<ContentBlock> assistantContent = new ArrayList<>();
        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("Checking weather...");
        assistantContent.add(assistantBlock);

        Message assistantMessage = new Message("assistant", assistantContent);
        List<ToolCall> toolCalls = new ArrayList<>();
        ToolCall.ToolFunction function = new ToolCall.ToolFunction("get_weather", "{\"location\":\"Seattle\"}");
        ToolCall toolCall = new ToolCall("call_123", "function", function);
        toolCalls.add(toolCall);
        assistantMessage.setToolCalls(toolCalls);
        messages.add(assistantMessage);

        // Tool result message
        List<ContentBlock> toolContent = new ArrayList<>();
        ContentBlock toolBlock = new ContentBlock();
        toolBlock.setType(ContentType.TEXT);
        toolBlock.setText("{\"temperature\":\"65F\",\"condition\":\"sunny\"}");
        toolContent.add(toolBlock);

        Message toolMessage = new Message("tool", toolContent);
        toolMessage.setToolCallId("call_123");
        messages.add(toolMessage);

        // Act
        Map<String, String> result = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"role\":\"tool\""));
        assertTrue(body.contains("\"tool_call_id\":\"call_123\""));
        assertTrue(body.contains("temperature"));
        assertTrue(body.contains("65F"));
    }

    @Test
    public void testMapMessages_WithMultipleToolCalls() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        // Assistant message with multiple tool calls
        List<ContentBlock> assistantContent = new ArrayList<>();
        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("Let me check both locations.");
        assistantContent.add(assistantBlock);

        Message assistantMessage = new Message("assistant", assistantContent);
        List<ToolCall> toolCalls = new ArrayList<>();

        ToolCall.ToolFunction function1 = new ToolCall.ToolFunction("get_weather", "{\"location\":\"Seattle\"}");
        ToolCall toolCall1 = new ToolCall("call_123", "function", function1);
        toolCalls.add(toolCall1);

        ToolCall.ToolFunction function2 = new ToolCall.ToolFunction("get_weather", "{\"location\":\"Portland\"}");
        ToolCall toolCall2 = new ToolCall("call_456", "function", function2);
        toolCalls.add(toolCall2);

        assistantMessage.setToolCalls(toolCalls);
        messages.add(assistantMessage);

        // Act
        Map<String, String> result = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"tool_calls\""));
        assertTrue(body.contains("\"id\":\"call_123\""));
        assertTrue(body.contains("\"id\":\"call_456\""));
        assertTrue(body.contains("Seattle"));
        assertTrue(body.contains("Portland"));
    }

    @Test
    public void testMapMessages_WithToolCallsAndResults() {
        // Arrange - Complete conversation with tool usage
        List<Message> messages = new ArrayList<>();

        // 1. User asks question
        List<ContentBlock> userContent = new ArrayList<>();
        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("What's the weather in Seattle?");
        userContent.add(userBlock);
        messages.add(new Message("user", userContent));

        // 2. Assistant decides to use tool
        List<ContentBlock> assistantContent1 = new ArrayList<>();
        ContentBlock assistantBlock1 = new ContentBlock();
        assistantBlock1.setType(ContentType.TEXT);
        assistantBlock1.setText("Let me check the weather for you.");
        assistantContent1.add(assistantBlock1);

        Message assistantMessage1 = new Message("assistant", assistantContent1);
        List<ToolCall> toolCalls = new ArrayList<>();
        ToolCall.ToolFunction function = new ToolCall.ToolFunction("get_weather", "{\"location\":\"Seattle, WA\"}");
        ToolCall toolCall = new ToolCall("call_123", "function", function);
        toolCalls.add(toolCall);
        assistantMessage1.setToolCalls(toolCalls);
        messages.add(assistantMessage1);

        // 3. Tool result
        List<ContentBlock> toolContent = new ArrayList<>();
        ContentBlock toolBlock = new ContentBlock();
        toolBlock.setType(ContentType.TEXT);
        toolBlock.setText("{\"temperature\":\"65F\",\"condition\":\"partly cloudy\"}");
        toolContent.add(toolBlock);

        Message toolMessage = new Message("tool", toolContent);
        toolMessage.setToolCallId("call_123");
        messages.add(toolMessage);

        // 4. Assistant provides final answer
        List<ContentBlock> assistantContent2 = new ArrayList<>();
        ContentBlock assistantBlock2 = new ContentBlock();
        assistantBlock2.setType(ContentType.TEXT);
        assistantBlock2.setText("The weather in Seattle is 65F and partly cloudy.");
        assistantContent2.add(assistantBlock2);
        messages.add(new Message("assistant", assistantContent2));

        // Act
        Map<String, String> result = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");

        // Verify all messages are present
        assertTrue(body.contains("What's the weather in Seattle?"));
        assertTrue(body.contains("Let me check the weather for you."));
        assertTrue(body.contains("\"tool_calls\""));
        assertTrue(body.contains("\"id\":\"call_123\""));
        assertTrue(body.contains("\"role\":\"tool\""));
        assertTrue(body.contains("\"tool_call_id\":\"call_123\""));
        assertTrue(body.contains("The weather in Seattle is 65F and partly cloudy."));
    }

    @Test
    public void testMapAgentInput_WithToolCalls() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        List<ContentBlock> userContent = new ArrayList<>();
        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("What's the weather?");
        userContent.add(userBlock);
        messages.add(new Message("user", userContent));

        List<ContentBlock> assistantContent = new ArrayList<>();
        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("Checking...");
        assistantContent.add(assistantBlock);

        Message assistantMessage = new Message("assistant", assistantContent);
        List<ToolCall> toolCalls = new ArrayList<>();
        ToolCall.ToolFunction function = new ToolCall.ToolFunction("get_weather", "{\"location\":\"Seattle\"}");
        ToolCall toolCall = new ToolCall("call_123", "function", function);
        toolCalls.add(toolCall);
        assistantMessage.setToolCalls(toolCalls);
        messages.add(assistantMessage);

        AgentInput agentInput = new AgentInput();
        agentInput.setInput(messages);

        // Act
        Map<String, String> result = provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"tool_calls\""));
        assertTrue(body.contains("\"id\":\"call_123\""));
        assertTrue(body.contains("\"name\":\"get_weather\""));
    }
}
