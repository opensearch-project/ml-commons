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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.client.Client;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.pipeline.Processor;
import org.opensearch.searchpipelines.questionanswering.generative.client.ConversationalMemoryClient;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParamExtBuilder;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParameters;
import org.opensearch.searchpipelines.questionanswering.generative.llm.ChatCompletionInput;
import org.opensearch.searchpipelines.questionanswering.generative.llm.ChatCompletionOutput;
import org.opensearch.searchpipelines.questionanswering.generative.llm.Llm;
import org.opensearch.test.OpenSearchTestCase;

public class GenerativeQAResponseProcessorTests extends OpenSearchTestCase {

    private BooleanSupplier alwaysOn = () -> true;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    public void testProcessorFactoryRemoteModel() throws Exception {
        Client client = mock(Client.class);
        Map<String, Object> config = new HashMap<>();
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_MODEL_ID, "xyz");
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_CONTEXT_FIELD_LIST, List.of("text"));

        GenerativeQAResponseProcessor processor = (GenerativeQAResponseProcessor) new GenerativeQAResponseProcessor.Factory(
            client,
            alwaysOn
        ).create(null, "tag", "desc", true, config, null);
        assertNotNull(processor);
    }

    public void testGetType() {
        Client client = mock(Client.class);
        Llm llm = mock(Llm.class);
        GenerativeQAResponseProcessor processor = new GenerativeQAResponseProcessor(
            client,
            null,
            null,
            false,
            llm,
            "foo",
            List.of("text"),
            "system_prompt",
            "user_instructions",
            alwaysOn
        );
        assertEquals(GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE, processor.getType());
    }

    public void testProcessResponseNoSearchHits() throws Exception {
        Client client = mock(Client.class);
        Map<String, Object> config = new HashMap<>();
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_MODEL_ID, "dummy-model");
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_CONTEXT_FIELD_LIST, List.of("text"));

        GenerativeQAResponseProcessor processor = (GenerativeQAResponseProcessor) new GenerativeQAResponseProcessor.Factory(
            client,
            alwaysOn
        ).create(null, "tag", "desc", true, config, null);

        SearchRequest request = new SearchRequest();
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        GenerativeQAParameters params = new GenerativeQAParameters("12345", "llm_model", "You are kind.", null, null, null);
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
        doAnswer(
            invocation -> {
                ((ActionListener<ChatCompletionOutput>) invocation.getArguments()[2]).onResponse(output);
                return null;
            }
        ).when(llm).doChatCompletion(any(), any());
        //when(llm.doChatCompletion(any())).thenReturn(output);
        when(output.getAnswers()).thenReturn(List.of("foo"));
        processor.setLlm(llm);

        ArgumentCaptor<ChatCompletionInput> captor = ArgumentCaptor.forClass(ChatCompletionInput.class);
        boolean errorThrown = false;
        try {
            processor.asyncProcessResponse(request, response, ActionListener.wrap(r->{}, e->{}));
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

        GenerativeQAResponseProcessor processor = (GenerativeQAResponseProcessor) new GenerativeQAResponseProcessor.Factory(
            client,
            alwaysOn
        ).create(null, "tag", "desc", true, config, null);

        ConversationalMemoryClient memoryClient = mock(ConversationalMemoryClient.class);
        List<Interaction> chatHistory = List.of(
            new Interaction("0", Instant.now(), "1", "question", "", "answer", "foo", "{}"));
        doAnswer(
            invocation -> {
                ((ActionListener<List<Interaction>>) invocation.getArguments()[2]).onResponse(chatHistory);
                return null;
            }
        ).when(memoryClient).getInteractions(any(), anyInt(), any());
        processor.setMemoryClient(memoryClient);

        SearchRequest request = new SearchRequest();
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        GenerativeQAParameters params = new GenerativeQAParameters("12345", "llm_model", "You are kind.", null, null, null);
        GenerativeQAParamExtBuilder extBuilder = new GenerativeQAParamExtBuilder();
        extBuilder.setParams(params);
        request.source(sourceBuilder);
        sourceBuilder.ext(List.of(extBuilder));

        int numHits = 10;
        SearchHit[] hitsArray = new SearchHit[numHits];
        for (int i = 0; i < numHits; i++) {
            XContentBuilder sourceContent = JsonXContent
                .contentBuilder()
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
        doAnswer(invocation -> {
            ((ActionListener<ChatCompletionOutput>) invocation.getArguments()[1]).onResponse(output);
            return null;
        }).when(llm).doChatCompletion(any(), any());
        when(output.getAnswers()).thenReturn(List.of("foo"));
        processor.setLlm(llm);

        ArgumentCaptor<ChatCompletionInput> captor = ArgumentCaptor.forClass(ChatCompletionInput.class);
        processor.asyncProcessResponse(request, response, ActionListener.wrap(
            r -> {
                assertTrue(r instanceof GenerativeSearchResponse);
            }, e -> {}
            ));
        verify(llm).doChatCompletion(captor.capture(), any());
        ChatCompletionInput input = captor.getValue();
        assertTrue(input instanceof ChatCompletionInput);
        List<String> passages = input.getContexts();
        assertEquals("passage0", passages.get(0));
        assertEquals("passage1", passages.get(1));
        assertEquals(numHits, passages.size());
    }

    public void testProcessResponseSmallerContextSize() throws Exception {
        Client client = mock(Client.class);
        Map<String, Object> config = new HashMap<>();
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_MODEL_ID, "dummy-model");
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_CONTEXT_FIELD_LIST, List.of("text"));

        GenerativeQAResponseProcessor processor = (GenerativeQAResponseProcessor) new GenerativeQAResponseProcessor.Factory(
            client,
            alwaysOn
        ).create(null, "tag", "desc", true, config, null);

        ConversationalMemoryClient memoryClient = mock(ConversationalMemoryClient.class);
        List<Interaction> chatHistory = List.of(
            new Interaction("0", Instant.now(), "1", "question", "", "answer", "foo", "{}"));
        doAnswer(
            invocation -> {
                ((ActionListener<List<Interaction>>) invocation.getArguments()[2]).onResponse(chatHistory);
                return null;
            }
        ).when(memoryClient).getInteractions(any(), anyInt(), any());
        processor.setMemoryClient(memoryClient);

        SearchRequest request = new SearchRequest();
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        int contextSize = 5;
        GenerativeQAParameters params = new GenerativeQAParameters("12345", "llm_model", "You are kind.", contextSize, null, null);
        GenerativeQAParamExtBuilder extBuilder = new GenerativeQAParamExtBuilder();
        extBuilder.setParams(params);
        request.source(sourceBuilder);
        sourceBuilder.ext(List.of(extBuilder));

        int numHits = 10;
        SearchHit[] hitsArray = new SearchHit[numHits];
        for (int i = 0; i < numHits; i++) {
            XContentBuilder sourceContent = JsonXContent
                .contentBuilder()
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
        doAnswer(invocation -> {
            ((ActionListener<ChatCompletionOutput>) invocation.getArguments()[1]).onResponse(output);
            return null;
        }).when(llm).doChatCompletion(any(), any());
        when(output.getAnswers()).thenReturn(List.of("foo"));
        processor.setLlm(llm);

        ArgumentCaptor<ChatCompletionInput> captor = ArgumentCaptor.forClass(ChatCompletionInput.class);
        processor.asyncProcessResponse(request, response, ActionListener.wrap(
            r -> {
                assertTrue(r instanceof GenerativeSearchResponse);
            }, e -> {}
        ));
        verify(llm).doChatCompletion(captor.capture(), any());
        ChatCompletionInput input = captor.getValue();
        assertTrue(input instanceof ChatCompletionInput);
        List<String> passages = ((ChatCompletionInput) input).getContexts();
        assertEquals("passage0", passages.get(0));
        assertEquals("passage1", passages.get(1));
        assertEquals(contextSize, passages.size());
    }

    public void testProcessResponseMissingContextField() throws Exception {
        Client client = mock(Client.class);
        Map<String, Object> config = new HashMap<>();
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_MODEL_ID, "dummy-model");
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_CONTEXT_FIELD_LIST, List.of("text"));

        GenerativeQAResponseProcessor processor = (GenerativeQAResponseProcessor) new GenerativeQAResponseProcessor.Factory(
            client,
            alwaysOn
        ).create(null, "tag", "desc", true, config, null);

        ConversationalMemoryClient memoryClient = mock(ConversationalMemoryClient.class);
        List<Interaction> chatHistory = List.of(
            new Interaction("0", Instant.now(), "1", "question", "", "answer", "foo", "{}"));
        doAnswer(
            invocation -> {
                ((ActionListener<List<Interaction>>) invocation.getArguments()[2]).onResponse(chatHistory);
                return null;
            }
        ).when(memoryClient).getInteractions(any(), anyInt(), any());
        processor.setMemoryClient(memoryClient);

        SearchRequest request = new SearchRequest();
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        GenerativeQAParameters params = new GenerativeQAParameters("12345", "llm_model", "You are kind.", null, null, null);
        GenerativeQAParamExtBuilder extBuilder = new GenerativeQAParamExtBuilder();
        extBuilder.setParams(params);
        request.source(sourceBuilder);
        sourceBuilder.ext(List.of(extBuilder));

        int numHits = 10;
        SearchHit[] hitsArray = new SearchHit[numHits];
        for (int i = 0; i < numHits; i++) {
            XContentBuilder sourceContent = JsonXContent
                .contentBuilder()
                .startObject()
                .field("_id", String.valueOf(i))
                // .field("text", "passage" + i)
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
        doAnswer(invocation -> {
            ((ActionListener<ChatCompletionOutput>) invocation.getArguments()[1]).onResponse(output);
            return null;
        }).when(llm).doChatCompletion(any(), any());
        when(output.getAnswers()).thenReturn(List.of("foo"));
        processor.setLlm(llm);

        boolean exceptionThrown = false;

        try {
            processor.asyncProcessResponse(request, response, ActionListener.wrap(r->{}, e->{}));
        } catch (Exception e) {
            exceptionThrown = true;
        }

        assertTrue(exceptionThrown);
    }

    public void testProcessorFactoryFeatureDisabled() throws Exception {

        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage(GenerativeQAProcessorConstants.FEATURE_NOT_ENABLED_ERROR_MSG);

        Client client = mock(Client.class);
        Map<String, Object> config = new HashMap<>();
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_MODEL_ID, "xyz");
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_CONTEXT_FIELD_LIST, List.of("text"));

        Processor processor = new GenerativeQAResponseProcessor.Factory(client, () -> false)
            .create(null, "tag", "desc", true, config, null);
    }

    // Use this only for the following test case.
    private boolean featureEnabled001;

    public void testProcessorFeatureOffOnOff() throws Exception {
        Client client = mock(Client.class);
        Map<String, Object> config = new HashMap<>();
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_MODEL_ID, "xyz");
        config.put(GenerativeQAProcessorConstants.CONFIG_NAME_CONTEXT_FIELD_LIST, List.of("text"));

        featureEnabled001 = false;
        BooleanSupplier supplier = () -> featureEnabled001;
        Processor.Factory factory = new GenerativeQAResponseProcessor.Factory(client, supplier);
        GenerativeQAResponseProcessor processor;
        boolean firstExceptionThrown = false;
        try {
            processor = (GenerativeQAResponseProcessor) factory.create(null, "tag", "desc", true, config, null);
        } catch (MLException e) {
            assertEquals(GenerativeQAProcessorConstants.FEATURE_NOT_ENABLED_ERROR_MSG, e.getMessage());
            firstExceptionThrown = true;
        }
        assertTrue(firstExceptionThrown);
        featureEnabled001 = true;
        processor = (GenerativeQAResponseProcessor) factory.create(null, "tag", "desc", true, config, null);

        featureEnabled001 = false;
        boolean secondExceptionThrown = false;
        try {
            processor.asyncProcessResponse(mock(SearchRequest.class), mock(SearchResponse.class), ActionListener.wrap(r->{}, e->{}));
        } catch (MLException e) {
            assertEquals(GenerativeQAProcessorConstants.FEATURE_NOT_ENABLED_ERROR_MSG, e.getMessage());
            secondExceptionThrown = true;
        }
        assertTrue(secondExceptionThrown);
    }
}
