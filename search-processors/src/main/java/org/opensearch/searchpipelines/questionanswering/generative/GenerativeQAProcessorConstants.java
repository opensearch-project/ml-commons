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

import org.opensearch.common.settings.Setting;

public class GenerativeQAProcessorConstants {

    // Identifier for the generative QA request processor
    public static final String REQUEST_PROCESSOR_TYPE = "question_rewrite";

    // Identifier for the generative QA response processor
    public static final String RESPONSE_PROCESSOR_TYPE = "retrieval_augmented_generation";

    // The model_id of the model registered and deployed in OpenSearch.
    public static final String CONFIG_NAME_MODEL_ID = "model_id";

    // The name of the model supported by an LLM, e.g. "gpt-3.5" in OpenAI.
    public static final String CONFIG_NAME_LLM_MODEL = "llm_model";

    // The field in search results that contain the context to be sent to the LLM.
    public static final String CONFIG_NAME_CONTEXT_FIELD_LIST = "context_field_list";

    public static final Setting<Boolean> RAG_PIPELINE_FEATURE_ENABLED = Setting
        .boolSetting("plugins.ml_commons.rag_pipeline_feature_enabled", false, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final String FEATURE_NOT_ENABLED_ERROR_MSG = RAG_PIPELINE_FEATURE_ENABLED.getKey() + " is not enabled.";
}
