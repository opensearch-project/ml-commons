/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.input.visual;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.ImageEmbeddingInputDataSet;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.MLInput;

import java.io.IOException;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;


/**
 * MLInput which supports an image embedding algorithm
 * Inputs are images. Outputs are image embeddings
 */
@org.opensearch.ml.common.annotation.MLInput(functionNames = {FunctionName.IMAGE_EMBEDDING})
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
        if(parameters != null) {
            builder.field(ML_PARAMETERS_FIELD, parameters);
        }
        if(inputDataset != null) {
            ImageEmbeddingInputDataSet ds = (ImageEmbeddingInputDataSet) this.inputDataset;
            String base64Image = ds.getBase64Image();
            builder.field(IMAGE_FIELD, base64Image);
        }
        builder.endObject();
        return builder;
    }

    public ImageEmbeddingMLInput(XContentParser parser, FunctionName functionName) throws IOException {
        super();
        this.algorithm = functionName;
        String base64Image = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case IMAGE_FIELD:
                    base64Image = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        if(base64Image == null) {
            throw new IllegalArgumentException("Image is not provided");
        }

        inputDataset = new ImageEmbeddingInputDataSet(base64Image);
    }

}
