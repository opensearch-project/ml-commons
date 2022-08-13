/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.sentence_transformer;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.ExecuteOutput;
import org.opensearch.ml.common.output.Output;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@ExecuteOutput(algorithms={FunctionName.SENTENCE_TRANSFORMER})
@Data
public class SentenceTransformerOutput implements Output {

    public static final String RESULT_FIELD = "result";
    private List<float[]> result;

    @Builder
    public SentenceTransformerOutput(final List<float[]> result) {
        this.result = result;
    }

    public SentenceTransformerOutput(StreamInput in) throws IOException {
        if (in.readBoolean()) {
            this.result = new ArrayList<>();
            int size = in.readInt();
            for (int i=0; i<size; i++) {
                result.add(in.readFloatArray());
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (result != null) {
            out.writeBoolean(true);
            out.writeInt(result.size());
            for (float[] item : result) {
                out.writeFloatArray(item);
            }
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (result != null) {
            builder.field(RESULT_FIELD, result);
        }
        return builder;
    }
}
