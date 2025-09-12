/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.server;

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

import lombok.Getter;

public class MLMcpServerRequest extends ActionRequest {
    @Getter
    private String requestBody;

    public MLMcpServerRequest(StreamInput in) throws IOException {
        super(in);
        this.requestBody = in.readString();
    }

    public MLMcpServerRequest(String requestBody) {
        this.requestBody = requestBody;
    }

    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(requestBody);
    }

    public static MLMcpServerRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLMcpServerRequest) {
            return (MLMcpServerRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput in = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMcpServerRequest(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLMcpServerRequest", e);
        }
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
