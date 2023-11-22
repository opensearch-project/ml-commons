/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLModel;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

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
        mlModel = mlModel.fromStream(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        mlModel.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, ToXContent.Params params) throws IOException {
        return mlModel.toXContent(xContentBuilder, params);
    }

    public static MLModelGetResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLModelGetResponse) {
            return (MLModelGetResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLModelGetResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLModelGetResponse", e);
        }
    }
}
