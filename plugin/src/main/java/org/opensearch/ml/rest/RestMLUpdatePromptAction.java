/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_PROMPT_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.OpenSearchParseException;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLUpdatePromptAction;
import org.opensearch.ml.common.transport.prompt.MLUpdatePromptInput;
import org.opensearch.ml.common.transport.prompt.MLUpdatePromptRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * Rest Action class that handles UPDATE REST API request
 */
public class RestMLUpdatePromptAction extends BaseRestHandler {
    private static final String ML_UPDATE_PROMPT_ACTION = "ml_update_prompt_action";

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLUpdatePromptAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    /**
     * Get the name of rest action
     *
     * @return String that is used to register rest action
     */
    @Override
    public String getName() {
        return ML_UPDATE_PROMPT_ACTION;
    }

    /**
     * List the routes that this rest action is responsible for handling
     *
     * @return List of routes that this action handles
     */
    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.PUT, String.format(Locale.ROOT, "%s/prompts/{%s}", ML_BASE_URI, PARAMETER_PROMPT_ID)));
    }

    /**
     * Prepare the request by creating MLUpdatePromptRequest from RestRequest and
     * execute the request against a channel
     *
     * @param request RestRequest to create MLUpdatePromptRequest
     * @param client NodeClient to execute the request against
     * @return RestChannelConsumer
     * @throws IOException if an I/O exception occurred while reading from the request
     */
    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLUpdatePromptRequest mlUpdatePromptRequest = getRequest(request);
        return channel -> client.execute(MLUpdatePromptAction.INSTANCE, mlUpdatePromptRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLUpdatePromptRequest from a RestRequest
     *
     * @param request RestRequest to create MLUpdatePromptRequest
     * @return MLUpdatePromptRequest
     * @throws IllegalStateException if remote inference is disabled
     * @throws IOException if an I/O exception occurred while reading from the request
     */
    @VisibleForTesting
    MLUpdatePromptRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new OpenSearchParseException("Update prompt request has empty body");
        }

        String promptId = getParameterId(request, PARAMETER_PROMPT_ID);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        try {
            MLUpdatePromptInput input = MLUpdatePromptInput.parse(parser);
            input.setPromptId(promptId);
            input.setTenantId(tenantId);
            return new MLUpdatePromptRequest(input);
        } catch (IllegalStateException e) {
            throw new OpenSearchParseException(e.getMessage());
        }
    }
}
