/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.test.OpenSearchTestCase;

public class SearchTemplateFillToolValidatorTests extends OpenSearchTestCase {

    private MLFeatureEnabledSetting featureSetting;

    @Before
    public void setup() {
        featureSetting = mock(MLFeatureEnabledSetting.class);
        when(featureSetting.isAgenticSearchTemplateEnabled()).thenReturn(true);
    }

    private static Map<String, String> validParams() {
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "m1");
        params.put("_llm_interface", "bedrock/converse/claude");
        return params;
    }

    private static Map<String, String> validConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("template_ids", "[\"product_search\"]");
        return config;
    }

    private static MLToolSpec fillTool(Map<String, String> params) {
        return fillTool(params, validConfig());
    }

    private static MLToolSpec fillTool(Map<String, String> params, Map<String, String> config) {
        return MLToolSpec.builder().type("SearchTemplateFillTool").parameters(params).configMap(config).build();
    }

    @Test
    public void validTool_passes() {
        SearchTemplateFillToolValidator.validate(List.of(fillTool(validParams())), featureSetting);
    }

    @Test
    public void otherTools_areIgnored_evenWhenDisabled() {
        when(featureSetting.isAgenticSearchTemplateEnabled()).thenReturn(false);
        SearchTemplateFillToolValidator.validate(List.of(MLToolSpec.builder().type("QueryPlanningTool").build()), featureSetting);
        SearchTemplateFillToolValidator.validate(null, featureSetting);
    }

    @Test
    public void featureDisabled_rejectsWithForbidden() {
        when(featureSetting.isAgenticSearchTemplateEnabled()).thenReturn(false);
        OpenSearchStatusException e = expectThrows(
            OpenSearchStatusException.class,
            () -> SearchTemplateFillToolValidator.validate(List.of(fillTool(validParams())), featureSetting)
        );
        assertEquals(RestStatus.FORBIDDEN, e.status());
        assertTrue(e.getMessage().contains("agentic_search_template_enabled"));
    }

    @Test
    public void missingModelId_rejected() {
        Map<String, String> params = validParams();
        params.remove("model_id");
        expectThrows(
            IllegalArgumentException.class,
            () -> SearchTemplateFillToolValidator.validate(List.of(fillTool(params)), featureSetting)
        );
    }

    @Test
    public void missingOrUnsupportedLlmInterface_rejected() {
        Map<String, String> params = validParams();
        params.remove("_llm_interface");
        expectThrows(
            IllegalArgumentException.class,
            () -> SearchTemplateFillToolValidator.validate(List.of(fillTool(params)), featureSetting)
        );
        params.put("_llm_interface", "bedrock/converse/deepseek_r1");
        expectThrows(
            IllegalArgumentException.class,
            () -> SearchTemplateFillToolValidator.validate(List.of(fillTool(params)), featureSetting)
        );
    }

    @Test
    public void missingTemplateIds_rejected() {
        expectThrows(
            IllegalArgumentException.class,
            () -> SearchTemplateFillToolValidator.validate(List.of(fillTool(validParams(), new HashMap<>())), featureSetting)
        );
    }

    @Test
    public void templateIdsInParameters_rejected() {
        // Execution parameters override tool parameters, so template ids there could be replaced per request.
        Map<String, String> params = validParams();
        params.put("template_ids", "[\"product_search\"]");
        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> SearchTemplateFillToolValidator.validate(List.of(fillTool(params, validConfig())), featureSetting)
        );
        assertTrue(e.getMessage().contains("config"));
    }

    @Test
    public void configValues_countAsEffectiveSettings() {
        Map<String, String> params = new HashMap<>();
        Map<String, String> config = validConfig();
        config.put("model_id", "m1");
        config.put("_llm_interface", "openai/v1/chat/completions");
        SearchTemplateFillToolValidator.validate(List.of(fillTool(params, config)), featureSetting);
        // A config value overrides the parameter at run time, so an unsupported one there is rejected.
        config.put("_llm_interface", "bedrock/converse/deepseek_r1");
        expectThrows(
            IllegalArgumentException.class,
            () -> SearchTemplateFillToolValidator.validate(List.of(fillTool(validParams(), config)), featureSetting)
        );
    }
}
