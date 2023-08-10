/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.searchpipelines.questionanswering.generative;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.TotalHits;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.client.Client;
import org.opensearch.common.document.DocumentField;
//import org.opensearch.index.query.QueryBuilder;
import org.opensearch.ingest.ConfigurationUtils;
//import org.opensearch.neuralsearch.query.NeuralQueryBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.pipeline.AbstractProcessor;
import org.opensearch.search.pipeline.Processor;
import org.opensearch.search.pipeline.SearchResponseProcessor;
import org.opensearch.searchpipelines.questionanswering.generative.llm.ChatCompletionOutput;
import org.opensearch.searchpipelines.questionanswering.generative.llm.Llm;
import org.opensearch.searchpipelines.questionanswering.generative.llm.LlmIOUtil;
import org.opensearch.searchpipelines.questionanswering.generative.llm.ModelLocator;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines the response processor for generative QA search pipelines.
 *
 */
public class GenerativeQAResponseProcessor extends AbstractProcessor implements SearchResponseProcessor {

    private static final Logger logger = LogManager.getLogger();
    public static final String TYPE = "generative_qa";
    public static final String CONFIG_NAME_OPENSEARCH_MODEL_ID = "opensearch_model_id";
    public static final String CONFIG_NAME_LLM_MODEL = "llm_model";
    public static final String CONFIG_NAME_CONTEXT_FIELD = "context_field";

    // TODO Add "interaction_count".  This is how far back in chat history we want to go back when calling LLM.

    private final String openSearchModelId;
    private final String llmModel;
    private final String contextField;

    private final Client client;

    @Getter
    @Setter
    // Mainly for unit testing purpose
    private Llm llm;

    protected GenerativeQAResponseProcessor(Client client, String tag, String description, boolean ignoreFailure,
        String openSearchModelId, String llmModel, String contextField) {
        super(tag, description, ignoreFailure);
        Preconditions.checkNotNull(openSearchModelId);
        this.openSearchModelId = openSearchModelId;
        this.llmModel = llmModel;
        this.contextField = contextField;
        this.llm = ModelLocator.getRemoteLlm(openSearchModelId, client);
        this.client = client;
    }

    @Override
    public SearchResponse processResponse(SearchRequest request, SearchResponse response) throws Exception {

        logger.info("Entering processResponse.");

        List<String> chatHistory = getChatHistory(request);
        GenerativeQAParameters params = GenerativeQAParamUtil.getGenerativeQAParameters(request);
        String llmQuestion = params.getLlmQuestion();
        String llmModel = params.getLlmModel();
        String conversationId = params.getConversationId();
        logger.info("LLM question: {}, LLM model {}, conversation id: {}", llmQuestion, llmModel, conversationId);

        ChatCompletionOutput output = llm.createChatCompletion(LlmIOUtil.createChatCompletionInput(llmModel, llmQuestion, chatHistory, getSearchResults(response)));

        return insertAnswer(response, output.getAnswer());
    }

    @Override
    public String getType() {
        return GenerativeQAResponseProcessor.TYPE;
    }

    @Deprecated
    private SearchHit getNewHit(String answer) throws IOException {
        Map<String, Object> m = new HashMap<>();
        m.put("answer", answer);
        SearchHit hit = new SearchHit(0, "_doc_id", null, null);
        hit.setDocumentField("question_answer", new DocumentField("question_answer", List.of(m)));
        hit.score(1.0f);
        return hit;
    }

    /**
     * Inject the generative answer into SearchResponse as a SearchHit.
     *
     * @param response
     * @param hit
     * @return
     */
    @Deprecated
    SearchResponse insertNewSearchHit(SearchResponse response, SearchHit hit) {
        SearchResponse newResponse = null;

        SearchResponseSections internal = response.getInternalResponse();

        SearchHit[] hits = internal.hits().getHits();
        SearchHit[] hits2 = new SearchHit[hits.length+1];
        for (int i = 0; i < hits.length; i++) {
            hits2[i] = hits[i];
        }
        hits2[hits.length]  = hit;
        SearchResponseSections newInternal = new SearchResponseSections(new SearchHits(hits2, new TotalHits(hits2.length,
            TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), internal.hits().getMaxScore()), internal.aggregations(), internal.suggest(),
            internal.timedOut(), internal.terminatedEarly(), null, internal.getNumReducePhases());

        newResponse = new SearchResponse(newInternal, response.getScrollId(), response.getTotalShards(), response.getSuccessfulShards(),
            response.getSkippedShards(), response.getSuccessfulShards(), response.getShardFailures(), response.getClusters());

        return  newResponse;
    }

    private SearchResponse insertAnswer(SearchResponse response, String answer) {
        return new GenerativeSearchResponse(answer, response.getInternalResponse(), response.getScrollId(), response.getTotalShards(), response.getSuccessfulShards(),
            response.getSkippedShards(), response.getSuccessfulShards(), response.getShardFailures(), response.getClusters());
    }

    // TODO Integrate with Conversational Memory
    private List<String> getChatHistory(SearchRequest request) {
        return new ArrayList<>();
    }

    private List<String> getSearchResults(SearchResponse response) {
        List<String> searchResults = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> docSourceMap = hit.getSourceAsMap();
            Object context = docSourceMap.get(contextField);
            if (context == null) {
                logger.error("Context " + contextField + " not found in search hit " + hit);
                // TODO throw a more meaningful error here?
                throw new RuntimeException();
            }
            searchResults.add(context.toString());
        }
        return searchResults;
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
            String openSearchModelId = ConfigurationUtils.readOptionalStringProperty(TYPE, tag, config, CONFIG_NAME_OPENSEARCH_MODEL_ID);
            String llm_model = ConfigurationUtils.readOptionalStringProperty(TYPE, tag, config, CONFIG_NAME_LLM_MODEL);
            String context_field = ConfigurationUtils.readStringProperty(TYPE, tag, config, CONFIG_NAME_CONTEXT_FIELD);
            logger.info("opensearch model_id {}, llm_model {}, context_field {}", openSearchModelId, llm_model, context_field);
            return new GenerativeQAResponseProcessor(client, tag, description, ignoreFailure, openSearchModelId, llm_model, context_field);
        }
    }
}
