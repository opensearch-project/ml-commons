/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.indexInsight;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;

import lombok.Getter;

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
    public ActionRequestValidationException validate() {
        return null;
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
