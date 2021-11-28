/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.common.parameter;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.IOException;

@Getter
public class MLTrainingOutput extends MLOutput{

    private static final MLOutputType OUTPUT_TYPE = MLOutputType.TRAINING;
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String STATUS_FIELD = "status";
    private String modelId;
    private String status;

    @Builder
    public MLTrainingOutput(String modelId, String status) {
        super(OUTPUT_TYPE);
        this.modelId = modelId;
        this.status= status;
    }

    public MLTrainingOutput(StreamInput in) throws IOException {
        super(OUTPUT_TYPE);
        this.modelId = in.readOptionalString();
        this.status = in.readOptionalString();
    }

    @Override
    public MLOutputType getType() {
        return OUTPUT_TYPE;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(modelId);
        out.writeOptionalString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_ID_FIELD, modelId);
        builder.field(STATUS_FIELD, status);
        builder.endObject();
        return builder;
    }
}
