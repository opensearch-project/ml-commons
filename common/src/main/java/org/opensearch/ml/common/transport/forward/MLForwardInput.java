/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.forward;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

@Data
@Log4j2
public class MLForwardInput implements Writeable {

    private String taskId;
    private String modelId;
    private String workerNodeId;
    private MLForwardRequestType requestType;
    private MLTask mlTask;
    MLInput modelInput;
    private String error;
    private String[] workerNodes;
    private MLRegisterModelInput registerModelInput;

    @Builder(toBuilder = true)
    public MLForwardInput(
        String taskId,
        String modelId,
        String workerNodeId,
        MLForwardRequestType requestType,
        MLTask mlTask,
        MLInput modelInput,
        String error,
        String[] workerNodes,
        MLRegisterModelInput registerModelInput
    ) {
        this.taskId = taskId;
        this.modelId = modelId;
        this.workerNodeId = workerNodeId;
        this.requestType = requestType;
        this.mlTask = mlTask;
        this.modelInput = modelInput;
        this.error = error;
        this.workerNodes = workerNodes;
        this.registerModelInput = registerModelInput;
    }

    public MLForwardInput(StreamInput in) throws IOException {
        this.taskId = in.readOptionalString();
        this.modelId = in.readOptionalString();
        this.workerNodeId = in.readOptionalString();
        this.requestType = in.readEnum(MLForwardRequestType.class);
        if (in.readBoolean()) {
            mlTask = new MLTask(in);
        }
        if (in.readBoolean()) {
            this.modelInput = new MLInput(in);
        }
        this.error = in.readOptionalString();
        this.workerNodes = in.readOptionalStringArray();
        if (in.readBoolean()) {
            this.registerModelInput = new MLRegisterModelInput(in);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(taskId);
        out.writeOptionalString(modelId);
        out.writeOptionalString(workerNodeId);
        out.writeEnum(requestType);
        if (this.mlTask != null) {
            out.writeBoolean(true);
            mlTask.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (modelInput != null) {
            out.writeBoolean(true);
            modelInput.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(error);
        out.writeOptionalStringArray(workerNodes);
        if (registerModelInput != null) {
            out.writeBoolean(true);
            registerModelInput.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
    }

}
