/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.indexInsight;

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

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class MLIndexInsightContainerCreateRequest extends ActionRequest {
    private String containerName;
    private String tenantId;

    public MLIndexInsightContainerCreateRequest(String containerName, String tenantId) {
        this.containerName = containerName;
        this.tenantId = tenantId;
    }

    public MLIndexInsightContainerCreateRequest(StreamInput in) throws IOException {
        super(in);
        this.containerName = in.readString();
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.containerName);
        out.writeOptionalString(tenantId);
    }

    public static MLIndexInsightContainerCreateRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLIndexInsightContainerCreateRequest) {
            return (MLIndexInsightContainerCreateRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLIndexInsightContainerCreateRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLIndexInsightContainerCreateRequest", e);
        }

    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.containerName == null) {
            exception = addValidationError("Index Insight's container index can't be null", exception);
        }

        return exception;
    }
}
