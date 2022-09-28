/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.common.ParseField;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Locale;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Setter
@Getter
public class TextEmbeddingModelConfig extends MLModelConfig {
    public static final String PARSE_FIELD_NAME = MLModelTaskType.TEXT_EMBEDDING.name();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
            TextEmbeddingModelConfig.class,
            new ParseField(PARSE_FIELD_NAME),
            it -> parse(it)
    );

    public static final String EMBEDDING_DIMENSION_FIELD = "embedding_dimension";
    public static final String FRAMEWORK_TYPE_FIELD = "framework_type";

    private Integer embeddingDimension;
    private FrameworkType frameworkType;

    @Builder
    public TextEmbeddingModelConfig(String modelType, Integer embeddingDimension, FrameworkType frameworkType, String allConfig) {
        super(modelType, allConfig);
        if (embeddingDimension == null) {
            throw new IllegalArgumentException("embedding dimension is null");
        }
        if (frameworkType == null) {
            throw new IllegalArgumentException("framework type is null");
        }
        this.embeddingDimension = embeddingDimension;
        this.frameworkType = frameworkType;
    }

    public static TextEmbeddingModelConfig parse(XContentParser parser) throws IOException {
        String modelType = null;
        Integer embeddingDimension = null;
        FrameworkType frameworkType = null;
        String allConfig = null;

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
                    frameworkType = FrameworkType.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case ALL_CONFIG_FIELD:
                    allConfig = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new TextEmbeddingModelConfig(modelType,  embeddingDimension, frameworkType, allConfig);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    public TextEmbeddingModelConfig(StreamInput in) throws IOException{
        super(in);
        embeddingDimension = in.readInt();
        frameworkType = in.readEnum(FrameworkType.class);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeInt(embeddingDimension);
        out.writeEnum(frameworkType);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_TYPE_FIELD, modelType);
        builder.field(EMBEDDING_DIMENSION_FIELD, embeddingDimension);
        builder.field(FRAMEWORK_TYPE_FIELD, frameworkType);
        if (allConfig != null) {
            builder.field(ALL_CONFIG_FIELD, allConfig);
        }
        builder.endObject();
        return builder;
    }

    public enum FrameworkType {
        HUGGING_FACE_TRANSFORMERS,
        SENTENCE_TRANSFORMERS;

        public static FrameworkType from(String value) {
            try {
                return FrameworkType.valueOf(value);
            } catch (Exception e) {
                throw new IllegalArgumentException("Wrong framework type");
            }
        }
    }

}
