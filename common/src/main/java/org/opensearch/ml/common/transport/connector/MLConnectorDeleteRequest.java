/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.InputStreamStreamInput;
import org.opensearch.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.opensearch.action.ValidateActions.addValidationError;

public class MLConnectorDeleteRequest extends ActionRequest {
    @Getter
    String connectorId;

    @Builder
    public MLConnectorDeleteRequest(String connectorId) {
        this.connectorId = connectorId;
    }

    public MLConnectorDeleteRequest(StreamInput input) throws IOException {
        super(input);
        this.connectorId = input.readString();
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);
        output.writeString(connectorId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.connectorId == null) {
            exception = addValidationError("ML connector id can't be null", exception);
        }

        return exception;
    }

    public static MLConnectorDeleteRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLConnectorDeleteRequest) {
            return (MLConnectorDeleteRequest)actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLConnectorDeleteRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLConnectorDeleteRequest", e);
        }
    }

}
