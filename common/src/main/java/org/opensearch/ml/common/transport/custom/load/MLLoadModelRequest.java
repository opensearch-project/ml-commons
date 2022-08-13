/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.custom.load;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.InputStreamStreamInput;
import org.opensearch.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.common.transport.MLTaskRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.opensearch.action.ValidateActions.addValidationError;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLLoadModelRequest extends MLTaskRequest {

    MLDeployModelInput mlDeployModelInput;
    boolean async;

    @Builder
    public MLLoadModelRequest(MLDeployModelInput mlDeployModelInput, boolean async, boolean dispatchTask) {
        super(dispatchTask);
        this.mlDeployModelInput = mlDeployModelInput;
        this.async = async;
    }

    public MLLoadModelRequest(MLDeployModelInput mlDeployModelInput, boolean async) {
        this(mlDeployModelInput, async, true);
    }

    public MLLoadModelRequest(StreamInput in) throws IOException {
        super(in);
        this.mlDeployModelInput = new MLDeployModelInput(in);
        this.async = in.readBoolean();
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlDeployModelInput == null) {
            exception = addValidationError("ML input can't be null", exception);
        }

        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.mlDeployModelInput.writeTo(out);
        out.writeBoolean(async);
    }

    public static MLLoadModelRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLLoadModelRequest) {
            return (MLLoadModelRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLLoadModelRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLTrainingTaskRequest", e);
        }

    }
}
