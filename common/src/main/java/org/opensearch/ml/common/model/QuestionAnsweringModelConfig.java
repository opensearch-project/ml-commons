/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.Locale;

import org.opensearch.core.ParseField;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class QuestionAnsweringModelConfig extends MLModelConfig {
    public static final String PARSE_FIELD_NAME = FunctionName.QUESTION_ANSWERING.name();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        QuestionAnsweringModelConfig.class,
        new ParseField(PARSE_FIELD_NAME),
        it -> parse(it)
    );
    public static final String FRAMEWORK_TYPE_FIELD = "framework_type";
    public static final String NORMALIZE_RESULT_FIELD = "normalize_result";
    public static final String MODEL_MAX_LENGTH_FIELD = "model_max_length";

    private final FrameworkType frameworkType;
    private final boolean normalizeResult;
    private final Integer modelMaxLength;

    @Builder(toBuilder = true)
    public QuestionAnsweringModelConfig(
        String modelType,
        FrameworkType frameworkType,
        String allConfig,
        boolean normalizeResult,
        Integer modelMaxLength
    ) {
        super(modelType, allConfig);
        if (frameworkType == null) {
            throw new IllegalArgumentException("framework type is null");
        }
        this.frameworkType = frameworkType;
        this.normalizeResult = normalizeResult;
        this.modelMaxLength = modelMaxLength;
    }

    public static QuestionAnsweringModelConfig parse(XContentParser parser) throws IOException {
        String modelType = null;
        FrameworkType frameworkType = null;
        String allConfig = null;
        boolean normalizeResult = false;
        Integer modelMaxLength = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_TYPE_FIELD:
                    modelType = parser.text();
                    break;
                case FRAMEWORK_TYPE_FIELD:
                    frameworkType = FrameworkType.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case ALL_CONFIG_FIELD:
                    allConfig = parser.text();
                    break;
                case NORMALIZE_RESULT_FIELD:
                    normalizeResult = parser.booleanValue();
                    break;
                case MODEL_MAX_LENGTH_FIELD:
                    modelMaxLength = parser.intValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new QuestionAnsweringModelConfig(modelType, frameworkType, allConfig, normalizeResult, modelMaxLength);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    public QuestionAnsweringModelConfig(StreamInput in) throws IOException {
        super(in);
        frameworkType = in.readEnum(FrameworkType.class);
        normalizeResult = in.readBoolean();
        modelMaxLength = in.readOptionalInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeEnum(frameworkType);
        out.writeBoolean(normalizeResult);
        out.writeOptionalInt(modelMaxLength);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (modelType != null) {
            builder.field(MODEL_TYPE_FIELD, modelType);
        }
        if (frameworkType != null) {
            builder.field(FRAMEWORK_TYPE_FIELD, frameworkType);
        }
        if (allConfig != null) {
            builder.field(ALL_CONFIG_FIELD, allConfig);
        }
        if (modelMaxLength != null) {
            builder.field(MODEL_MAX_LENGTH_FIELD, modelMaxLength);
        }
        if (normalizeResult) {
            builder.field(NORMALIZE_RESULT_FIELD, normalizeResult);
        }
        builder.endObject();
        return builder;
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

}
