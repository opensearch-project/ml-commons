/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Builder;
import lombok.Getter;

/**
 * ML execute agent request.
 */
@Getter
public class MLExecuteAgentRequest extends ActionRequest {
    private String agentId;
    private String method;
    private Map<String, String> parameters;

    @Builder
    public MLExecuteAgentRequest(String agentId, String method, Map<String, String> parameters) {
        this.agentId = agentId;
        this.method = method;
        this.parameters = parameters;
    }

    public MLExecuteAgentRequest(StreamInput in) throws IOException {
        super(in);
        this.agentId = in.readString();
        this.method = in.readString();
        this.parameters = in.readMap(StreamInput::readString, StreamInput::readString);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.agentId);
        out.writeString(this.method);
        out.writeMap(this.parameters, StreamOutput::writeString, StreamOutput::writeString);
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    public static MLExecuteAgentRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLExecuteAgentRequest) {
            return (MLExecuteAgentRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLExecuteAgentRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLExecuteAgentRequest", e);
        }
    }
}
