/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;

public class AgentInputTest {

    @Test
    public void testConstructorWithTextInput() {
        // Arrange
        String textInput = "Hello, this is a test";

        // Act
        AgentInput agentInput = new AgentInput(textInput);

        // Assert
        assertNotNull(agentInput);
        assertEquals(textInput, agentInput.getInput());
        assertEquals(InputType.TEXT, agentInput.getInputType());
    }

    @Test
    public void testConstructorWithContentBlocks() {
        // Arrange
        List<ContentBlock> contentBlocks = new ArrayList<>();
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Sample text");
        contentBlocks.add(textBlock);

        // Act
        AgentInput agentInput = new AgentInput(contentBlocks);

        // Assert
        assertNotNull(agentInput);
        assertEquals(contentBlocks, agentInput.getInput());
        assertEquals(InputType.CONTENT_BLOCKS, agentInput.getInputType());
    }

    @Test
    public void testConstructorWithMessages() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello");
        
        List<ContentBlock> content = new ArrayList<>();
        content.add(textBlock);
        
        Message message = new Message("user", content);
        messages.add(message);

        // Act
        AgentInput agentInput = new AgentInput(messages);

        // Assert
        assertNotNull(agentInput);
        assertEquals(messages, agentInput.getInput());
        assertEquals(InputType.MESSAGES, agentInput.getInputType());
    }

    @Test
    public void testGetInputType_WithTextInput() {
        // Arrange
        AgentInput agentInput = new AgentInput("test text");

        // Act
        InputType inputType = agentInput.getInputType();

        // Assert
        assertEquals(InputType.TEXT, inputType);
    }

    @Test
    public void testGetInputType_WithContentBlocks() {
        // Arrange
        List<ContentBlock> contentBlocks = new ArrayList<>();
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText("test");
        contentBlocks.add(block);
        
        AgentInput agentInput = new AgentInput(contentBlocks);

        // Act
        InputType inputType = agentInput.getInputType();

        // Assert
        assertEquals(InputType.CONTENT_BLOCKS, inputType);
    }

    @Test
    public void testGetInputType_WithMessages() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        Message message = new Message();
        message.setRole("user");
        message.setContent(new ArrayList<>());
        messages.add(message);
        
        AgentInput agentInput = new AgentInput(messages);

        // Act
        InputType inputType = agentInput.getInputType();

        // Assert
        assertEquals(InputType.MESSAGES, inputType);
    }

    @Test
    public void testGetInputType_WithUnsupportedType() {
        // Arrange
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(123); // Invalid type

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            agentInput::getInputType
        );
        assertTrue(exception.getMessage().contains("Input type not supported"));
    }

    @Test
    public void testWriteToAndReadFrom_TextInput() throws IOException {
        // Arrange
        String textInput = "Test message";
        AgentInput originalInput = new AgentInput(textInput);
        
        // Act - Write to stream
        BytesStreamOutput output = new BytesStreamOutput();
        originalInput.writeTo(output);
        
        // Read from stream
        StreamInput input = output.bytes().streamInput();
        AgentInput deserializedInput = new AgentInput(input);

        // Assert
        assertEquals(textInput, deserializedInput.getInput());
        assertEquals(InputType.TEXT, deserializedInput.getInputType());
    }

    @Test
    public void testWriteToAndReadFrom_ContentBlocks() throws IOException {
        // Arrange
        List<ContentBlock> contentBlocks = new ArrayList<>();
        
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello world");
        contentBlocks.add(textBlock);
        
        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        ImageContent imageContent = new ImageContent(SourceType.URL, "jpeg", "https://example.com/image.jpg");
        imageBlock.setImage(imageContent);
        contentBlocks.add(imageBlock);
        
        AgentInput originalInput = new AgentInput(contentBlocks);
        
        // Act - Write to stream
        BytesStreamOutput output = new BytesStreamOutput();
        originalInput.writeTo(output);
        
        // Read from stream
        StreamInput input = output.bytes().streamInput();
        AgentInput deserializedInput = new AgentInput(input);

        // Assert
        assertEquals(InputType.CONTENT_BLOCKS, deserializedInput.getInputType());
        @SuppressWarnings("unchecked")
        List<ContentBlock> deserializedBlocks = (List<ContentBlock>) deserializedInput.getInput();
        assertEquals(2, deserializedBlocks.size());
        assertEquals(ContentType.TEXT, deserializedBlocks.get(0).getType());
        assertEquals("Hello world", deserializedBlocks.get(0).getText());
        assertEquals(ContentType.IMAGE, deserializedBlocks.get(1).getType());
        assertEquals("jpeg", deserializedBlocks.get(1).getImage().getFormat());
    }

    @Test
    public void testWriteToAndReadFrom_Messages() throws IOException {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("User message");
        
        List<ContentBlock> content = new ArrayList<>();
        content.add(textBlock);
        
        Message message = new Message("user", content);
        messages.add(message);
        
        AgentInput originalInput = new AgentInput(messages);
        
        // Act - Write to stream
        BytesStreamOutput output = new BytesStreamOutput();
        originalInput.writeTo(output);
        
        // Read from stream
        StreamInput input = output.bytes().streamInput();
        AgentInput deserializedInput = new AgentInput(input);

        // Assert
        assertEquals(InputType.MESSAGES, deserializedInput.getInputType());
        @SuppressWarnings("unchecked")
        List<Message> deserializedMessages = (List<Message>) deserializedInput.getInput();
        assertEquals(1, deserializedMessages.size());
        assertEquals("user", deserializedMessages.get(0).getRole());
        assertEquals(1, deserializedMessages.get(0).getContent().size());
    }

    @Test
    public void testWriteToAndReadFrom_VideoContent() throws IOException {
        // Arrange
        List<ContentBlock> contentBlocks = new ArrayList<>();
        
        ContentBlock videoBlock = new ContentBlock();
        videoBlock.setType(ContentType.VIDEO);
        VideoContent videoContent = new VideoContent(SourceType.URL, "mp4", "https://example.com/video.mp4");
        videoBlock.setVideo(videoContent);
        contentBlocks.add(videoBlock);
        
        AgentInput originalInput = new AgentInput(contentBlocks);
        
        // Act - Write to stream
        BytesStreamOutput output = new BytesStreamOutput();
        originalInput.writeTo(output);
        
        // Read from stream
        StreamInput input = output.bytes().streamInput();
        AgentInput deserializedInput = new AgentInput(input);

        // Assert
        @SuppressWarnings("unchecked")
        List<ContentBlock> deserializedBlocks = (List<ContentBlock>) deserializedInput.getInput();
        assertEquals(1, deserializedBlocks.size());
        assertEquals(ContentType.VIDEO, deserializedBlocks.get(0).getType());
        assertEquals("mp4", deserializedBlocks.get(0).getVideo().getFormat());
        assertEquals(SourceType.URL, deserializedBlocks.get(0).getVideo().getType());
    }

    @Test
    public void testWriteToAndReadFrom_DocumentContent() throws IOException {
        // Arrange
        List<ContentBlock> contentBlocks = new ArrayList<>();
        
        ContentBlock docBlock = new ContentBlock();
        docBlock.setType(ContentType.DOCUMENT);
        DocumentContent docContent = new DocumentContent(SourceType.BASE64, "pdf", "base64encodeddata");
        docBlock.setDocument(docContent);
        contentBlocks.add(docBlock);
        
        AgentInput originalInput = new AgentInput(contentBlocks);
        
        // Act - Write to stream
        BytesStreamOutput output = new BytesStreamOutput();
        originalInput.writeTo(output);
        
        // Read from stream
        StreamInput input = output.bytes().streamInput();
        AgentInput deserializedInput = new AgentInput(input);

        // Assert
        @SuppressWarnings("unchecked")
        List<ContentBlock> deserializedBlocks = (List<ContentBlock>) deserializedInput.getInput();
        assertEquals(1, deserializedBlocks.size());
        assertEquals(ContentType.DOCUMENT, deserializedBlocks.get(0).getType());
        assertEquals("pdf", deserializedBlocks.get(0).getDocument().getFormat());
        assertEquals(SourceType.BASE64, deserializedBlocks.get(0).getDocument().getType());
    }

    @Test
    public void testStreamInput_InvalidInputType() throws IOException {
        // Arrange
        BytesStreamOutput output = new BytesStreamOutput();
        output.writeString("INVALID_TYPE");
        
        StreamInput input = output.bytes().streamInput();

        // Act & Assert
        IOException exception = assertThrows(
            IOException.class,
            () -> new AgentInput(input)
        );
        assertTrue(exception.getMessage().contains("Invalid input type"));
    }

    @Test
    public void testXContentParser_PlainTextInput() throws IOException {
        // Arrange
        String jsonStr = "\"Hello world\"";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        // Act
        AgentInput agentInput = new AgentInput(parser);

        // Assert
        assertEquals("Hello world", agentInput.getInput());
        assertEquals(InputType.TEXT, agentInput.getInputType());
    }

    @Test
    public void testXContentParser_InvalidFormat() throws IOException {
        // Arrange
        String jsonStr = "123";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new AgentInput(parser)
        );
        assertTrue(exception.getMessage().contains("Invalid input format"));
    }

    @Test
    public void testXContentParser_ContentBlocksArray() throws IOException {
        // Arrange
        String jsonStr = "[{\"type\":\"text\",\"text\":\"Sample text content\"}]";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        // Act
        AgentInput agentInput = new AgentInput(parser);

        // Assert
        assertNotNull(agentInput.getInput());
        assertEquals(InputType.CONTENT_BLOCKS, agentInput.getInputType());
        @SuppressWarnings("unchecked")
        List<ContentBlock> blocks = (List<ContentBlock>) agentInput.getInput();
        assertEquals(1, blocks.size());
        assertEquals(ContentType.TEXT, blocks.get(0).getType());
        assertEquals("Sample text content", blocks.get(0).getText());
    }

    @Test
    public void testXContentParser_MessagesArray() throws IOException {
        // Arrange
        String jsonStr = "[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"Hello\"}]}]";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        // Act
        AgentInput agentInput = new AgentInput(parser);

        // Assert
        assertNotNull(agentInput.getInput());
        assertEquals(InputType.MESSAGES, agentInput.getInputType());
        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) agentInput.getInput();
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).getRole());
        assertEquals(1, messages.get(0).getContent().size());
    }

    @Test
    public void testXContentParser_ImageContent() throws IOException {
        // Arrange
        String jsonStr = "[{\"type\":\"image\",\"source\":{\"type\":\"url\",\"format\":\"jpeg\",\"data\":\"https://example.com/image.jpg\"}}]";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        // Act
        AgentInput agentInput = new AgentInput(parser);

        // Assert
        assertNotNull(agentInput.getInput());
        @SuppressWarnings("unchecked")
        List<ContentBlock> blocks = (List<ContentBlock>) agentInput.getInput();
        assertEquals(1, blocks.size());
        assertEquals(ContentType.IMAGE, blocks.get(0).getType());
        assertNotNull(blocks.get(0).getImage());
        assertEquals("jpeg", blocks.get(0).getImage().getFormat());
        assertEquals(SourceType.URL, blocks.get(0).getImage().getType());
        assertEquals("https://example.com/image.jpg", blocks.get(0).getImage().getData());
    }

    @Test
    public void testXContentParser_VideoContent() throws IOException {
        // Arrange
        String jsonStr = "[{\"type\":\"video\",\"source\":{\"type\":\"url\",\"format\":\"mp4\",\"data\":\"https://example.com/video.mp4\"}}]";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        // Act
        AgentInput agentInput = new AgentInput(parser);

        // Assert
        assertNotNull(agentInput.getInput());
        @SuppressWarnings("unchecked")
        List<ContentBlock> blocks = (List<ContentBlock>) agentInput.getInput();
        assertEquals(1, blocks.size());
        assertEquals(ContentType.VIDEO, blocks.get(0).getType());
        assertNotNull(blocks.get(0).getVideo());
        assertEquals("mp4", blocks.get(0).getVideo().getFormat());
        assertEquals(SourceType.URL, blocks.get(0).getVideo().getType());
    }

    @Test
    public void testXContentParser_DocumentContent() throws IOException {
        // Arrange
        String jsonStr = "[{\"type\":\"document\",\"source\":{\"type\":\"base64\",\"format\":\"pdf\",\"data\":\"base64encodeddata\"}}]";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        // Act
        AgentInput agentInput = new AgentInput(parser);

        // Assert
        assertNotNull(agentInput.getInput());
        @SuppressWarnings("unchecked")
        List<ContentBlock> blocks = (List<ContentBlock>) agentInput.getInput();
        assertEquals(1, blocks.size());
        assertEquals(ContentType.DOCUMENT, blocks.get(0).getType());
        assertNotNull(blocks.get(0).getDocument());
        assertEquals("pdf", blocks.get(0).getDocument().getFormat());
        assertEquals(SourceType.BASE64, blocks.get(0).getDocument().getType());
    }

    @Test
    public void testXContentParser_ImageContent_MissingFormat() throws IOException {
        // Arrange
        String jsonStr = "[{\"type\":\"image\",\"source\":{\"type\":\"url\",\"data\":\"https://example.com/image.jpg\"}}]";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new AgentInput(parser)
        );
        assertTrue(exception.getMessage().contains("Image format is required"));
    }

    @Test
    public void testXContentParser_ImageContent_InvalidSourceType() throws IOException {
        // Arrange
        String jsonStr = "[{\"type\":\"image\",\"source\":{\"type\":\"invalid\",\"format\":\"jpeg\",\"data\":\"data\"}}]";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new AgentInput(parser)
        );
        assertTrue(exception.getMessage().contains("Invalid source type"));
    }

    @Test
    public void testComplexMessage_MultipleContentBlocks() throws IOException {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Check this image:");
        
        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        ImageContent imageContent = new ImageContent(SourceType.URL, "png", "https://example.com/image.png");
        imageBlock.setImage(imageContent);
        
        List<ContentBlock> content = new ArrayList<>();
        content.add(textBlock);
        content.add(imageBlock);
        
        Message message = new Message("user", content);
        messages.add(message);
        
        AgentInput originalInput = new AgentInput(messages);
        
        // Act - Write to stream
        BytesStreamOutput output = new BytesStreamOutput();
        originalInput.writeTo(output);
        
        // Read from stream
        StreamInput input = output.bytes().streamInput();
        AgentInput deserializedInput = new AgentInput(input);

        // Assert
        assertEquals(InputType.MESSAGES, deserializedInput.getInputType());
        @SuppressWarnings("unchecked")
        List<Message> deserializedMessages = (List<Message>) deserializedInput.getInput();
        assertEquals(1, deserializedMessages.size());
        assertEquals(2, deserializedMessages.get(0).getContent().size());
        assertEquals(ContentType.TEXT, deserializedMessages.get(0).getContent().get(0).getType());
        assertEquals(ContentType.IMAGE, deserializedMessages.get(0).getContent().get(1).getType());
    }

    @Test
    public void testEmptyContentBlocksList() {
        // Arrange
        List<ContentBlock> emptyList = new ArrayList<>();
        AgentInput agentInput = new AgentInput(emptyList);

        // Act & Assert
        // Empty list should still be valid, but getInputType might throw exception
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            agentInput::getInputType
        );
        assertTrue(exception.getMessage().contains("Input type not supported"));
    }

    @Test
    public void testMultipleMessages_Conversation() throws IOException {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        // User message
        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("What is the weather?");
        List<ContentBlock> userContent = new ArrayList<>();
        userContent.add(userBlock);
        messages.add(new Message("user", userContent));
        
        // Assistant message
        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("The weather is sunny.");
        List<ContentBlock> assistantContent = new ArrayList<>();
        assistantContent.add(assistantBlock);
        messages.add(new Message("assistant", assistantContent));
        
        AgentInput originalInput = new AgentInput(messages);
        
        // Act - Write to stream
        BytesStreamOutput output = new BytesStreamOutput();
        originalInput.writeTo(output);
        
        // Read from stream
        StreamInput input = output.bytes().streamInput();
        AgentInput deserializedInput = new AgentInput(input);

        // Assert
        assertEquals(InputType.MESSAGES, deserializedInput.getInputType());
        @SuppressWarnings("unchecked")
        List<Message> deserializedMessages = (List<Message>) deserializedInput.getInput();
        assertEquals(2, deserializedMessages.size());
        assertEquals("user", deserializedMessages.get(0).getRole());
        assertEquals("assistant", deserializedMessages.get(1).getRole());
    }
}
