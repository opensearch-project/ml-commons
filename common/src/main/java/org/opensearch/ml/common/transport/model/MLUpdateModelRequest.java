/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLUpdateModelRequest extends ActionRequest {

    MLUpdateModelInput updateModelInput;

    @Builder
    public MLUpdateModelRequest(MLUpdateModelInput updateModelInput) {
        this.updateModelInput = updateModelInput;
    }

    public MLUpdateModelRequest(StreamInput in) throws IOException {
        super(in);
        updateModelInput = new MLUpdateModelInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (updateModelInput == null) {
            exception = addValidationError("Update Model Input can't be null", exception);
        }

        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.updateModelInput.writeTo(out);
    }

    public static MLUpdateModelRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLUpdateModelRequest) {
            return (MLUpdateModelRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput in = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUpdateModelRequest(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLUpdateModelRequest", e);
        }
    }
}
