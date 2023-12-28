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

public class MLUpdateModelControllerRequest extends ActionRequest {
    private MLModelController updateModelControllerInput;

    @Builder
    public MLUpdateModelControllerRequest(MLModelController updateModelControllerInput) {
        this.updateModelControllerInput = updateModelControllerInput;
    }

    public MLUpdateModelControllerRequest(StreamInput in) throws IOException {
        super(in);
        this.updateModelControllerInput = new MLModelController(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        updateModelControllerInput.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (updateModelControllerInput == null) {
            exception = addValidationError("Update model controller input can't be null", exception);
        } 
        return exception;
    }

    public static MLUpdateModelControllerRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLUpdateModelControllerRequest) {
            return (MLUpdateModelControllerRequest) actionRequest;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUpdateModelControllerRequest(input);
            } 
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse action request to MLCreateModelControllerRequest", e);
        }

    }
}
