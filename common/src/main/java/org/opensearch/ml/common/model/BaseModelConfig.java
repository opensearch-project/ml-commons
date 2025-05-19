/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.VERSION_3_1_0;

import java.io.IOException;
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
 * Base configuration class for ML models. This class handles 
 * the basic configuration parameters that every ML model can support.
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

    public static final String ADDITIONAL_CONFIG_FIELD = "additional_config";
    protected Map<String, Object> additionalConfig;

    @Builder(toBuilder = true)
    public BaseModelConfig(String modelType, String allConfig, Map<String, Object> additionalConfig) {
        super(modelType, allConfig);
        this.additionalConfig = additionalConfig;
        validateNoDuplicateKeys(allConfig, additionalConfig);
    }

    public static BaseModelConfig parse(XContentParser parser) throws IOException {
        String modelType = null;
        String allConfig = null;
        Map<String, Object> additionalConfig = null;

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
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new BaseModelConfig(modelType, allConfig, additionalConfig);
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
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (out.getVersion().onOrAfter(VERSION_3_1_0)) {
            out.writeMap(additionalConfig);
        }
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
        builder.endObject();
        return builder;
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
