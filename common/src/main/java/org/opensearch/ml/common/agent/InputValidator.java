/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.List;

import org.opensearch.ml.common.exception.MLValidationException;

/**
 * Validates agent input formats and content to ensure they meet the required structure
 * and contain valid data before processing.
 */
// ToDo: this validation is too strict, take a look at the validation logic and fix it
public class InputValidator {

    /**
     * Validates an AgentInput object based on its detected input type.
     *
     * @param input the AgentInput to validate
     * @throws MLValidationException if validation fails
     */
    public void validateAgentInput(AgentInput input) throws MLValidationException {
        if (input == null || input.getInput() == null) {
            throw new MLValidationException("Input cannot be null");
        }

        InputType type = input.getInputType();
        switch (type) {
            case CONTENT_BLOCKS:
                validateContentBlocks((List<ContentBlock>) input.getInput());
                break;
            case MESSAGES:
                validateMessages((List<Message>) input.getInput());
                break;
            case TEXT:
                validateText((String) input.getInput());
                break;
            case UNKNOWN:
            default:
                throw new MLValidationException("Invalid input format. Expected string, array of content blocks, or array of messages");
        }
    }

    /**
     * Validates an array of content blocks.
     *
     * @param blocks the content blocks to validate
     * @throws MLValidationException if validation fails
     */
    public void validateContentBlocks(List<ContentBlock> blocks) throws MLValidationException {
        if (blocks == null || blocks.isEmpty()) {
            throw new MLValidationException("Content blocks cannot be null or empty");
        }

        int index = 0;
        for (ContentBlock block : blocks) {
            try {
                validateContentBlock(block);
            } catch (MLValidationException e) {
                throw new MLValidationException("Content block at index " + index + " is invalid: " + e.getMessage());
            }
            index++;
        }
    }

    /**
     * Validates an array of messages.
     *
     * @param messages the messages to validate
     * @throws MLValidationException if validation fails
     */
    public void validateMessages(List<Message> messages) throws MLValidationException {
        if (messages == null || messages.isEmpty()) {
            throw new MLValidationException("Messages cannot be null or empty");
        }

        int index = 0;
        for (Message message : messages) {
            try {
                if (message == null) {
                    throw new MLValidationException("Message cannot be null");
                }

                if (message.getRole() == null || message.getRole().trim().isEmpty()) {
                    throw new MLValidationException("Message must have a non-empty role");
                }

                if (message.getContent() == null) {
                    throw new MLValidationException("Message must have content");
                }

                validateContentBlocks(message.getContent());
            } catch (MLValidationException e) {
                throw new MLValidationException("Message at index " + index + " is invalid: " + e.getMessage());
            }
            index++;
        }
    }

    /**
     * Validates a single content block.
     *
     * @param block the content block to validate
     * @throws MLValidationException if validation fails
     */
    public void validateContentBlock(ContentBlock block) throws MLValidationException {
        if (block == null) {
            throw new MLValidationException("Content block cannot be null");
        }

        if (block.getType() == null) {
            throw new MLValidationException("Content block must have a type");
        }

        switch (block.getType()) {
            case TEXT:
                if (block.getText() == null || block.getText().trim().isEmpty()) {
                    throw new MLValidationException("Text content block must have non-empty text field");
                }
                break;
            case IMAGE:
                validateImageContent(block.getImage());
                break;
            case VIDEO:
                validateVideoContent(block.getVideo());
                break;
            case DOCUMENT:
                validateDocumentContent(block.getDocument());
                break;
            default:
                throw new MLValidationException("Unsupported content block type: " + block.getType());
        }
    }

    /**
     * Validates image content.
     *
     * @param imageContent the image content to validate
     * @throws MLValidationException if validation fails
     */
    public void validateImageContent(ImageContent imageContent) throws MLValidationException {
        if (imageContent == null) {
            throw new MLValidationException("Image content cannot be null for image content block");
        }

        if (imageContent.getType() == null) {
            throw new MLValidationException("Image content must have a source type (URL or BASE64)");
        }

        if (imageContent.getFormat() == null || imageContent.getFormat().trim().isEmpty()) {
            throw new MLValidationException("Image content must specify a format (e.g., jpeg, png, gif, webp)");
        }

        if (imageContent.getData() == null || imageContent.getData().trim().isEmpty()) {
            throw new MLValidationException("Image content must have data (URL or base64 encoded data)");
        }

        // Validate format is reasonable for images
        String format = imageContent.getFormat().toLowerCase();
        if (!format.matches("^(jpeg|jpg|png|gif|webp|bmp|tiff|svg)$")) {
            throw new MLValidationException(
                "Unsupported image format: " + imageContent.getFormat() + ". Supported formats: jpeg, jpg, png, gif, webp, bmp, tiff, svg"
            );
        }

        // Basic validation for URL vs base64
        if (imageContent.getType() == SourceType.URL) {
            if (!imageContent.getData().matches("^https?://.*")) {
                throw new MLValidationException("URL source type requires data to be a valid HTTP/HTTPS URL");
            }
        } else if (imageContent.getType() == SourceType.BASE64) {
            // Basic base64 validation - should not contain spaces and have reasonable length
            String data = imageContent.getData().trim();
            if (data.contains(" ") || data.length() < 4) {
                throw new MLValidationException("BASE64 source type requires valid base64 encoded data");
            }
        }
    }

