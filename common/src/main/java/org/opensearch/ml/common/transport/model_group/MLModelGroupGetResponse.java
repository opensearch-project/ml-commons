/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLModelGroup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Getter
@ToString
public class MLModelGroupGetResponse extends ActionResponse implements ToXContentObject {

    MLModelGroup mlModelGroup;

    @Builder
    public MLModelGroupGetResponse(MLModelGroup mlModelGroup) {
        this.mlModelGroup = mlModelGroup;
    }


    public MLModelGroupGetResponse(StreamInput in) throws IOException {
        super(in);
        mlModelGroup = mlModelGroup.fromStream(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException{
        mlModelGroup.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return mlModelGroup.toXContent(xContentBuilder, params);
    }

    public static MLModelGroupGetResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLModelGroupGetResponse) {
            return (MLModelGroupGetResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLModelGroupGetResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLModelGroupGetResponse", e);
        }
    }
}
