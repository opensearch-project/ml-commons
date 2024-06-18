/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.task;


import lombok.Builder;
import lombok.Getter;
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

public class MLTaskGetRequest extends ActionRequest {
    @Getter
    String taskId;
    @Getter
    String tenantId;

    @Builder
    public MLTaskGetRequest(String taskId, String tenantId) {
        this.taskId = taskId;
        this.tenantId = tenantId;
    }

    public MLTaskGetRequest(StreamInput in) throws IOException {
        super(in);
        this.taskId = in.readString();
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.taskId);
        out.writeOptionalString(this.tenantId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.taskId == null) {
            exception = addValidationError("ML task id can't be null", exception);
        }

        return exception;
    }

    public static MLTaskGetRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLTaskGetRequest) {
            return (MLTaskGetRequest)actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLTaskGetRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLTaskGetRequest", e);
        }
    }
}
