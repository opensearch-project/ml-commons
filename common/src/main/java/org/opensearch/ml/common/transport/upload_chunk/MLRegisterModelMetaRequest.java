/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

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
public class MLRegisterModelMetaRequest extends ActionRequest {

    MLRegisterModelMetaInput mlRegisterModelMetaInput;

    @Builder
    public MLRegisterModelMetaRequest(MLRegisterModelMetaInput mlRegisterModelMetaInput) {
        this.mlRegisterModelMetaInput = mlRegisterModelMetaInput;
    }

    public MLRegisterModelMetaRequest(StreamInput in) throws IOException {
        super(in);
        this.mlRegisterModelMetaInput = new MLRegisterModelMetaInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlRegisterModelMetaInput == null) {
            exception = addValidationError("Model meta input can't be null", exception);
        }

        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.mlRegisterModelMetaInput.writeTo(out);
    }

    public static MLRegisterModelMetaRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLRegisterModelMetaRequest) {
            return (MLRegisterModelMetaRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLRegisterModelMetaRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLRegisterModelMetaRequest", e);
        }

    }
}
