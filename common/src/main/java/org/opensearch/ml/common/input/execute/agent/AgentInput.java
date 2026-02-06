/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

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
 * - Multi-modal content blocks (List&lt;ContentBlock&gt;)
 * - Message-based conversations (List&lt;Message&gt;)
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
        String inputTypeStr = in.readString();
        InputType inputType;
        try {
            inputType = InputType.valueOf(inputTypeStr);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid input type. Supported types: TEXT, CONTENT_BLOCKS, MESSAGES", e);
        }

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
                throw new IOException("Unsupported input type. Supported types: TEXT, CONTENT_BLOCKS, MESSAGES");
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

        // Read optional tool-related fields
        boolean hasToolCalls = in.readBoolean();
        if (hasToolCalls) {
            message.setToolCalls(readToolCallsList(in));
        }

        boolean hasToolCallId = in.readBoolean();
        if (hasToolCallId) {
            message.setToolCallId(in.readString());
        }

        return message;
    }

    /**
     * Reads a list of ToolCalls from stream input.
     */
    private List<ToolCall> readToolCallsList(StreamInput in) throws IOException {
        int size = in.readInt();
        List<ToolCall> toolCalls = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ToolCall toolCall = readToolCall(in);
            toolCalls.add(toolCall);
        }
        return toolCalls;
    }

    /**
     * Reads a single ToolCall from stream input.
     */
    private ToolCall readToolCall(StreamInput in) throws IOException {
        String id = in.readString();
        String type = in.readString();
        ToolCall.ToolFunction function = readToolFunction(in);

        ToolCall toolCall = new ToolCall();
        toolCall.setId(id);
        toolCall.setType(type);
        toolCall.setFunction(function);
        return toolCall;
    }

    /**
     * Reads a ToolFunction from stream input.
     */
    private ToolCall.ToolFunction readToolFunction(StreamInput in) throws IOException {
        String name = in.readString();
        String arguments = in.readString();

        ToolCall.ToolFunction function = new ToolCall.ToolFunction();
        function.setName(name);
        function.setArguments(arguments);
        return function;
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
                // Validate all items are Messages before casting
                for (int i = 0; i < items.size(); i++) {
                    Object item = items.get(i);
                    if (!(item instanceof Message)) {
                        throw new IllegalArgumentException(
                            "Mixed array types detected. Expected all items to be Messages, but item at index "
                                + i
                                + " is of type "
                                + item.getClass().getSimpleName()
                        );
                    }
                }
                List<Message> messages = new ArrayList<>();
                for (Object item : items) {
                    messages.add((Message) item);
                }
                return messages;
            } else if (firstItem instanceof ContentBlock) {
                // Validate all items are ContentBlocks before casting
                for (int i = 0; i < items.size(); i++) {
                    Object item = items.get(i);
                    if (!(item instanceof ContentBlock)) {
                        throw new IllegalArgumentException(
                            "Mixed array types detected. Expected all items to be ContentBlocks, but item at index "
                                + i
                                + " is of type "
                                + item.getClass().getSimpleName()
                        );
                    }
                }
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
                case "toolCalls":
                    // Parse tool calls array for assistant messages
                    itemMap.put("toolCalls", parseToolCallsArray(parser));
                    break;
                case "toolCallId":
                    // Parse tool call ID for tool result messages
                    itemMap.put("toolCallId", parser.text());
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

        int index = 0;
        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);

            Object item = parseArrayItem(parser);
            if (!(item instanceof ContentBlock)) {
                throw new IllegalArgumentException(
                    "Invalid content array. Expected ContentBlock at index " + index + " but found " + item.getClass().getSimpleName()
                );
            }
            contentBlocks.add((ContentBlock) item);
            index++;
        }

        return contentBlocks;
    }

    /**
     * Parses tool calls array for assistant messages.
     */
    private List<ToolCall> parseToolCallsArray(XContentParser parser) throws IOException {
        List<ToolCall> toolCalls = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);

        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);

            ToolCall toolCall = parseToolCallFromXContent(parser);
            toolCalls.add(toolCall);
        }

        return toolCalls;
    }

    /**
     * Parses a single tool call from XContent.
     */
    private ToolCall parseToolCallFromXContent(XContentParser parser) throws IOException {
        String id = null;
        String type = null;
        ToolCall.ToolFunction function = null;

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case "id":
                    id = parser.text();
                    break;
                case "type":
                    type = parser.text();
                    break;
                case "function":
                    function = parseToolFunctionFromXContent(parser);
                    break;
                default:
                    // Skip unknown fields
                    parser.skipChildren();
                    break;
            }
        }

        if (id == null || function == null) {
            throw new IllegalArgumentException("ToolCall must have 'id' and 'function' fields");
        }

        // Default to "function" if type not provided
        return new ToolCall(id, type != null ? type : "function", function);
    }

    /**
     * Parses a tool function from XContent.
     */
    private ToolCall.ToolFunction parseToolFunctionFromXContent(XContentParser parser) throws IOException {
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);

        String name = null;
        String arguments = null;

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case "name":
                    name = parser.text();
                    break;
                case "arguments":
                    arguments = parser.text();
                    break;
                default:
                    // Skip unknown fields
                    parser.skipChildren();
                    break;
            }
        }

        if (name == null || arguments == null) {
            throw new IllegalArgumentException("ToolFunction must have 'name' and 'arguments' fields");
        }

        return new ToolCall.ToolFunction(name, arguments);
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
                throw new IllegalArgumentException(
                    "Unexpected token. Expected: VALUE_STRING, VALUE_NUMBER, VALUE_BOOLEAN, VALUE_NULL, START_OBJECT, or START_ARRAY"
                );
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

        // Set optional tool-related fields
        if (itemMap.containsKey("toolCalls")) {
            message.setToolCalls((List<ToolCall>) itemMap.get("toolCalls"));
        }

        if (itemMap.containsKey("toolCallId")) {
            message.setToolCallId((String) itemMap.get("toolCallId"));
        }

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
        if (source == null) {
            throw new IllegalArgumentException("Image source cannot be null");
        }

        String format = (String) source.get("format");
        if (format == null || format.trim().isEmpty()) {
            throw new IllegalArgumentException("Image format is required");
        }

        String type = (String) source.get("type");
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Image source type is required");
        }

        String data = (String) source.get("data");
        if (data == null || data.trim().isEmpty()) {
            throw new IllegalArgumentException("Image data is required");
        }

        SourceType sourceType;
        try {
            sourceType = SourceType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid source type. Supported types: BYTES, URL", e);
        }

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
        if (source == null) {
            throw new IllegalArgumentException("Video source cannot be null");
        }

        String format = (String) source.get("format");
        if (format == null || format.trim().isEmpty()) {
            throw new IllegalArgumentException("Video format is required");
        }

        String type = (String) source.get("type");
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Video source type is required");
        }

        String data = (String) source.get("data");
        if (data == null || data.trim().isEmpty()) {
            throw new IllegalArgumentException("Video data is required");
        }

        SourceType sourceType;
        try {
            sourceType = SourceType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid source type. Supported types: BYTES, URL", e);
        }

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
        if (source == null) {
            throw new IllegalArgumentException("Document source cannot be null");
        }

        String format = (String) source.get("format");
        if (format == null || format.trim().isEmpty()) {
            throw new IllegalArgumentException("Document format is required");
        }

        String type = (String) source.get("type");
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Document source type is required");
        }

        String data = (String) source.get("data");
        if (data == null || data.trim().isEmpty()) {
            throw new IllegalArgumentException("Document data is required");
        }

        SourceType sourceType;
        try {
            sourceType = SourceType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid source type. Supported types: BYTES, URL", e);
        }

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

        // Write optional tool-related fields
        boolean hasToolCalls = message.getToolCalls() != null && !message.getToolCalls().isEmpty();
        out.writeBoolean(hasToolCalls);
        if (hasToolCalls) {
            writeToolCallsList(out, message.getToolCalls());
        }

        boolean hasToolCallId = message.getToolCallId() != null;
        out.writeBoolean(hasToolCallId);
        if (hasToolCallId) {
            out.writeString(message.getToolCallId());
        }
    }

    /**
     * Writes a list of ToolCalls to stream output.
     */
    private void writeToolCallsList(StreamOutput out, List<ToolCall> toolCalls) throws IOException {
        out.writeInt(toolCalls.size());
        for (ToolCall toolCall : toolCalls) {
            writeToolCall(out, toolCall);
        }
    }

    /**
     * Writes a single ToolCall to stream output.
     */
    private void writeToolCall(StreamOutput out, ToolCall toolCall) throws IOException {
        out.writeString(toolCall.getId());
        out.writeString(toolCall.getType());
        writeToolFunction(out, toolCall.getFunction());
    }

    /**
     * Writes a ToolFunction to stream output.
     */
    private void writeToolFunction(StreamOutput out, ToolCall.ToolFunction function) throws IOException {
        out.writeString(function.getName());
        out.writeString(function.getArguments());
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

        throw new IllegalArgumentException("Input type not supported. Expected String, List<ContentBlock>, or List<Message>");
    }
}
