/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataset.remote;

import java.io.IOException;
import java.util.Map;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.annotation.InputDataSet;
import org.opensearch.ml.common.connector.ConnectorAction.ActionType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.MLInputDataset;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@InputDataSet(MLInputDataType.REMOTE)
public class RemoteInferenceInputDataSet extends MLInputDataset {
    private static final Version MINIMAL_SUPPORTED_VERSION_FOR_CLIENT_CONFIG = CommonValue.VERSION_2_16_0;
    @Setter
    private Map<String, String> parameters;
    @Setter
    private ActionType actionType;

    @Builder(toBuilder = true)
    public RemoteInferenceInputDataSet(Map<String, String> parameters, ActionType actionType) {
        super(MLInputDataType.REMOTE);
        this.parameters = parameters;
        this.actionType = actionType;
    }

    public RemoteInferenceInputDataSet(Map<String, String> parameters) {
        this(parameters, null);
    }

    public RemoteInferenceInputDataSet(StreamInput streamInput) throws IOException {
        super(MLInputDataType.REMOTE);
        Version streamInputVersion = streamInput.getVersion();
        if (streamInput.readBoolean()) {
            parameters = streamInput.readMap(StreamInput::readString, StreamInput::readString);
        }
        if (streamInputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_CLIENT_CONFIG)) {
            if (streamInput.readBoolean()) {
                actionType = streamInput.readEnum(ActionType.class);
            } else {
                this.actionType = null;
            }
        }
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        super.writeTo(streamOutput);
        Version streamOutputVersion = streamOutput.getVersion();
        if (parameters != null) {
            streamOutput.writeBoolean(true);
            streamOutput.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            streamOutput.writeBoolean(false);
        }
        if (streamOutputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_CLIENT_CONFIG)) {
            if (actionType != null) {
                streamOutput.writeBoolean(true);
                streamOutput.writeEnum(actionType);
            } else {
                streamOutput.writeBoolean(false);
            }
        }
    }

}
