/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.input.visual;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.ImageEmbeddingInputDataSet;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.MLInput;

/**
 * MLInput which supports an image embedding algorithm
 * Inputs are images. Outputs are image embeddings
 */
@org.opensearch.ml.common.annotation.MLInput(functionNames = { FunctionName.IMAGE_EMBEDDING })
public class ImageEmbeddingMLInput extends MLInput {

    public ImageEmbeddingMLInput(FunctionName algorithm, MLInputDataset dataset) {
        super(algorithm, null, dataset);
    }

    public ImageEmbeddingMLInput(StreamInput in) throws IOException {
        super(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(ALGORITHM_FIELD, algorithm.name());
        if (parameters != null) {
            builder.field(ML_PARAMETERS_FIELD, parameters);
        }
        if (inputDataset != null) {
            ImageEmbeddingInputDataSet ds = (ImageEmbeddingInputDataSet) this.inputDataset;
            List<String> base64Image = ds.getBase64Images();
            builder.field(IMAGE_FIELD, base64Image);
        }
        builder.endObject();
        return builder;
    }

    public ImageEmbeddingMLInput(XContentParser parser, FunctionName functionName) throws IOException {
        super();
        this.algorithm = functionName;
        List<String> base64Images = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case IMAGE_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        if (parser.currentToken() == null || parser.currentToken() == XContentParser.Token.VALUE_NULL) {
                            base64Images.add(null);
                        } else {
                            base64Images.add(parser.text());
                        }
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        if (base64Images.isEmpty()) {
            throw new IllegalArgumentException("Image in base64 is not provided");
        }

        inputDataset = new ImageEmbeddingInputDataSet(base64Images);
    }

}
