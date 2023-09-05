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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.ingest.ConfigurationUtils;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.search.SearchHit;
import org.opensearch.search.pipeline.AbstractProcessor;
import org.opensearch.search.pipeline.Processor;
import org.opensearch.search.pipeline.SearchResponseProcessor;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParamUtil;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParameters;
import org.opensearch.searchpipelines.questionanswering.generative.client.ConversationalMemoryClient;
import org.opensearch.searchpipelines.questionanswering.generative.llm.ChatCompletionOutput;
import org.opensearch.searchpipelines.questionanswering.generative.llm.Llm;
import org.opensearch.searchpipelines.questionanswering.generative.llm.LlmIOUtil;
import org.opensearch.searchpipelines.questionanswering.generative.llm.ModelLocator;
import org.opensearch.searchpipelines.questionanswering.generative.prompt.PromptUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.opensearch.ingest.ConfigurationUtils.newConfigurationException;

/**
 * Defines the response processor for generative QA search pipelines.
 *
 */
@Log4j2
public class GenerativeQAResponseProcessor extends AbstractProcessor implements SearchResponseProcessor {

    private static final int DEFAULT_CHAT_HISTORY_WINDOW = 10;

    // TODO Add "interaction_count".  This is how far back in chat history we want to go back when calling LLM.

    private final String llmModel;
    private final List<String> contextFields;

    @Setter
    private ConversationalMemoryClient memoryClient;

    @Getter
    @Setter
    // Mainly for unit testing purpose
    private Llm llm;

    protected GenerativeQAResponseProcessor(Client client, String tag, String description, boolean ignoreFailure,
        Llm llm, String llmModel, List<String> contextFields) {
        super(tag, description, ignoreFailure);
        this.llmModel = llmModel;
        this.contextFields = contextFields;
        this.llm = llm;
        this.memoryClient = new ConversationalMemoryClient(client);
    }

    @Override
    public SearchResponse processResponse(SearchRequest request, SearchResponse response) throws Exception {

        log.info("Entering processResponse.");

        GenerativeQAParameters params = GenerativeQAParamUtil.getGenerativeQAParameters(request);
        String llmQuestion = params.getLlmQuestion();
        String llmModel = params.getLlmModel() == null ? this.llmModel : params.getLlmModel();
        String conversationId = params.getConversationId();
        log.info("LLM question: {}, LLM model {}, conversation id: {}", llmQuestion, llmModel, conversationId);
        List<Interaction> chatHistory = (conversationId == null) ? Collections.emptyList() : memoryClient.getInteractions(conversationId, DEFAULT_CHAT_HISTORY_WINDOW);
        List<String> searchResults = getSearchResults(response);
        ChatCompletionOutput output = llm.doChatCompletion(LlmIOUtil.createChatCompletionInput(llmModel, llmQuestion, chatHistory, searchResults));
        String answer = (String) output.getAnswers().get(0);

        String interactionId = null;
        if (conversationId != null) {
            interactionId = memoryClient.createInteraction(conversationId, llmQuestion, PromptUtil.DEFAULT_CHAT_COMPLETION_PROMPT_TEMPLATE, answer,
                GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE, jsonArrayToString(searchResults));
        }

        return insertAnswer(response, answer, interactionId);
    }

    @Override
    public String getType() {
        return GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE;
    }

    private SearchResponse insertAnswer(SearchResponse response, String answer, String interactionId) {

        // TODO return the interaction id in the response.

        return new GenerativeSearchResponse(answer, response.getInternalResponse(), response.getScrollId(), response.getTotalShards(), response.getSuccessfulShards(),
            response.getSkippedShards(), response.getSuccessfulShards(), response.getShardFailures(), response.getClusters());
    }

    private List<String> getSearchResults(SearchResponse response) {
        List<String> searchResults = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> docSourceMap = hit.getSourceAsMap();
            for (String contextField : contextFields) {
                Object context = docSourceMap.get(contextField);
                if (context == null) {
                    log.error("Context " + contextField + " not found in search hit " + hit);
                    // TODO throw a more meaningful error here?
                    throw new RuntimeException();
                }
                searchResults.add(context.toString());
            }
        }
        return searchResults;
    }

    private static String jsonArrayToString(List<String> listOfStrings) {
        JsonArray array = new JsonArray(listOfStrings.size());
        listOfStrings.forEach(array::add);
        return array.toString();
    }

    public static final class Factory implements Processor.Factory<SearchResponseProcessor> {

        private final Client client;

        public Factory(Client client) {
            this.client = client;
        }

        @Override
        public SearchResponseProcessor create(
            Map<String, Processor.Factory<SearchResponseProcessor>> processorFactories,
            String tag,
            String description,
            boolean ignoreFailure,
            Map<String, Object> config,
            PipelineContext pipelineContext
        ) throws Exception {
            String modelId = ConfigurationUtils.readOptionalStringProperty(GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE, tag, config, GenerativeQAProcessorConstants.CONFIG_NAME_MODEL_ID);
            String llmModel = ConfigurationUtils.readOptionalStringProperty(GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE, tag, config, GenerativeQAProcessorConstants.CONFIG_NAME_LLM_MODEL);
            List<String> contextFields = ConfigurationUtils.readList(GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE, tag, config, GenerativeQAProcessorConstants.CONFIG_NAME_CONTEXT_FIELD_LIST);
            if (contextFields.isEmpty()) {
                throw newConfigurationException(GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE, tag, GenerativeQAProcessorConstants.CONFIG_NAME_CONTEXT_FIELD_LIST, "required property can't be empty.");
            }
            log.info("model_id {}, llm_model {}, context_field_list {}", modelId, llmModel, contextFields);
            return new GenerativeQAResponseProcessor(client, tag, description, ignoreFailure, ModelLocator.getLlm(modelId, client), llmModel, contextFields);
        }
    }
}
