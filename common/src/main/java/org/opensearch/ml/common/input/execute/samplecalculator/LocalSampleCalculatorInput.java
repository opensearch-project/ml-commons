/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.samplecalculator;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.core.ParseField;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.ExecuteInput;
import org.opensearch.ml.common.input.Input;

import lombok.Builder;
import lombok.Data;

@ExecuteInput(algorithms = { FunctionName.LOCAL_SAMPLE_CALCULATOR })
@Data
public class LocalSampleCalculatorInput implements Input {
    public static final String PARSE_FIELD_NAME = FunctionName.LOCAL_SAMPLE_CALCULATOR.name();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        Input.class,
        new ParseField(PARSE_FIELD_NAME),
        it -> parse(it)
    );

    public static final String OPERATION_FIELD = "operation";
    public static final String INPUT_DATA_FIELD = "input_data";

    public static LocalSampleCalculatorInput parse(XContentParser parser) throws IOException {
        String operation = null;
        List<Double> inputData = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case OPERATION_FIELD:
                    operation = parser.text();
                    break;
                case INPUT_DATA_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        inputData.add(parser.doubleValue(false));
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new LocalSampleCalculatorInput(operation, inputData);
    }

    String operation;
    List<Double> inputData;

    @Builder
    public LocalSampleCalculatorInput(String operation, List<Double> inputData) {
        if (operation == null) {
            throw new IllegalArgumentException("wrong operation");
        }
        if (inputData == null || inputData.size() == 0) {
            throw new IllegalArgumentException("empty input data");
        }
        this.operation = operation;
        this.inputData = inputData;
    }

    @Override
    public FunctionName getFunctionName() {
        return FunctionName.LOCAL_SAMPLE_CALCULATOR;
    }

    public LocalSampleCalculatorInput(StreamInput in) throws IOException {
        this.operation = in.readString();
        int size = in.readInt();
        this.inputData = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            inputData.add(in.readDouble());
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(operation);
        out.writeInt(inputData.size());
        for (Double d : inputData) {
            out.writeDouble(d.doubleValue());
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(OPERATION_FIELD, operation);
        builder.field(INPUT_DATA_FIELD, inputData);
        builder.endObject();
        return builder;
    }
}
