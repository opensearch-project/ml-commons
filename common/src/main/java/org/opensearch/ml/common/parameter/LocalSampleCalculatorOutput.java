/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.parameter;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.annotation.ExecuteOutput;

import java.io.IOException;

@ExecuteOutput(algorithms={FunctionName.LOCAL_SAMPLE_CALCULATOR})
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
