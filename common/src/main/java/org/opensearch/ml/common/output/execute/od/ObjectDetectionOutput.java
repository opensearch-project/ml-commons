/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.od;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.ExecuteOutput;
import org.opensearch.ml.common.output.Output;

import java.io.IOException;

@ExecuteOutput(algorithms={FunctionName.OBJECT_DETECTION})
@Data
public class ObjectDetectionOutput implements Output {

    public static final String OBJECTS_FIELD = "objects";
    private String[] objects;

    @Builder
    public ObjectDetectionOutput(final String[] objects) {
        this.objects = objects;
    }

    public ObjectDetectionOutput(StreamInput in) throws IOException {
        objects = in.readOptionalStringArray();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalStringArray(objects);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (objects != null) {
            builder.field(OBJECTS_FIELD, objects);
        }
        return builder;
    }
}
