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
 * carries no target id. {@code tenantId} is captured from the request context by the REST layer for
 * transport-serialization symmetry with the other memory-container requests; the multi-tenancy
 * decision itself is made by the processor from the cluster setting (the scheduled job refuses to
 * run under multi-tenancy), so {@code tenantId} is not used for gating and {@code validate()} has no
 * required fields.
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLExecuteMemoryRetentionRequest extends ActionRequest {

    String tenantId;

    @Builder
    public MLExecuteMemoryRetentionRequest(String tenantId) {
        this.tenantId = tenantId;
    }

    public MLExecuteMemoryRetentionRequest(StreamInput in) throws IOException {
        super(in);
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(tenantId);
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
