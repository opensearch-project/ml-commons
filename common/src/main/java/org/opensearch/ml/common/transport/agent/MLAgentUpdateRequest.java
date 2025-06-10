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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLAgentUpdateRequest extends ActionRequest {

    MLAgentUpdateInput mlAgentUpdateInput;

    @Builder
    public MLAgentUpdateRequest(MLAgentUpdateInput mlAgentUpdateInput) {
        this.mlAgentUpdateInput = mlAgentUpdateInput;
    }

    public MLAgentUpdateRequest(StreamInput in) throws IOException {
        super(in);
        this.mlAgentUpdateInput = new MLAgentUpdateInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlAgentUpdateInput == null) {
            exception = addValidationError("ML Agent Update Input cannot be null", exception);
        }
        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.mlAgentUpdateInput.writeTo(out);
    }

    public static MLAgentUpdateRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLAgentUpdateRequest) {
            return (MLAgentUpdateRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLAgentUpdateRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLAgentUpdateRequest", e);
        }
    }
}
