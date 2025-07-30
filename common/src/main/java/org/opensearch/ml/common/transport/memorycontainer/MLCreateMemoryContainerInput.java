/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.memorycontainer.SemanticStorageConfig;

import lombok.Builder;
import lombok.Data;

@Data
public class MLCreateMemoryContainerInput implements ToXContentObject, Writeable {

    public static final String CONTAINER_NAME_FIELD = "container_name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String INDEX_NAME_FIELD = "index_name";
    public static final String SEMANTIC_STORAGE_FIELD = "semantic_storage";
    public static final String TENANT_ID_FIELD = "tenant_id";

    private String containerName;
    private String description;
    private String indexName;
    private SemanticStorageConfig semanticStorage;
    private String tenantId;

    @Builder(toBuilder = true)
    public MLCreateMemoryContainerInput(
        String containerName,
        String description,
        String indexName,
        SemanticStorageConfig semanticStorage,
        String tenantId
    ) {
        if (containerName == null) {
            throw new IllegalArgumentException("container name is null");
        }
        this.containerName = containerName;
        this.description = description;
        this.indexName = indexName;
        this.semanticStorage = semanticStorage;
        this.tenantId = tenantId;
    }

    public MLCreateMemoryContainerInput(StreamInput in) throws IOException {
        this.containerName = in.readString();
        this.description = in.readOptionalString();
        this.indexName = in.readOptionalString();
        if (in.readBoolean()) {
            this.semanticStorage = new SemanticStorageConfig(in);
        } else {
            this.semanticStorage = null;
        }
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(containerName);
        out.writeOptionalString(description);
        out.writeOptionalString(indexName);
        if (semanticStorage != null) {
            out.writeBoolean(true);
            semanticStorage.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(tenantId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(CONTAINER_NAME_FIELD, containerName);
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (indexName != null) {
            builder.field(INDEX_NAME_FIELD, indexName);
        }
        if (semanticStorage != null) {
            builder.field(SEMANTIC_STORAGE_FIELD, semanticStorage);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }

    public static MLCreateMemoryContainerInput parse(XContentParser parser) throws IOException {
        String containerName = null;
        String description = null;
        String indexName = null;
        SemanticStorageConfig semanticStorage = null;
        String tenantId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case CONTAINER_NAME_FIELD:
                    containerName = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case INDEX_NAME_FIELD:
                    indexName = parser.text();
                    break;
                case SEMANTIC_STORAGE_FIELD:
                    semanticStorage = SemanticStorageConfig.parse(parser);
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLCreateMemoryContainerInput
            .builder()
            .containerName(containerName)
            .description(description)
            .indexName(indexName)
            .semanticStorage(semanticStorage)
            .tenantId(tenantId)
            .build();
    }
}
