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
import org.opensearch.ml.common.FunctionName;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TextEmbeddingModelConfig extends BaseModelConfig {
    public static final String PARSE_FIELD_NAME = FunctionName.TEXT_EMBEDDING.name();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        TextEmbeddingModelConfig.class,
        new ParseField(PARSE_FIELD_NAME),
        it -> parse(it)
    );

    public TextEmbeddingModelConfig(
        String modelType,
        Integer embeddingDimension,
        FrameworkType frameworkType,
        String allConfig,
        Map<String, Object> additionalConfig,
        PoolingMode poolingMode,
        boolean normalizeResult,
        Integer modelMaxLength
    ) {
        this(
            modelType,
            embeddingDimension,
            frameworkType,
            allConfig,
            additionalConfig,
            poolingMode,
            normalizeResult,
            modelMaxLength,
            null,
            null
        );
    }

    @Builder(toBuilder = true)
    public TextEmbeddingModelConfig(
        String modelType,
        Integer embeddingDimension,
        BaseModelConfig.FrameworkType frameworkType,
        String allConfig,
        Map<String, Object> additionalConfig,
        BaseModelConfig.PoolingMode poolingMode,
        boolean normalizeResult,
        Integer modelMaxLength,
        String queryPrefix,
        String passagePrefix
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
            queryPrefix,
            passagePrefix
        );
        if (embeddingDimension == null) {
            throw new IllegalArgumentException("embedding dimension is null");
        }
        if (frameworkType == null) {
            throw new IllegalArgumentException("framework type is null");
        }
        validateNoDuplicateKeys(allConfig, additionalConfig);
    }

    public static TextEmbeddingModelConfig parse(XContentParser parser) throws IOException {
        String modelType = null;
        Integer embeddingDimension = null;
        BaseModelConfig.FrameworkType frameworkType = null;
        String allConfig = null;
        Map<String, Object> additionalConfig = null;
        BaseModelConfig.PoolingMode poolingMode = null;
        boolean normalizeResult = false;
        Integer modelMaxLength = null;
        String queryPrefix = null;
        String passagePrefix = null;

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
                case ADDITIONAL_CONFIG_FIELD:
                    additionalConfig = parser.map();
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
                case QUERY_PREFIX:
                    queryPrefix = parser.text();
                    break;
                case PASSAGE_PREFIX:
                    passagePrefix = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new TextEmbeddingModelConfig(
            modelType,
            embeddingDimension,
            frameworkType,
            allConfig,
            additionalConfig,
            poolingMode,
            normalizeResult,
            modelMaxLength,
            queryPrefix,
            passagePrefix
        );
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    public TextEmbeddingModelConfig(StreamInput in) throws IOException {
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
        if (additionalConfig != null) {
            builder.field(ADDITIONAL_CONFIG_FIELD, additionalConfig);
        }
        if (modelMaxLength != null) {
            builder.field(MODEL_MAX_LENGTH_FIELD, modelMaxLength);
        }
        if (poolingMode != null) {
            builder.field(POOLING_MODE_FIELD, poolingMode);
        }
        if (normalizeResult) {
            builder.field(NORMALIZE_RESULT_FIELD, normalizeResult);
        }
        if (queryPrefix != null) {
            builder.field(QUERY_PREFIX, queryPrefix);
        }
        if (passagePrefix != null) {
            builder.field(PASSAGE_PREFIX, passagePrefix);
        }
        builder.endObject();
        return builder;
    }
}
