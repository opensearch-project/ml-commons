/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class AgentInputProcessorTest {

    // ========== validateInput Tests - Null/Empty Cases ==========

    @Test
    public void testValidateInput_NullAgentInput() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(null)
        );
        assertEquals("AgentInput and its input field cannot be null", exception.getMessage());
    }

    @Test
    public void testValidateInput_NullInput() {
        // Arrange
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("AgentInput and its input field cannot be null", exception.getMessage());
    }

    // ========== validateInput Tests - TEXT Type ==========

    @Test
    public void testValidateInput_ValidTextInput() {
        // Arrange
        AgentInput agentInput = new AgentInput("Hello world");

        // Act & Assert - should not throw
        AgentInputProcessor.validateInput(agentInput);
    }

    @Test
    public void testValidateInput_EmptyTextInput() {
        // Arrange
        AgentInput agentInput = new AgentInput("");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Text input cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testValidateInput_WhitespaceOnlyTextInput() {
        // Arrange
        AgentInput agentInput = new AgentInput("   ");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Text input cannot be null or empty", exception.getMessage());
    }

    // ========== validateInput Tests - CONTENT_BLOCKS Type ==========

    @Test
    public void testValidateInput_ValidContentBlocks() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Sample text");
        blocks.add(textBlock);

        AgentInput agentInput = new AgentInput(blocks);

        // Act & Assert - should not throw
        AgentInputProcessor.validateInput(agentInput);
    }

    @Test
    public void testValidateInput_EmptyContentBlocks() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        AgentInput agentInput = new AgentInput(blocks);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Input type not supported. Expected String, List<ContentBlock>, or List<Message>", exception.getMessage());
    }

    @Test
    public void testValidateInput_ContentBlock_NullType() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock block = new ContentBlock();
        block.setType(null);
        blocks.add(block);

        AgentInput agentInput = new AgentInput(blocks);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Content block type cannot be null", exception.getMessage());
    }

    @Test
    public void testValidateInput_TextContentBlock_NullText() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText(null);
        blocks.add(block);

        AgentInput agentInput = new AgentInput(blocks);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Text content block cannot have null or empty text", exception.getMessage());
    }

    @Test
    public void testValidateInput_TextContentBlock_EmptyText() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText("   ");
        blocks.add(block);

        AgentInput agentInput = new AgentInput(blocks);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Text content block cannot have null or empty text", exception.getMessage());
    }

    @Test
    public void testValidateInput_ImageContentBlock_NullImage() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.IMAGE);
        block.setImage(null);
        blocks.add(block);

        AgentInput agentInput = new AgentInput(blocks);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Image content block must have image data", exception.getMessage());
    }

    @Test
    public void testValidateInput_ValidImageContentBlock() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.IMAGE);
        ImageContent image = new ImageContent(SourceType.URL, "jpeg", "https://example.com/image.jpg");
        block.setImage(image);
        blocks.add(block);

        AgentInput agentInput = new AgentInput(blocks);

        // Act & Assert - should not throw
        AgentInputProcessor.validateInput(agentInput);
    }

    @Test
    public void testValidateInput_DocumentContentBlock_NullDocument() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.DOCUMENT);
        block.setDocument(null);
        blocks.add(block);

        AgentInput agentInput = new AgentInput(blocks);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Document content block must have document data", exception.getMessage());
    }

    @Test
    public void testValidateInput_VideoContentBlock_NullVideo() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.VIDEO);
        block.setVideo(null);
        blocks.add(block);

        AgentInput agentInput = new AgentInput(blocks);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Video content block must have video data", exception.getMessage());
    }

    @Test
    public void testValidateInput_MultipleContentBlocks() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Text content");
        blocks.add(textBlock);
        
        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        ImageContent image = new ImageContent(SourceType.URL, "png", "https://example.com/image.png");
        imageBlock.setImage(image);
        blocks.add(imageBlock);

        AgentInput agentInput = new AgentInput(blocks);

        // Act & Assert - should not throw
        AgentInputProcessor.validateInput(agentInput);
    }

    // ========== validateInput Tests - MESSAGES Type ==========

    @Test
    public void testValidateInput_ValidMessages() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello");
        
        List<ContentBlock> content = new ArrayList<>();
        content.add(textBlock);
        
        Message message = new Message("user", content);
        messages.add(message);

        AgentInput agentInput = new AgentInput(messages);

        // Act & Assert - should not throw
        AgentInputProcessor.validateInput(agentInput);
    }

    @Test
    public void testValidateInput_EmptyMessages() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        AgentInput agentInput = new AgentInput(messages);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Input type not supported. Expected String, List<ContentBlock>, or List<Message>", exception.getMessage());
    }

    @Test
    public void testValidateInput_Message_NullRole() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello");
        
        List<ContentBlock> content = new ArrayList<>();
        content.add(textBlock);
        
        Message message = new Message(null, content);
        messages.add(message);

        AgentInput agentInput = new AgentInput(messages);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Message role cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testValidateInput_Message_EmptyRole() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello");
        
        List<ContentBlock> content = new ArrayList<>();
        content.add(textBlock);
        
        Message message = new Message("   ", content);
        messages.add(message);

        AgentInput agentInput = new AgentInput(messages);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Message role cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testValidateInput_Message_NullContent() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        Message message = new Message("user", null);
        messages.add(message);

        AgentInput agentInput = new AgentInput(messages);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Message content cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testValidateInput_Message_EmptyContent() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        Message message = new Message("user", new ArrayList<>());
        messages.add(message);

        AgentInput agentInput = new AgentInput(messages);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Message content cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testValidateInput_LastMessage_NotFromUser() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello");
        
        List<ContentBlock> content = new ArrayList<>();
        content.add(textBlock);
        
        Message message = new Message("assistant", content);
        messages.add(message);

        AgentInput agentInput = new AgentInput(messages);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> AgentInputProcessor.validateInput(agentInput)
        );
        assertEquals("Last message must be from role 'user'", exception.getMessage());
    }

    @Test
    public void testValidateInput_MultipleMessages_LastIsUser() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        // Assistant message
        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("Hello");
        List<ContentBlock> assistantContent = new ArrayList<>();
        assistantContent.add(assistantBlock);
        messages.add(new Message("assistant", assistantContent));
        
        // User message
        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("Hi");
        List<ContentBlock> userContent = new ArrayList<>();
        userContent.add(userBlock);
        messages.add(new Message("user", userContent));

        AgentInput agentInput = new AgentInput(messages);

        // Act & Assert - should not throw
        AgentInputProcessor.validateInput(agentInput);
    }

    @Test
    public void testValidateInput_Messages_CaseInsensitiveUserRole() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello");
        
        List<ContentBlock> content = new ArrayList<>();
        content.add(textBlock);
        
        Message message = new Message("USER", content);
        messages.add(message);

        AgentInput agentInput = new AgentInput(messages);

        // Act & Assert - should not throw (case insensitive)
        AgentInputProcessor.validateInput(agentInput);
    }

    // ========== extractQuestionText Tests - TEXT Type ==========

    @Test
    public void testExtractQuestionText_TextInput() {
        // Arrange
        String text = "What is the weather today?";
        AgentInput agentInput = new AgentInput(text);

        // Act
        String result = AgentInputProcessor.extractQuestionText(agentInput);

        // Assert
        assertEquals(text, result);
    }

    // ========== extractQuestionText Tests - CONTENT_BLOCKS Type ==========

    @Test
    public void testExtractQuestionText_SingleTextBlock() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("What is AI?");
        blocks.add(textBlock);

        AgentInput agentInput = new AgentInput(blocks);

        // Act
        String result = AgentInputProcessor.extractQuestionText(agentInput);

        // Assert
        assertEquals("What is AI?\n", result);
    }

    @Test
    public void testExtractQuestionText_MultipleTextBlocks() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        
        ContentBlock block1 = new ContentBlock();
        block1.setType(ContentType.TEXT);
        block1.setText("First question.");
        blocks.add(block1);
        
        ContentBlock block2 = new ContentBlock();
        block2.setType(ContentType.TEXT);
        block2.setText("Second question.");
        blocks.add(block2);

        AgentInput agentInput = new AgentInput(blocks);

        // Act
        String result = AgentInputProcessor.extractQuestionText(agentInput);

        // Assert
        assertEquals("First question.\nSecond question.\n", result);
    }

    @Test
    public void testExtractQuestionText_MixedContentBlocks_IgnoresNonText() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Describe this image");
        blocks.add(textBlock);
        
        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        ImageContent image = new ImageContent(SourceType.URL, "jpeg", "https://example.com/image.jpg");
        imageBlock.setImage(image);
        blocks.add(imageBlock);

        AgentInput agentInput = new AgentInput(blocks);

        // Act
        String result = AgentInputProcessor.extractQuestionText(agentInput);

        // Assert
        assertEquals("Describe this image\n", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtractQuestionText_ContentBlocks_EmptyTextBlock() {
        // Arrange
        List<ContentBlock> blocks = new ArrayList<>();
        
        ContentBlock block1 = new ContentBlock();
        block1.setType(ContentType.TEXT);
        block1.setText("Valid text");
        blocks.add(block1);
        
        ContentBlock block2 = new ContentBlock();
        block2.setType(ContentType.TEXT);
        block2.setText("   ");
        blocks.add(block2);

        AgentInput agentInput = new AgentInput(blocks);

        // Act
        AgentInputProcessor.extractQuestionText(agentInput);
    }

    // ========== extractQuestionText Tests - MESSAGES Type ==========

    @Test
    public void testExtractQuestionText_SingleUserMessage() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("What is machine learning?");
        
        List<ContentBlock> content = new ArrayList<>();
        content.add(textBlock);
        
        Message message = new Message("user", content);
        messages.add(message);

        AgentInput agentInput = new AgentInput(messages);

        // Act
        String result = AgentInputProcessor.extractQuestionText(agentInput);

        // Assert
        assertEquals("What is machine learning?\n", result);
    }

    @Test
    public void testExtractQuestionText_MultipleTrailingUserMessages() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        // First user message
        ContentBlock block1 = new ContentBlock();
        block1.setType(ContentType.TEXT);
        block1.setText("First question");
        List<ContentBlock> content1 = new ArrayList<>();
        content1.add(block1);
        messages.add(new Message("user", content1));
        
        // Second user message
        ContentBlock block2 = new ContentBlock();
        block2.setType(ContentType.TEXT);
        block2.setText("Second question");
        List<ContentBlock> content2 = new ArrayList<>();
        content2.add(block2);
        messages.add(new Message("user", content2));

        AgentInput agentInput = new AgentInput(messages);

        // Act
        String result = AgentInputProcessor.extractQuestionText(agentInput);

        // Assert
        assertEquals("First question\n\nSecond question\n", result);
    }

    @Test
    public void testExtractQuestionText_ConversationWithTrailingUser() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        // User message
        ContentBlock userBlock1 = new ContentBlock();
        userBlock1.setType(ContentType.TEXT);
        userBlock1.setText("Hello");
        List<ContentBlock> userContent1 = new ArrayList<>();
        userContent1.add(userBlock1);
        messages.add(new Message("user", userContent1));
        
        // Assistant message
        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("Hi there");
        List<ContentBlock> assistantContent = new ArrayList<>();
        assistantContent.add(assistantBlock);
        messages.add(new Message("assistant", assistantContent));
        
        // User message
        ContentBlock userBlock2 = new ContentBlock();
        userBlock2.setType(ContentType.TEXT);
        userBlock2.setText("What is AI?");
        List<ContentBlock> userContent2 = new ArrayList<>();
        userContent2.add(userBlock2);
        messages.add(new Message("user", userContent2));

        AgentInput agentInput = new AgentInput(messages);

        // Act
        String result = AgentInputProcessor.extractQuestionText(agentInput);

        // Assert
        assertEquals("What is AI?\n", result);
    }

    // ========== filterTrailingUserMessages Tests ==========

    @Test
    public void testFilterTrailingUserMessages_NullMessages() {
        // Act
        List<Message> result = AgentInputProcessor.filterTrailingUserMessages(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFilterTrailingUserMessages_EmptyMessages() {
        // Arrange
        List<Message> messages = new ArrayList<>();

        // Act
        List<Message> result = AgentInputProcessor.filterTrailingUserMessages(messages);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFilterTrailingUserMessages_SingleUserMessage() {
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
        List<Message> result = AgentInputProcessor.filterTrailingUserMessages(messages);

        // Assert
        assertEquals(1, result.size());
        assertEquals("user", result.get(0).getRole());
    }

    @Test
    public void testFilterTrailingUserMessages_MultipleTrailingUsers() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        ContentBlock block1 = new ContentBlock();
        block1.setType(ContentType.TEXT);
        block1.setText("First");
        List<ContentBlock> content1 = new ArrayList<>();
        content1.add(block1);
        messages.add(new Message("user", content1));
        
        ContentBlock block2 = new ContentBlock();
        block2.setType(ContentType.TEXT);
        block2.setText("Second");
        List<ContentBlock> content2 = new ArrayList<>();
        content2.add(block2);
        messages.add(new Message("user", content2));

        // Act
        List<Message> result = AgentInputProcessor.filterTrailingUserMessages(messages);

        // Assert
        assertEquals(2, result.size());
        assertEquals("user", result.get(0).getRole());
        assertEquals("user", result.get(1).getRole());
    }

    @Test
    public void testFilterTrailingUserMessages_StopsAtAssistant() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        // User message
        ContentBlock userBlock1 = new ContentBlock();
        userBlock1.setType(ContentType.TEXT);
        userBlock1.setText("First user");
        List<ContentBlock> userContent1 = new ArrayList<>();
        userContent1.add(userBlock1);
        messages.add(new Message("user", userContent1));
        
        // Assistant message
        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("Assistant response");
        List<ContentBlock> assistantContent = new ArrayList<>();
        assistantContent.add(assistantBlock);
        messages.add(new Message("assistant", assistantContent));
        
        // User message
        ContentBlock userBlock2 = new ContentBlock();
        userBlock2.setType(ContentType.TEXT);
        userBlock2.setText("Second user");
        List<ContentBlock> userContent2 = new ArrayList<>();
        userContent2.add(userBlock2);
        messages.add(new Message("user", userContent2));

        // Act
        List<Message> result = AgentInputProcessor.filterTrailingUserMessages(messages);

        // Assert
        assertEquals(1, result.size());
        assertEquals("Second user", result.get(0).getContent().get(0).getText());
    }

    @Test
    public void testFilterTrailingUserMessages_AllAssistantMessages() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText("Assistant message");
        List<ContentBlock> content = new ArrayList<>();
        content.add(block);
        
        messages.add(new Message("assistant", content));

        // Act
        List<Message> result = AgentInputProcessor.filterTrailingUserMessages(messages);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFilterTrailingUserMessages_MessageWithNullRole() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText("Text");
        List<ContentBlock> content = new ArrayList<>();
        content.add(block);
        
        Message message = new Message(null, content);
        messages.add(message);

        // Act
        List<Message> result = AgentInputProcessor.filterTrailingUserMessages(messages);

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    public void testFilterTrailingUserMessages_CaseInsensitive() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        // User message (lowercase)
        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("User message");
        List<ContentBlock> userContent = new ArrayList<>();
        userContent.add(userBlock);
        messages.add(new Message("user", userContent));
        
        // Assistant message (mixed case)
        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("Assistant message");
        List<ContentBlock> assistantContent = new ArrayList<>();
        assistantContent.add(assistantBlock);
        messages.add(new Message("Assistant", assistantContent));
        
        // User message (uppercase)
        ContentBlock userBlock2 = new ContentBlock();
        userBlock2.setType(ContentType.TEXT);
        userBlock2.setText("Another user message");
        List<ContentBlock> userContent2 = new ArrayList<>();
        userContent2.add(userBlock2);
        messages.add(new Message("USER", userContent2));

        // Act
        List<Message> result = AgentInputProcessor.filterTrailingUserMessages(messages);

        // Assert
        assertEquals(1, result.size());
        assertEquals("Another user message", result.get(0).getContent().get(0).getText());
    }

    @Test
    public void testFilterTrailingUserMessages_ComplexConversation() {
        // Arrange
        List<Message> messages = new ArrayList<>();
        
        // Build a complex conversation
        ContentBlock block1 = new ContentBlock();
        block1.setType(ContentType.TEXT);
        block1.setText("User 1");
        List<ContentBlock> content1 = new ArrayList<>();
        content1.add(block1);
        messages.add(new Message("user", content1));
        
        ContentBlock block2 = new ContentBlock();
        block2.setType(ContentType.TEXT);
        block2.setText("Assistant 1");
        List<ContentBlock> content2 = new ArrayList<>();
        content2.add(block2);
        messages.add(new Message("assistant", content2));
        
        ContentBlock block3 = new ContentBlock();
        block3.setType(ContentType.TEXT);
        block3.setText("User 2");
        List<ContentBlock> content3 = new ArrayList<>();
        content3.add(block3);
        messages.add(new Message("user", content3));
        
        ContentBlock block4 = new ContentBlock();
        block4.setType(ContentType.TEXT);
        block4.setText("User 3");
        List<ContentBlock> content4 = new ArrayList<>();
        content4.add(block4);
        messages.add(new Message("user", content4));

        // Act
        List<Message> result = AgentInputProcessor.filterTrailingUserMessages(messages);

        // Assert
        assertEquals(2, result.size());
        assertEquals("User 2", result.get(0).getContent().get(0).getText());
        assertEquals("User 3", result.get(1).getContent().get(0).getText());
    }
}
