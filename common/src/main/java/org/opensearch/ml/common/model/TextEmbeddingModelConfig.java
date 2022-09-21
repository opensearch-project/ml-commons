/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.ParseField;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class TextEmbeddingModelConfig implements MLModelConfig {
    public static final String PARSE_FIELD_NAME = MLModelTaskType.TEXT_EMBEDDING.name();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
            TextEmbeddingModelConfig.class,
            new ParseField(PARSE_FIELD_NAME),
            it -> parse(it)
    );

    public static final String MODEL_TYPE_FIELD = "model_type";
    public static final String POSITION_EMBEDDING_TYPE_FIELD = "position_embedding_type";
    public static final String ATTENTION_HEADS_FIELD = "attention_heads";
    public static final String HIDDEN_LAYERS_FIELD = "hidden_layers";
    public static final String MAX_SEQ_LENGTH_FIELD = "max_seq_length";
    public static final String DENSE_VECTOR_DIMENSION_FIELD = "dense_vector_dimension";
    public static final String OTHERS_FIELD = "others";

    private String modelType; // bert, robert
    private String positionEmbeddingType;
    private Integer attentionHeads;
    private Integer hiddenLayers;
    private Integer maxSeqLength; // 512
    private Integer denseVectorDimension; // 768
    private String others;

    @Builder
    public TextEmbeddingModelConfig(String modelType, String positionEmbeddingType, Integer attentionHeads, Integer hiddenLayers, Integer maxSeqLength, Integer denseVectorDimension, String others) {
        this.modelType = modelType;
        this.positionEmbeddingType = positionEmbeddingType;
        this.attentionHeads = attentionHeads;
        this.hiddenLayers = hiddenLayers;
        this.maxSeqLength = maxSeqLength;
        this.denseVectorDimension = denseVectorDimension;
        this.others = others;
    }

    public static TextEmbeddingModelConfig parse(XContentParser parser) throws IOException {
        String modelType = null;
        String positionEmbeddingType = null;
        Integer attentionHeads = null;
        Integer hiddenLayers = null;
        Integer maxSeqLength = null;
        Integer denseVectorDimension = null;
        String others = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_TYPE_FIELD:
                    modelType = parser.text();
                    break;
                case POSITION_EMBEDDING_TYPE_FIELD:
                    positionEmbeddingType = parser.text();
                    break;
                case ATTENTION_HEADS_FIELD:
                    attentionHeads = parser.intValue();
                    break;
                case HIDDEN_LAYERS_FIELD:
                    hiddenLayers = parser.intValue();
                    break;
                case MAX_SEQ_LENGTH_FIELD:
                    maxSeqLength = parser.intValue();
                    break;
                case DENSE_VECTOR_DIMENSION_FIELD:
                    denseVectorDimension = parser.intValue();
                    break;
                case OTHERS_FIELD:
                    others = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new TextEmbeddingModelConfig(modelType, positionEmbeddingType, attentionHeads, hiddenLayers, maxSeqLength, denseVectorDimension, others);
    }

    @Override
    public String getModelType() {
        return modelType;
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    public TextEmbeddingModelConfig(StreamInput in) throws IOException{
        modelType = in.readOptionalString();
        positionEmbeddingType = in.readOptionalString();
        attentionHeads = in.readOptionalInt();
        hiddenLayers = in.readOptionalInt();
        maxSeqLength = in.readOptionalInt();
        denseVectorDimension = in.readOptionalInt();
        others = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(modelType);
        out.writeOptionalString(positionEmbeddingType);
        out.writeOptionalInt(attentionHeads);
        out.writeOptionalInt(hiddenLayers);
        out.writeOptionalInt(maxSeqLength);
        out.writeOptionalInt(denseVectorDimension);
        out.writeOptionalString(others);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {

        builder.startObject();
        if (modelType != null) {
            builder.field(MODEL_TYPE_FIELD, modelType);
        }
        if (positionEmbeddingType != null) {
            builder.field(POSITION_EMBEDDING_TYPE_FIELD, positionEmbeddingType);
        }
        if (attentionHeads != null) {
            builder.field(ATTENTION_HEADS_FIELD, attentionHeads);
        }
        if (hiddenLayers != null) {
            builder.field(HIDDEN_LAYERS_FIELD, hiddenLayers);
        }
        if (maxSeqLength != null) {
            builder.field(MAX_SEQ_LENGTH_FIELD, maxSeqLength);
        }
        if (denseVectorDimension != null) {
            builder.field(DENSE_VECTOR_DIMENSION_FIELD, denseVectorDimension);
        }
        if (others != null) {
            builder.field(OTHERS_FIELD, others);
        }
        builder.endObject();
        return builder;
    }
}
