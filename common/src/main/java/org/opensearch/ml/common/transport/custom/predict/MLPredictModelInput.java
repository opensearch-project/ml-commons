/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.custom.predict;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

/**
 * ML input data: algirithm name, parameters and input data set.
 */
@Data
public class MLPredictModelInput implements ToXContentObject, Writeable {

    public static final String MODEL_NAME_FIELD = "model_name";
    public static final String VERSION_FIELD = "version";
    public static final String URL_FIELD = "url";
    public static final String QUESTION_FIELD = "question";
    public static final String DOC_FIELD = "doc";

    private String modelName;
    private Integer version;
    private String imageUrl;
    private String question;
    private String doc;

    @Builder(toBuilder = true)
    public MLPredictModelInput(String modelName, Integer version, String imageUrl, String question, String doc) {
        this.modelName = modelName;
        this.version = version;
        this.imageUrl = imageUrl;
        this.question = question;
        this.doc = doc;
    }


    public MLPredictModelInput(StreamInput in) throws IOException {
        this.modelName = in.readString();
        this.version = in.readInt();
        this.imageUrl = in.readOptionalString();
        this.question = in.readOptionalString();
        this.doc = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelName);
        out.writeInt(version);
        out.writeOptionalString(imageUrl);
        out.writeOptionalString(question);
        out.writeOptionalString(doc);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_NAME_FIELD, modelName);
        builder.field(VERSION_FIELD, version);
        if (imageUrl != null) {
            builder.field(URL_FIELD, imageUrl);
        }
        if (question != null) {
            builder.field(URL_FIELD, question);
        }
        if (doc != null) {
            builder.field(URL_FIELD, doc);
        }
        builder.endObject();
        return builder;
    }

    public static MLPredictModelInput parse(XContentParser parser) throws IOException {
        String modelName = null;
        Integer version = null;
        String imageUrl = null;
        String question = null;
        String doc = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_NAME_FIELD:
                    modelName = parser.text();
                    break;
                case VERSION_FIELD:
                    version = parser.intValue();
                    break;
                case URL_FIELD:
                    imageUrl = parser.text();
                    break;
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
        return new MLPredictModelInput(modelName, version, imageUrl, question, doc);
    }


    public FunctionName getFunctionName() {
        return FunctionName.CUSTOM;
    }

}
