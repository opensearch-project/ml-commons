/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.samplecalculator;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.ExecuteOutput;
import org.opensearch.ml.common.output.Output;

import lombok.Builder;
import lombok.Data;

@ExecuteOutput(algorithms = { FunctionName.LOCAL_SAMPLE_CALCULATOR })
@Data
public class LocalSampleCalculatorOutput implements Output {

    private Double result;

    @Builder
    public LocalSampleCalculatorOutput(Double totalSum) {
        this.result = totalSum;
    }

    public LocalSampleCalculatorOutput(StreamInput in) throws IOException {
        result = in.readOptionalDouble();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalDouble(result);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (result != null) {
            builder.field("result", result);
        }
        return builder;
    }
}
