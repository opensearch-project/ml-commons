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
 * Request to preview what the scheduled retention job would delete.
 *
 * <p>When {@code memoryContainerId} is set, the dry-run targets a single container. When it is
 * {@code null}, the dry-run runs cluster-wide across every container (the response is an array).
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLMemoryRetentionDryRunRequest extends ActionRequest {

    String memoryContainerId;
    String tenantId;

    @Builder
    public MLMemoryRetentionDryRunRequest(String memoryContainerId, String tenantId) {
        this.memoryContainerId = memoryContainerId;
        this.tenantId = tenantId;
    }

    public MLMemoryRetentionDryRunRequest(StreamInput in) throws IOException {
        super(in);
        this.memoryContainerId = in.readOptionalString();
        this.tenantId = in.readOptionalString();
    }

    /** True when this dry-run targets every container in the cluster rather than a single one. */
    public boolean isClusterWide() {
        return memoryContainerId == null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(memoryContainerId);
        out.writeOptionalString(tenantId);
    }

    @Override
    public ActionRequestValidationException validate() {
        // memoryContainerId is intentionally optional: null means cluster-wide.
        return null;
    }

    public static MLMemoryRetentionDryRunRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLMemoryRetentionDryRunRequest) {
            return (MLMemoryRetentionDryRunRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLMemoryRetentionDryRunRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLMemoryRetentionDryRunRequest", e);
        }
    }
}
