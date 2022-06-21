/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.bertqa;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.opensearch.common.ParseField;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.ExecuteInput;
import org.opensearch.ml.common.input.Input;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@ExecuteInput(algorithms={FunctionName.BERT_QA})
@Data
@AllArgsConstructor
public class BertQAInput implements Input {

    public static final String QUESTION_FIELD = "question";
    public static final String DOC_FIELD = "doc";
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY_ENTRY = new NamedXContentRegistry.Entry(
            Input.class,
            new ParseField(FunctionName.BERT_QA.name()),
            parser -> parse(parser)
    );

    public static BertQAInput parse(XContentParser parser) throws IOException {
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        String question = null;
        String doc = null;

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case QUESTION_FIELD:
                    question = parser.text();
                    break;
                case DOC_FIELD:
                    doc = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new BertQAInput(question, doc);
    }

    private final String question;
    private final String doc;

    public BertQAInput(StreamInput in) throws IOException {
        this.question = in.readString();
        this.doc = in.readString();
    }

    @Override
    public FunctionName getFunctionName() {
        return FunctionName.BERT_QA;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(QUESTION_FIELD, question);
        builder.field(DOC_FIELD, doc);
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(question);
        out.writeString(doc);
    }
}
