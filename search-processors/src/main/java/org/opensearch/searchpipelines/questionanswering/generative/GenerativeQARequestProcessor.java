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

import java.util.Map;
import java.util.function.BooleanSupplier;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.ingest.ConfigurationUtils;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.search.pipeline.AbstractProcessor;
import org.opensearch.search.pipeline.Processor;
import org.opensearch.search.pipeline.SearchRequestProcessor;

/**
 * Defines the request processor for generative QA search pipelines.
 */
public class GenerativeQARequestProcessor extends AbstractProcessor implements SearchRequestProcessor {

    private String modelId;
    private final BooleanSupplier featureFlagSupplier;

    protected GenerativeQARequestProcessor(
        String tag,
        String description,
        boolean ignoreFailure,
        String modelId,
        BooleanSupplier supplier
    ) {
        super(tag, description, ignoreFailure);
        this.modelId = modelId;
        this.featureFlagSupplier = supplier;
    }

    @Override
    public SearchRequest processRequest(SearchRequest request) throws Exception {

        // TODO Use chat history to rephrase the question with full conversation context.

        if (!featureFlagSupplier.getAsBoolean()) {
            throw new MLException(GenerativeQAProcessorConstants.FEATURE_NOT_ENABLED_ERROR_MSG);
        }

        return request;
    }

    @Override
    public String getType() {
        return GenerativeQAProcessorConstants.REQUEST_PROCESSOR_TYPE;
    }

    public static final class Factory implements Processor.Factory<SearchRequestProcessor> {

        private final BooleanSupplier featureFlagSupplier;

        public Factory(BooleanSupplier supplier) {
            this.featureFlagSupplier = supplier;
        }

        @Override
        public SearchRequestProcessor create(
            Map<String, Processor.Factory<SearchRequestProcessor>> processorFactories,
            String tag,
            String description,
            boolean ignoreFailure,
            Map<String, Object> config,
            PipelineContext pipelineContext
        ) throws Exception {
            if (featureFlagSupplier.getAsBoolean()) {
                return new GenerativeQARequestProcessor(
                    tag,
                    description,
                    ignoreFailure,
                    ConfigurationUtils
                        .readStringProperty(
                            GenerativeQAProcessorConstants.REQUEST_PROCESSOR_TYPE,
                            tag,
                            config,
                            GenerativeQAProcessorConstants.CONFIG_NAME_MODEL_ID
                        ),
                    this.featureFlagSupplier
                );
            } else {
                throw new MLException(GenerativeQAProcessorConstants.FEATURE_NOT_ENABLED_ERROR_MSG);
            }
        }
    }
}
