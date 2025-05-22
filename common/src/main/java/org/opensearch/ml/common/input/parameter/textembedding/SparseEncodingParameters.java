/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.textembedding;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.Locale;

import org.opensearch.core.ParseField;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.MLAlgoParameter;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;

import lombok.Builder;

@MLAlgoParameter(algorithms = { FunctionName.SPARSE_ENCODING })
public class SparseEncodingParameters implements MLAlgoParams {

    public static final String PARSE_FIELD_NAME = FunctionName.SPARSE_ENCODING.name();
    public static final String SPARSE_ENCODING_FORMAT_FIELD = "sparse_encoding_format";

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
        out.writeOptionalString(sparseEncodingType.name());
    }

    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        MLAlgoParams.class,
        new ParseField(PARSE_FIELD_NAME),
        SparseEncodingParameters::parse
    );

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        xContentBuilder.startObject();
        if (sparseEncodingType != null) {
            xContentBuilder.field(SPARSE_ENCODING_FORMAT_FIELD, sparseEncodingType.name());
        }
        xContentBuilder.endObject();
        return xContentBuilder;
    }

    public enum SparseEncodingFormat {
        WORD,
        INT
    }

    // The type of the content to be embedded
    private final SparseEncodingFormat sparseEncodingType;

    @Builder(toBuilder = true)
    public SparseEncodingParameters(SparseEncodingFormat sparseEncodingType) {
        this.sparseEncodingType = sparseEncodingType;
    }

    public SparseEncodingFormat getSparseEncodingType() {
        return sparseEncodingType;
    }

    public static MLAlgoParams parse(XContentParser parser) throws IOException {
        SparseEncodingFormat sparseEncodingType = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            if (fieldName.equals(SPARSE_ENCODING_FORMAT_FIELD)) {
                String contentType = parser.text();
                sparseEncodingType = SparseEncodingFormat.valueOf(contentType.toUpperCase(Locale.ROOT));
            } else {
                parser.skipChildren();
            }
        }
        return new SparseEncodingParameters(sparseEncodingType);
    }
}
