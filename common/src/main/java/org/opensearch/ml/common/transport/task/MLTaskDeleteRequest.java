/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.task;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.Version;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Builder;
import lombok.Getter;

public class MLTaskDeleteRequest extends ActionRequest {
    @Getter
    String taskId;
    @Getter
    String tenantId;

    @Builder
    public MLTaskDeleteRequest(String taskId, String tenantId) {
        this.tenantId = tenantId;
        this.taskId = taskId;
    }

    public MLTaskDeleteRequest(StreamInput input) throws IOException {
        super(input);
        Version streamInputVersion = input.getVersion();
        this.taskId = input.readString();
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? input.readOptionalString() : null;
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);
        Version streamOutputVersion = output.getVersion();
        output.writeString(taskId);
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            output.writeOptionalString(tenantId);
        }
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.taskId == null) {
            exception = addValidationError("ML task id can't be null", exception);
        }

        return exception;
    }

    public static MLTaskDeleteRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLTaskDeleteRequest) {
            return (MLTaskDeleteRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLTaskDeleteRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLTaskDeleteRequest", e);
        }
    }
}
