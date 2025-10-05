/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEM_CONTAINER_MEMORY_TYPE_HISTORY;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEM_CONTAINER_MEMORY_TYPE_LONG_TERM;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEM_CONTAINER_MEMORY_TYPE_SESSIONS;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEM_CONTAINER_MEMORY_TYPE_WORKING;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.Builder;
import lombok.Getter;

public class MLMemoryContainerDeleteRequest extends ActionRequest {
    @Getter
    String memoryContainerId;

    @Getter
    boolean deleteAllMemories;

    @Getter
    Set<String> deleteMemories;

    @Getter
    String tenantId;

    @Builder
    public MLMemoryContainerDeleteRequest(
        String memoryContainerId,
        boolean deleteAllMemories,
        Set<String> deleteMemories,
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
        this.deleteMemories = tempList != null ? new LinkedHashSet<>(tempList) : null;
        this.tenantId = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        super.writeTo(output);
        output.writeString(memoryContainerId);
        output.writeBoolean(deleteAllMemories);
        output.writeOptionalStringCollection(deleteMemories);
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

        // Validate memory types
        if (this.deleteMemories != null && !this.deleteMemories.isEmpty()) {
            List<String> validMemoryTypes = Arrays
                .asList(
                    MEM_CONTAINER_MEMORY_TYPE_SESSIONS,
                    MEM_CONTAINER_MEMORY_TYPE_WORKING,
                    MEM_CONTAINER_MEMORY_TYPE_LONG_TERM,
                    MEM_CONTAINER_MEMORY_TYPE_HISTORY
                );
            for (String memoryType : this.deleteMemories) {
                if (!validMemoryTypes.contains(memoryType)) {
                    exception = addValidationError(
                        "Invalid memory type: "
                            + memoryType
                            + ". Must be one of: "
                            + MEM_CONTAINER_MEMORY_TYPE_SESSIONS
                            + ", "
                            + MEM_CONTAINER_MEMORY_TYPE_WORKING
                            + ", "
                            + MEM_CONTAINER_MEMORY_TYPE_LONG_TERM
                            + ", "
                            + MEM_CONTAINER_MEMORY_TYPE_HISTORY,
                        exception
                    );
                }
            }
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
