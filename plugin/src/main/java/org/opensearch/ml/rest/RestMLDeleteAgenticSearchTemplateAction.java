/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TEMPLATE_ID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agenticsearch.MLDeleteAgenticSearchTemplateAction;
import org.opensearch.ml.common.transport.agenticsearch.MLDeleteAgenticSearchTemplateRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLDeleteAgenticSearchTemplateAction extends BaseRestHandler {
    private static final String ACTION_NAME = "ml_delete_agentic_search_template_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLDeleteAgenticSearchTemplateAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
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
                    RestRequest.Method.DELETE,
                    String.format(Locale.ROOT, "%s/agentic_search_templates/{%s}", ML_BASE_URI, PARAMETER_TEMPLATE_ID)
                )
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLDeleteAgenticSearchTemplateRequest deleteRequest = getRequest(request);
        return channel -> client
            .execute(MLDeleteAgenticSearchTemplateAction.INSTANCE, deleteRequest, new RestToXContentListener<>(channel));
    }

    @VisibleForTesting
    MLDeleteAgenticSearchTemplateRequest getRequest(RestRequest request) {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException("Agent framework is disabled");
        }
        String templateId = request.param(PARAMETER_TEMPLATE_ID);
        if (templateId == null || templateId.trim().isEmpty()) {
            throw new IllegalArgumentException("Template id is required");
        }
        return new MLDeleteAgenticSearchTemplateRequest(templateId);
    }
}
