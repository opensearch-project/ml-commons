/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.bertqa;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.ExecuteOutput;
import org.opensearch.ml.common.output.Output;

import java.io.IOException;

@ExecuteOutput(algorithms={FunctionName.BERT_QA})
@Data
public class BertQAOutput implements Output {

    public static final String RESULT_FIELD = "objects";
    private String result;

    @Builder
    public BertQAOutput(final String result) {
        this.result = result;
    }

    public BertQAOutput(StreamInput in) throws IOException {
        result = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(result);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (result != null) {
            builder.field(RESULT_FIELD, result);
        }
        return builder;
    }
}
