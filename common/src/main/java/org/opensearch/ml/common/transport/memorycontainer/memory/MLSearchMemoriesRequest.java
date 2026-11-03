/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

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

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * ML search memories request
 */
@Getter
@Setter
@Builder
public class MLSearchMemoriesRequest extends ActionRequest {

    private MLSearchMemoriesInput mlSearchMemoriesInput;
    private String tenantId;

    public MLSearchMemoriesRequest(MLSearchMemoriesInput mlSearchMemoriesInput, String tenantId) {
        this.mlSearchMemoriesInput = mlSearchMemoriesInput;
        this.tenantId = tenantId;
    }

    public MLSearchMemoriesRequest(StreamInput in) throws IOException {
        super(in);
        this.mlSearchMemoriesInput = new MLSearchMemoriesInput(in);
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        mlSearchMemoriesInput.writeTo(out);
        out.writeOptionalString(tenantId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlSearchMemoriesInput == null) {
            exception = addValidationError("Search memories input can't be null", exception);
        }
        return exception;
    }

    public static MLSearchMemoriesRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLSearchMemoriesRequest) {
            return (MLSearchMemoriesRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLSearchMemoriesRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLSearchMemoriesRequest", e);
        }
    }
}
