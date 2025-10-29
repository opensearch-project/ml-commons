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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.memorycontainer.MemoryType;

import lombok.Builder;
import lombok.Getter;

public class MLMemoryContainerDeleteRequest extends ActionRequest {
    @Getter
    String memoryContainerId;

    @Getter
    boolean deleteAllMemories;

    @Getter
    Set<MemoryType> deleteMemories;

    @Getter
    String tenantId;

    @Builder
    public MLMemoryContainerDeleteRequest(
        String memoryContainerId,
        boolean deleteAllMemories,
        Set<MemoryType> deleteMemories,
        String tenantId
    ) {
        this.memoryContainerId = memoryContainerId;
        this.deleteAllMemories = deleteAllMemories;
        this.deleteMemories = deleteMemories;
        this.tenantId = tenantId;
    }

    public MLMemoryContainerDeleteRequest(StreamInput input) throws IOException {
        super(input);
        this.memoryContainerId = input.readString();
        this.deleteAllMemories = input.readBoolean();
        List<String> tempList = input.readOptionalStringList();
        this.deleteMemories = tempList != null
            ? tempList.stream().map(MemoryType::fromString).collect(Collectors.toCollection(LinkedHashSet::new))
            : null;
        this.tenantId = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);
        output.writeString(memoryContainerId);
        output.writeBoolean(deleteAllMemories);
        output
            .writeOptionalStringCollection(
                deleteMemories != null ? deleteMemories.stream().map(MemoryType::getValue).collect(Collectors.toList()) : null
            );
        output.writeOptionalString(tenantId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.memoryContainerId == null) {
            exception = addValidationError("ML memory container id can't be null", exception);
        }

        // Validate mutual exclusivity of deleteAllMemories and deleteMemories
        if (this.deleteAllMemories && this.deleteMemories != null && !this.deleteMemories.isEmpty()) {
            exception = addValidationError(
                "Cannot specify both delete_all_memories and delete_memories. Use either delete_all_memories=true OR delete_memories with specific types",
                exception
            );
        }

        return exception;
    }

    public static MLMemoryContainerDeleteRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLMemoryContainerDeleteRequest) {
            return (MLMemoryContainerDeleteRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMemoryContainerDeleteRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLMemoryContainerDeleteRequest", e);
        }
    }
}
