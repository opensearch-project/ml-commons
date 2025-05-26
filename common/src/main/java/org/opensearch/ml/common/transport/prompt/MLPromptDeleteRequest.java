/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

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

/**
 * MLPromptDeleteRequest is the request class for MLPromptDeleteAction.
 * It contains prompt id and tenant id that is required delete a prompt
 */
@Getter
public class MLPromptDeleteRequest extends ActionRequest {
    private final String promptId;
    private final String tenantId;

    /**
     * Construct to pass values to the MLPromptDeleteRequest constructor
     *
     * @param promptId a prompt id of a prompt that needs to be retrieved
     * @param tenantId a tenant id of a prompt that needs to be retrieved
     */
    @Builder
    public MLPromptDeleteRequest(String promptId, String tenantId) {
        this.promptId = promptId;
        this.tenantId = tenantId;
    }

    /**
     * Constructor to parse StreamInput to MLPromptDeleteRequest
     *
     * @param input StreamInput
     * @throws IOException if an I/O exception occurred while reading from the StreamInput
     */
    public MLPromptDeleteRequest(StreamInput input) throws IOException {
        super(input);
        this.promptId = input.readString();
        this.tenantId = input.readOptionalString();
    }

    /**
     * Write MLPromptDeleteRequest to StreamOutput
     *
     * @param output Stream Output
     * @throws IOException if an I/O exception occurred while writing to StreamOutput
     */
    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);
        output.writeString(this.promptId);
        output.writeOptionalString(this.tenantId);
    }

    /**
     * Validate MLPromptDeleteRequest
     *
     * @return ActionRequestValidationException if validation fails, null otherwise
     */
    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.promptId == null) {
            exception = addValidationError("ML prompt id can't be null", exception);
        }

        return exception;
    }

    /**
     * Parse ActionRequest into MLPromptDeleteRequest
     *
     * @param actionRequest ActionRequest
     * @return MLPromptDeleteRequest
     */
    public static MLPromptDeleteRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLPromptDeleteRequest) {
            return (MLPromptDeleteRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLPromptDeleteRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLPromptDeleteRequest", e);
        }
    }
}
