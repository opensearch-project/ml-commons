/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.common.input.nlp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.input.MLInput;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;


/**
 * MLInput which supports a text similarity algorithm
 * Inputs are pairs of texts. Outputs are real numbers
 * Use this for Cross Encoder models
 */
@org.opensearch.ml.common.annotation.MLInput(functionNames = {FunctionName.TEXT_SIMILARITY})
public class TextSimilarityMLInput extends MLInput {
    public static final String TEXT_PAIRS_FIELD = "text_pairs";

    public TextSimilarityMLInput(FunctionName algorithm, MLInputDataset dataset) {
        super(algorithm, null, dataset);
    }

    public TextSimilarityMLInput(StreamInput in) throws IOException {
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
            TextSimilarityInputDataSet ds = (TextSimilarityInputDataSet) this.inputDataset;
            List<Pair<String, String>> pairs = ds.getPairs();
            if (pairs != null && !pairs.isEmpty()) {
                builder.startArray(TEXT_PAIRS_FIELD);
                for(Pair<String, String> p : pairs) {
                    builder.startArray();
                    builder.value(p.getLeft());
                    builder.value(p.getRight());
                    builder.endArray();
                }
                builder.endArray();
            }
        }
        builder.endObject();
        return builder;
    }

    public TextSimilarityMLInput(XContentParser parser, FunctionName functionName) throws IOException {
        super();
        this.algorithm = functionName;
        List<Pair<String, String>> pairs = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TEXT_PAIRS_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                        parser.nextToken();
                        String query = parser.text();
                        parser.nextToken();
                        String context = parser.text();
                        pairs.add(Pair.of(query, context));
                        ensureExpectedToken(XContentParser.Token.END_ARRAY, parser.nextToken(), parser);
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }        
        if(pairs.isEmpty()) {
            throw new IllegalArgumentException("no text pairs");
        }
        inputDataset = new TextSimilarityInputDataSet(pairs);
    }

}
