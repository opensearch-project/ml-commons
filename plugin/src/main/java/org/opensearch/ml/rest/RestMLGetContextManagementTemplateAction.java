/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TEMPLATE_NAME;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.contextmanagement.MLGetContextManagementTemplateAction;
import org.opensearch.ml.common.transport.contextmanagement.MLGetContextManagementTemplateRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLGetContextManagementTemplateAction extends BaseRestHandler {
    private static final String ML_GET_CONTEXT_MANAGEMENT_TEMPLATE_ACTION = "ml_get_context_management_template_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLGetContextManagementTemplateAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_GET_CONTEXT_MANAGEMENT_TEMPLATE_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(
                    RestRequest.Method.GET,
                    String.format(Locale.ROOT, "%s/context_management/{%s}", ML_BASE_URI, PARAMETER_TEMPLATE_NAME)
                )
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLGetContextManagementTemplateRequest getRequest = getRequest(request);
        return channel -> client.execute(MLGetContextManagementTemplateAction.INSTANCE, getRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLGetContextManagementTemplateRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLGetContextManagementTemplateRequest
     */
    @VisibleForTesting
    MLGetContextManagementTemplateRequest getRequest(RestRequest request) throws IOException {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException("Agent framework is disabled");
        }

        String templateName = request.param(PARAMETER_TEMPLATE_NAME);
        if (templateName == null || templateName.trim().isEmpty()) {
            throw new IllegalArgumentException("Template name is required");
        }

        return new MLGetContextManagementTemplateRequest(templateName);
    }
}
