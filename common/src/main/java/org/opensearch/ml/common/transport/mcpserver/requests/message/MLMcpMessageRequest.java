/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.message;

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
import org.opensearch.transport.TransportRequest;

import lombok.Builder;
import lombok.Getter;

@Getter
public class MLMcpMessageRequest extends ActionRequest {

    private final String nodeId;

    private final String sessionId;

    private final String requestBody;

    public MLMcpMessageRequest(StreamInput in) throws IOException {
        super(in);
        this.nodeId = in.readString();
        this.sessionId = in.readString();
        this.requestBody = in.readString();
    }

    @Builder
    public MLMcpMessageRequest(String nodeId, String sessionId, String requestBody) {
        super();
        this.nodeId = nodeId;
        this.sessionId = sessionId;
        this.requestBody = requestBody;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(nodeId);
        out.writeString(sessionId);
        out.writeString(requestBody);
    }

    public static MLMcpMessageRequest fromActionRequest(TransportRequest actionRequest) {
        if (actionRequest instanceof MLMcpMessageRequest) {
            return (MLMcpMessageRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMcpMessageRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLMcpMessageRequest", e);
        }
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}
