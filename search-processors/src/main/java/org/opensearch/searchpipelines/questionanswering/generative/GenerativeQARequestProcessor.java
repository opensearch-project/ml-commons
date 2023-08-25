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

import org.opensearch.action.search.SearchRequest;
import org.opensearch.ingest.ConfigurationUtils;
import org.opensearch.search.pipeline.AbstractProcessor;
import org.opensearch.search.pipeline.Processor;
import org.opensearch.search.pipeline.SearchRequestProcessor;

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
