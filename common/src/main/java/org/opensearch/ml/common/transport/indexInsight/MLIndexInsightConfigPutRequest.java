/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.indexInsight;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.ml.common.CommonValue.VERSION_3_11_0;

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

@Getter
public class MLIndexInsightConfigPutRequest extends ActionRequest {
    private Boolean isEnable;
    private String tenantId;
    private String agentId;

    public MLIndexInsightConfigPutRequest(Boolean isEnable, String tenantId) {
        this(isEnable, tenantId, null);
    }

    @Builder
    public MLIndexInsightConfigPutRequest(Boolean isEnable, String tenantId, String agentId) {
        this.isEnable = isEnable;
        this.tenantId = tenantId;
        this.agentId = agentId;
    }

    public MLIndexInsightConfigPutRequest(StreamInput in) throws IOException {
        super(in);
        this.isEnable = in.readBoolean();
        this.tenantId = in.readOptionalString();
        this.agentId = in.getVersion().onOrAfter(VERSION_3_11_0) ? in.readOptionalString() : null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeBoolean(this.isEnable);
        out.writeOptionalString(tenantId);
        if (out.getVersion().onOrAfter(VERSION_3_11_0)) {
            out.writeOptionalString(agentId);
        }
    }

    public static MLIndexInsightConfigPutRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLIndexInsightConfigPutRequest) {
            return (MLIndexInsightConfigPutRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLIndexInsightConfigPutRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLIndexInsightConfigPutRequest", e);
        }

    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.isEnable == null) {
            exception = addValidationError("Index Insight config's isEnable can't be null", exception);
        }
        if (this.agentId != null && this.agentId.isBlank()) {
            exception = addValidationError("Index Insight config's agent_id can't be blank", exception);
        }

        return exception;
    }
}
