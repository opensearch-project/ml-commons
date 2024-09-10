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

import static org.opensearch.ingest.ConfigurationUtils.newConfigurationException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.ingest.ConfigurationUtils;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.search.SearchHit;
import org.opensearch.search.pipeline.AbstractProcessor;
import org.opensearch.search.pipeline.PipelineProcessingContext;
import org.opensearch.search.pipeline.Processor;
import org.opensearch.search.pipeline.SearchResponseProcessor;
import org.opensearch.searchpipelines.questionanswering.generative.client.ConversationalMemoryClient;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParamUtil;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParameters;
import org.opensearch.searchpipelines.questionanswering.generative.llm.ChatCompletionInput;
import org.opensearch.searchpipelines.questionanswering.generative.llm.ChatCompletionOutput;
import org.opensearch.searchpipelines.questionanswering.generative.llm.Llm;
import org.opensearch.searchpipelines.questionanswering.generative.llm.LlmIOUtil;
import org.opensearch.searchpipelines.questionanswering.generative.llm.ModelLocator;
import org.opensearch.searchpipelines.questionanswering.generative.prompt.PromptUtil;

import com.google.gson.JsonArray;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * Defines the response processor for generative QA search pipelines.
 *
 */
@Log4j2
public class GenerativeQAResponseProcessor extends AbstractProcessor implements SearchResponseProcessor {
    public static String IllegalArgumentMessage =
        "Please check the provided generative_qa_parameters are complete and non-null(https://opensearch.org/docs/latest/search-plugins/conversational-search/#rag-pipeline). Messages in the memory can not have Null value for input and response";
    private static final int DEFAULT_CHAT_HISTORY_WINDOW = 10;

    private static final int DEFAULT_PROCESSOR_TIME_IN_SECONDS = 30;

    private final String llmModel;
    private final List<String> contextFields;

    private final String systemPrompt;
    private final String userInstructions;

    @Setter
    private ConversationalMemoryClient memoryClient;

    @Getter
    @Setter
    // Mainly for unit testing purpose
    private Llm llm;

    private final BooleanSupplier featureFlagSupplier;

    protected GenerativeQAResponseProcessor(
        Client client,
        String tag,
        String description,
        boolean ignoreFailure,
        Llm llm,
        String llmModel,
        List<String> contextFields,
        String systemPrompt,
        String userInstructions,
        BooleanSupplier supplier
    ) {
        super(tag, description, ignoreFailure);
        this.llmModel = llmModel;
        this.contextFields = contextFields;
        this.systemPrompt = systemPrompt;
        this.userInstructions = userInstructions;
        this.llm = llm;
        this.memoryClient = new ConversationalMemoryClient(client);
        this.featureFlagSupplier = supplier;
    }

    @Override
    public SearchResponse processResponse(SearchRequest searchRequest, SearchResponse searchResponse) {
        // Synchronous call is no longer supported because this execution can occur on a transport thread.
        throw new UnsupportedOperationException();
    }

    @Override
    public void processResponseAsync(
        SearchRequest request,
        SearchResponse response,
        PipelineProcessingContext requestContext,
        ActionListener<SearchResponse> responseListener
    ) {
        log.debug("Entering processResponse.");

        if (!this.featureFlagSupplier.getAsBoolean()) {
            throw new MLException(GenerativeQAProcessorConstants.FEATURE_NOT_ENABLED_ERROR_MSG);
        }

        GenerativeQAParameters params = GenerativeQAParamUtil.getGenerativeQAParameters(request);

        Integer t = params.getTimeout();
        if (t == null || t == GenerativeQAParameters.SIZE_NULL_VALUE) {
            t = DEFAULT_PROCESSOR_TIME_IN_SECONDS;
        }
        final int timeout = t;
        log.debug("Timeout for this request: {} seconds.", timeout);

        String llmQuestion = params.getLlmQuestion();
        String llmModel = params.getLlmModel() == null ? this.llmModel : params.getLlmModel();
        if (llmModel == null) {
            throw new IllegalArgumentException("llm_model cannot be null.");
        }
        String conversationId = params.getConversationId();

        if (conversationId != null && !Strings.hasText(conversationId)) {
            throw new IllegalArgumentException("Empty conversation_id is not allowed.");
        }
        Instant start = Instant.now();
        Integer interactionSize = params.getInteractionSize();
        if (interactionSize == null || interactionSize == GenerativeQAParameters.SIZE_NULL_VALUE) {
            interactionSize = DEFAULT_CHAT_HISTORY_WINDOW;
        }
        log.debug("Using interaction size of {}", interactionSize);

        Integer topN = params.getContextSize();
        if (topN == null) {
            topN = GenerativeQAParameters.SIZE_NULL_VALUE;
        }
        List<String> searchResults = getSearchResults(response, topN);

        // See if the prompt is being overridden at the request level.
        String effectiveSystemPrompt = systemPrompt;
        String effectiveUserInstructions = userInstructions;
        if (params.getSystemPrompt() != null) {
            effectiveSystemPrompt = params.getSystemPrompt();
        }
        if (params.getUserInstructions() != null) {
            effectiveUserInstructions = params.getUserInstructions();
        }

        final List<Interaction> chatHistory = new ArrayList<>();
        if (conversationId == null) {
            doChatCompletion(
                LlmIOUtil
                    .createChatCompletionInput(
                        systemPrompt,
                        userInstructions,
                        llmModel,
                        llmQuestion,
                        chatHistory,
                        searchResults,
                        timeout,
                        params.getLlmResponseField()
                    ),
                null,
                llmQuestion,
                searchResults,
                response,
                responseListener
            );
        } else {
            final Instant memoryStart = Instant.now();
            memoryClient.getInteractions(conversationId, interactionSize, ActionListener.wrap(r -> {
                log.debug("getInteractions complete. ({})", getDuration(memoryStart));
                chatHistory.addAll(r);
                doChatCompletion(
                    LlmIOUtil
                        .createChatCompletionInput(
                            systemPrompt,
                            userInstructions,
                            llmModel,
                            llmQuestion,
                            chatHistory,
                            searchResults,
                            timeout,
                            params.getLlmResponseField()
                        ),
                    conversationId,
                    llmQuestion,
                    searchResults,
                    response,
                    responseListener
                );
            }, responseListener::onFailure));
        }
    }

