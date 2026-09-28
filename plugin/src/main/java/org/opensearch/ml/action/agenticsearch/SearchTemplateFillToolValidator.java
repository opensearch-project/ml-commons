/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_SEARCH_TEMPLATE_DISABLED_MESSAGE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.engine.tools.SearchTemplateFillTool;

/**
 * Registration-time checks for agents that use {@link SearchTemplateFillTool}, so a misconfigured or
 * disabled tool is rejected when the agent is registered or updated rather than on the first search.
 */
public final class SearchTemplateFillToolValidator {

    private SearchTemplateFillToolValidator() {}

    /**
     * @throws OpenSearchStatusException if a tool is a {@link SearchTemplateFillTool} while the feature is disabled
     * @throws IllegalArgumentException if such a tool is missing a model, a supported {@code _llm_interface}, or
     *     template ids in its {@code config}
     */
    public static void validate(List<MLToolSpec> tools, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        if (tools == null) {
            return;
        }
        for (MLToolSpec tool : tools) {
            if (!SearchTemplateFillTool.TYPE.equals(tool.getType())) {
                continue;
            }
            if (!mlFeatureEnabledSetting.isAgenticSearchTemplateEnabled()) {
                throw new OpenSearchStatusException(ML_COMMONS_AGENTIC_SEARCH_TEMPLATE_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
            }
            Map<String, String> parameters = tool.getParameters() == null ? Map.of() : tool.getParameters();
            Map<String, String> config = tool.getConfigMap() == null ? Map.of() : tool.getConfigMap();
            // Execution parameters override a tool's parameters but not its config, so the templates a caller
            // may fill are fixed in config: otherwise an execute request could name any registered template.
            if (parameters.containsKey(SearchTemplateFillTool.TEMPLATE_IDS_FIELD)
                || !config.containsKey(SearchTemplateFillTool.TEMPLATE_IDS_FIELD)) {
                throw new IllegalArgumentException(
                    SearchTemplateFillTool.TYPE
                        + " requires "
                        + SearchTemplateFillTool.TEMPLATE_IDS_FIELD
                        + " in the tool's config, not its parameters, so that an execute request cannot override it"
                );
            }
            SearchTemplateFillTool.Factory.parseTemplateIds(config.get(SearchTemplateFillTool.TEMPLATE_IDS_FIELD));

            // At run time config values take precedence over parameters, so validate the effective values.
            Map<String, String> effective = new HashMap<>(parameters);
            effective.putAll(config);
            String modelId = effective.get(SearchTemplateFillTool.MODEL_ID_FIELD);
            if (modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException(SearchTemplateFillTool.TYPE + " requires a model_id");
            }
            SearchTemplateFillTool.Factory.forcedToolFunctionCalling(effective.get(LLM_INTERFACE));
        }
    }
}
