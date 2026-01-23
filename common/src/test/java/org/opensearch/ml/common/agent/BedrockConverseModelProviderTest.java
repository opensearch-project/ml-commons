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
import org.opensearch.ml.common.FunctionName;
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
import org.opensearch.ml.common.input.execute.agent.VideoContent;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

public class BedrockConverseModelProviderTest {

    private BedrockConverseModelProvider provider;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        provider = new BedrockConverseModelProvider();
    }

    @Test
    public void testGetLLMInterface() {
        // Act
        String result = provider.getLLMInterface();

        // Assert
        assertEquals("bedrock/converse/claude", result);
    }

    @Test
    public void testCreateConnector_WithFullParameters() {
        // Arrange
        String modelId = "us.anthropic.claude-3-5-sonnet-20241022-v2:0";
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
        assertEquals("Auto-generated Bedrock Converse connector for Agent", awsConnector.getName());
        assertEquals("Auto-generated connector for Bedrock Converse API", awsConnector.getDescription());
        assertEquals("aws_sigv4", awsConnector.getProtocol());
        assertEquals("us-west-2", awsConnector.getParameters().get("region"));
        assertEquals("bedrock", awsConnector.getParameters().get("service_name"));
        assertEquals(modelId, awsConnector.getParameters().get("model"));
        assertNotNull(awsConnector.getActions());
        assertEquals(1, awsConnector.getActions().size());
    }

    @Test
    public void testCreateConnector_WithDefaultRegion() {
        // Arrange
        String modelId = "us.anthropic.claude-3-5-sonnet-20241022-v2:0";
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
    public void testCreateConnector_WithNullCredential() {
        // Arrange
        String modelId = "us.anthropic.claude-3-5-sonnet-20241022-v2:0";
        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("region", "eu-west-1");

        // Arrange & Assert
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");
        // Act
        provider.createConnector(modelId, null, modelParameters);
    }

    @Test
    public void testCreateConnector_WithEmptyCredential() {
        // Arrange
        String modelId = "us.anthropic.claude-3-5-sonnet-20241022-v2:0";
        Map<String, String> credential = new HashMap<>();
        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("region", "ap-southeast-1");

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");

        // Act
        provider.createConnector(modelId, credential, modelParameters);
    }

    @Test
    public void testCreateModelInput() {
        // Arrange
        String modelName = "us.anthropic.claude-3-5-sonnet-20241022-v2:0";

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");
        provider.createConnector(modelName, new HashMap<>(), new HashMap<>());
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
        assertTrue(body.contains("\"image\""));
        assertTrue(body.contains("\"format\":\"png\""));
        assertTrue(body.contains("\"bytes\""));
        assertTrue(body.contains("base64encodeddata"));
    }

    @Test
    public void testMapContentBlocks_ImageWithS3URL() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        ImageContent image = new ImageContent(SourceType.URL, "jpeg", "s3://bucket/image.jpg");
        imageBlock.setImage(image);
        blocks.add(imageBlock);

        // Act
        Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("s3Location"));
        assertTrue(body.contains("s3:") && body.contains("bucket") && body.contains("image.jpg"));
    }

    @Test
    public void testMapContentBlocks_ImageWithInvalidURL() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        ImageContent image = new ImageContent(SourceType.URL, "jpeg", "https://example.com/image.jpg");
        imageBlock.setImage(image);
        blocks.add(imageBlock);

        // Act & Assert
        try {
            provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);
            fail("Should throw IllegalArgumentException for non-S3 URL");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("URL-based content must use S3 URIs"));
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

        // Act
        Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"document\""));
        assertTrue(body.contains("\"format\":\"pdf\""));
        assertTrue(body.contains("\"bytes\""));
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
        assertTrue(body.contains("\"video\""));
        assertTrue(body.contains("\"format\":\"mp4\""));
        assertTrue(body.contains("\"bytes\""));
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
        assertTrue(body.contains("\"image\""));
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
        String modelId = "us.anthropic.claude-3-5-sonnet-20241022-v2:0";
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

    @Test
    public void testMapContentBlocks_VideoWithS3URL() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock videoBlock = new ContentBlock();
        videoBlock.setType(ContentType.VIDEO);
        VideoContent video = new VideoContent(SourceType.URL, "mp4", "s3://bucket/video.mp4");
        videoBlock.setVideo(video);
        blocks.add(videoBlock);

        // Act
        Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("s3Location"));
        assertTrue(body.contains("s3:") && body.contains("bucket") && body.contains("video.mp4"));
    }

    @Test
    public void testMapContentBlocks_DocumentWithS3URL() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock docBlock = new ContentBlock();
        docBlock.setType(ContentType.DOCUMENT);
        DocumentContent doc = new DocumentContent(SourceType.URL, "pdf", "s3://bucket/document.pdf");
        docBlock.setDocument(doc);
        blocks.add(docBlock);

        // Act
        Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("s3Location"));
        assertTrue(body.contains("s3:") && body.contains("bucket") && body.contains("document.pdf"));
    }

    @Test
    public void testMapContentBlocks_VideoWithInvalidURL() {
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
            fail("Should throw IllegalArgumentException for non-S3 URL");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("URL-based content must use S3 URIs"));
        }
    }

    @Test
    public void testMapContentBlocks_DocumentWithInvalidURL() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock docBlock = new ContentBlock();
        docBlock.setType(ContentType.DOCUMENT);
        DocumentContent doc = new DocumentContent(SourceType.URL, "pdf", "http://example.com/doc.pdf");
        docBlock.setDocument(doc);
        blocks.add(docBlock);

        // Act & Assert
        try {
            provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);
            fail("Should throw IllegalArgumentException for non-S3 URL");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("URL-based content must use S3 URIs"));
        }
    }

    @Test
    public void testMapContentBlocks_ImageWithNullURL() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        ImageContent image = new ImageContent(SourceType.URL, "jpeg", null);
        imageBlock.setImage(image);
        blocks.add(imageBlock);

        // Act & Assert
        try {
            provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);
            fail("Should throw IllegalArgumentException for null URL");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("URL-based content must use S3 URIs"));
        }
    }

    // Tests for mapAgentInput method

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
        assertTrue(body.contains("\"image\""));
        assertTrue(body.contains("\"format\":\"png\""));
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

        // Act
        Map<String, String> result = provider.mapAgentInput(agentInput, MLAgentType.CONVERSATIONAL);

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"video\""));
        assertTrue(body.contains("\"format\":\"mp4\""));
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
        assertTrue(body.contains("\"document\""));
        assertTrue(body.contains("\"format\":\"pdf\""));
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
}
