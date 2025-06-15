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

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLPromptDeleteAction;
import org.opensearch.ml.common.transport.prompt.MLPromptDeleteRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * This class consists of the REST handler to delete ML Prompt.
 */
public class RestMLDeletePromptAction extends BaseRestHandler {
    private static final String ML_DELETE_PROMPT_ACTION = "ml_delete_prompt_action";

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLDeletePromptAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    /**
     * Get the name of rest action
     *
     * @return String that is used to register rest action
     */
    @Override
    public String getName() {
        return ML_DELETE_PROMPT_ACTION;
    }

    /**
     * List the routes that this rest action is responsible for handling
     *
     * @return List of routes that this action handles
     */
    @Override
    public List<RestHandler.Route> routes() {
        return ImmutableList
            .of(
                new RestHandler.Route(
                    RestRequest.Method.DELETE,
                    String.format(Locale.ROOT, "%s/prompts/{%s}", ML_BASE_URI, PARAMETER_PROMPT_ID)
                )
            );
    }

    /**
     * Prepare the request by creating MLPromptDeleteRequest from RestRequest and
     * execute the request against a channel
     *
     * @param request RestRequest to create MLPromptDeleteRequest
     * @param client NodeClient to execute the request against
     * @return RestChannelConsumer
     * @throws IOException if an I/O exception occurred while reading from the request
     */
    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLPromptDeleteRequest mlPromptDeleteRequest = getRequest(request);
        return channel -> client.execute(MLPromptDeleteAction.INSTANCE, mlPromptDeleteRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLPromptDeleteRequest from a RestRequest
     *
     * @param request RestRequest to create MLPromptDeleteRequest
     * @return MLPromptDeleteRequest
     * @throws IllegalStateException if remote inference is disabled
     * @throws IOException if an I/O exception occurred while reading from the request
     */
    @VisibleForTesting
    MLPromptDeleteRequest getRequest(RestRequest request) throws IOException {
        String promptId = getParameterId(request, PARAMETER_PROMPT_ID);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        return new MLPromptDeleteRequest(promptId, tenantId);
    }
}