    /**
     * Validates video content.
     *
     * @param videoContent the video content to validate
     * @throws MLValidationException if validation fails
     */
    public void validateVideoContent(VideoContent videoContent) throws MLValidationException {
        if (videoContent == null) {
            throw new MLValidationException("Video content cannot be null for video content block");
        }

        if (videoContent.getType() == null) {
            throw new MLValidationException("Video content must have a source type (URL or BASE64)");
        }

        if (videoContent.getFormat() == null || videoContent.getFormat().trim().isEmpty()) {
            throw new MLValidationException("Video content must specify a format (e.g., mp4, mov, avi)");
        }

        if (videoContent.getData() == null || videoContent.getData().trim().isEmpty()) {
            throw new MLValidationException("Video content must have data (URL or base64 encoded data)");
        }

        // Validate format is reasonable for videos
        String format = videoContent.getFormat().toLowerCase();
        if (!format.matches("^(mp4|mov|avi|mkv|wmv|flv|webm|m4v|3gp)$")) {
            throw new MLValidationException(
                "Unsupported video format: "
                    + videoContent.getFormat()
                    + ". Supported formats: mp4, mov, avi, mkv, wmv, flv, webm, m4v, 3gp"
            );
        }

        // Basic validation for URL vs base64
        if (videoContent.getType() == SourceType.URL) {
            if (!videoContent.getData().matches("^https?://.*")) {
                throw new MLValidationException("URL source type requires data to be a valid HTTP/HTTPS URL");
            }
        } else if (videoContent.getType() == SourceType.BASE64) {
            // Basic base64 validation - should not contain spaces and have reasonable length
            String data = videoContent.getData().trim();
            if (data.contains(" ") || data.length() < 4) {
                throw new MLValidationException("BASE64 source type requires valid base64 encoded data");
            }
        }
    }

    /**
     * Validates document content.
     *
     * @param documentContent the document content to validate
     * @throws MLValidationException if validation fails
     */
    public void validateDocumentContent(DocumentContent documentContent) throws MLValidationException {
        if (documentContent == null) {
            throw new MLValidationException("Document content cannot be null for document content block");
        }

        if (documentContent.getType() == null) {
            throw new MLValidationException("Document content must have a source type (URL or BASE64)");
        }

        if (documentContent.getFormat() == null || documentContent.getFormat().trim().isEmpty()) {
            throw new MLValidationException("Document content must specify a format (e.g., pdf, docx, txt)");
        }

        if (documentContent.getData() == null || documentContent.getData().trim().isEmpty()) {
            throw new MLValidationException("Document content must have data (URL or base64 encoded data)");
        }

        // Validate format is reasonable for documents
        String format = documentContent.getFormat().toLowerCase();
        if (!format.matches("^(pdf|docx|doc|txt|rtf|odt|html|xml|csv|xlsx|xls|pptx|ppt)$")) {
            throw new MLValidationException(
                "Unsupported document format: "
                    + documentContent.getFormat()
                    + ". Supported formats: pdf, docx, doc, txt, rtf, odt, html, xml, csv, xlsx, xls, pptx, ppt"
            );
        }

        // Basic validation for URL vs base64
        if (documentContent.getType() == SourceType.URL) {
            if (!documentContent.getData().matches("^https?://.*")) {
                throw new MLValidationException("URL source type requires data to be a valid HTTP/HTTPS URL");
            }
        } else if (documentContent.getType() == SourceType.BASE64) {
            // Basic base64 validation - should not contain spaces and have reasonable length
            String data = documentContent.getData().trim();
            if (data.contains(" ") || data.length() < 4) {
                throw new MLValidationException("BASE64 source type requires valid base64 encoded data");
            }
        }
    }

    /**
     * Validates plain text input.
     *
     * @param text the text to validate
     * @throws MLValidationException if validation fails
     */
    private void validateText(String text) throws MLValidationException {
        if (text == null || text.trim().isEmpty()) {
            throw new MLValidationException("Text input cannot be null or empty");
        }
    }
}
