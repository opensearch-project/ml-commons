/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLMemoryContainerGetRequest extends ActionRequest {

    String memoryContainerId;
    String tenantId;

    @Builder
    public MLMemoryContainerGetRequest(String memoryContainerId, String tenantId) {
        this.memoryContainerId = memoryContainerId;
        this.tenantId = tenantId;
    }

    public MLMemoryContainerGetRequest(StreamInput in) throws IOException {
        super(in);
        this.memoryContainerId = in.readString();
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.memoryContainerId);
        out.writeOptionalString(tenantId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.memoryContainerId == null) {
            exception = addValidationError("Memory container id can't be null", exception);
        }

        return exception;
    }

    public static MLMemoryContainerGetRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLMemoryContainerGetRequest) {
            return (MLMemoryContainerGetRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMemoryContainerGetRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLMemoryContainerGetRequest", e);
        }
    }
}
