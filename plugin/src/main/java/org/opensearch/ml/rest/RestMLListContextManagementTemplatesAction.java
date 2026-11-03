/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.contextmanagement.MLListContextManagementTemplatesAction;
import org.opensearch.ml.common.transport.contextmanagement.MLListContextManagementTemplatesRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLListContextManagementTemplatesAction extends BaseRestHandler {
    private static final String ML_LIST_CONTEXT_MANAGEMENT_TEMPLATES_ACTION = "ml_list_context_management_templates_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLListContextManagementTemplatesAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_LIST_CONTEXT_MANAGEMENT_TEMPLATES_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/context_management", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLListContextManagementTemplatesRequest listRequest = getRequest(request);
        return channel -> client
            .execute(MLListContextManagementTemplatesAction.INSTANCE, listRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLListContextManagementTemplatesRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLListContextManagementTemplatesRequest
     */
    @VisibleForTesting
    MLListContextManagementTemplatesRequest getRequest(RestRequest request) throws IOException {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException("Agent framework is disabled");
        }

        int from = request.paramAsInt("from", 0);
        int size = request.paramAsInt("size", 10);

        if (from < 0) {
            throw new IllegalArgumentException("Parameter 'from' must be non-negative");
        }
        if (size <= 0 || size > 1000) {
            throw new IllegalArgumentException("Parameter 'size' must be between 1 and 1000");
        }

        return new MLListContextManagementTemplatesRequest(from, size);
    }
}
