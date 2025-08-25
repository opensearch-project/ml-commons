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
public class MLIndexInsightConfigPutRequest extends ActionRequest {
    private Boolean isEnable;
    private String tenantId;

    public MLIndexInsightConfigPutRequest(Boolean isEnable, String tenantId) {
        this.isEnable = isEnable;
        this.tenantId = tenantId;
    }

    public MLIndexInsightConfigPutRequest(StreamInput in) throws IOException {
        super(in);
        this.isEnable = in.readBoolean();
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeBoolean(this.isEnable);
        out.writeOptionalString(tenantId);
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

        return exception;
    }
}
