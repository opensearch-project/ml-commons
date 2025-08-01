/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.AGENT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.ENCODING_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.FACT_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.RAW_MESSAGES_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SUMMARY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TAGS_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TYPE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.USER_ID_FIELD;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * ML Memory data model that stores memory content
 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
public class MLMemory implements ToXContentObject, Writeable {

    private String rawMessage;
    private String fact;
    // type: raw message, fact, or summary
    private String type;
    private String summary;
    private List<String> tags;
    private String encoding;
    private String userId;
    private String agentId;
    private String sessionId;
    private String tenantId;
    private Instant createdTime;
    private Instant lastUpdatedTime;

    public MLMemory(
        String rawMessage,
        String fact,
        String type,
        String summary,
        List<String> tags,
        String encoding,
        String userId,
        String agentId,
        String sessionId,
        String tenantId,
        Instant createdTime,
        Instant lastUpdatedTime
    ) {
        this.rawMessage = rawMessage;
        this.fact = fact;
        this.type = type;
        this.summary = summary;
        this.tags = tags;
        this.encoding = encoding;
        this.userId = userId;
        this.agentId = agentId;
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.createdTime = createdTime;
        this.lastUpdatedTime = lastUpdatedTime;
    }

    public MLMemory(StreamInput in) throws IOException {
        this.rawMessage = in.readOptionalString();
        this.fact = in.readOptionalString();
        this.type = in.readString();
        this.summary = in.readOptionalString();
        this.tags = in.readOptionalStringList();
        this.encoding = in.readOptionalString();
        this.userId = in.readOptionalString();
        this.agentId = in.readOptionalString();
        this.sessionId = in.readOptionalString();
        this.tenantId = in.readOptionalString();
        this.createdTime = in.readOptionalInstant();
        this.lastUpdatedTime = in.readOptionalInstant();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(rawMessage);
        out.writeOptionalString(fact);
        out.writeString(type);
        out.writeOptionalString(summary);
        out.writeOptionalStringCollection(tags);
        out.writeOptionalString(encoding);
        out.writeOptionalString(userId);
        out.writeOptionalString(agentId);
        out.writeOptionalString(sessionId);
        out.writeOptionalString(tenantId);
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdatedTime);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (rawMessage != null) {
            builder.field(RAW_MESSAGES_FIELD, rawMessage);
        }
        if (fact != null) {
            builder.field(FACT_FIELD, fact);
        }
        if (type != null) {
            builder.field(TYPE_FIELD, type);
        }
        if (summary != null) {
            builder.field(SUMMARY_FIELD, summary);
        }
        if (tags != null) {
            builder.field(TAGS_FIELD, tags);
        }
        if (encoding != null) {
            builder.field(ENCODING_FIELD, encoding);
        }
        if (userId != null) {
            builder.field(USER_ID_FIELD, userId);
        }
        if (agentId != null) {
            builder.field(AGENT_ID_FIELD, agentId);
        }
        if (sessionId != null) {
            builder.field(SESSION_ID_FIELD, sessionId);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastUpdatedTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdatedTime.toEpochMilli());
        }
        builder.endObject();
        return builder;
    }

    public static MLMemory parse(XContentParser parser) throws IOException {
        String rawMessage = null;
        String fact = null;
        String type = null;
        String summary = null;
        List<String> tags = null;
        String encoding = null;
        String userId = null;
        String agentId = null;
        String sessionId = null;
        String tenantId = null;
        Instant createdTime = null;
        Instant lastUpdatedTime = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case RAW_MESSAGES_FIELD:
                    rawMessage = parser.text();
                    break;
                case FACT_FIELD:
                    fact = parser.text();
                    break;
                case TYPE_FIELD:
                    type = parser.text();
                    break;
                case SUMMARY_FIELD:
                    summary = parser.text();
                    break;
                case TAGS_FIELD:
                    List<Object> tagObjects = parser.list();
                    if (tagObjects != null) {
                        tags = tagObjects.stream().map(Object::toString).toList();
                    }
                    break;
                case ENCODING_FIELD:
                    encoding = parser.text();
                    break;
                case USER_ID_FIELD:
                    userId = parser.text();
                    break;
                case AGENT_ID_FIELD:
                    agentId = parser.text();
                    break;
                case SESSION_ID_FIELD:
                    sessionId = parser.text();
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdatedTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLMemory
            .builder()
            .rawMessage(rawMessage)
            .fact(fact)
            .type(type)
            .summary(summary)
            .tags(tags)
            .encoding(encoding)
            .userId(userId)
            .agentId(agentId)
            .sessionId(sessionId)
            .tenantId(tenantId)
            .createdTime(createdTime)
            .lastUpdatedTime(lastUpdatedTime)
            .build();
    }
}
