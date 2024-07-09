/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.ParseField;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;

import java.io.IOException;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@Setter
@Getter
public class ImageEmbeddingModelConfig extends MLModelConfig {
    public static final String PARSE_FIELD_NAME = FunctionName.IMAGE_EMBEDDING.name();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
            ImageEmbeddingModelConfig.class,
            new ParseField(PARSE_FIELD_NAME),
            it -> parse(it)
    );

    public static final String EMBEDDING_DIMENSION_FIELD = "embedding_dimension";

    private final Integer embeddingDimension;

    @Builder(toBuilder = true)
    public ImageEmbeddingModelConfig(String modelType, Integer embeddingDimension, String allConfig) {
        super(modelType, allConfig);
        if (embeddingDimension == null) {
            throw new IllegalArgumentException("embedding dimension is null");
        }
        this.embeddingDimension = embeddingDimension;
    }

    public static ImageEmbeddingModelConfig parse(XContentParser parser) throws IOException {
        String modelType = null;
        Integer embeddingDimension = null;
        String allConfig = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_TYPE_FIELD: modelType = parser.text();
                case EMBEDDING_DIMENSION_FIELD: embeddingDimension = parser.intValue();
                case ALL_CONFIG_FIELD: allConfig = parser.text();
                default: parser.skipChildren();
            }
        }
        return new ImageEmbeddingModelConfig(modelType, embeddingDimension, allConfig);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    public ImageEmbeddingModelConfig(StreamInput in) throws IOException{
        super(in);
        embeddingDimension = in.readInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeInt(embeddingDimension);
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
        if (allConfig != null) {
            builder.field(ALL_CONFIG_FIELD, allConfig);
        }
        builder.endObject();
        return builder;
    }
}
