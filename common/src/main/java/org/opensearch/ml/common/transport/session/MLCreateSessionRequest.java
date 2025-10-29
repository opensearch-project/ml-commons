/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.session;

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

@Getter
public class MLCreateSessionRequest extends ActionRequest {

    private final MLCreateSessionInput mlCreateSessionInput;

    @Builder
    public MLCreateSessionRequest(MLCreateSessionInput mlCreateSessionInput) {
        this.mlCreateSessionInput = mlCreateSessionInput;
    }

    public MLCreateSessionRequest(StreamInput in) throws IOException {
        super(in);
        this.mlCreateSessionInput = new MLCreateSessionInput(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.mlCreateSessionInput.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        if (mlCreateSessionInput == null) {
            return addValidationError("Session input can't be null", null);
        }

        if (mlCreateSessionInput.getMemoryContainerId() == null || mlCreateSessionInput.getMemoryContainerId().isBlank()) {
            return addValidationError("Memory container ID is required", null);
        }

        return null;
    }

    public static MLCreateSessionRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLCreateSessionRequest) {
            return (MLCreateSessionRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLCreateSessionRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLCreateSessionRequest", e);
        }
    }
}
