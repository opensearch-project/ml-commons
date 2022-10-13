/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.upload_chunk;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.InputStreamStreamInput;
import org.opensearch.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.opensearch.action.ValidateActions.addValidationError;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLUploadModelChunkRequest extends ActionRequest {

    /**
     * the name of algorithm
     */
    MLUploadChunkInput mlUploadInput;

    @Builder
    public MLUploadModelChunkRequest(MLUploadChunkInput mlUploadInput) {
        this.mlUploadInput = mlUploadInput;
    }

    public MLUploadModelChunkRequest(StreamInput in) throws IOException {
        super(in);
        this.mlUploadInput = new MLUploadChunkInput(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (mlUploadInput == null) {
            exception = addValidationError("ML input can't be null", exception);
        }

        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.mlUploadInput.writeTo(out);
    }

    public static MLUploadModelChunkRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLUploadModelChunkRequest) {
            return (MLUploadModelChunkRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUploadModelChunkRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLTrainingTaskRequest", e);
        }

    }
}