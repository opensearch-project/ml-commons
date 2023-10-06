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
package org.opensearch.searchpipelines.questionanswering.generative.llm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opensearch.client.Client;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.searchpipelines.questionanswering.generative.client.MachineLearningInternalClient;
import org.opensearch.searchpipelines.questionanswering.generative.prompt.PromptUtil;
import org.opensearch.test.OpenSearchTestCase;

public class DefaultLlmImplTests extends OpenSearchTestCase {

    @Mock
    Client client;

    public void testBuildMessageParameter() {
        DefaultLlmImpl connector = new DefaultLlmImpl("model_id", client);
        String question = "Who am I";
        List<String> contexts = new ArrayList<>();
        contexts.add("context 1");
        contexts.add("context 2");
        List<Interaction> chatHistory = List
            .of(
                Interaction
                    .fromMap(
                        "convo1",
                        Map
                            .of(
                                ConversationalIndexConstants.INTERACTIONS_CREATE_TIME_FIELD,
                                Instant.now().toString(),
                                ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD,
                                "message 1",
                                ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD,
                                "answer1"
                            )
                    ),
                Interaction
                    .fromMap(
                        "convo1",
                        Map
                            .of(
                                ConversationalIndexConstants.INTERACTIONS_CREATE_TIME_FIELD,
                                Instant.now().toString(),
                                ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD,
                                "message 2",
                                ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD,
                                "answer2"
                            )
                    )
            );
        String parameter = PromptUtil.getChatCompletionPrompt(question, chatHistory, contexts);
        Map<String, String> parameters = Map.of("model", "foo", "messages", parameter);
        assertTrue(isJson(parameter));
    }

    public void testChatCompletionApi() throws Exception {
        MachineLearningInternalClient mlClient = mock(MachineLearningInternalClient.class);
        ArgumentCaptor<MLInput> captor = ArgumentCaptor.forClass(MLInput.class);
        DefaultLlmImpl connector = new DefaultLlmImpl("model_id", client);
        connector.setMlClient(mlClient);

        Map<String, String> messageMap = Map.of("role", "agent", "content", "answer");
        Map<String, ?> dataAsMap = Map.of("choices", List.of(Map.of("message", messageMap)));
        ModelTensor tensor = new ModelTensor("tensor", new Number[0], new long[0], MLResultDataType.STRING, null, null, dataAsMap);
        ModelTensorOutput mlOutput = new ModelTensorOutput(List.of(new ModelTensors(List.of(tensor))));
        ActionFuture<MLOutput> future = mock(ActionFuture.class);
        when(future.actionGet(anyLong())).thenReturn(mlOutput);
        when(mlClient.predict(any(), any())).thenReturn(future);
        ChatCompletionInput input = new ChatCompletionInput(
            "model",
            "question",
            Collections.emptyList(),
            Collections.emptyList(),
            0,
            "prompt",
            "instructions"
        );
        doAnswer(
            invocation -> {
                ((ActionListener<MLOutput>) invocation.getArguments()[2]).onResponse(mlOutput);
                return null;
            }
        ).when(mlClient).predict(any(), any(), any());
        connector.doChatCompletion(input, new ActionListener<ChatCompletionOutput>() {
            @Override
            public void onResponse(ChatCompletionOutput output) {
                assertEquals("answer", (String) output.getAnswers().get(0));
            }

            @Override
            public void onFailure(Exception e) {

            }
        });
        verify(mlClient, times(1)).predict(any(), captor.capture(), any());
        MLInput mlInput = captor.getValue();
        assertTrue(mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet);
    }

    public void testChatCompletionThrowingError() throws Exception {
        MachineLearningInternalClient mlClient = mock(MachineLearningInternalClient.class);
        ArgumentCaptor<MLInput> captor = ArgumentCaptor.forClass(MLInput.class);
        DefaultLlmImpl connector = new DefaultLlmImpl("model_id", client);
        connector.setMlClient(mlClient);

        String errorMessage = "throttled";
        Map<String, String> messageMap = Map.of("message", errorMessage);
        Map<String, ?> dataAsMap = Map.of("error", messageMap);
        ModelTensor tensor = new ModelTensor("tensor", new Number[0], new long[0], MLResultDataType.STRING, null, null, dataAsMap);
        ModelTensorOutput mlOutput = new ModelTensorOutput(List.of(new ModelTensors(List.of(tensor))));
        ActionFuture<MLOutput> future = mock(ActionFuture.class);
        when(future.actionGet(anyLong())).thenReturn(mlOutput);
        when(mlClient.predict(any(), any())).thenReturn(future);
        ChatCompletionInput input = new ChatCompletionInput(
            "model",
            "question",
            Collections.emptyList(),
            Collections.emptyList(),
            0,
            "prompt",
            "instructions"
        );

        doAnswer(
            invocation -> {
                ((ActionListener<MLOutput>) invocation.getArguments()[2]).onResponse(mlOutput);
                return null;
            }
        ).when(mlClient).predict(any(), any(), any());
        connector.doChatCompletion(input, new ActionListener<ChatCompletionOutput>() {
            @Override
            public void onResponse(ChatCompletionOutput output) {
                assertTrue(output.isErrorOccurred());
                assertEquals(errorMessage, (String) output.getErrors().get(0));
            }

            @Override
            public void onFailure(Exception e) {

            }
        });
        verify(mlClient, times(1)).predict(any(), captor.capture(), any());
        MLInput mlInput = captor.getValue();
        assertTrue(mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet);
    }

    private boolean isJson(String Json) {
        try {
            new JSONObject(Json);
        } catch (JSONException ex) {
            try {
                new JSONArray(Json);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }
}
