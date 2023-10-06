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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.searchpipelines.questionanswering.generative.client.MachineLearningInternalClient;
import org.opensearch.searchpipelines.questionanswering.generative.prompt.PromptUtil;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

/**
 * Wrapper for talking to LLMs via OpenSearch HttpConnector.
 */
@Log4j2
public class DefaultLlmImpl implements Llm {

    private static final String CONNECTOR_INPUT_PARAMETER_MODEL = "model";
    private static final String CONNECTOR_INPUT_PARAMETER_MESSAGES = "messages";
    private static final String CONNECTOR_OUTPUT_CHOICES = "choices";
    private static final String CONNECTOR_OUTPUT_MESSAGE = "message";
    private static final String CONNECTOR_OUTPUT_MESSAGE_ROLE = "role";
    private static final String CONNECTOR_OUTPUT_MESSAGE_CONTENT = "content";
    private static final String CONNECTOR_OUTPUT_ERROR = "error";

    private final String openSearchModelId;

    private MachineLearningInternalClient mlClient;

    public DefaultLlmImpl(String openSearchModelId, Client client) {
        checkNotNull(openSearchModelId);
        this.openSearchModelId = openSearchModelId;
        this.mlClient = new MachineLearningInternalClient(client);
    }

    @VisibleForTesting
    void setMlClient(MachineLearningInternalClient mlClient) {
        this.mlClient = mlClient;
    }

    /**
     * Use ChatCompletion API to generate an answer.
     *
     * @param chatCompletionInput
     * @return
     */
    @Override
    public void doChatCompletion(ChatCompletionInput chatCompletionInput, ActionListener<ChatCompletionOutput> listener) {
        Map<String, String> inputParameters = new HashMap<>();
        inputParameters.put(CONNECTOR_INPUT_PARAMETER_MODEL, chatCompletionInput.getModel());
        String messages = PromptUtil
            .getChatCompletionPrompt(
                chatCompletionInput.getSystemPrompt(),
                chatCompletionInput.getUserInstructions(),
                chatCompletionInput.getQuestion(),
                chatCompletionInput.getChatHistory(),
                chatCompletionInput.getContexts()
            );
        inputParameters.put(CONNECTOR_INPUT_PARAMETER_MESSAGES, messages);
        log.info("Messages to LLM: {}", messages);
        MLInputDataset dataset = RemoteInferenceInputDataSet.builder().parameters(inputParameters).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(dataset).build();
        mlClient.predict(this.openSearchModelId, mlInput, new ActionListener<>() {
            @Override
            public void onResponse(MLOutput mlOutput) {
                // Response from a remote model
                Map<String, ?> dataAsMap = ((ModelTensorOutput) mlOutput).getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
                log.info("dataAsMap: {}", dataAsMap.toString());

                List choices = (List) dataAsMap.get(CONNECTOR_OUTPUT_CHOICES);
                List<Object> answers = null;
                List<String> errors = null;
                if (choices == null) {
                    Map error = (Map) dataAsMap.get(CONNECTOR_OUTPUT_ERROR);
                    errors = List.of((String) error.get(CONNECTOR_OUTPUT_MESSAGE));
                } else {
                    Map firstChoiceMap = (Map) choices.get(0);
                    log.info("Choices: {}", firstChoiceMap.toString());
                    Map message = (Map) firstChoiceMap.get(CONNECTOR_OUTPUT_MESSAGE);
                    log.info("role: {}, content: {}", message.get(CONNECTOR_OUTPUT_MESSAGE_ROLE), message.get(CONNECTOR_OUTPUT_MESSAGE_CONTENT));
                    answers = List.of(message.get(CONNECTOR_OUTPUT_MESSAGE_CONTENT));
                }

                listener.onResponse(new ChatCompletionOutput(answers, errors));
            }

            @Override
            public void onFailure(Exception e) {
                listener.onFailure(e);
            }
        });
    }
}
