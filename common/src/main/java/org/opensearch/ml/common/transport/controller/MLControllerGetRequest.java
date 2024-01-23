/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.controller;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.opensearch.action.ValidateActions.addValidationError;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLControllerGetRequest extends ActionRequest {

    String modelId;
    boolean returnContent;

    @Builder
    public MLControllerGetRequest(String modelId, boolean returnContent) {
        this.modelId = modelId;
        this.returnContent = returnContent;
    }

    public MLControllerGetRequest(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
        this.returnContent = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.modelId);
        out.writeBoolean(returnContent);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.modelId == null) {
            exception = addValidationError("ML model id can't be null", exception);
        }

        return exception;
    }

    public static MLControllerGetRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLControllerGetRequest) {
            return (MLControllerGetRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLControllerGetRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLControllerGetRequest", e);
        }
    }
}
