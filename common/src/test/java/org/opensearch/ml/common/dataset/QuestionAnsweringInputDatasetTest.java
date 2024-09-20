/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.dataset;

import static org.junit.Assert.assertThrows;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.BytesStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class QuestionAnsweringInputDatasetTest {

    @Test
    public void testStreaming() throws IOException {
        String question = "What color is apple";
        String context = "I like Apples. They are red";
        QuestionAnsweringInputDataSet dataset = QuestionAnsweringInputDataSet.builder().question(question).context(context).build();
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        dataset.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        QuestionAnsweringInputDataSet newDs = (QuestionAnsweringInputDataSet) MLInputDataset.fromStream(in);
        assert (question.equals("What color is apple"));
        assert (context.equals("I like Apples. They are red"));
    }

    @Test
    public void noContext_ThenFail() {
        String question = "What color is apple";
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> QuestionAnsweringInputDataSet.builder().question(question).build()
        );
        assert (e.getMessage().equals("Context is not provided"));
    }

    @Test
    public void noQuestion_ThenFail() {
        String context = "I like Apples. They are red";
        assertThrows(IllegalArgumentException.class, () -> QuestionAnsweringInputDataSet.builder().context(context).build());
    }
}
