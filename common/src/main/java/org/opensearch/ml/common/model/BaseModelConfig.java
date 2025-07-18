/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.VERSION_3_1_0;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
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
 * Base configuration class for ML local models. This class handles
 * the basic configuration parameters that every local model can support.
 */
@Setter
@Getter
public class BaseModelConfig extends MLModelConfig {
    public static final String PARSE_FIELD_NAME = "base";
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        BaseModelConfig.class,
        new ParseField(PARSE_FIELD_NAME),
        it -> parse(it)
    );

    public static final String EMBEDDING_DIMENSION_FIELD = "embedding_dimension";
    public static final String FRAMEWORK_TYPE_FIELD = "framework_type";
    public static final String POOLING_MODE_FIELD = "pooling_mode";
    public static final String NORMALIZE_RESULT_FIELD = "normalize_result";
    public static final String MODEL_MAX_LENGTH_FIELD = "model_max_length";
    public static final String QUERY_PREFIX = "query_prefix";
    public static final String PASSAGE_PREFIX = "passage_prefix";
    public static final String ADDITIONAL_CONFIG_FIELD = "additional_config";

    protected Integer embeddingDimension;
    protected FrameworkType frameworkType;
    protected PoolingMode poolingMode;
    protected boolean normalizeResult;
    protected Integer modelMaxLength;
    protected String queryPrefix;
    protected String passagePrefix;
    protected Map<String, Object> additionalConfig;

    @Builder(builderMethodName = "baseModelConfigBuilder")
    public BaseModelConfig(
        String modelType,
        String allConfig,
        Map<String, Object> additionalConfig,
        Integer embeddingDimension,
        FrameworkType frameworkType,
        PoolingMode poolingMode,
        boolean normalizeResult,
        Integer modelMaxLength,
        String queryPrefix,
        String passagePrefix
    ) {
        super(modelType, allConfig);
        this.additionalConfig = additionalConfig;
        this.embeddingDimension = embeddingDimension;
        this.frameworkType = frameworkType;
        this.poolingMode = poolingMode;
        this.normalizeResult = normalizeResult;
        this.modelMaxLength = modelMaxLength;
        this.queryPrefix = queryPrefix;
        this.passagePrefix = passagePrefix;
        validateNoDuplicateKeys(allConfig, additionalConfig);
    }

    public static BaseModelConfig parse(XContentParser parser) throws IOException {
        String modelType = null;
        String allConfig = null;
        Map<String, Object> additionalConfig = null;
        Integer embeddingDimension = null;
        FrameworkType frameworkType = null;
        PoolingMode poolingMode = null;
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
                case ALL_CONFIG_FIELD:
                    allConfig = parser.text();
                    break;
                case ADDITIONAL_CONFIG_FIELD:
                    additionalConfig = parser.map();
                    break;
                case EMBEDDING_DIMENSION_FIELD:
                    embeddingDimension = parser.intValue();
                    break;
                case FRAMEWORK_TYPE_FIELD:
                    frameworkType = FrameworkType.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case POOLING_MODE_FIELD:
                    poolingMode = PoolingMode.from(parser.text().toUpperCase(Locale.ROOT));
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
        return new BaseModelConfig(
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
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    public BaseModelConfig(StreamInput in) throws IOException {
        super(in);
        if (in.getVersion().onOrAfter(VERSION_3_1_0)) {
            this.additionalConfig = in.readMap();
        }
        embeddingDimension = in.readOptionalInt();
        if (in.readBoolean()) {
            frameworkType = in.readEnum(FrameworkType.class);
        } else {
            frameworkType = null;
        }
        if (in.readBoolean()) {
            poolingMode = in.readEnum(PoolingMode.class);
        } else {
            poolingMode = null;
        }
        normalizeResult = in.readBoolean();
        modelMaxLength = in.readOptionalInt();
        queryPrefix = in.readOptionalString();
        passagePrefix = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (out.getVersion().onOrAfter(VERSION_3_1_0)) {
            out.writeMap(additionalConfig);
        }
        out.writeOptionalInt(embeddingDimension);
        if (frameworkType != null) {
            out.writeBoolean(true);
            out.writeEnum(frameworkType);
        } else {
            out.writeBoolean(false);
        }
        if (poolingMode != null) {
            out.writeBoolean(true);
            out.writeEnum(poolingMode);
        } else {
            out.writeBoolean(false);
        }
        out.writeBoolean(normalizeResult);
        out.writeOptionalInt(modelMaxLength);
        out.writeOptionalString(queryPrefix);
        out.writeOptionalString(passagePrefix);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (modelType != null) {
            builder.field(MODEL_TYPE_FIELD, modelType);
        }
        if (allConfig != null) {
            builder.field(ALL_CONFIG_FIELD, allConfig);
        }
        if (additionalConfig != null) {
            builder.field(ADDITIONAL_CONFIG_FIELD, additionalConfig);
        }
        if (embeddingDimension != null) {
            builder.field(EMBEDDING_DIMENSION_FIELD, embeddingDimension);
        }
        if (frameworkType != null) {
            builder.field(FRAMEWORK_TYPE_FIELD, frameworkType);
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

    public enum PoolingMode {
        MEAN("mean"),
        MEAN_SQRT_LEN("mean_sqrt_len"),
        MAX("max"),
        WEIGHTED_MEAN("weightedmean"),
        CLS("cls"),
        LAST_TOKEN("lasttoken");

        private String name;

        public String getName() {
            return name;
        }

        PoolingMode(String name) {
            this.name = name;
        }

        public static PoolingMode from(String value) {
            try {
                return PoolingMode.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                throw new IllegalArgumentException("Wrong pooling method");
            }
        }
    }

    public enum FrameworkType {
        HUGGINGFACE_TRANSFORMERS,
        SENTENCE_TRANSFORMERS,
        HUGGINGFACE_TRANSFORMERS_NEURON;

        public static FrameworkType from(String value) {
            try {
                return FrameworkType.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                throw new IllegalArgumentException("Wrong framework type");
            }
        }
    }

    protected void validateNoDuplicateKeys(String allConfig, Map<String, Object> additionalConfig) {
        if (allConfig == null || additionalConfig == null || additionalConfig.isEmpty()) {
            return;
        }

        Map<String, Object> allConfigMap = XContentHelper.convertToMap(XContentType.JSON.xContent(), allConfig, false);
        Set<String> duplicateKeys = allConfigMap.keySet().stream().filter(additionalConfig::containsKey).collect(Collectors.toSet());
        if (!duplicateKeys.isEmpty()) {
            throw new IllegalArgumentException(
                "Duplicate keys found in both all_config and additional_config: " + String.join(", ", duplicateKeys)
            );
        }
    }

    public Map<String, Object> getAdditionalConfig() {
        return this.additionalConfig;
    }
}
