/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataset;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.common.annotation.InputDataSet;
import org.opensearch.ml.common.output.model.ModelResultFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@InputDataSet(MLInputDataType.METRICS)
public class MetricsInputDataSet extends MLInputDataset{

    private ModelResultFilter resultFilter;

    private List<float[]> metrics;

    @Builder(toBuilder = true)
    public MetricsInputDataSet(List<float[]> metrics, ModelResultFilter resultFilter) {
        super(MLInputDataType.TEXT_DOCS);
        this.resultFilter = resultFilter;
        Objects.requireNonNull(metrics);
        if (metrics.size() == 0) {
            throw new IllegalArgumentException("empty metrics");
        }
        this.metrics = metrics;
    }

    public MetricsInputDataSet(StreamInput streamInput) throws IOException {
        super(MLInputDataType.METRICS);
        metrics = streamInput.readList(StreamInput::readFloatArray);
        if (streamInput.readBoolean()) {
            resultFilter = new ModelResultFilter(streamInput);
        } else {
            resultFilter = null;
        }
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        super.writeTo(streamOutput);
        streamOutput.writeCollection(metrics, StreamOutput::writeFloatArray);
        if (resultFilter != null) {
            streamOutput.writeBoolean(true);
            resultFilter.writeTo(streamOutput);
        } else {
            streamOutput.writeBoolean(false);
        }
    }
}
