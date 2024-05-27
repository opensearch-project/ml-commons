/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.unit.TimeValue;
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
public class MLModelDeleteRequest extends ActionRequest {
    String modelId;
    Boolean retry;
    Integer maxRetry;
    TimeValue retryDelay;
    TimeValue retryTimeout;

    @Builder
    public MLModelDeleteRequest(String modelId, Boolean retry, Integer maxRetry, TimeValue retryDelay, TimeValue retryTimeout) {
        this.modelId = modelId;
        this.retry = retry;
        this.maxRetry = maxRetry;
        this.retryDelay = retryDelay;
        this.retryTimeout = retryTimeout;
    }

    public MLModelDeleteRequest(StreamInput input) throws IOException {
        super(input);
        this.modelId = input.readString();
        this.retry = input.readOptionalBoolean();
        this.maxRetry = input.readOptionalVInt();
        this.retryDelay = input.readOptionalTimeValue();
        this.retryTimeout = input.readOptionalTimeValue();
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);
        output.writeString(modelId);
        output.writeOptionalBoolean(retry);
        output.writeOptionalVInt(maxRetry);
        output.writeOptionalTimeValue(retryDelay);
        output.writeOptionalTimeValue(retryTimeout);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.modelId == null) {
            exception = addValidationError("ML model id can't be null", exception);
        }
        if (this.maxRetry != null && (this.maxRetry < 0 || this.maxRetry > 5)) {
            exception = addValidationError("Retry count should be greater than or equal to 0 and less than 5", exception);
        }

        if (this.retryDelay != null && (this.retryDelay.getMillis() < 0 || this.retryDelay.getMillis() > 30000)) {
            exception = addValidationError("Retry delay should be greater than or equal to 0 or less than 30000 milliseconds", exception);
        }

        if (this.retryTimeout != null && (this.retryTimeout.getMillis() < 0 || this.retryTimeout.getMillis() > 30000)) {
            exception = addValidationError("Retry delay should be greater than or equal to 0 or less than 30000 milliseconds", exception);
        }

        return exception;
    }

    public static MLModelDeleteRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLModelDeleteRequest) {
            return (MLModelDeleteRequest)actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLModelDeleteRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLModelDeleteRequest", e);
        }
    }
}
