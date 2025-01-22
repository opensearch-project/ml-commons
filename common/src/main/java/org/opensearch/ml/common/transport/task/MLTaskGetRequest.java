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

public class MLTaskGetRequest extends ActionRequest {
    @Getter
    String taskId;

    @Getter
    String tenantId;

    // This is to identify if the get request is initiated by user or not. During batch task polling job,
    // we also perform get operation. This field is to distinguish between
    // these two situations.
    @Getter
    boolean isUserInitiatedGetTaskRequest;

    @Builder
    public MLTaskGetRequest(String taskId, String tenantId) {
        this(taskId, tenantId, true);
    }

    @Builder
    public MLTaskGetRequest(String taskId, String tenantId, Boolean isUserInitiatedGetTaskRequest) {
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.isUserInitiatedGetTaskRequest = isUserInitiatedGetTaskRequest;
    }

    public MLTaskGetRequest(StreamInput in) throws IOException {
        super(in);
        Version streamInputVersion = in.getVersion();
        this.taskId = in.readString();
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? in.readOptionalString() : null;
        this.isUserInitiatedGetTaskRequest = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        Version streamOutputVersion = out.getVersion();
        out.writeString(this.taskId);
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
        out.writeBoolean(isUserInitiatedGetTaskRequest);
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
            return (MLTaskGetRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLTaskGetRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLTaskGetRequest", e);
        }
    }
}
