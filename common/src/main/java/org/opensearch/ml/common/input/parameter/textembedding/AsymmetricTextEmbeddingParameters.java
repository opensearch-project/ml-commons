/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.textembedding;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

import org.opensearch.Version;
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
 * <p>
 * Also supports embedding format control for sparse encoding algorithms.
 */
@Data
@MLAlgoParameter(algorithms = { FunctionName.TEXT_EMBEDDING, FunctionName.SPARSE_ENCODING, FunctionName.SPARSE_TOKENIZE })
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
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY_SPARSE_ENCODING = new NamedXContentRegistry.Entry(
        MLAlgoParams.class,
        new ParseField(FunctionName.SPARSE_ENCODING.name()),
        it -> parse(it)
    );
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY_SPARSE_TOKENIZE = new NamedXContentRegistry.Entry(
        MLAlgoParams.class,
        new ParseField(FunctionName.SPARSE_TOKENIZE.name()),
        it -> parse(it)
    );

    @Builder(toBuilder = true)
    public AsymmetricTextEmbeddingParameters(EmbeddingContentType embeddingContentType, SparseEmbeddingFormat sparseEmbeddingFormat) {
        this.embeddingContentType = embeddingContentType;
        this.sparseEmbeddingFormat = sparseEmbeddingFormat != null ? sparseEmbeddingFormat : SparseEmbeddingFormat.WORD;
    }

    // Constructor for backward compatibility
    public AsymmetricTextEmbeddingParameters(EmbeddingContentType embeddingContentType) {
        this.embeddingContentType = embeddingContentType;
        this.sparseEmbeddingFormat = SparseEmbeddingFormat.WORD;
    }

    public AsymmetricTextEmbeddingParameters(StreamInput in) throws IOException {
        Version streamInputVersion = in.getVersion();
        String contentType = in.readOptionalString();
        this.embeddingContentType = contentType != null ? EmbeddingContentType.valueOf(contentType) : null;
        if (streamInputVersion.onOrAfter(Version.V_3_2_0)) {
            String formatName = in.readOptionalString();
            this.sparseEmbeddingFormat = formatName != null ? SparseEmbeddingFormat.valueOf(formatName) : SparseEmbeddingFormat.WORD;
        } else {
            this.sparseEmbeddingFormat = SparseEmbeddingFormat.WORD;
        }
    }

    public static MLAlgoParams parse(XContentParser parser) throws IOException {
        EmbeddingContentType embeddingContentType = null;
        SparseEmbeddingFormat sparseEmbeddingFormat = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case EMBEDDING_CONTENT_TYPE_FIELD:
                    String contentType = parser.text();
                    embeddingContentType = EmbeddingContentType.valueOf(contentType.toUpperCase(Locale.ROOT));
                    break;
                case SPARSE_EMBEDDING_FORMAT_FIELD:
                    String formatType = parser.text();
                    sparseEmbeddingFormat = SparseEmbeddingFormat.valueOf(formatType.toUpperCase(Locale.ROOT));
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new AsymmetricTextEmbeddingParameters(embeddingContentType, sparseEmbeddingFormat);
    }

    public static final String EMBEDDING_CONTENT_TYPE_FIELD = "content_type";
    public static final String SPARSE_EMBEDDING_FORMAT_FIELD = "sparse_embedding_format";

    // The type of the content to be embedded
    private EmbeddingContentType embeddingContentType;

    // The format of the embedding output
    private SparseEmbeddingFormat sparseEmbeddingFormat;

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
        Version streamOutputVersion = out.getVersion();
        out.writeOptionalString(embeddingContentType != null ? embeddingContentType.name() : null);
        if (streamOutputVersion.onOrAfter(Version.V_3_2_0)) {
            out.writeOptionalString(sparseEmbeddingFormat != null ? sparseEmbeddingFormat.name() : null);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        xContentBuilder.startObject();
        if (embeddingContentType != null) {
            xContentBuilder.field(EMBEDDING_CONTENT_TYPE_FIELD, embeddingContentType.name());
        }
        xContentBuilder.field(SPARSE_EMBEDDING_FORMAT_FIELD, sparseEmbeddingFormat.name());
        xContentBuilder.endObject();
        return xContentBuilder;
    }

    public EmbeddingContentType getEmbeddingContentType() {
        return embeddingContentType;
    }

    public SparseEmbeddingFormat getSparseEmbeddingFormat() {
        return sparseEmbeddingFormat;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        AsymmetricTextEmbeddingParameters other = (AsymmetricTextEmbeddingParameters) obj;
        return Objects.equals(embeddingContentType, other.embeddingContentType)
            && Objects.equals(sparseEmbeddingFormat, other.sparseEmbeddingFormat);
    }
}
