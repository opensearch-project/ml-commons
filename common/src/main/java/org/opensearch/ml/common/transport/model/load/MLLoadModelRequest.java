/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.load;

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
import org.opensearch.ml.common.transport.MLTaskRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.opensearch.action.ValidateActions.addValidationError;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLLoadModelRequest extends MLTaskRequest {

    private String modelId;
    boolean async;

    @Builder
    public MLLoadModelRequest(String modelId, boolean async, boolean dispatchTask) {
        super(dispatchTask);
        this.modelId = modelId;
        this.async = async;
    }

    public MLLoadModelRequest(String modelId, boolean async) {
        this(modelId, async, true);
    }

    public MLLoadModelRequest(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
        this.async = in.readBoolean();
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (modelId == null) {
            exception = addValidationError("ML model id can't be null", exception);
        }

        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(modelId);
        out.writeBoolean(async);
    }

    public static MLLoadModelRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLLoadModelRequest) {
            return (MLLoadModelRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLLoadModelRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLTrainingTaskRequest", e);
        }

    }
}
