/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

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

@Getter
public class MLConnectorDeleteRequest extends ActionRequest {
    private final String connectorId;
    private final String tenantId;

    @Builder
    public MLConnectorDeleteRequest(String connectorId, String tenantId) {
        this.connectorId = connectorId;
        this.tenantId = tenantId;
    }

    public MLConnectorDeleteRequest(String connectorId) {
        this.connectorId = connectorId;
        this.tenantId = null;
    }

    public MLConnectorDeleteRequest(StreamInput input) throws IOException {
        super(input);
        Version streamInputVersion = input.getVersion();
        this.connectorId = input.readString();
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? input.readOptionalString() : null;
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        Version streamOutputVersion = output.getVersion();
        super.writeTo(output);
        output.writeString(connectorId);
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            output.writeOptionalString(tenantId);
        }
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
            return (MLConnectorDeleteRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLConnectorDeleteRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLConnectorDeleteRequest", e);
        }
    }

}
