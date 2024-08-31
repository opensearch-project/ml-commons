/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.task;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Builder;
import lombok.Getter;

@Getter
public class MLCancelBatchJobResponse extends ActionResponse implements ToXContentObject {

    RestStatus status;

    @Builder
    public MLCancelBatchJobResponse(RestStatus status) {
        this.status = status;
    }

    public MLCancelBatchJobResponse(StreamInput in) throws IOException {
        super(in);
        status = in.readEnum(RestStatus.class);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(status);
    }

    public static MLCancelBatchJobResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLCancelBatchJobResponse) {
            return (MLCancelBatchJobResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLCancelBatchJobResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLTaskGetResponse", e);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return xContentBuilder.startObject().field("status", status).endObject();
    }
}
