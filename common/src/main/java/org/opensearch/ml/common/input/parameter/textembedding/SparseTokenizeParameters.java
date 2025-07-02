/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.textembedding;

import java.io.IOException;

import org.opensearch.core.ParseField;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.MLAlgoParameter;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;

import lombok.Builder;

@MLAlgoParameter(algorithms = { FunctionName.SPARSE_TOKENIZE })
public class SparseTokenizeParameters extends AbstractSparseEncodingParameters {

    public static final String PARSE_FIELD_NAME = FunctionName.SPARSE_TOKENIZE.name();

    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        MLAlgoParams.class,
        new ParseField(PARSE_FIELD_NAME),
        SparseTokenizeParameters::parse
    );

    // Default constructor with LEXICAL format
    public SparseTokenizeParameters() {
        super(EmbeddingFormat.LEXICAL);
    }

    @Builder(toBuilder = true)
    public SparseTokenizeParameters(EmbeddingFormat sparseEncodingType) {
        super(sparseEncodingType);
    }

    /**
     * Constructor for deserialization from StreamInput
     */
    public SparseTokenizeParameters(StreamInput in) throws IOException {
        super(in);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    public static MLAlgoParams parse(XContentParser parser) throws IOException {
        EmbeddingFormat sparseEncodingType = parseCommon(parser);
        return new SparseTokenizeParameters(sparseEncodingType);
    }
}
