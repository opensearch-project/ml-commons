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
 * MLCreatePromptRequest is the request class for MLCreatePromptAction.
 * It contains MLCreatePromptInput that is required to create a prompt.
 */
@Getter
public class MLCreatePromptRequest extends ActionRequest {
    private MLCreatePromptInput mlCreatePromptInput;

    /**
     * Construct to pass values to the MLCreatePromptRequest constructor
     *
     * @param mlCreatePromptInput MLCreatePromptInput that contains request body for create
     */
    @Builder
    public MLCreatePromptRequest(MLCreatePromptInput mlCreatePromptInput) {
        this.mlCreatePromptInput = mlCreatePromptInput;
    }

    /**
     * Construct MLCreatePromptRequest from StreamInput
     *
     * @param in StreamInput
     * @throws IOException if an I/O exception occurred while reading the object from StreamInput
     */
    public MLCreatePromptRequest(StreamInput in) throws IOException {
        super(in);
        this.mlCreatePromptInput = new MLCreatePromptInput(in);
    }

    /**
     * Validate MLCreatePromptRequest
     *
     * @return ActionRequestValidationException if validation fails, null otherwise
     */
    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlCreatePromptInput == null) {
            exception = addValidationError("ML Prompt Input can't be null", exception);
        }

        return exception;
    }

    /**
     * Write MLCreatePromptRequest to StreamOutput
     *
     * @param out Stream Output
     * @throws IOException if an I/O exception occurred while writing to StreamOutput
     */
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.mlCreatePromptInput.writeTo(out);
    }

    /**
     * Parse ActionRequest into MLCreatePromptRequest
     *
     * @param actionRequest ActionRequest
     * @return MLCreatePromptRequest
     */
    public static MLCreatePromptRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLCreatePromptRequest) {
            return (MLCreatePromptRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLCreatePromptRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLCreatePromptRequest", e);
        }
    }
}
