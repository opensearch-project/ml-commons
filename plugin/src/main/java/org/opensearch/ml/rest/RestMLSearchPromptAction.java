/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.ML_PROMPT_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import org.opensearch.ml.common.MLPrompt;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLPromptSearchAction;

import com.google.common.collect.ImmutableList;

/**
 * Rest Action class that handles SEARCH REST API request
 */
public class RestMLSearchPromptAction extends AbstractMLSearchAction<MLPrompt> {
    private static final String ML_SEARCH_PROMPT_ACTION = "ml_search_prompt_action";
    private static final String SEARCH_PROMPT_PATH = ML_BASE_URI + "/prompts/_search";

    public RestMLSearchPromptAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        super(
            ImmutableList.of(SEARCH_PROMPT_PATH),
            ML_PROMPT_INDEX,
            MLPrompt.class,
            MLPromptSearchAction.INSTANCE,
            mlFeatureEnabledSetting
        );
    }

    @Override
    public String getName() {
        return ML_SEARCH_PROMPT_ACTION;
    }
}
