/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_SEARCH_TEMPLATE_DISABLED_MESSAGE;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TEMPLATE_ID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agenticsearch.MLGetAgenticSearchTemplateAction;
import org.opensearch.ml.common.transport.agenticsearch.MLGetAgenticSearchTemplateRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLGetAgenticSearchTemplateAction extends BaseRestHandler {
    private static final String ACTION_NAME = "ml_get_agentic_search_template_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLGetAgenticSearchTemplateAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(
                    RestRequest.Method.GET,
                    String.format(Locale.ROOT, "%s/agentic_search_templates/{%s}", ML_BASE_URI, PARAMETER_TEMPLATE_ID)
                )
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLGetAgenticSearchTemplateRequest getRequest = getRequest(request);
        return channel -> client.execute(MLGetAgenticSearchTemplateAction.INSTANCE, getRequest, new RestToXContentListener<>(channel));
    }

    @VisibleForTesting
    MLGetAgenticSearchTemplateRequest getRequest(RestRequest request) {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException("Agent framework is disabled");
        }
        if (!mlFeatureEnabledSetting.isAgenticSearchTemplateEnabled()) {
            throw new IllegalStateException(ML_COMMONS_AGENTIC_SEARCH_TEMPLATE_DISABLED_MESSAGE);
        }
        String templateId = request.param(PARAMETER_TEMPLATE_ID);
        if (templateId == null || templateId.trim().isEmpty()) {
            throw new IllegalArgumentException("Template id is required");
        }
        return new MLGetAgenticSearchTemplateRequest(templateId);
    }
}
