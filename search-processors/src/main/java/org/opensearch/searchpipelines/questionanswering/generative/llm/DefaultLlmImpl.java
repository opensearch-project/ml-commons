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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opensearch.client.Client;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.searchpipelines.questionanswering.generative.client.MachineLearningInternalClient;
import org.opensearch.searchpipelines.questionanswering.generative.prompt.PromptUtil;

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
        Objects.requireNonNull(openSearchModelId);
        this.openSearchModelId = openSearchModelId;
        this.mlClient = new MachineLearningInternalClient(client);
    }

    // VisibleForTesting
    protected void setMlClient(MachineLearningInternalClient mlClient) {
        this.mlClient = mlClient;
    }

    /**
     * Use ChatCompletion API to generate an answer.
     *
     * @param chatCompletionInput
     * @return
     */
    @Override
    public ChatCompletionOutput doChatCompletion(ChatCompletionInput chatCompletionInput) {

        MLInputDataset dataset = RemoteInferenceInputDataSet.builder().parameters(getInputParameters(chatCompletionInput)).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(dataset).build();
        ActionFuture<MLOutput> future = mlClient.predict(this.openSearchModelId, mlInput);
        ModelTensorOutput modelOutput = (ModelTensorOutput) future.actionGet(chatCompletionInput.getTimeoutInSeconds() * 1000);

        // Response from a remote model
        Map<String, ?> dataAsMap = modelOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        log.info("dataAsMap: {}", dataAsMap.toString());

        // TODO dataAsMap can be null or can contain information such as throttling. Handle non-happy cases.

        return buildChatCompletionOutput(chatCompletionInput.getModelProvider(), dataAsMap);
    }

    protected Map<String, String> getInputParameters(ChatCompletionInput chatCompletionInput) {
        Map<String, String> inputParameters = new HashMap<>();

        if (chatCompletionInput.getModelProvider() == ModelProvider.OPENAI) {
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
        } else if (chatCompletionInput.getModelProvider() == ModelProvider.BEDROCK) {
            inputParameters
                .put(
                    "inputs",
                    PromptUtil
                        .buildSingleStringPrompt(
                            chatCompletionInput.getSystemPrompt(),
                            chatCompletionInput.getUserInstructions(),
                            chatCompletionInput.getQuestion(),
                            chatCompletionInput.getChatHistory(),
                            chatCompletionInput.getContexts()
                        )
                );
        } else {
            throw new IllegalArgumentException("Unknown/unsupported model provider: " + chatCompletionInput.getModelProvider());
        }

        log.info("LLM input parameters: {}", inputParameters.toString());
        return inputParameters;
    }

    protected ChatCompletionOutput buildChatCompletionOutput(ModelProvider provider, Map<String, ?> dataAsMap) {

        List<Object> answers = null;
        List<String> errors = null;

        if (provider == ModelProvider.OPENAI) {
            List choices = (List) dataAsMap.get(CONNECTOR_OUTPUT_CHOICES);
            if (choices == null) {
                Map error = (Map) dataAsMap.get(CONNECTOR_OUTPUT_ERROR);
                errors = List.of((String) error.get(CONNECTOR_OUTPUT_MESSAGE));
            } else {
                Map firstChoiceMap = (Map) choices.get(0);
                log.info("Choices: {}", firstChoiceMap.toString());
                Map message = (Map) firstChoiceMap.get(CONNECTOR_OUTPUT_MESSAGE);
                log
                    .info(
                        "role: {}, content: {}",
                        message.get(CONNECTOR_OUTPUT_MESSAGE_ROLE),
                        message.get(CONNECTOR_OUTPUT_MESSAGE_CONTENT)
                    );
                answers = List.of(message.get(CONNECTOR_OUTPUT_MESSAGE_CONTENT));
            }
        } else if (provider == ModelProvider.BEDROCK) {
            String response = (String) dataAsMap.get("completion");
            if (response != null) {
                answers = List.of(response);
            } else {
                Map error = (Map) dataAsMap.get("error");
                if (error != null) {
                    errors = List.of((String) error.get("message"));
                } else {
                    errors = List.of("Unknown error or response.");
                }
            }
        } else {
            throw new IllegalArgumentException("Unknown/unsupported model provider: " + provider);
        }

        return new ChatCompletionOutput(answers, errors);
    }
}
