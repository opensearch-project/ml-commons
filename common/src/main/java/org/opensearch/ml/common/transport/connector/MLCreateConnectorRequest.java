/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

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
public class MLCreateConnectorRequest extends ActionRequest {
    private MLCreateConnectorInput mlCreateConnectorInput;

    @Builder
    public MLCreateConnectorRequest(MLCreateConnectorInput mlCreateConnectorInput) {
        this.mlCreateConnectorInput = mlCreateConnectorInput;
    }

    public MLCreateConnectorRequest(StreamInput in) throws IOException {
        super(in);
        this.mlCreateConnectorInput = new MLCreateConnectorInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlCreateConnectorInput == null) {
            exception = addValidationError("ML Connector input can't be null", exception);
        }

        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.mlCreateConnectorInput.writeTo(out);
    }

    public static MLCreateConnectorRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLCreateConnectorRequest) {
            return (MLCreateConnectorRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLCreateConnectorRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLCreateConnectorRequest", e);
        }
    }
}
