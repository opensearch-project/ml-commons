/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.XContentParser;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents standardized agent input that can handle different input formats:
 * - Plain text (String)
 * - Multi-modal content blocks (List<ContentBlock>)
 * - Message-based conversations (List<Message>)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentInput implements Writeable {
    // String, List<ContentBlock>, or List<Message>
    private Object input;

    /**
     * Constructor for stream input deserialization.
     * Supports all input types including images, videos, and documents.
     */
    public AgentInput(StreamInput in) throws IOException {
        InputType inputType = InputType.valueOf(in.readString());
        switch (inputType) {
            case TEXT:
                this.input = in.readString();
                break;
            case CONTENT_BLOCKS:
                this.input = readContentBlocksList(in);
                break;
            case MESSAGES:
                this.input = readMessagesList(in);
                break;
            default:
                throw new IOException("Unsupported input type: " + inputType);
        }
    }

    /**
     * Reads a list of ContentBlocks from stream input.
     */
    private List<ContentBlock> readContentBlocksList(StreamInput in) throws IOException {
        int size = in.readInt();
        List<ContentBlock> contentBlocks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ContentBlock block = readContentBlock(in);
            contentBlocks.add(block);
        }

        return contentBlocks;
    }

    /**
     * Reads a single ContentBlock from stream input.
     */
    private ContentBlock readContentBlock(StreamInput in) throws IOException {
        ContentType type = ContentType.valueOf(in.readString());
        ContentBlock block = new ContentBlock();
        block.setType(type);

        switch (type) {
            case TEXT:
                block.setText(in.readString());
                break;
            case IMAGE:
                block.setImage(readImageContent(in));
                break;
            case VIDEO:
                block.setVideo(readVideoContent(in));
                break;
            case DOCUMENT:
                block.setDocument(readDocumentContent(in));
                break;
        }

        return block;
    }

    /**
     * Reads ImageContent from stream input.
     */
    private ImageContent readImageContent(StreamInput in) throws IOException {
        SourceType sourceType = SourceType.valueOf(in.readString());
        String format = in.readString();
        String data = in.readString();

        return new ImageContent(sourceType, format, data);
    }

    /**
     * Reads VideoContent from stream input.
     */
    private VideoContent readVideoContent(StreamInput in) throws IOException {
        SourceType sourceType = SourceType.valueOf(in.readString());
        String format = in.readString();
        String data = in.readString();

        return new VideoContent(sourceType, format, data);
    }

    /**
     * Reads DocumentContent from stream input.
     */
    private DocumentContent readDocumentContent(StreamInput in) throws IOException {
        SourceType sourceType = SourceType.valueOf(in.readString());
        String format = in.readString();
        String data = in.readString();

        return new DocumentContent(sourceType, format, data);
    }

    /**
     * Reads a list of Messages from stream input.
     */
    private List<Message> readMessagesList(StreamInput in) throws IOException {
        int size = in.readInt();
        List<Message> messages = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Message message = readMessage(in);
            messages.add(message);
        }

        return messages;
    }

    /**
     * Reads a single Message from stream input.
     */
    private Message readMessage(StreamInput in) throws IOException {
        String role = in.readString();
        List<ContentBlock> content = readContentBlocksList(in);

        Message message = new Message();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    /**
     * Constructor for XContent parsing.
     * Supports the simplified format where everything is under "input" field.
     */
    public AgentInput(XContentParser parser) throws IOException {
        XContentParser.Token currentToken = parser.currentToken();
        if (currentToken == XContentParser.Token.VALUE_STRING) {
            // Plain text: {"input": "hi does this work"}
            this.input = parser.text();
        } else if (currentToken == XContentParser.Token.START_ARRAY) {
            // Array format: could be content blocks or messages
            this.input = parseInputArray(parser);
        } else {
            throw new IllegalArgumentException("Invalid input format. Expected string or array.");
        }
    }

    /**
     * Parses an array input and determines if it's content blocks or messages.
     */
    private Object parseInputArray(XContentParser parser) throws IOException {
        List<Object> items = new ArrayList<>();

        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);

            Object item = parseArrayItem(parser);
            items.add(item);
        }

        // Determine if this is messages or content blocks based on first item
        if (!items.isEmpty()) {
            Object firstItem = items.getFirst();
            if (firstItem instanceof Message) {
                List<Message> messages = new ArrayList<>();
                for (Object item : items) {
                    messages.add((Message) item);
                }
                return messages;
            } else if (firstItem instanceof ContentBlock) {
                List<ContentBlock> contentBlocks = new ArrayList<>();
                for (Object item : items) {
                    contentBlocks.add((ContentBlock) item);
                }
                return contentBlocks;
            }
        }

        return items;
    }

    /**
     * Parses a single item from the input array.
     * Determines if it's a Message or ContentBlock based on structure.
     */
    private Object parseArrayItem(XContentParser parser) throws IOException {
        Map<String, Object> itemMap = new HashMap<>();

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case "role":
                    // This indicates it's a Message
                    itemMap.put("role", parser.text());
                    break;
                case "content":
                    // Parse content array for messages
                    itemMap.put("content", parseContentArray(parser));
                    break;
                case "type":
                    // This indicates it's a ContentBlock
                    itemMap.put("type", parser.text());
                    break;
                case "text":
                    itemMap.put("text", parser.text());
                    break;
                case "source":
                    itemMap.put("source", parseSource(parser));
                    break;
                default:
                    // Store other fields as-is
                    itemMap.put(fieldName, parseValue(parser));
                    break;
            }
        }

        // Determine if this is a Message or ContentBlock
        if (itemMap.containsKey("role")) {
            return createMessage(itemMap);
        }

        if (itemMap.containsKey("type")) {
            return createContentBlock(itemMap);
        }

        throw new IllegalArgumentException("Invalid item format. Must have 'role' (for messages) or 'type' (for content blocks).");
    }

    /**
     * Parses content array for messages.
     */
    @SuppressWarnings("unchecked")
    private List<ContentBlock> parseContentArray(XContentParser parser) throws IOException {
        List<ContentBlock> contentBlocks = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);

        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);

            ContentBlock contentBlock = (ContentBlock) parseArrayItem(parser);
            contentBlocks.add(contentBlock);
        }

        return contentBlocks;
    }

    /**
     * Parses source object for media content.
     */
    private Map<String, Object> parseSource(XContentParser parser) throws IOException {
        Map<String, Object> source = new HashMap<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            source.put(fieldName, parseValue(parser));
        }

        return source;
    }

    /**
     * Parses a generic value from the parser.
     */
    private Object parseValue(XContentParser parser) throws IOException {
        XContentParser.Token token = parser.currentToken();

        switch (token) {
            case VALUE_STRING:
                return parser.text();
            case VALUE_NUMBER:
                return parser.numberValue();
            case VALUE_BOOLEAN:
                return parser.booleanValue();
            case VALUE_NULL:
                return null;
            case START_OBJECT:
                Map<String, Object> map = new HashMap<>();
                while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                    String fieldName = parser.currentName();
                    parser.nextToken();
                    map.put(fieldName, parseValue(parser));
                }
                return map;
            case START_ARRAY:
                List<Object> list = new ArrayList<>();
                while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                    list.add(parseValue(parser));
                }
                return list;
            default:
                throw new IllegalArgumentException("Unexpected token: " + token);
        }
    }

    /**
     * Creates a Message object from parsed data.
     */
    @SuppressWarnings("unchecked")
    private Message createMessage(Map<String, Object> itemMap) {
        String role = (String) itemMap.get("role");
        List<ContentBlock> content = (List<ContentBlock>) itemMap.get("content");

        Message message = new Message();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    /**
     * Creates a ContentBlock object from parsed data.
     */
    @SuppressWarnings("unchecked")
    private ContentBlock createContentBlock(Map<String, Object> itemMap) {
        String type = (String) itemMap.get("type");
        ContentType contentType = ContentType.valueOf(type.toUpperCase());

        ContentBlock contentBlock = new ContentBlock();
        contentBlock.setType(contentType);

        switch (contentType) {
            case TEXT:
                contentBlock.setText((String) itemMap.get("text"));
                break;
            case IMAGE:
                Map<String, Object> source = (Map<String, Object>) itemMap.get("source");
                ImageContent imageContent = createImageContent(source);
                contentBlock.setImage(imageContent);
                break;
            case VIDEO:
                Map<String, Object> videoSource = (Map<String, Object>) itemMap.get("source");
                VideoContent videoContent = createVideoContent(videoSource);
                contentBlock.setVideo(videoContent);
                break;
            case DOCUMENT:
                Map<String, Object> docSource = (Map<String, Object>) itemMap.get("source");
                DocumentContent documentContent = createDocumentContent(docSource);
                contentBlock.setDocument(documentContent);
                break;
        }

        return contentBlock;
    }

    /**
     * Creates ImageContent from source data.
     */
    private ImageContent createImageContent(Map<String, Object> source) {
        String format = (String) source.get("format");
        String type = (String) source.get("type");
        String data = (String) source.get("data");

        SourceType sourceType = SourceType.valueOf(type.toUpperCase());

        ImageContent imageContent = new ImageContent();
        imageContent.setFormat(format);
        imageContent.setType(sourceType);
        imageContent.setData(data);
        return imageContent;
    }

    /**
     * Creates VideoContent from source data.
     */
    private VideoContent createVideoContent(Map<String, Object> source) {
        String format = (String) source.get("format");
        String type = (String) source.get("type");
        String data = (String) source.get("data");

        SourceType sourceType = SourceType.valueOf(type.toUpperCase());

        VideoContent videoContent = new VideoContent();
        videoContent.setFormat(format);
        videoContent.setType(sourceType);
        videoContent.setData(data);
        return videoContent;
    }

    /**
     * Creates DocumentContent from source data.
     */
    private DocumentContent createDocumentContent(Map<String, Object> source) {
        String format = (String) source.get("format");
        String type = (String) source.get("type");
        String data = (String) source.get("data");

        SourceType sourceType = SourceType.valueOf(type.toUpperCase());

        DocumentContent documentContent = new DocumentContent();
        documentContent.setFormat(format);
        documentContent.setType(sourceType);
        documentContent.setData(data);
        return documentContent;
    }

    @Override
    public void writeTo(StreamOutput out) throws IllegalArgumentException, IOException {
        InputType inputType = getInputType();
        out.writeString(inputType.name());

        switch (inputType) {
            case TEXT:
                out.writeString((String) input);
                break;
            case CONTENT_BLOCKS:
                @SuppressWarnings("unchecked")
                List<ContentBlock> contentBlocks = (List<ContentBlock>) input;
                writeContentBlocksList(out, contentBlocks);
                break;
            case MESSAGES:
                @SuppressWarnings("unchecked")
                List<Message> messages = (List<Message>) input;
                writeMessagesList(out, messages);
                break;
            default:
                throw new IllegalArgumentException("Unsupported input type: " + inputType);
        }
    }

    /**
     * Writes a list of ContentBlocks to stream output.
     */
    private void writeContentBlocksList(StreamOutput out, List<ContentBlock> contentBlocks) throws IOException {
        out.writeInt(contentBlocks.size());
        for (ContentBlock block : contentBlocks) {
            writeContentBlock(out, block);
        }
    }

    /**
     * Writes a single ContentBlock to stream output.
     */
    private void writeContentBlock(StreamOutput out, ContentBlock block) throws IOException {
        out.writeString(block.getType().name());

        switch (block.getType()) {
            case TEXT:
                out.writeString(block.getText());
                break;
            case IMAGE:
                writeImageContent(out, block.getImage());
                break;
            case VIDEO:
                writeVideoContent(out, block.getVideo());
                break;
            case DOCUMENT:
                writeDocumentContent(out, block.getDocument());
                break;
        }
    }

    /**
     * Writes ImageContent to stream output.
     */
    private void writeImageContent(StreamOutput out, ImageContent image) throws IOException {
        out.writeString(image.getType().name());
        out.writeString(image.getFormat());
        out.writeString(image.getData());
    }

    /**
     * Writes VideoContent to stream output.
     */
    private void writeVideoContent(StreamOutput out, VideoContent video) throws IOException {
        out.writeString(video.getType().name());
        out.writeString(video.getFormat());
        out.writeString(video.getData());
    }

    /**
     * Writes DocumentContent to stream output.
     */
    private void writeDocumentContent(StreamOutput out, DocumentContent document) throws IOException {
        out.writeString(document.getType().name());
        out.writeString(document.getFormat());
        out.writeString(document.getData());
    }

    /**
     * Writes a list of Messages to stream output.
     */
    private void writeMessagesList(StreamOutput out, List<Message> messages) throws IOException {
        out.writeInt(messages.size());

        for (Message message : messages) {
            writeMessage(out, message);
        }
    }

    /**
     * Writes a single Message to stream output.
     */
    private void writeMessage(StreamOutput out, Message message) throws IOException {
        out.writeString(message.getRole());
        writeContentBlocksList(out, message.getContent());
    }

    /**
     * Determines the type of input based on the input object.
     * @return InputType enum value indicating the format of the input
     */
    public InputType getInputType() throws IllegalArgumentException {
        if (input instanceof String) {
            return InputType.TEXT;
        }

        if (input instanceof List<?> list) {
            if (!list.isEmpty()) {
                Object firstElement = list.getFirst();
                if (firstElement instanceof ContentBlock) {
                    return InputType.CONTENT_BLOCKS;
                }

                if (firstElement instanceof Message) {
                    return InputType.MESSAGES;
                }
            }
        }

        throw new IllegalArgumentException("Input type not supported: " + input);
    }
}
