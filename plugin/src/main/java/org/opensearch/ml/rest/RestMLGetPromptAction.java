/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_PROMPT_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.ml.common.transport.prompt.MLPromptGetAction;
import org.opensearch.ml.common.transport.prompt.MLPromptGetRequest;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * Rest Action class that handles GET REST API request
 */
public class RestMLGetPromptAction extends BaseRestHandler {
    private static final String ML_GET_PROMPT_ACTION = "ml_get_prompt_action";

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLGetPromptAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    /**
     * Get the name of rest action
     *
     * @return String that is used to register rest action
     */
    @Override
    public String getName() {
        return ML_GET_PROMPT_ACTION;
    }

    /**
     * List the routes that this rest action is responsible for handling
     *
     * @return List of routes that this action handles
     */
    @Override
    public List<Route> routes() {
        return ImmutableList
                .of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/prompts/{%s}", ML_BASE_URI, PARAMETER_PROMPT_ID)));
    }

    /**
     * Prepare the request by creating MLPromptGetRequest from RestRequest and
     * execute the request against a channel
     *
     * @param request RestRequest to create MLPromptGetRequest
     * @param client NodeClient to execute the request against
     * @return RestChannelConsumer
     * @throws IOException if an I/O exception occurred while reading from the request
     */
    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLPromptGetRequest mlPromptGetRequest = getRequest(request);
        return channel -> client.execute(MLPromptGetAction.INSTANCE, mlPromptGetRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLPromptGetRequest from a RestRequest
     *
     * @param request RestRequest to create MLPromptGetRequest
     * @return MLPromptGetRequest
     * @throws IllegalStateException if remote inference is disabled
     * @throws IOException if an I/O exception occurred while reading from the request
     */
    @VisibleForTesting
    MLPromptGetRequest getRequest(RestRequest request) throws IOException {
        String promptId = getParameterId(request, PARAMETER_PROMPT_ID);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        return new MLPromptGetRequest(promptId, tenantId);
    }
}
