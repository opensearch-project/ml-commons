/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.Version;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Builder;
import lombok.Getter;

public class MLModelDeleteRequest extends ActionRequest {
    @Getter
    String modelId;

    @Getter
    String tenantId;

    @Builder
    public MLModelDeleteRequest(String modelId, String tenantId) {
        this.modelId = modelId;
        this.tenantId = tenantId;
    }

    public MLModelDeleteRequest(StreamInput input) throws IOException {
        super(input);
        Version streamInputVersion = input.getVersion();
        this.modelId = input.readString();
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? input.readOptionalString() : null;
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);
        Version streamOutputVersion = output.getVersion();
        output.writeString(modelId);
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            output.writeOptionalString(tenantId);
        }
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.modelId == null) {
            exception = addValidationError("ML model id can't be null", exception);
        }

        return exception;
    }

    public static MLModelDeleteRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLModelDeleteRequest) {
            return (MLModelDeleteRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLModelDeleteRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLModelDeleteRequest", e);
        }
    }
}
