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
package org.opensearch.searchpipelines.questionanswering.generative;

import org.mockito.ArgumentCaptor;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.client.Client;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParamExtBuilder;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParameters;
import org.opensearch.searchpipelines.questionanswering.generative.llm.ChatCompletionOutput;
import org.opensearch.searchpipelines.questionanswering.generative.llm.Llm;
import org.opensearch.searchpipelines.questionanswering.generative.llm.ChatCompletionInput;
import org.opensearch.test.OpenSearchTestCase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GenerativeQAResponseProcessorTests extends OpenSearchTestCase {

    public void testProcessorFactoryRemoteModel() throws Exception {
        Client client = mock(Client.class);
        Map<String, Object> config = new HashMap<>();
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_MODEL_ID, "xyz");
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_CONTEXT_FIELD_LIST, List.of("text"));

        GenerativeQAResponseProcessor processor = (GenerativeQAResponseProcessor) new GenerativeQAResponseProcessor.Factory(client)
            .create(null, "tag", "desc", true, config, null);
        assertNotNull(processor);
    }

    public void testProcessResponseNoSearchHits() throws Exception {
        Client client = mock(Client.class);
        Map<String, Object> config = new HashMap<>();
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_MODEL_ID, "dummy-model");
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_CONTEXT_FIELD_LIST, List.of("text"));

        GenerativeQAResponseProcessor processor = (GenerativeQAResponseProcessor) new GenerativeQAResponseProcessor.Factory(client)
            .create(null, "tag", "desc", true, config, null);

        SearchRequest request = new SearchRequest(); // mock(SearchRequest.class);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder(); // mock(SearchSourceBuilder.class);
        GenerativeQAParameters params = new GenerativeQAParameters("12345", "llm_model", "You are kind.");
        GenerativeQAParamExtBuilder extBuilder = new GenerativeQAParamExtBuilder();
        extBuilder.setParams(params);
        request.source(sourceBuilder);
        sourceBuilder.ext(List.of(extBuilder));

        int numHits = 10;
        SearchHit[] hitsArray = new SearchHit[numHits];
        for (int i = 0; i < numHits; i++) {
            hitsArray[i] = new SearchHit(i, "doc" + i, Map.of(), Map.of());
        }

        SearchHits searchHits = new SearchHits(hitsArray, null, 1.0f);
        SearchResponseSections internal = new SearchResponseSections(searchHits, null, null, false, false, null, 0);
        SearchResponse response = new SearchResponse(internal, null, 1, 1, 0, 1, null, null, null);

        Llm llm = mock(Llm.class);
        ChatCompletionOutput output = mock(ChatCompletionOutput.class);
        when(llm.doChatCompletion(any())).thenReturn(output);
        when(output.getAnswers()).thenReturn(List.of("foo"));
        processor.setLlm(llm);

        ArgumentCaptor<ChatCompletionInput> captor = ArgumentCaptor.forClass(ChatCompletionInput.class);
        boolean errorThrown = false;
        try {
            SearchResponse res = processor.processResponse(request, response);
        } catch (Exception e) {
            errorThrown = true;
        }
        assertTrue(errorThrown);
    }

    public void testProcessResponse() throws Exception {
        Client client = mock(Client.class);
        Map<String, Object> config = new HashMap<>();
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_MODEL_ID, "dummy-model");
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_CONTEXT_FIELD_LIST, List.of("text"));

        GenerativeQAResponseProcessor processor = (GenerativeQAResponseProcessor) new GenerativeQAResponseProcessor.Factory(client)
            .create(null, "tag", "desc", true, config, null);

        SearchRequest request = new SearchRequest(); // mock(SearchRequest.class);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder(); // mock(SearchSourceBuilder.class);
        GenerativeQAParameters params = new GenerativeQAParameters("12345", "llm_model", "You are kind.");
        GenerativeQAParamExtBuilder extBuilder = new GenerativeQAParamExtBuilder();
        extBuilder.setParams(params);
        request.source(sourceBuilder);
        sourceBuilder.ext(List.of(extBuilder));

        int numHits = 10;
        SearchHit[] hitsArray = new SearchHit[numHits];
        for (int i = 0; i < numHits; i++) {
            XContentBuilder sourceContent = JsonXContent.contentBuilder()
                .startObject()
                .field("_id", String.valueOf(i))
                .field("text", "passage" + i)
                .field("title", "This is the title for document " + i)
                .endObject();
            hitsArray[i] = new SearchHit(i, "doc" + i, Map.of(), Map.of());
            hitsArray[i].sourceRef(BytesReference.bytes(sourceContent));
        }

        SearchHits searchHits = new SearchHits(hitsArray, null, 1.0f);
        SearchResponseSections internal = new SearchResponseSections(searchHits, null, null, false, false, null, 0);
        SearchResponse response = new SearchResponse(internal, null, 1, 1, 0, 1, null, null, null);

        Llm llm = mock(Llm.class);
        ChatCompletionOutput output = mock(ChatCompletionOutput.class);
        when(llm.doChatCompletion(any())).thenReturn(output);
        when(output.getAnswers()).thenReturn(List.of("foo"));
        processor.setLlm(llm);

        ArgumentCaptor<ChatCompletionInput> captor = ArgumentCaptor.forClass(ChatCompletionInput.class);
        SearchResponse res = processor.processResponse(request, response);
        verify(llm).doChatCompletion(captor.capture());
        ChatCompletionInput input = captor.getValue();
        assertTrue(input instanceof ChatCompletionInput);
        List<String> passages = ((ChatCompletionInput) input).getContexts();
        assertEquals("passage0", passages.get(0));
        assertEquals("passage1", passages.get(1));
        assertTrue(res instanceof GenerativeSearchResponse);
    }
}
