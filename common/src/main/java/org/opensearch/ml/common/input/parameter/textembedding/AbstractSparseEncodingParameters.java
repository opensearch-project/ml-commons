/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.textembedding;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.Locale;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;

import lombok.Getter;

/**
 * Abstract base class for sparse encoding related parameters.
 * Contains common logic shared between SPARSE_ENCODING and SPARSE_TOKENIZE algorithms.
 */
@Getter
public abstract class AbstractSparseEncodingParameters implements MLAlgoParams {

    public static final String EMBEDDING_FORMAT_FIELD = "embedding_format";

    public enum EmbeddingFormat {
        LEXICAL,
        VECTOR
    }

    // The type of the content to be encoded
    protected final EmbeddingFormat embeddingFormat;

    protected AbstractSparseEncodingParameters(EmbeddingFormat embeddingFormat) {
        // Set default to LEXICAL if null
        this.embeddingFormat = embeddingFormat != null ? embeddingFormat : EmbeddingFormat.LEXICAL;
    }

    /**
     * Constructor for deserialization from StreamInput
     */
    protected AbstractSparseEncodingParameters(StreamInput in) throws IOException {
        String formatName = in.readOptionalString();
        this.embeddingFormat = formatName != null ? EmbeddingFormat.valueOf(formatName) : EmbeddingFormat.LEXICAL;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(embeddingFormat.name());
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        xContentBuilder.startObject();
        xContentBuilder.field(EMBEDDING_FORMAT_FIELD, embeddingFormat.name());
        xContentBuilder.endObject();
        return xContentBuilder;
    }

    /**
     * Common parsing method that can be used by subclasses.
     * 
     * @param parser XContentParser to parse from
     * @return parsed EmbeddingFormat, defaults to LEXICAL if not specified
     * @throws IOException if parsing fails
     */
    protected static EmbeddingFormat parseCommon(XContentParser parser) throws IOException {
        EmbeddingFormat sparseEncodingType = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            if (fieldName.equals(EMBEDDING_FORMAT_FIELD)) {
                String contentType = parser.text();
                sparseEncodingType = EmbeddingFormat.valueOf(contentType.toUpperCase(Locale.ROOT));
            } else {
                parser.skipChildren();
            }
        }
        // Return LEXICAL as default if not specified
        return sparseEncodingType != null ? sparseEncodingType : EmbeddingFormat.LEXICAL;
    }

    public EmbeddingFormat getEmbeddingFormat() {
        return embeddingFormat;
    }
}
