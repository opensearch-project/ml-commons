/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.common.input.nlp;

import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
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
import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.search.SearchModule;

public class TextSimilarityMLInputTest {
    
    MLInput input;

    private final FunctionName algorithm = FunctionName.TEXT_SIMILARITY;

    @Before
    public void setup() {
        List<String> docs = List.of("That is a happy dog", "it's summer");
        String queryText = "today is sunny";
        MLInputDataset dataset = TextSimilarityInputDataSet.builder().textDocs(docs).queryText(queryText).build();
        input = new TextSimilarityMLInput(algorithm, dataset);
    }

    @Test
    public void testXContent_IsInternallyConsistent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        System.out.println(jsonStr);
        XContentParser parser = XContentType.JSON.xContent()
                .createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                        Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();

        MLInput parsedInput = MLInput.parse(parser, input.getFunctionName().name());
        assert (parsedInput instanceof TextSimilarityMLInput);
        TextSimilarityMLInput parsedTSMLI = (TextSimilarityMLInput) parsedInput;
        List<String> docs = ((TextSimilarityInputDataSet) parsedTSMLI.getInputDataset()).getTextDocs();
        String queryText = ((TextSimilarityInputDataSet) parsedTSMLI.getInputDataset()).getQueryText();
        assert (docs.size() == 2);
        assert (docs.get(0).equals("That is a happy dog"));
        assert (docs.get(1).equals("it's summer"));
        assert (queryText.equals("today is sunny"));
    }

    @Test
    public void testXContent_String() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assert (jsonStr.equals("{\"algorithm\":\"TEXT_SIMILARITY\",\"query_text\":\"today is sunny\",\"text_docs\":[\"That is a happy dog\",\"it's summer\"]}"));
    }

    @Test
    public void testParseJson() throws IOException {
        String json = "{\"algorithm\":\"TEXT_SIMILARITY\",\"query_text\":\"today is sunny\",\"text_docs\":[\"That is a happy dog\",\"it's summer\"]}";
        XContentParser parser = XContentType.JSON.xContent()
                .createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                        Collections.emptyList()).getNamedXContents()), null, json);
        parser.nextToken();

        MLInput parsedInput = MLInput.parse(parser, input.getFunctionName().name());
        assert (parsedInput instanceof TextSimilarityMLInput);
        TextSimilarityMLInput parsedTSMLI = (TextSimilarityMLInput) parsedInput;
        List<String> docs = ((TextSimilarityInputDataSet) parsedTSMLI.getInputDataset()).getTextDocs();
        String queryText = ((TextSimilarityInputDataSet) parsedTSMLI.getInputDataset()).getQueryText();
        assert (docs.size() == 2);
        assert (docs.get(0).equals("That is a happy dog"));
        assert (docs.get(1).equals("it's summer"));
        assert (queryText.equals("today is sunny"));
    }

    @Test
    public void testParseJson_NoPairs_ThenFail() throws IOException {
        String json = "{\"algorithm\":\"TEXT_SIMILARITY\",\"query_text\":\"today is sunny\",\"text_docs\":[]}";
        XContentParser parser = XContentType.JSON.xContent()
                .createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                        Collections.emptyList()).getNamedXContents()), null, json);
        parser.nextToken();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> MLInput.parse(parser, input.getFunctionName().name()));
        assert (e.getMessage().equals("no text docs"));
    }

    @Test
    public void testStreaming() throws IOException {
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        input.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        TextSimilarityMLInput newInput = new TextSimilarityMLInput(in);
        List<String> newPairs = ((TextSimilarityInputDataSet) newInput.getInputDataset()).getTextDocs();
        List<String> oldPairs = ((TextSimilarityInputDataSet) input.getInputDataset()).getTextDocs();
        assert (newPairs.equals(oldPairs));
    }

}
