/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataset.remote;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.PredictMode;
import org.opensearch.ml.common.annotation.InputDataSet;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.MLInputDataset;

import java.io.IOException;
import java.util.Map;

@Getter
@InputDataSet(MLInputDataType.REMOTE)
public class RemoteInferenceInputDataSet extends MLInputDataset {

    @Setter
    private Map<String, String> parameters;
    private PredictMode predictMode;

    @Builder(toBuilder = true)
    public RemoteInferenceInputDataSet(Map<String, String> parameters, PredictMode predictMode) {
        super(MLInputDataType.REMOTE);
        this.parameters = parameters;
        this.predictMode = predictMode;
    }

    public RemoteInferenceInputDataSet(Map<String, String> parameters) {
        this(parameters, null);
    }

    public RemoteInferenceInputDataSet(StreamInput streamInput) throws IOException {
        super(MLInputDataType.REMOTE);
        if (streamInput.readBoolean()) {
            parameters = streamInput.readMap(s -> s.readString(), s-> s.readString());
        }
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        super.writeTo(streamOutput);
        if (parameters !=  null) {
            streamOutput.writeBoolean(true);
            streamOutput.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            streamOutput.writeBoolean(false);
        }
    }

}
