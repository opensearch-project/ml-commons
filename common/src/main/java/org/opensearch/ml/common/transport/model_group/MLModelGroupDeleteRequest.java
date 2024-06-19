/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.opensearch.action.ValidateActions.addValidationError;

public class MLModelGroupDeleteRequest extends ActionRequest {
    @Getter
    String modelGroupId;
    @Getter
    String tenantId;

    @Builder
    public MLModelGroupDeleteRequest(String modelGroupId, String tenantId) {
        this.modelGroupId = modelGroupId;
        this.tenantId = tenantId;
    }

    public MLModelGroupDeleteRequest(StreamInput input) throws IOException {
        super(input);
        this.modelGroupId = input.readString();
        this.tenantId = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);
        output.writeString(modelGroupId);
        output.writeOptionalString(tenantId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.modelGroupId == null) {
            exception = addValidationError("ML model group id can't be null", exception);
        }

        return exception;
    }

    public static MLModelGroupDeleteRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLModelGroupDeleteRequest) {
            return (MLModelGroupDeleteRequest)actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLModelGroupDeleteRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLModelGroupDeleteRequest", e);
        }
    }
}
