/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.custom;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.InputStreamStreamInput;
import org.opensearch.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Getter
@ToString
public class MLForwardResponse extends ActionResponse implements ToXContentObject {

    String result;

    @Builder
    public MLForwardResponse(String result) {
        this.result = result;
    }


    public MLForwardResponse(StreamInput in) throws IOException {
        super(in);
        result = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException{
        out.writeString(result);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("result", result);
        builder.endObject();
        return builder;
    }

    public static MLForwardResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLForwardResponse) {
            return (MLForwardResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLForwardResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLModelGetResponse", e);
        }
    }
}
