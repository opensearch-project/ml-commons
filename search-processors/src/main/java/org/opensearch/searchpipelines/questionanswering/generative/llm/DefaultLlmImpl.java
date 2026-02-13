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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.searchpipelines.questionanswering.generative.client.MachineLearningInternalClient;
import org.opensearch.searchpipelines.questionanswering.generative.prompt.PromptUtil;
import org.opensearch.transport.client.Client;

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
    private static final String BEDROCK_COMPLETION_FIELD = "completion";
    private static final String BEDROCK_CONTENT_FIELD = "content";
    private static final String BEDROCK_TEXT_FIELD = "text";

    private final String openSearchModelId;

    private MachineLearningInternalClient mlClient;

    public DefaultLlmImpl(String openSearchModelId, Client client) {
        checkNotNull(openSearchModelId);
        this.openSearchModelId = openSearchModelId;
        this.mlClient = new MachineLearningInternalClient(client);
    }

    @VisibleForTesting
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
    public void doChatCompletion(ChatCompletionInput chatCompletionInput, ActionListener<ChatCompletionOutput> listener) {
        MLInputDataset dataset = RemoteInferenceInputDataSet.builder().parameters(getInputParameters(chatCompletionInput)).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(dataset).build();
        mlClient.predict(this.openSearchModelId, mlInput, new ActionListener<>() {
            @Override
            public void onResponse(MLOutput mlOutput) {
                // Response from a remote model
                Map<String, ?> dataAsMap = ((ModelTensorOutput) mlOutput)
                    .getMlModelOutputs()
                    .get(0)
                    .getMlModelTensors()
                    .get(0)
                    .getDataAsMap();
                listener
                    .onResponse(
                        buildChatCompletionOutput(
                            chatCompletionInput.getModelProvider(),
                            dataAsMap,
                            chatCompletionInput.getLlmResponseField()
                        )
                    );
            }

            @Override
            public void onFailure(Exception e) {
                listener.onFailure(e);
            }
        });
    }

    protected Map<String, String> getInputParameters(ChatCompletionInput chatCompletionInput) {
        Map<String, String> inputParameters = new HashMap<>();

        if (chatCompletionInput.getModelProvider() == ModelProvider.OPENAI) {
            inputParameters.put(CONNECTOR_INPUT_PARAMETER_MODEL, chatCompletionInput.getModel());
            String messages = PromptUtil
                .getChatCompletionPrompt(
                    chatCompletionInput.getModelProvider(),
                    chatCompletionInput.getSystemPrompt(),
                    chatCompletionInput.getUserInstructions(),
                    chatCompletionInput.getQuestion(),
                    chatCompletionInput.getChatHistory(),
                    chatCompletionInput.getContexts(),
                    chatCompletionInput.getLlmMessages()
                );
            inputParameters.put(CONNECTOR_INPUT_PARAMETER_MESSAGES, messages);
        } else if (chatCompletionInput.getModelProvider() == ModelProvider.BEDROCK
            || chatCompletionInput.getModelProvider() == ModelProvider.COHERE
            || chatCompletionInput.getLlmResponseField() != null) {
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
        } else if (chatCompletionInput.getModelProvider() == ModelProvider.BEDROCK_CONVERSE) {
            // Bedrock Converse API does not include the system prompt as part of the Messages block.
            String messages = PromptUtil
                .getChatCompletionPrompt(
                    chatCompletionInput.getModelProvider(),
                    null,
                    chatCompletionInput.getUserInstructions(),
                    chatCompletionInput.getQuestion(),
                    chatCompletionInput.getChatHistory(),
                    chatCompletionInput.getContexts(),
                    chatCompletionInput.getLlmMessages()
                );
            inputParameters.put(CONNECTOR_INPUT_PARAMETER_MESSAGES, messages);
        } else {
            throw new IllegalArgumentException(
                "Unknown/unsupported model provider: "
                    + chatCompletionInput.getModelProvider()
                    + ".  You must provide a valid model provider or llm_response_field."
            );
        }

        return inputParameters;
    }

    protected ChatCompletionOutput buildChatCompletionOutput(ModelProvider provider, Map<String, ?> dataAsMap, String llmResponseField) {

        List<Object> answers = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        String answerField = null;
        String errorField = "error";
        String defaultErrorMessageField = "message";

        if (llmResponseField != null) {
            answerField = llmResponseField;
            fillAnswersOrErrors(dataAsMap, answers, errors, answerField, errorField, defaultErrorMessageField);
        } else if (provider == ModelProvider.OPENAI) {
            List choices = (List) dataAsMap.get(CONNECTOR_OUTPUT_CHOICES);
            if (choices == null) {
                Map error = (Map) dataAsMap.get(CONNECTOR_OUTPUT_ERROR);
                errors = List.of((String) error.get(CONNECTOR_OUTPUT_MESSAGE));
            } else {
                Map firstChoiceMap = (Map) choices.get(0);
                // log.info("Choices: {}", firstChoiceMap.toString());
                Map message = (Map) firstChoiceMap.get(CONNECTOR_OUTPUT_MESSAGE);
                /*
                log
                    .info(
                        "role: {}, content: {}",
                        message.get(CONNECTOR_OUTPUT_MESSAGE_ROLE),
                        message.get(CONNECTOR_OUTPUT_MESSAGE_CONTENT)
                    );*/
                answers = List.of(message.get(CONNECTOR_OUTPUT_MESSAGE_CONTENT));
            }
        } else if (provider == ModelProvider.BEDROCK) {
            // Handle Bedrock model responses (supports both legacy completion and newer content/text formats)

            Object contentObj = dataAsMap.get(BEDROCK_CONTENT_FIELD);
            if (contentObj == null) {
                // Legacy completion-style format
                Object completion = dataAsMap.get(BEDROCK_COMPLETION_FIELD);
                if (completion != null) {
                    answers.add(completion.toString());
                } else {
                    errors.add("Unsupported Bedrock response format: " + dataAsMap.keySet());
                    log.error("Unknown Bedrock response format: {}", dataAsMap);
                }
            } else {
                // Fail-fast checks for new content/text format
                if (!(contentObj instanceof List<?> contentList)) {
                    errors.add("Unexpected type for '" + BEDROCK_CONTENT_FIELD + "' in Bedrock response.");
                } else if (contentList.isEmpty()) {
                    errors.add("Empty content list in Bedrock response.");
                } else {
                    Object first = contentList.get(0);
                    if (!(first instanceof Map<?, ?> firstMap)) {
                        errors.add("Unexpected content format in Bedrock response.");
                    } else {
                        Object text = firstMap.get(BEDROCK_TEXT_FIELD);
                        if (text == null) {
                            errors.add("Bedrock content response missing '" + BEDROCK_TEXT_FIELD + "' field.");
                        } else {
                            answers.add(text.toString());
                        }
                    }
                }
            }
        } else if (provider == ModelProvider.COHERE) {
            answerField = "text";
            fillAnswersOrErrors(dataAsMap, answers, errors, answerField, errorField, defaultErrorMessageField);
        } else if (provider == ModelProvider.BEDROCK_CONVERSE) {
            Map output = (Map) dataAsMap.get("output");
            Map message = (Map) output.get("message");
            if (message != null) {
                List content = (List) message.get("content");
                String answer = (String) ((Map) content.get(0)).get("text");
                answers.add(answer);
            } else {
                Map error = (Map) output.get("error");
                if (error == null) {
                    throw new RuntimeException("Unexpected output: " + output);
                }
                errors.add((String) error.get("message"));
            }
        } else {
            throw new IllegalArgumentException(
                "Unknown/unsupported model provider: " + provider + ".  You must provide a valid model provider or llm_response_field."
            );
        }

        return new ChatCompletionOutput(answers, errors);
    }

    private void fillAnswersOrErrors(
        final Map<String, ?> dataAsMap,
        List<Object> answers,
        List<String> errors,
        String answerField,
        String errorField,
        String defaultErrorMessageField
    ) {
        String response = (String) dataAsMap.get(answerField);
        if (response != null) {
            answers.add(response);
        } else {
            Map error = (Map) dataAsMap.get(errorField);
            if (error != null && error.get(defaultErrorMessageField) != null) {
                errors.add((String) error.get(defaultErrorMessageField));
            } else {
                errors.add("Unknown error or response.");
                log.error("{}", dataAsMap);
            }
        }
    }
}
