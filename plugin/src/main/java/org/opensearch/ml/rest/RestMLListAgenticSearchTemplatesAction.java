/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_SEARCH_TEMPLATE_DISABLED_MESSAGE;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agenticsearch.MLListAgenticSearchTemplatesAction;
import org.opensearch.ml.common.transport.agenticsearch.MLListAgenticSearchTemplatesRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLListAgenticSearchTemplatesAction extends BaseRestHandler {
    private static final String ACTION_NAME = "ml_list_agentic_search_templates_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLListAgenticSearchTemplatesAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/agentic_search_templates", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLListAgenticSearchTemplatesRequest listRequest = getRequest(request);
        return channel -> client.execute(MLListAgenticSearchTemplatesAction.INSTANCE, listRequest, new RestToXContentListener<>(channel));
    }

    @VisibleForTesting
    MLListAgenticSearchTemplatesRequest getRequest(RestRequest request) {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException("Agent framework is disabled");
        }
        if (!mlFeatureEnabledSetting.isAgenticSearchTemplateEnabled()) {
            throw new IllegalStateException(ML_COMMONS_AGENTIC_SEARCH_TEMPLATE_DISABLED_MESSAGE);
        }
        int from = request.paramAsInt("from", 0);
        int size = request.paramAsInt("size", 10);
        if (from < 0) {
            throw new IllegalArgumentException("Parameter 'from' must be non-negative");
        }
        if (size <= 0 || size > 1000) {
            throw new IllegalArgumentException("Parameter 'size' must be between 1 and 1000");
        }
        return new MLListAgenticSearchTemplatesRequest(from, size);
    }
}
