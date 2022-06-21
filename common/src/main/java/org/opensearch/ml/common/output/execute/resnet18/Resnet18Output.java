/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.resnet18;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.ExecuteOutput;
import org.opensearch.ml.common.output.Output;

import java.io.IOException;

@ExecuteOutput(algorithms={FunctionName.RESNET18})
@Data
public class Resnet18Output implements Output {

    public static final String CLASSIFICATIONS_FIELD = "objects";
    private String classifications;

    @Builder
    public Resnet18Output(final String classifications) {
        this.classifications = classifications;
    }

    public Resnet18Output(StreamInput in) throws IOException {
        classifications = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(classifications);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (classifications != null) {
            builder.field(CLASSIFICATIONS_FIELD, classifications);
        }
        return builder;
    }
}
