/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataset;

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
import org.opensearch.ml.common.annotation.MLAlgoParameter;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;

import lombok.Builder;
import lombok.Data;

/**
 * This class defines the modes of operation of an asymmetric text embedding model.
 * Asymmetric embedding models treat the input text differently, depending on whether it is a
 * passage or a query. One example asymmetric model, that requires different prefixes is e5
 * (cf. https://arxiv.org/pdf/2212.03533.pdf).
 * <p>
 * Use this parameter only if the model is asymmetric and has been registered with the corresponding
 * `query_prefix` and `passage_prefix` configuration parameters.
 */
@Data
@MLAlgoParameter(algorithms = { FunctionName.TEXT_EMBEDDING })
public class AsymmetricTextEmbeddingParameters implements MLAlgoParams {

    public enum EmbeddingContentType {
        QUERY,
        PASSAGE
    }

    public static final String PARSE_FIELD_NAME = FunctionName.TEXT_EMBEDDING.name();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        MLAlgoParams.class,
        new ParseField(PARSE_FIELD_NAME),
        it -> parse(it)
    );

    @Builder(toBuilder = true)
    public AsymmetricTextEmbeddingParameters(EmbeddingContentType embeddingContentType) {
        this.embeddingContentType = embeddingContentType;
    }

    public AsymmetricTextEmbeddingParameters(StreamInput in) throws IOException {
        this.embeddingContentType = EmbeddingContentType.valueOf(in.readOptionalString());
    }

    public static MLAlgoParams parse(XContentParser parser) throws IOException {
        EmbeddingContentType embeddingContentType = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case EMBEDDING_CONTENT_TYPE_FIELD:
                    String contentType = parser.text();
                    embeddingContentType = EmbeddingContentType.valueOf(contentType.toUpperCase(Locale.ROOT));
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new AsymmetricTextEmbeddingParameters(embeddingContentType);
    }

    public static final String EMBEDDING_CONTENT_TYPE_FIELD = "content_type";

    // The type of the content to be embedded
    private EmbeddingContentType embeddingContentType;

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(embeddingContentType.name());
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        xContentBuilder.startObject();
        if (embeddingContentType != null) {
            xContentBuilder.field(EMBEDDING_CONTENT_TYPE_FIELD, embeddingContentType.name());
        }
        xContentBuilder.endObject();
        return xContentBuilder;
    }

    public EmbeddingContentType getEmbeddingContentType() {
        return embeddingContentType;
    }
}
