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
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class MLIndexInsightGetRequest extends ActionRequest {
    String indexName;
    MLIndexInsightType targetIndexInsight;
    String tenantId;

    public MLIndexInsightGetRequest(String indexName, MLIndexInsightType targetIndexInsight, String tenantId) {
        this.indexName = indexName;
        this.targetIndexInsight = targetIndexInsight;
        this.tenantId = tenantId;
    }

    public MLIndexInsightGetRequest(StreamInput in) throws IOException {
        super(in);
        this.indexName = in.readString();
        this.targetIndexInsight = MLIndexInsightType.fromString(in.readString());
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.indexName);
        out.writeString(this.targetIndexInsight.name());
        out.writeOptionalString(tenantId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.indexName == null) {
            exception = addValidationError("Index insight's target index can't be null", exception);
        }

        if (this.targetIndexInsight == null) {
            exception = addValidationError("Index insight's target type can't be null", exception);
        }

        return exception;
    }

    public static MLIndexInsightGetRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLIndexInsightGetRequest) {
            return (MLIndexInsightGetRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLIndexInsightGetRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLIndexInsightGetRequest", e);
        }

    }
}
