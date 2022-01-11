/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import lombok.Builder;
import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.InputStreamStreamInput;
import org.opensearch.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLModel;
import org.opensearch.ml.common.parameter.MLOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

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
    public void writeTo(StreamOutput out) throws IOException{
        mlModel.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        return mlModel.toXContent(xContentBuilder, params);
    }
}
