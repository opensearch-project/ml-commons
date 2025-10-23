/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.List;

/**
 * Utility class for validating standardized agent input formats.
 * The AgentInput itself is already standardized - this validator just validates it
 * and ensures it's ready to be passed to ModelProviders for conversion to their
 * specific request body formats.
 */
public final class AgentInputProcessor {

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
     * @return the same AgentInput after validation
     * @throws IllegalArgumentException if input is invalid
     */
    public static AgentInput validateInput(AgentInput agentInput) {
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

        return agentInput;
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
}
