/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

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
import org.opensearch.ml.common.MLModel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Getter
@ToString
public class MLModelGetResponse extends ActionResponse implements ToXContentObject {

    MLModel mlModel;

    @Builder
    public MLModelGetResponse(MLModel mlModel) {
        this.mlModel = mlModel;
    }


    public MLModelGetResponse(StreamInput in) throws IOException {
        super(in);
        mlModel = MLModel.fromStream(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException{
        mlModel.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return mlModel.toXContent(xContentBuilder, params);
    }

    public static MLModelGetResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLModelGetResponse) {
            return (MLModelGetResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLModelGetResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLModelGetResponse", e);
        }
    }
}
