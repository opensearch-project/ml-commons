/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.list;

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
public class MLMcpConnectorListToolsRequest extends ActionRequest {

    private final String connectorId;
    private final String tenantId;

    @Builder
    public MLMcpConnectorListToolsRequest(String connectorId, String tenantId) {
        this.connectorId = connectorId;
        this.tenantId = tenantId;
    }

    public MLMcpConnectorListToolsRequest(StreamInput in) throws IOException {
        super(in);
        this.connectorId = in.readString();
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(connectorId);
        out.writeOptionalString(tenantId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (connectorId == null || connectorId.isEmpty()) {
            exception = addValidationError("connector_id cannot be null or empty", exception);
        }
        return exception;
    }

    public static MLMcpConnectorListToolsRequest fromActionRequest(ActionRequest request) {
        if (request instanceof MLMcpConnectorListToolsRequest) {
            return (MLMcpConnectorListToolsRequest) request;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            request.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMcpConnectorListToolsRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLMcpConnectorListToolsRequest", e);
        }
    }
}
