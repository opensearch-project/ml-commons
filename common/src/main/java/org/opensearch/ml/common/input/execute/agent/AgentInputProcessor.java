/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

import java.util.List;

import lombok.extern.log4j.Log4j2;

/**
 * Utility class for validating standardized agent input formats.
 * The AgentInput itself is already standardized - this validator just validates it
 * and ensures it's ready to be passed to ModelProviders for conversion to their
 * specific request body formats.
 */
@Log4j2
public class AgentInputProcessor {

    // Private constructor to prevent instantiation
    private AgentInputProcessor() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Validates the standardized AgentInput.
     * The AgentInput is passed through after validation - ModelProviders will
     * handle the conversion to their specific request body parameters.
     *
     * @param agentInput the standardized agent input
     * @throws IllegalArgumentException if input is invalid
     */
    public static void validateInput(AgentInput agentInput) {
        if (agentInput == null || agentInput.getInput() == null) {
            throw new IllegalArgumentException("AgentInput and its input field cannot be null");
        }

        InputType type = agentInput.getInputType();

        switch (type) {
            case TEXT:
                validateTextInput((String) agentInput.getInput());
                break;
            case CONTENT_BLOCKS:
                @SuppressWarnings("unchecked")
                List<ContentBlock> blocks = (List<ContentBlock>) agentInput.getInput();
                validateContentBlocks(blocks);
                break;
            case MESSAGES:
                @SuppressWarnings("unchecked")
                List<Message> messages = (List<Message>) agentInput.getInput();
                validateMessages(messages);
                break;
            default:
                throw new IllegalArgumentException("Unsupported input type: " + type);
        }
    }

    /**
     * Validates simple text input.
     *
     * @param text the text input
     * @throws IllegalArgumentException if text is invalid
     */
    private static void validateTextInput(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text input cannot be null or empty");
        }
    }

    /**
     * Validates multi-modal content blocks.
     *
     * @param blocks the list of content blocks
     * @throws IllegalArgumentException if content blocks are invalid
     */
    private static void validateContentBlocks(List<ContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("Content blocks cannot be null or empty");
        }

        for (ContentBlock block : blocks) {
            if (block.getType() == null) {
                throw new IllegalArgumentException("Content block type cannot be null");
            }

            switch (block.getType()) {
                case TEXT:
                    if (block.getText() == null || block.getText().trim().isEmpty()) {
                        throw new IllegalArgumentException("Text content block cannot have null or empty text");
                    }
                    break;
                case IMAGE:
                    if (block.getImage() == null) {
                        throw new IllegalArgumentException("Image content block must have image data");
                    }
                    break;
                case DOCUMENT:
                    if (block.getDocument() == null) {
                        throw new IllegalArgumentException("Document content block must have document data");
                    }
                    break;
                case VIDEO:
                    if (block.getVideo() == null) {
                        throw new IllegalArgumentException("Video content block must have video data");
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported content block type: " + block.getType());
            }
        }
    }

    /**
     * Validates message-based conversation input.
     *
     * @param messages the list of messages
     * @throws IllegalArgumentException if messages are invalid
     */
    private static void validateMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Messages cannot be null or empty");
        }

        for (Message message : messages) {
            if (message.getRole() == null || message.getRole().trim().isEmpty()) {
                throw new IllegalArgumentException("Message role cannot be null or empty");
            }

            if (message.getContent() == null || message.getContent().isEmpty()) {
                throw new IllegalArgumentException("Message content cannot be null or empty");
            }

            // Validate each content block in the message
            validateContentBlocks(message.getContent());
        }
    }

    /**
     * Extracts question text from AgentInput for prompt template usage.
     * This provides the text that will be used in prompt templates that reference $parameters.question.
     */
    public static String extractQuestionText(AgentInput agentInput) {
        validateInput(agentInput);
        return switch (agentInput.getInputType()) {
            case TEXT -> (String) agentInput.getInput();
            case CONTENT_BLOCKS -> {
                // For content blocks, extract and combine text content
                @SuppressWarnings("unchecked")
                List<ContentBlock> blocks = (List<ContentBlock>) agentInput.getInput();
                yield extractTextFromContentBlocks(blocks);
            }
            case MESSAGES -> {
                // For messages, extract the last user message text
                @SuppressWarnings("unchecked")
                List<Message> messages = (List<Message>) agentInput.getInput();
                yield extractTextFromMessages(messages);
            }
            default -> throw new IllegalArgumentException("Unsupported input type: " + agentInput.getInputType());
        };
    }

    /**
     * Extracts text content from content blocks for human-readable display.
     * Ignores non text blocks
     * @throws IllegalArgumentException if content blocks are invalid[
     */
    private static String extractTextFromContentBlocks(List<ContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("Content blocks cannot be null or empty");
        }

        StringBuilder textBuilder = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block.getType() == ContentType.TEXT) {
                String text = block.getText();
                if (text != null && !text.trim().isEmpty()) {
                    textBuilder.append(text.trim());
                    textBuilder.append("\n");
                }
            }
        }

        return textBuilder.toString();
    }

    /**
     * Extracts text content from last message.
     */
    private static String extractTextFromMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Messages cannot be null or empty");
        }

        Message message = messages.getLast();
        return extractTextFromContentBlocks(message.getContent());
    }
}
