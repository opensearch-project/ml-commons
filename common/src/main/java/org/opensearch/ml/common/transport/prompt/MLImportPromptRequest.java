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

@Getter
public class MLImportPromptRequest extends ActionRequest {
    private MLImportPromptInput mlImportPromptInput;

    @Builder
    public MLImportPromptRequest(MLImportPromptInput mlImportPromptInput) {
        this.mlImportPromptInput = mlImportPromptInput;
    }

    public MLImportPromptRequest(StreamInput in) throws IOException {
        super(in);
        this.mlImportPromptInput = new MLImportPromptInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlImportPromptInput == null) {
            exception = addValidationError("ML Prompt Input can't be null", exception);
        }

        return exception;
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);
        this.mlImportPromptInput.writeTo(output);
    }

    public static MLImportPromptRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLImportPromptRequest) {
            return (MLImportPromptRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLImportPromptRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLImportPromptRequest", e);
        }
    }
}
