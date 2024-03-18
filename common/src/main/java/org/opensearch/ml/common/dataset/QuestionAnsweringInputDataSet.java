/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.dataset;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.annotation.InputDataSet;

import java.io.IOException;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@InputDataSet(MLInputDataType.QUESTION_ANSWERING)
public class QuestionAnsweringInputDataSet extends MLInputDataset {

   String question;

   String context;

    @Builder(toBuilder = true)
    public QuestionAnsweringInputDataSet(String question, String context) {
        super(MLInputDataType.QUESTION_ANSWERING);
        if(question == null) {
            throw new IllegalArgumentException("Question is not provided");
        }
        if(context == null) {
            throw new IllegalArgumentException("Context is not provided");
        }
        this.question = question;
        this.context = context;
    }

    public QuestionAnsweringInputDataSet(StreamInput in) throws IOException {
        super(MLInputDataType.TEXT_SIMILARITY);
        this.question = in.readString();
        this.context = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(question);
        out.writeString(context);
    }
}
