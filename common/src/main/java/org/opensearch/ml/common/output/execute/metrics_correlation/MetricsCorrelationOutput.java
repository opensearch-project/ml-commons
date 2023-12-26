/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.metrics_correlation;

import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.ExecuteOutput;
import org.opensearch.ml.common.output.MLOutputType;
import org.opensearch.ml.common.output.Output;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Output of metrics correlation algorithm results
 */

@Log4j2
@Data
@ExecuteOutput(algorithms={FunctionName.METRICS_CORRELATION})
public class MetricsCorrelationOutput implements Output {

    private static final MLOutputType OUTPUT_TYPE = MLOutputType.MCORR_TENSOR;
    private List<MCorrModelTensors> modelOutput;

    public static final String INFERENCE_RESULT_FIELD = "inference_results";

    @Builder
    public MetricsCorrelationOutput(List<MCorrModelTensors> modelOutput) {
        this.modelOutput = modelOutput;
    }

    public MetricsCorrelationOutput(StreamInput in) throws IOException {
        if (in.readBoolean()) {
            modelOutput = new ArrayList<>();
            int size = in.readInt();
            for (int i=0; i<size; i++) {
                modelOutput.add(new MCorrModelTensors(in));
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (modelOutput != null && modelOutput.size() > 0) {
            out.writeBoolean(true);
            out.writeInt(modelOutput.size());
            for (MCorrModelTensors output : modelOutput) {
                output.writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }
    }

    @SneakyThrows
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (modelOutput != null && modelOutput.size() > 0) {
            builder.startArray(INFERENCE_RESULT_FIELD);
            for (MCorrModelTensors output : modelOutput) {
                output.toXContent(builder, params);
            }
            builder.endArray();
        }
        builder.endObject();
        return builder;
    }
}
