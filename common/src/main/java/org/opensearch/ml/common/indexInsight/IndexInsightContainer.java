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
public class IndexInsightContainer implements ToXContentObject, Writeable {
    private String containerName;
    private String tenantId;

    public static final String CONTAINER_NAME_FIELD = "container_name";

    @Builder(toBuilder = true)
    public IndexInsightContainer(String containerName, String tenantId) {
        this.containerName = containerName;
        this.tenantId = tenantId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(containerName);
        out.writeOptionalString(tenantId);
    }

    public IndexInsightContainer(StreamInput input) throws IOException {
        containerName = input.readString();
        tenantId = input.readOptionalString();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(CONTAINER_NAME_FIELD, containerName);
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }

    public static IndexInsightContainer parse(XContentParser parser) throws IOException {
        String indexName = null;
        String tenantId = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case CONTAINER_NAME_FIELD:
                    indexName = parser.text();
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }

        }
        return IndexInsightContainer.builder().containerName(indexName).tenantId(tenantId).build();
    }
}