    private void doChatCompletion(
        ChatCompletionInput input,
        String conversationId,
        String llmQuestion,
        List<String> searchResults,
        SearchResponse response,
        ActionListener<SearchResponse> responseListener
    ) {

        final Instant chatStart = Instant.now();
        llm.doChatCompletion(input, new ActionListener<>() {
            @Override
            public void onResponse(ChatCompletionOutput output) {
                log.debug("doChatCompletion complete. ({})", getDuration(chatStart));

                final String answer = getAnswer(output);
                final String errorMessage = getError(output);

                if (conversationId != null) {
                    final Instant memoryStart = Instant.now();
                    memoryClient
                        .createInteraction(
                            conversationId,
                            llmQuestion,
                            PromptUtil.getPromptTemplate(systemPrompt, userInstructions),
                            answer,
                            GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE,
                            Collections.singletonMap("metadata", jsonArrayToString(searchResults)),
                            ActionListener.wrap(r -> {
                                responseListener.onResponse(insertAnswer(response, answer, errorMessage, r));
                                log.info("Created a new interaction: {} ({})", r, getDuration(memoryStart));
                            }, responseListener::onFailure)
                        );

                } else {
                    responseListener.onResponse(insertAnswer(response, answer, errorMessage, null));
                }

            }

            @Override
            public void onFailure(Exception e) {
                responseListener.onFailure(e);
            }

            private String getError(ChatCompletionOutput output) {
                return output.isErrorOccurred() ? output.getErrors().get(0) : null;
            }

            private String getAnswer(ChatCompletionOutput output) {
                return output.isErrorOccurred() ? null : (String) output.getAnswers().get(0);
            }
        });
    }

    @Override
    public String getType() {
        return GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE;
    }

    private long getDuration(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }

    private SearchResponse insertAnswer(SearchResponse response, String answer, String errorMessage, String interactionId) {

        return new GenerativeSearchResponse(
            answer,
            errorMessage,
            response.getInternalResponse(),
            response.getScrollId(),
            response.getTotalShards(),
            response.getSuccessfulShards(),
            response.getSkippedShards(),
            response.getSuccessfulShards(),
            response.getShardFailures(),
            response.getClusters(),
            interactionId
        );
    }

    private List<String> getSearchResults(SearchResponse response, Integer topN) {
        List<String> searchResults = new ArrayList<>();
        SearchHit[] hits = response.getHits().getHits();
        int total = hits.length;
        int end = (topN != GenerativeQAParameters.SIZE_NULL_VALUE) ? Math.min(topN, total) : total;
        for (int i = 0; i < end; i++) {
            Map<String, Object> docSourceMap = hits[i].getSourceAsMap();
            for (String contextField : contextFields) {
                Object context = docSourceMap.get(contextField);
                if (context == null) {
                    throw new RuntimeException("Context " + contextField + " not found in search hit " + hits[i]);
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
        private final BooleanSupplier featureFlagSupplier;

        public Factory(Client client, BooleanSupplier supplier) {
            this.client = client;
            this.featureFlagSupplier = supplier;
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
            if (this.featureFlagSupplier.getAsBoolean()) {
                String modelId = ConfigurationUtils
                    .readOptionalStringProperty(
                        GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE,
                        tag,
                        config,
                        GenerativeQAProcessorConstants.CONFIG_NAME_MODEL_ID
                    );
                String llmModel = ConfigurationUtils
                    .readOptionalStringProperty(
                        GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE,
                        tag,
                        config,
                        GenerativeQAProcessorConstants.CONFIG_NAME_LLM_MODEL
                    );
                List<String> contextFields = ConfigurationUtils
                    .readList(
                        GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE,
                        tag,
                        config,
                        GenerativeQAProcessorConstants.CONFIG_NAME_CONTEXT_FIELD_LIST
                    );
                if (contextFields.isEmpty()) {
                    throw newConfigurationException(
                        GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE,
                        tag,
                        GenerativeQAProcessorConstants.CONFIG_NAME_CONTEXT_FIELD_LIST,
                        "required property can't be empty."
                    );
                }
                String systemPrompt = ConfigurationUtils
                    .readOptionalStringProperty(
                        GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE,
                        tag,
                        config,
                        GenerativeQAProcessorConstants.CONFIG_NAME_SYSTEM_PROMPT
                    );
                String userInstructions = ConfigurationUtils
                    .readOptionalStringProperty(
                        GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE,
                        tag,
                        config,
                        GenerativeQAProcessorConstants.CONFIG_NAME_USER_INSTRUCTIONS
                    );
                return new GenerativeQAResponseProcessor(
                    client,
                    tag,
                    description,
                    ignoreFailure,
                    ModelLocator.getLlm(modelId, client),
                    llmModel,
                    contextFields,
                    systemPrompt,
                    userInstructions,
                    featureFlagSupplier
                );
            } else {
                throw new MLException(GenerativeQAProcessorConstants.FEATURE_NOT_ENABLED_ERROR_MSG);
            }
        }
    }
}
