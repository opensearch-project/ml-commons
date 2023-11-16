/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

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
public class MLModelGetRequest extends ActionRequest {

    String modelId;
    boolean returnContent;
    boolean isDirectRequest;

    @Builder
    public MLModelGetRequest(String modelId, boolean returnContent, boolean isDirectRequest) {
        this.modelId = modelId;
        this.returnContent = returnContent;
        this.isDirectRequest = isDirectRequest;
    }

    public MLModelGetRequest(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
        this.returnContent = in.readBoolean();
        this.isDirectRequest = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.modelId);
        out.writeBoolean(returnContent);
        out.writeBoolean(isDirectRequest);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.modelId == null) {
            exception = addValidationError("ML model id can't be null", exception);
        }

        return exception;
    }

    public static MLModelGetRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLModelGetRequest) {
            return (MLModelGetRequest)actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLModelGetRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLModelGetRequest", e);
        }
    }
}
