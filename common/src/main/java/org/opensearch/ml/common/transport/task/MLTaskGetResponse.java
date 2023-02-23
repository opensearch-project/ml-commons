/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.task;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.InputStreamStreamInput;
import org.opensearch.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Getter
public class MLTaskGetResponse extends ActionResponse implements ToXContentObject {
    MLTask mlTask;

    @Builder
    public MLTaskGetResponse(MLTask mlTask) {
        this.mlTask = mlTask;
    }

    public MLTaskGetResponse(StreamInput in) throws IOException {
        super(in);
        mlTask = mlTask.fromStream(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException{
        mlTask.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return mlTask.toXContent(xContentBuilder, params);
    }

    public static MLTaskGetResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLTaskGetResponse) {
            return (MLTaskGetResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLTaskGetResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLTaskGetResponse", e);
        }
    }

}
