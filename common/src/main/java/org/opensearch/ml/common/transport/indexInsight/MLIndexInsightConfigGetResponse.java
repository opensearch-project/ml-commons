/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.indexInsight;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.indexInsight.IndexInsightConfig;

import lombok.Builder;
import lombok.Getter;

public class MLIndexInsightConfigGetResponse extends ActionResponse implements ToXContentObject {
    @Getter
    private IndexInsightConfig indexInsightConfig;

    @Builder
    public MLIndexInsightConfigGetResponse(IndexInsightConfig indexInsightConfig) {
        this.indexInsightConfig = indexInsightConfig;
    }

    public MLIndexInsightConfigGetResponse(StreamInput in) throws IOException {
        super(in);
        this.indexInsightConfig = IndexInsightConfig.fromStream(in);
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        indexInsightConfig.writeTo(streamOutput);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return indexInsightConfig.toXContent(xContentBuilder, params);
    }

    public static MLIndexInsightConfigGetResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLIndexInsightConfigGetResponse) {
            return (MLIndexInsightConfigGetResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLIndexInsightConfigGetResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLIndexInsightConfigGetResponse", e);
        }
    }
}
