/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_3_11_0;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Getter;

@Getter
public class IndexInsightConfig implements ToXContentObject, Writeable {
    private final Boolean isEnable;
    private final String tenantId;
    // The agent Index Insight runs its LLM step with. It is stored in the ML config document
    // os_index_insight_agent, not in the index insight config document.
    private final String agentId;

    public static final String IS_ENABLE_FIELD = "is_enable";
    public static final String AGENT_ID_FIELD = "agent_id";

    public IndexInsightConfig(Boolean isEnable, String tenantId) {
        this(isEnable, tenantId, null);
    }

    @Builder(toBuilder = true)
    public IndexInsightConfig(Boolean isEnable, String tenantId, String agentId) {
        this.isEnable = isEnable;
        this.tenantId = tenantId;
        this.agentId = agentId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(isEnable);
        out.writeOptionalString(tenantId);
        if (out.getVersion().onOrAfter(VERSION_3_11_0)) {
            out.writeOptionalString(agentId);
        }
    }

    public IndexInsightConfig(StreamInput input) throws IOException {
        isEnable = input.readBoolean();
        tenantId = input.readOptionalString();
        agentId = input.getVersion().onOrAfter(VERSION_3_11_0) ? input.readOptionalString() : null;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(IS_ENABLE_FIELD, isEnable);
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        if (agentId != null) {
            builder.field(AGENT_ID_FIELD, agentId);
        }
        builder.endObject();
        return builder;
    }

    public static IndexInsightConfig parse(XContentParser parser) throws IOException {
        return parse(parser, false);
    }

    /**
     * Parses an index insight config.
     * @param parser the parser positioned at the start of the object
     * @param rejectUnknownFields whether to fail on unknown fields; true for a request body, false for a stored document
     */
    public static IndexInsightConfig parse(XContentParser parser, boolean rejectUnknownFields) throws IOException {
        Boolean isEnable = null;
        String tenantId = null;
        String agentId = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case IS_ENABLE_FIELD:
                    isEnable = parser.booleanValue();
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                case AGENT_ID_FIELD:
                    agentId = parser.textOrNull();
                    break;
                default:
                    if (rejectUnknownFields) {
                        throw new IllegalArgumentException("Unknown field [" + fieldName + "] in index insight config");
                    }
                    parser.skipChildren();
                    break;
            }

        }
        return IndexInsightConfig.builder().isEnable(isEnable).tenantId(tenantId).agentId(agentId).build();
    }

    public static IndexInsightConfig fromStream(StreamInput in) throws IOException {
        return new IndexInsightConfig(in);
    }
}
