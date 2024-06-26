/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.opensearch.action.ValidateActions.addValidationError;

@Getter
public class MLAgentDeleteRequest extends ActionRequest {

    String agentId;
    String tenantId;

    @Builder
    public MLAgentDeleteRequest(String agentId, String tenantId) {
        this.agentId = agentId;
        this.tenantId = tenantId;
    }

    public MLAgentDeleteRequest(StreamInput input) throws IOException {
        super(input);
        this.agentId = input.readString();
        //TODO: Will check BWC later
        this.tenantId = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);
        output.writeString(agentId);
        // TODO: will check BWC later.
        output.writeOptionalString(tenantId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.agentId == null) {
            exception = addValidationError("ML agent id can't be null", exception);
        }

        return exception;
    }

    public static MLAgentDeleteRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLAgentDeleteRequest) {
            return (MLAgentDeleteRequest)actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLAgentDeleteRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLAgentDeleteRequest", e);
        }
    }

}
