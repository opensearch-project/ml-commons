/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.searchpipelines.questionanswering.generative.llm;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.client.Client;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.ml.client.MachineLearningClient;
import org.opensearch.ml.client.MachineLearningNodeClient;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Wrapper for talking to LLMs via OpenSearch HttpConnector.
 */
public class OpenSearchChatConnector implements Llm {

    private static final Logger log = LogManager.getLogger();

    private static final String roleUser = "user";

    private final String openSearchModelId;

    private MachineLearningClient mlClient;

    public OpenSearchChatConnector(String openSearchModelId, Client client) {
        checkNotNull(openSearchModelId);
        this.openSearchModelId = openSearchModelId;
        this.mlClient = new MachineLearningNodeClient(client);

    }

    @VisibleForTesting
    void setMlClient(MachineLearningClient mlClient) {
        this.mlClient = mlClient;
    }

    /**
     * Use ChatCompletion API to generate an answer.
     *
     * @param input
     * @return
     */
    @Override
    public ChatCompletionOutput createChatCompletion(ChatCompletionInput input) {

        OpenSearchChatCompletionInput openAiInput = (OpenSearchChatCompletionInput) input;
        Map<String, String> inputParameters = new HashMap<>();
        inputParameters.put("model", openAiInput.getModel());
        inputParameters.put("messages", buildMessageParameter(openAiInput.getQuestion(), openAiInput.getChatHistory(), openAiInput.getContexts()));
        MLInputDataset dataset = RemoteInferenceInputDataSet.builder().parameters(inputParameters).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(dataset).build();
        ActionFuture<MLOutput> future = mlClient.predict(this.openSearchModelId, mlInput);
        ModelTensorOutput modelOutput = (ModelTensorOutput) future.actionGet();

        // Response from OpenAI
        Map<String, ?> dataAsMap = modelOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        log.info("dataAsMap: {}", dataAsMap.toString());

        // TODO dataAsMap can be null or con contain information such as throttling.  Handle non-happy cases.

        List choices = (List) dataAsMap.get("choices");
        Map firstChoiceMap = (Map) choices.get(0);
        log.info("Choices: {}", firstChoiceMap.toString());
        Map message = (Map) firstChoiceMap.get("message");
        log.info("role: {}, content: {}", message.get("role"), message.get("content"));

        return new OpenSearchChatCompletionOutput((String) message.get("content"));
    }

    @VisibleForTesting
    String buildMessageParameter(String question, List<String> chatHistory, List<String> contexts) {
        // TODO better prompt template management is needed here.
        String instructions = "Generate a concise and informative answer in less than 100 words for the given question, taking into context: "
            + "- An enumerated list of search results"
            + "- A rephrase of the question that was used to generate the search results"
            + "- The conversation history"
            + "Cite search results using [${number}] notation."
            + "Do not repeat yourself, and NEVER repeat anything in the chat history."
            + "If there are any necessary steps or procedures in your answer, enumerate them.";
        StringBuffer sb = new StringBuffer();
        sb.append("[\n");
        sb.append(formatMessage(roleUser, instructions));
        sb.append(",\n");
        for (String result : contexts) {
            sb.append(formatMessage(roleUser, "SEARCH RESULTS: " + result));
            sb.append(",\n");
        }
        sb.append(formatMessage(roleUser, "QUESTION: " + question));
        sb.append(",\n");
        sb.append(formatMessage(roleUser, "ANSWER:"));
        sb.append("\n");
        sb.append("]");
        return sb.toString();
    }

    private String formatMessage(String role, String content) {
        return String.format(Locale.ROOT, "{\"role\": \"%s\", \"content\": \"%s\"}", role, StringEscapeUtils.escapeJson(content));
    }
}
