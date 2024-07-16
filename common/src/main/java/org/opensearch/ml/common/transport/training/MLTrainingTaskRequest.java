/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.training;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.MLTaskRequest;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLTrainingTaskRequest extends MLTaskRequest {

    /**
     * the name of algorithm
     */
    MLInput mlInput;
    boolean async;

    @Builder
    public MLTrainingTaskRequest(MLInput mlInput, boolean async, boolean dispatchTask) {
        super(dispatchTask);
        this.mlInput = mlInput;
        this.async = async;
    }

    public MLTrainingTaskRequest(MLInput mlInput, boolean async) {
        this(mlInput, async, true);
    }

    public MLTrainingTaskRequest(StreamInput in) throws IOException {
        super(in);
        this.mlInput = new MLInput(in);
        this.async = in.readBoolean();
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlInput == null) {
            exception = addValidationError("ML input can't be null", exception);
        } else if (Objects.isNull(mlInput.getInputDataset())) {
            exception = addValidationError("input data can't be null", exception);
        }

        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.mlInput.writeTo(out);
        out.writeBoolean(async);
    }

    public static MLTrainingTaskRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLTrainingTaskRequest) {
            return (MLTrainingTaskRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLTrainingTaskRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLTrainingTaskRequest", e);
        }

    }
}
