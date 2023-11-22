/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.metricscorrelation;

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

@ExecuteInput(algorithms = { FunctionName.METRICS_CORRELATION })
@Data
public class MetricsCorrelationInput implements Input {
    public static final String PARSE_FIELD_NAME = FunctionName.METRICS_CORRELATION.name();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        Input.class,
        new ParseField(PARSE_FIELD_NAME),
        it -> parse(it)
    );

    public static final String METRICS_FIELD = "metrics";

    List<float[]> inputData;

    @Builder
    public MetricsCorrelationInput(List<float[]> inputData) {
        if (inputData == null || inputData.size() == 0) {
            throw new IllegalArgumentException("empty input data");
        }
        int expectedLength = inputData.get(0).length;
        for (int i = 1; i < inputData.size(); i++) {
            float[] array = inputData.get(i);
            if (array.length != expectedLength) {
                // found an array with different length
                throw new IllegalArgumentException("All the input metrics sizes should be same");
            }
        }
        if (inputData.size() >= expectedLength) {
            throw new IllegalArgumentException("The number of metrics to correlate must be smaller than the length of each time series.");
        }
        this.inputData = inputData;
    }

    public MetricsCorrelationInput(StreamInput in) throws IOException {
        this.inputData = in.readList(StreamInput::readFloatArray);
    }

    public static MetricsCorrelationInput parse(XContentParser parser) throws IOException {
        List<float[]> inputData = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case METRICS_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                        List<Float> inputItem = new ArrayList<>();
                        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                            inputItem.add(parser.floatValue());
                        }
                        float[] floatArray = new float[inputItem.size()];
                        int i = 0;

                        for (Float f : inputItem) {
                            floatArray[i++] = (f != null ? f : Float.NaN);
                        }
                        inputData.add(floatArray);
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MetricsCorrelationInput(inputData);
    }

    @Override
    public FunctionName getFunctionName() {
        return FunctionName.METRICS_CORRELATION;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeCollection(this.inputData, StreamOutput::writeFloatArray);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(METRICS_FIELD, inputData);
        builder.endObject();
        return builder;
    }
}
