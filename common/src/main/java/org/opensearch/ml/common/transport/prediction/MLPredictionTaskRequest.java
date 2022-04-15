/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prediction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.InputStreamStreamInput;
import org.opensearch.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.common.parameter.MLInput;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.opensearch.ml.common.transport.MLTaskRequest;

import static org.opensearch.action.ValidateActions.addValidationError;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLPredictionTaskRequest extends MLTaskRequest {

    String modelId;
    MLInput mlInput;

    @Builder
    public MLPredictionTaskRequest(String modelId, MLInput mlInput, boolean dispatchTask) {
        super(dispatchTask);
        this.mlInput = mlInput;
        this.modelId = modelId;
    }

    public MLPredictionTaskRequest(String modelId, MLInput mlInput) {
        this(modelId, mlInput, true);
    }

    public MLPredictionTaskRequest(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readOptionalString();
        this.mlInput = new MLInput(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(this.modelId);
        this.mlInput.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (this.mlInput == null) {
            exception = addValidationError("ML input can't be null", exception);
        } else if (this.mlInput.getInputDataset() == null) {
            exception = addValidationError("input data can't be null", exception);
        }

        return exception;
    }


    public static MLPredictionTaskRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLPredictionTaskRequest) {
            return (MLPredictionTaskRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLPredictionTaskRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLPredictionTaskRequest", e);
        }

    }
}
