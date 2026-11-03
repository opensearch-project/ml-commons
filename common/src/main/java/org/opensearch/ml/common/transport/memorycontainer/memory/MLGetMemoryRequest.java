/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

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
import org.opensearch.ml.common.memorycontainer.MemoryType;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLGetMemoryRequest extends ActionRequest {

    String memoryContainerId;
    MemoryType memoryType;
    String memoryId;
    String tenantId;

    @Builder
    public MLGetMemoryRequest(String memoryContainerId, MemoryType memoryType, String memoryId, String tenantId) {
        this.memoryContainerId = memoryContainerId;
        this.memoryType = memoryType;
        this.memoryId = memoryId;
        this.tenantId = tenantId;
    }

    public MLGetMemoryRequest(StreamInput in) throws IOException {
        super(in);
        this.memoryContainerId = in.readString();
        this.memoryType = in.readEnum(MemoryType.class);
        this.memoryId = in.readString();
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.memoryContainerId);
        out.writeEnum(this.memoryType);
        out.writeString(this.memoryId);
        out.writeOptionalString(this.tenantId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.memoryContainerId == null || this.memoryId == null) {
            exception = addValidationError("memoryContainerId and memoryId id can not be null", exception);
        }
        if (this.memoryType == null) {
            exception = addValidationError("Memory type can not be null", exception);
        }

        return exception;
    }

    public static MLGetMemoryRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLGetMemoryRequest) {
            return (MLGetMemoryRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLGetMemoryRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLMemoryGetRequest", e);
        }
    }
}
