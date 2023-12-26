/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.controller;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.controller.MLModelController;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.opensearch.action.ValidateActions.addValidationError;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLCreateModelControllerRequest extends ActionRequest {
    private MLModelController modelControllerInput;

    @Builder
    public MLCreateModelControllerRequest(MLModelController modelControllerInput) {
        this.modelControllerInput = modelControllerInput;
    }

    public MLCreateModelControllerRequest(StreamInput in) throws IOException {
        super(in);
        this.modelControllerInput = new MLModelController(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        modelControllerInput.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (modelControllerInput == null) {
            exception = addValidationError("Model controller input can't be null", exception);
        } 
        return exception;
    }

    public static MLCreateModelControllerRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLCreateModelControllerRequest) {
            return (MLCreateModelControllerRequest) actionRequest;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray())))
                {
                    return new MLCreateModelControllerRequest(input);
                }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLCreateModelControllerRequest", e);
        }
    }
}
