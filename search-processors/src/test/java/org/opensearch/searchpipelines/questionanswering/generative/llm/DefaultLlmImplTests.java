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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.client.Client;
import org.opensearch.ml.client.MachineLearningClient;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.searchpipelines.questionanswering.generative.prompt.PromptUtil;
import org.opensearch.test.OpenSearchTestCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DefaultLlmImplTests extends OpenSearchTestCase {

    @Mock
    Client client;

    public void testBuildMessageParameter() {
        DefaultLlmImpl connector = new DefaultLlmImpl("model_id", client);
        String question = "Who am I";
        List<String> contexts = new ArrayList<>();
        List<String> chatHistory = new ArrayList<>();
        contexts.add("context 1");
        contexts.add("context 2");
        chatHistory.add("message 1");
        chatHistory.add("message 2");
        String parameter = PromptUtil.getChatCompletionPrompt(question, chatHistory, contexts);
        //System.out.println(parameter);
        Map<String, String> parameters = Map.of("model", "foo", "messages", parameter);
        assertTrue(isJson(parameter));
    }

    public void testChatCompletionApi() throws Exception {
        MachineLearningClient mlClient = mock(MachineLearningClient.class);
        ArgumentCaptor<MLInput> captor = ArgumentCaptor.forClass(MLInput.class);
        DefaultLlmImpl connector = new DefaultLlmImpl("model_id", client);
        connector.setMlClient(mlClient);

        Map<String, String> messageMap = Map.of("role", "agent", "content", "answer");
        Map<String, ?> dataAsMap = Map.of("choices", List.of(Map.of("message", messageMap)));
        ModelTensor tensor = new ModelTensor("tensor", new Number[0], new long[0], MLResultDataType.STRING, null, null, dataAsMap);
        ModelTensorOutput mlOutput = new ModelTensorOutput(List.of(new ModelTensors(List.of(tensor))));
        ActionFuture<MLOutput> future = mock(ActionFuture.class);
        when(future.actionGet()).thenReturn(mlOutput);
        when(mlClient.predict(any(), any())).thenReturn(future);
        ChatCompletionInput input = new ChatCompletionInput("model", "question", Collections.emptyList(), Collections.emptyList());
        ChatCompletionOutput output = connector.doChatCompletion(input);
        verify(mlClient, times(1)).predict(any(), captor.capture());
        MLInput mlInput = captor.getValue();
        assertTrue(mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet);
        assertEquals("answer", (String) output.getAnswers().get(0));
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
