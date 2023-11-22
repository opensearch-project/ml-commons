/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

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
public class MLUpdateModelGroupRequest extends ActionRequest {

    MLUpdateModelGroupInput updateModelGroupInput;

    @Builder
    public MLUpdateModelGroupRequest(MLUpdateModelGroupInput updateModelGroupInput) {
        this.updateModelGroupInput = updateModelGroupInput;
    }

    public MLUpdateModelGroupRequest(StreamInput in) throws IOException {
        super(in);
        this.updateModelGroupInput = new MLUpdateModelGroupInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (updateModelGroupInput == null) {
            exception = addValidationError("Update Model group input can't be null", exception);
        }

        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.updateModelGroupInput.writeTo(out);
    }

    public static MLUpdateModelGroupRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLUpdateModelGroupRequest) {
            return (MLUpdateModelGroupRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUpdateModelGroupRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLUpdateModelGroupRequest", e);
        }

    }
}
