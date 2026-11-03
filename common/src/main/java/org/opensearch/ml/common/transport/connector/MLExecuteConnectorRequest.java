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
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.MLTaskRequest;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString
public class MLExecuteConnectorRequest extends MLTaskRequest {

    String connectorId;
    MLInput mlInput;

    @Builder
    public MLExecuteConnectorRequest(String connectorId, MLInput mlInput, boolean dispatchTask) {
        super(dispatchTask);
        this.mlInput = mlInput;
        this.connectorId = connectorId;
    }

    public MLExecuteConnectorRequest(String connectorId, MLInput mlInput) {
        this(connectorId, mlInput, true);
    }

    public MLExecuteConnectorRequest(StreamInput in) throws IOException {
        super(in);
        this.connectorId = in.readString();
        this.mlInput = new MLInput(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.connectorId);
        this.mlInput.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (this.mlInput == null) {
            exception = addValidationError("ML input can't be null", exception);
        } else if (this.mlInput.getInputDataset() == null) {
            exception = addValidationError("input data can't be null", exception);
        }
        if (this.connectorId == null) {
            exception = addValidationError("connectorId can't be null", exception);
        }
        return exception;
    }

    public static MLExecuteConnectorRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLExecuteConnectorRequest) {
            return (MLExecuteConnectorRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLExecuteConnectorRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLExecuteConnectorRequest", e);
        }
    }
}
