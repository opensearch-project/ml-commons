/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptAction;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * Rest Action class that handles CREATE REST API request
 */
public class RestMLCreatePromptAction extends BaseRestHandler {
    private static final String ML_CREATE_PROMPT_ACTION = "ml_create_prompt_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLCreatePromptAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    /**
     * Get the name of rest action
     *
     * @return String that is used to register rest action
     */
    @Override
    public String getName() {
        return ML_CREATE_PROMPT_ACTION;
    }

    /**
     * List the routes that this rest action is responsible for handling
     *
     * @return List of routes that this action handles
     */
    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/prompts/_create", ML_BASE_URI)));
    }

    /**
     * Prepare the request by creating MLCreatePromptRequest from RestRequest and
     * execute the request against a channel
     *
     * @param request RestRequest to create MLCreatePromptRequest
     * @param client NodeClient to execute the request against
     * @return RestChannelConsumer
     * @throws IOException if an I/O exception occurred while reading from the request or if request
     * body is invalid
     */
    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLCreatePromptRequest mlCreatePromptRequest = getRequest(request);
        return channel -> client.execute(MLCreatePromptAction.INSTANCE, mlCreatePromptRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLCreatePromptRequest from a RestRequest
     *
     * @param request RestRequest to create MLCreatePromptRequest
     * @return MLCreatePromptRequest
     * @throws IllegalStateException if remote inference is disabled
     * @throws IOException if an I/O exception occurred while reading from the request or if request
     * body is invalid
     */
    @VisibleForTesting
    MLCreatePromptRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new IOException("Create Prompt request has empty body");
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLCreatePromptInput mlCreatePromptInput = MLCreatePromptInput.parse(parser);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        mlCreatePromptInput.setTenantId(tenantId);
        return new MLCreatePromptRequest(mlCreatePromptInput);
    }
}
