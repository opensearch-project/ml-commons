/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import org.opensearch.core.ParseField;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration class for remote models. This class extends BaseModelConfig
 * and provides specific configuration parameters for remote models.
 */
@Setter
@Getter
public class RemoteModelConfig extends BaseModelConfig {
    public static final String PARSE_FIELD_NAME = "remote";
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        RemoteModelConfig.class,
        new ParseField(PARSE_FIELD_NAME),
        it -> parse(it)
    );

    @Builder(toBuilder = true)
    public RemoteModelConfig(
        String modelType,
        Integer embeddingDimension,
        BaseModelConfig.FrameworkType frameworkType,
        String allConfig,
        BaseModelConfig.PoolingMode poolingMode,
        boolean normalizeResult,
        Integer modelMaxLength,
        Map<String, Object> additionalConfig
    ) {
        super(
            modelType,
            allConfig,
            additionalConfig,
            embeddingDimension,
            frameworkType,
            poolingMode,
            normalizeResult,
            modelMaxLength,
            null,
            null
        );
        validateNoDuplicateKeys(allConfig, additionalConfig);
        validateTextEmbeddingConfig();
    }

    public static RemoteModelConfig parse(XContentParser parser) throws IOException {
        String modelType = null;
        Integer embeddingDimension = null;
        BaseModelConfig.FrameworkType frameworkType = null;
        String allConfig = null;
        BaseModelConfig.PoolingMode poolingMode = null;
        boolean normalizeResult = false;
        Integer modelMaxLength = null;
        Map<String, Object> additionalConfig = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_TYPE_FIELD:
                    modelType = parser.text();
                    break;
                case EMBEDDING_DIMENSION_FIELD:
                    embeddingDimension = parser.intValue();
                    break;
                case FRAMEWORK_TYPE_FIELD:
                    frameworkType = BaseModelConfig.FrameworkType.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case ALL_CONFIG_FIELD:
                    allConfig = parser.text();
                    break;
                case POOLING_MODE_FIELD:
                    poolingMode = BaseModelConfig.PoolingMode.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case NORMALIZE_RESULT_FIELD:
                    normalizeResult = parser.booleanValue();
                    break;
                case MODEL_MAX_LENGTH_FIELD:
                    modelMaxLength = parser.intValue();
                    break;
                case ADDITIONAL_CONFIG_FIELD:
                    additionalConfig = parser.map();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new RemoteModelConfig(
            modelType,
            embeddingDimension,
            frameworkType,
            allConfig,
            poolingMode,
            normalizeResult,
            modelMaxLength,
            additionalConfig
        );
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    public RemoteModelConfig(StreamInput in) throws IOException {
        super(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (modelType != null) {
            builder.field(MODEL_TYPE_FIELD, modelType);
        }
        if (embeddingDimension != null) {
            builder.field(EMBEDDING_DIMENSION_FIELD, embeddingDimension);
        }
        if (frameworkType != null) {
            builder.field(FRAMEWORK_TYPE_FIELD, frameworkType);
        }
        if (allConfig != null) {
            builder.field(ALL_CONFIG_FIELD, allConfig);
        }
        if (poolingMode != null) {
            builder.field(POOLING_MODE_FIELD, poolingMode);
        }
        if (normalizeResult) {
            builder.field(NORMALIZE_RESULT_FIELD, normalizeResult);
        }
        if (modelMaxLength != null) {
            builder.field(MODEL_MAX_LENGTH_FIELD, modelMaxLength);
        }
        if (additionalConfig != null) {
            builder.field(ADDITIONAL_CONFIG_FIELD, additionalConfig);
        }
        builder.endObject();
        return builder;
    }

    private void validateTextEmbeddingConfig() {
        if (modelType != null && modelType.equalsIgnoreCase("text_embedding")) {
            if (embeddingDimension == null) {
                throw new IllegalArgumentException("Embedding dimension must be provided for remote text embedding model");
            }
            if (frameworkType == null) {
                throw new IllegalArgumentException("Framework type must be provided for remote text embedding model");
            }
            if (additionalConfig == null || !additionalConfig.containsKey("space_type")) {
                throw new IllegalArgumentException("Space type must be provided in additional_config for remote text embedding model");
            }
        }
    }
}
