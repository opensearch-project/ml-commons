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
public class MLRegisterModelGroupRequest extends ActionRequest {

    MLRegisterModelGroupInput registerModelGroupInput;

    @Builder
    public MLRegisterModelGroupRequest(MLRegisterModelGroupInput registerModelGroupInput) {
        this.registerModelGroupInput = registerModelGroupInput;
    }

    public MLRegisterModelGroupRequest(StreamInput in) throws IOException {
        super(in);
        this.registerModelGroupInput = new MLRegisterModelGroupInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (registerModelGroupInput == null) {
            exception = addValidationError("Model meta input can't be null", exception);
        }

        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.registerModelGroupInput.writeTo(out);
    }

    public static MLRegisterModelGroupRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLRegisterModelGroupRequest) {
            return (MLRegisterModelGroupRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLRegisterModelGroupRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLCreateModelMetaRequest", e);
        }

    }
}
