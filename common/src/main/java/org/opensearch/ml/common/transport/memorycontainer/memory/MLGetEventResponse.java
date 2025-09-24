/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.memorycontainer.ShortTermMemoryType;
import org.opensearch.ml.common.utils.StringUtils;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Response containing event details from a memory container
 */
@Log4j2
@Getter
@Builder
public class MLGetEventResponse extends ActionResponse implements ToXContentObject {

    private String eventId;
    private MLCreateEventInput eventData;
    private Long createdTime;
    private Long lastUpdatedTime;

    public MLGetEventResponse(String eventId, MLCreateEventInput eventData, Long createdTime, Long lastUpdatedTime) {
        this.eventId = eventId;
        this.eventData = eventData;
        this.createdTime = createdTime;
        this.lastUpdatedTime = lastUpdatedTime;
    }

    public MLGetEventResponse(StreamInput in) throws IOException {
        super(in);
        this.eventId = in.readOptionalString();
        if (in.readBoolean()) {
            this.eventData = new MLCreateEventInput(in);
        }
        this.createdTime = in.readOptionalLong();
        this.lastUpdatedTime = in.readOptionalLong();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(eventId);
        if (eventData != null) {
            out.writeBoolean(true);
            eventData.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalLong(createdTime);
        out.writeOptionalLong(lastUpdatedTime);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();

        if (eventId != null) {
            builder.field(EVENT_ID_FIELD, eventId);
        }

        // Write fields from eventData directly (without nesting)
        if (eventData != null) {
            if (eventData.getMemoryContainerId() != null) {
                builder.field(MEMORY_CONTAINER_ID_FIELD, eventData.getMemoryContainerId());
            }
            if (eventData.getMemoryType() != null) {
                builder.field(MEMORY_TYPE_FIELD, eventData.getMemoryType().getValue());
            }
            if (eventData.getMessages() != null && !eventData.getMessages().isEmpty()) {
                builder.startArray(MESSAGES_FIELD);
                for (MessageInput message : eventData.getMessages()) {
                    message.toXContent(builder, params);
                }
                builder.endArray();
            }
            if (eventData.getData() != null) {
                builder.field(DATA_FIELD, eventData.getData());
            }
            if (eventData.getNamespace() != null && !eventData.getNamespace().isEmpty()) {
                builder.field(NAMESPACE_FIELD, eventData.getNamespace());
            }
            builder.field(INFER_FIELD, eventData.isInfer());
            if (eventData.getMetadata() != null && !eventData.getMetadata().isEmpty()) {
                builder.field(METADATA_FIELD, eventData.getMetadata());
            }
        }

        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime);
        }
        if (lastUpdatedTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdatedTime);
        }

        builder.endObject();
        return builder;
    }

    /**
     * Parse MLGetEventResponse from XContentParser
     * @param parser The XContentParser
     * @param eventId The event ID
     * @return MLGetEventResponse instance
     */
    public static MLGetEventResponse parse(XContentParser parser, String eventId) throws IOException {
        String memoryContainerId = null;
        ShortTermMemoryType memoryType = null;
        List<MessageInput> messages = null;
        Map<String, Object> data = null;
        Map<String, String> namespace = null;
        Map<String, String> metadata = null;
        boolean infer = false;
        Long createdTime = null;
        Long lastUpdatedTime = null;

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MEMORY_CONTAINER_ID_FIELD:
                    memoryContainerId = parser.text();
                    break;
                case MEMORY_TYPE_FIELD:
                    memoryType = ShortTermMemoryType.fromString(parser.text());
                    break;
                case MESSAGES_FIELD:
                    messages = new ArrayList<>();
                    if (parser.currentToken() == XContentParser.Token.START_ARRAY) {
                        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                            messages.add(MessageInput.parse(parser));
                        }
                    }
                    break;
                case DATA_FIELD:
                    data = parser.map();
                    break;
                case NAMESPACE_FIELD:
                    namespace = StringUtils.getParameterMap(parser.map());
                    break;
                case METADATA_FIELD:
                    metadata = StringUtils.getParameterMap(parser.map());
                    break;
                case INFER_FIELD:
                    infer = parser.booleanValue();
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = parser.longValue();
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdatedTime = parser.longValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        // Build the MLCreateEventInput from parsed fields
        MLCreateEventInput eventData = MLCreateEventInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType)
            .messages(messages)
            .data(data)
            .namespace(namespace)
            .metadata(metadata)
            .infer(infer)
            .build();

        return MLGetEventResponse
            .builder()
            .eventId(eventId)
            .eventData(eventData)
            .createdTime(createdTime)
            .lastUpdatedTime(lastUpdatedTime)
            .build();
    }
}
