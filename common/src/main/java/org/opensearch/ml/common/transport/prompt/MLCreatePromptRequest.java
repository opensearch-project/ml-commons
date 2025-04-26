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
public class MLCreatePromptRequest extends ActionRequest {
    private MLCreatePromptInput mlCreatePromptInput;

    @Builder
    public MLCreatePromptRequest(MLCreatePromptInput mlCreatePromptInput) {
        this.mlCreatePromptInput = mlCreatePromptInput;
    }

    public MLCreatePromptRequest(StreamInput in) throws IOException {
        super(in);
        this.mlCreatePromptInput = new MLCreatePromptInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlCreatePromptInput == null) {
            exception = addValidationError("ML Prompt input can't be null", exception);
        }

        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.mlCreatePromptInput.writeTo(out);
    }

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
