/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;

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

    public static final String IS_ENABLE_FIELD = "is_enable";

    @Builder(toBuilder = true)
    public IndexInsightConfig(Boolean isEnable, String tenantId) {
        this.isEnable = isEnable;
        this.tenantId = tenantId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(isEnable);
        out.writeString(tenantId);
    }

    public IndexInsightConfig(StreamInput input) throws IOException {
        isEnable = input.readBoolean();
        tenantId = input.readString();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(IS_ENABLE_FIELD, isEnable);
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }

    public static IndexInsightConfig parse(XContentParser parser) throws IOException {
        Boolean isEnable = null;
        String tenantId = null;
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
                default:
                    parser.skipChildren();
                    break;
            }

        }
        return IndexInsightConfig.builder().isEnable(isEnable).tenantId(tenantId).build();
    }

    public static IndexInsightConfig fromStream(StreamInput in) throws IOException {
        return new IndexInsightConfig(in);
    }
}
