/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.input.nlp;

import java.io.IOException;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.BytesStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.QuestionAnsweringInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.search.SearchModule;

public class QuestionAnsweringMLInputTest {

    MLInput input;

    private final FunctionName algorithm = FunctionName.QUESTION_ANSWERING;

    @Before
    public void setup() {
        String question = "What color is apple";
        String context = "I like Apples. They are red";
        MLInputDataset dataset = QuestionAnsweringInputDataSet.builder().question(question).context(context).build();
        input = new QuestionAnsweringMLInput(algorithm, dataset);
    }

    @Test
    public void testXContent_IsInternallyConsistent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();

        MLInput parsedInput = MLInput.parse(parser, input.getFunctionName().name());
        assert (parsedInput instanceof QuestionAnsweringMLInput);
        QuestionAnsweringMLInput parsedQAMLI = (QuestionAnsweringMLInput) parsedInput;
        String question = ((QuestionAnsweringInputDataSet) parsedQAMLI.getInputDataset()).getQuestion();
        String context = ((QuestionAnsweringInputDataSet) parsedQAMLI.getInputDataset()).getContext();
        assert (question.equals("What color is apple"));
        assert (context.equals("I like Apples. They are red"));
    }

    @Test
    public void testXContent_String() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assert (jsonStr
            .equals(
                "{\"algorithm\":\"QUESTION_ANSWERING\",\"question\":\"What color is apple\",\"context\":\"I like Apples. They are red\"}"
            ));
    }

    @Test
    public void testParseJson() throws IOException {
        String json =
            "{\"algorithm\":\"QUESTION_ANSWERING\",\"question\":\"What color is apple\",\"context\":\"I like Apples. They are red\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                json
            );
        parser.nextToken();

        MLInput parsedInput = MLInput.parse(parser, input.getFunctionName().name());
        assert (parsedInput instanceof QuestionAnsweringMLInput);
        QuestionAnsweringMLInput parsedQAMLI = (QuestionAnsweringMLInput) parsedInput;
        String question = ((QuestionAnsweringInputDataSet) parsedQAMLI.getInputDataset()).getQuestion();
        String context = ((QuestionAnsweringInputDataSet) parsedQAMLI.getInputDataset()).getContext();
        assert (question.equals("What color is apple"));
        assert (context.equals("I like Apples. They are red"));
    }

    @Test
    public void testStreaming() throws IOException {
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        input.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        QuestionAnsweringMLInput newInput = new QuestionAnsweringMLInput(in);
        String newQuestion = ((QuestionAnsweringInputDataSet) newInput.getInputDataset()).getQuestion();
        String oldQuestion = ((QuestionAnsweringInputDataSet) input.getInputDataset()).getQuestion();
        assert (newQuestion.equals(oldQuestion));
    }

}
