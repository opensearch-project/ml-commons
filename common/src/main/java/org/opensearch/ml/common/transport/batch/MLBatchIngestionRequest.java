package org.opensearch.ml.common.transport.batch;

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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLBatchIngestionRequest extends ActionRequest {

    private MLBatchIngestionInput mlBatchIngestionInput;

    @Builder
    public MLBatchIngestionRequest(MLBatchIngestionInput mlBatchIngestionInput) {
        this.mlBatchIngestionInput = mlBatchIngestionInput;
    }

    public MLBatchIngestionRequest(StreamInput in) throws IOException {
        super(in);
        this.mlBatchIngestionInput = new MLBatchIngestionInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlBatchIngestionInput == null) {
            exception = addValidationError("ML batch ingestion input can't be null", exception);
        }
        if (mlBatchIngestionInput.getCredential() == null) {
            exception = addValidationError("ML batch ingestion credentials can't be null", exception);
        }
        if (mlBatchIngestionInput.getDataSources() == null) {
            exception = addValidationError("ML batch ingestion data sources can't be null", exception);
        }

        return exception;
    }

    public static MLBatchIngestionRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLBatchIngestionRequest) {
            return (MLBatchIngestionRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLBatchIngestionRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLBatchIngestionRequest", e);
        }

    }
}
