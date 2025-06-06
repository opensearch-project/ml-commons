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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * MLUpdatePromptRequest is the request class for MLUpdatePromptAction.
 * It contains MLUpdatePromptInput that is required to update prompt
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLUpdatePromptRequest extends ActionRequest {
    String promptId;
    MLUpdatePromptInput mlUpdatePromptInput;

    /**
     * Construct to pass values to the MLUpdatePromptRequest constructor
     *
     * @param mlUpdatePromptInput MLUpdatePromptInput that contains request body for create
     */
    @Builder
    public MLUpdatePromptRequest(String promptId, MLUpdatePromptInput mlUpdatePromptInput) {
        this.promptId = promptId;
        this.mlUpdatePromptInput = mlUpdatePromptInput;
    }

    /**
     * Construct MLUpdatePromptRequest from StreamInput
     *
     * @param in StreamInput
     * @throws IOException if an I/O exception occurred while reading the object from StreamInput
     */
    public MLUpdatePromptRequest(StreamInput in) throws IOException {
        super(in);
        this.promptId = in.readString();
        this.mlUpdatePromptInput = new MLUpdatePromptInput(in);
    }

    /**
     * Validate MLUpdatePromptRequest
     *
     * @return ActionRequestValidationException if validation fails, null otherwise
     */
    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (this.promptId == null) {
            exception = addValidationError("ML prompt id can't be null", exception);
        }
        if (this.mlUpdatePromptInput == null) {
            exception = addValidationError("Update Prompt Input can't be null", exception);
        }

        return exception;
    }

    /**
     * Write MLUpdatePromptRequest to StreamOutput
     *
     * @param out Stream Output
     * @throws IOException if an I/O exception occurred while writing to StreamOutput
     */
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.promptId);
        this.mlUpdatePromptInput.writeTo(out);
    }

    /**
     * Parse ActionRequest into MLUpdatePromptRequest
     *
     * @param actionRequest ActionRequest
     * @return MLUpdatePromptRequest
     */
    public static MLUpdatePromptRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLUpdatePromptRequest) {
            return (MLUpdatePromptRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUpdatePromptRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLUpdatePromptRequest", e);
        }
    }
}
