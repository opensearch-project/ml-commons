/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.session;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.conversation.ActionConstants.ADDITIONAL_INFO_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.AGENTS_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.METADATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SUMMARY_FIELD;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Input data for creating a session
 */
@Getter
@Setter
@Builder
public class MLCreateSessionInput implements ToXContentObject, Writeable {

    private String sessionId;
    private String ownerId;
    private String summary;
    private Map<String, Object> metadata;
    private Map<String, Object> agents;
    private Map<String, Object> additionalInfo;
    private Map<String, String> namespace;
    private String tenantId;
    private String memoryContainerId;

    public MLCreateSessionInput(
        String sessionId,
        String ownerId,
        String summary,
        Map<String, Object> metadata,
        Map<String, Object> agents,
        Map<String, Object> additionalInfo,
        Map<String, String> namespace,
        String tenantId,
        String memoryContainerId
    ) {
        this.sessionId = sessionId;
        this.ownerId = ownerId;
        this.summary = summary;
        this.metadata = metadata;
        this.agents = agents;
        this.additionalInfo = additionalInfo;
        this.namespace = namespace;
        this.tenantId = tenantId;
        this.memoryContainerId = memoryContainerId;
    }

    public MLCreateSessionInput(StreamInput in) throws IOException {
        this.sessionId = in.readOptionalString();
        this.ownerId = in.readOptionalString();
        this.summary = in.readOptionalString();
        if (in.readBoolean()) {
            this.metadata = in.readMap();
        }
        if (in.readBoolean()) {
            this.agents = in.readMap();
        }
        if (in.readBoolean()) {
            this.additionalInfo = in.readMap();
        }
        if (in.readBoolean()) {
            this.namespace = in.readMap(StreamInput::readString, StreamInput::readString);
        }
        this.tenantId = in.readOptionalString();
        this.memoryContainerId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(sessionId);
        out.writeOptionalString(ownerId);
        out.writeOptionalString(summary);

        if (metadata != null) {
            out.writeBoolean(true);
            out.writeMap(metadata);
        } else {
            out.writeBoolean(false);
        }
        if (agents != null) {
            out.writeBoolean(true);
            out.writeMap(agents);
        } else {
            out.writeBoolean(false);
        }
        if (additionalInfo != null) {
            out.writeBoolean(true);
            out.writeMap(additionalInfo);
        } else {
            out.writeBoolean(false);
        }
        if (namespace != null) {
            out.writeBoolean(true);
            out.writeMap(namespace, StreamOutput::writeString, StreamOutput::writeOptionalString);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(tenantId);
        out.writeOptionalString(memoryContainerId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (sessionId != null) {
            builder.field(SESSION_ID_FIELD, sessionId);
        }
        if (ownerId != null) {
            builder.field(OWNER_ID_FIELD, ownerId);
        }
        if (summary != null) {
            builder.field(SUMMARY_FIELD, summary);
        }
        if (metadata != null) {
            builder.field(METADATA_FIELD, metadata);
        }
        if (metadata != null) {
            builder.field(AGENTS_FIELD, agents);
        }
        if (additionalInfo != null) {
            builder.field(ADDITIONAL_INFO_FIELD, additionalInfo);
        }
        if (namespace != null) {
            builder.field(NAMESPACE_FIELD, namespace);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }

    public static MLCreateSessionInput parse(XContentParser parser) throws IOException {
        String sessionId = null;
        String ownerId = null;
        String summary = null;
        Map<String, Object> metadata = null;
        Map<String, Object> agents = null;
        Map<String, Object> additionalInfo = null;
        Map<String, String> namespace = null;
        String tenantId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case SESSION_ID_FIELD:
                    sessionId = parser.text();
                    break;
                case OWNER_ID_FIELD:
                    ownerId = parser.text();
                    break;
                case SUMMARY_FIELD:
                    summary = parser.text();
                    break;
                case METADATA_FIELD:
                    metadata = parser.map();
                    break;
                case AGENTS_FIELD:
                    agents = parser.map();
                    break;
                case ADDITIONAL_INFO_FIELD:
                    additionalInfo = parser.map();
                    break;
                case NAMESPACE_FIELD:
                    namespace = parser.mapStrings();
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLCreateSessionInput
            .builder()
            .sessionId(sessionId)
            .ownerId(ownerId)
            .summary(summary)
            .metadata(metadata)
            .agents(agents)
            .additionalInfo(additionalInfo)
            .namespace(namespace)
            .tenantId(tenantId)
            .build();
    }
}
