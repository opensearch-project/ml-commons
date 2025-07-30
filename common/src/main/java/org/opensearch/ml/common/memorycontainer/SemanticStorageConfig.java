/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DIMENSION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MODEL_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MODEL_TYPE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_ENABLED_FIELD;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for semantic storage in memory containers
 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
public class SemanticStorageConfig implements ToXContentObject, Writeable {

    private boolean semanticStorageEnabled;
    private FunctionName modelType;
    private String modelId;
    private Integer dimension;

    public SemanticStorageConfig(boolean semanticStorageEnabled, FunctionName modelType, String modelId, Integer dimension) {
        this.semanticStorageEnabled = semanticStorageEnabled;
        this.modelType = modelType;
        this.modelId = modelId;
        this.dimension = dimension;
    }

    public SemanticStorageConfig(StreamInput input) throws IOException {
        this.semanticStorageEnabled = input.readBoolean();
        String modelTypeStr = input.readOptionalString();
        this.modelType = modelTypeStr != null ? FunctionName.from(modelTypeStr) : null;
        this.modelId = input.readOptionalString();
        this.dimension = input.readOptionalInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(semanticStorageEnabled);
        out.writeOptionalString(modelType != null ? modelType.name() : null);
        out.writeOptionalString(modelId);
        out.writeOptionalInt(dimension);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(SEMANTIC_STORAGE_ENABLED_FIELD, semanticStorageEnabled);
        if (modelType != null) {
            builder.field(MODEL_TYPE_FIELD, modelType.name());
        }
        if (modelId != null) {
            builder.field(MODEL_ID_FIELD, modelId);
        }
        if (dimension != null) {
            builder.field(DIMENSION_FIELD, dimension);
        }
        builder.endObject();
        return builder;
    }

    public static SemanticStorageConfig parse(XContentParser parser) throws IOException {
        boolean semanticStorageEnabled = false;
        FunctionName modelType = null;
        String modelId = null;
        Integer dimension = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case SEMANTIC_STORAGE_ENABLED_FIELD:
                    semanticStorageEnabled = parser.booleanValue();
                    break;
                case MODEL_TYPE_FIELD:
                    modelType = FunctionName.from(parser.text());
                    break;
                case MODEL_ID_FIELD:
                    modelId = parser.text();
                    break;
                case DIMENSION_FIELD:
                    dimension = parser.intValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return SemanticStorageConfig
            .builder()
            .semanticStorageEnabled(semanticStorageEnabled)
            .modelType(modelType)
            .modelId(modelId)
            .dimension(dimension)
            .build();
    }
}
