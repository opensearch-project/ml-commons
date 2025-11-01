/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

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
import org.opensearch.ml.common.agent.MLAgent;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLRegisterAgentRequest extends ActionRequest {

    MLAgent mlAgent;

    @Builder
    public MLRegisterAgentRequest(MLAgent mlAgent) {
        this.mlAgent = mlAgent;
    }

    public MLRegisterAgentRequest(StreamInput in) throws IOException {
        super(in);
        this.mlAgent = new MLAgent(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlAgent == null) {
            exception = addValidationError("ML agent can't be null", exception);
        } else {
            // Basic validation - check for conflicting configuration (following connector pattern)
            if (mlAgent.getContextManagementName() != null && mlAgent.getContextManagement() != null) {
                exception = addValidationError("Cannot specify both context_management_name and context_management", exception);
            }
        }

        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.mlAgent.writeTo(out);
    }

    public static MLRegisterAgentRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLRegisterAgentRequest) {
            return (MLRegisterAgentRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLRegisterAgentRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLRegisterAgentRequest", e);
        }

    }
}
