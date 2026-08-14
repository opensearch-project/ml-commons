/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

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

/**
 * Request to trigger the memory retention job on demand.
 *
 * <p>The retention pipeline is a singleton, cluster-wide operation that iterates every container.
 * There is no per-container scoping today (see MLExecuteMemoryRetentionAction), so this request
 * carries no target id and no fields. It always runs cluster-wide; under multi-tenancy the run is
 * tenant-isolated by the processor via per-container {@code memory_container_id} filters, and it
 * skips only when a remote metadata store is configured. {@code validate()} has no required fields.
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLExecuteMemoryRetentionRequest extends ActionRequest {

    @Builder
    public MLExecuteMemoryRetentionRequest() {}

    public MLExecuteMemoryRetentionRequest(StreamInput in) throws IOException {
        super(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    public static MLExecuteMemoryRetentionRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLExecuteMemoryRetentionRequest) {
            return (MLExecuteMemoryRetentionRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLExecuteMemoryRetentionRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLExecuteMemoryRetentionRequest", e);
        }
    }
}
