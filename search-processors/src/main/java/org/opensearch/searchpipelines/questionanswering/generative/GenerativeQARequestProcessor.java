/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.searchpipelines.questionanswering.generative;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.Client;
import org.opensearch.ingest.ConfigurationUtils;
import org.opensearch.search.pipeline.AbstractProcessor;
import org.opensearch.search.pipeline.Processor;
import org.opensearch.search.pipeline.SearchRequestProcessor;
import org.opensearch.search.pipeline.SearchResponseProcessor;

import java.util.Map;

/**
 * Defines the request processor for generative QA search pipelines.
 */
public class GenerativeQARequestProcessor extends AbstractProcessor implements SearchRequestProcessor {

    // TODO Use a bracket processor to combine this processor and 'generative_qa' response processor.
    public static final String TYPE = "generative_question";
    public static final String CONFIG_NAME_MODEL_ID = "model_id";

    private String modelId;

    protected GenerativeQARequestProcessor(String tag, String description, boolean ignoreFailure, String modelId) {
        super(tag, description, ignoreFailure);
        this.modelId = modelId;
    }

    @Override
    public SearchRequest processRequest(SearchRequest request) throws Exception {

        // TODO Use chat history to rephrase the question with full conversation context.

        return request;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory implements Processor.Factory<SearchRequestProcessor> {

        @Override
        public SearchRequestProcessor create(
            Map<String, Processor.Factory<SearchRequestProcessor>> processorFactories,
            String tag,
            String description,
            boolean ignoreFailure,
            Map<String, Object> config,
            PipelineContext pipelineContext
        ) throws Exception {
            return new GenerativeQARequestProcessor(tag, description, ignoreFailure,
                ConfigurationUtils.readStringProperty(TYPE, tag, config, CONFIG_NAME_MODEL_ID)
            );
        }
    }
}
