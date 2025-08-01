/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;

import lombok.Builder;
import lombok.Data;

@Data
public class MLCreateMemoryContainerInput implements ToXContentObject, Writeable {

    public static final String NAME_FIELD = "name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String MEMORY_STORAGE_CONFIG_FIELD = "memory_storage_config";

    private String name;
    private String description;
    private MemoryStorageConfig memoryStorageConfig;
    private String tenantId;

    @Builder(toBuilder = true)
    public MLCreateMemoryContainerInput(String name, String description, MemoryStorageConfig memoryStorageConfig, String tenantId) {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        this.name = name;
        this.description = description;
        this.memoryStorageConfig = memoryStorageConfig;
        this.tenantId = tenantId;
    }

    public MLCreateMemoryContainerInput(StreamInput in) throws IOException {
        this.name = in.readString();
        this.description = in.readOptionalString();
        if (in.readBoolean()) {
            this.memoryStorageConfig = new MemoryStorageConfig(in);
        } else {
            this.memoryStorageConfig = null;
        }
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeOptionalString(description);
        if (memoryStorageConfig != null) {
            out.writeBoolean(true);
            memoryStorageConfig.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(tenantId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME_FIELD, name);
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (memoryStorageConfig != null) {
            builder.field(MEMORY_STORAGE_CONFIG_FIELD, memoryStorageConfig);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }

    public static MLCreateMemoryContainerInput parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        MemoryStorageConfig memoryStorageConfig = null;
        String tenantId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case MEMORY_STORAGE_CONFIG_FIELD:
                    memoryStorageConfig = MemoryStorageConfig.parse(parser);
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
            .name(name)
            .description(description)
            .memoryStorageConfig(memoryStorageConfig)
            .tenantId(tenantId)
            .build();
    }
}
