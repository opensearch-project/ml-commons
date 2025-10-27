/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TEMPLATE_NAME;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.contextmanagement.MLCreateContextManagementTemplateAction;
import org.opensearch.ml.common.transport.contextmanagement.MLCreateContextManagementTemplateRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLCreateContextManagementTemplateAction extends BaseRestHandler {
    private static final String ML_CREATE_CONTEXT_MANAGEMENT_TEMPLATE_ACTION = "ml_create_context_management_template_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLCreateContextManagementTemplateAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_CREATE_CONTEXT_MANAGEMENT_TEMPLATE_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(
                    RestRequest.Method.PUT,
                    String.format(Locale.ROOT, "%s/context_management/{%s}", ML_BASE_URI, PARAMETER_TEMPLATE_NAME)
                )
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLCreateContextManagementTemplateRequest createRequest = getRequest(request);
        return channel -> client
            .execute(MLCreateContextManagementTemplateAction.INSTANCE, createRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLCreateContextManagementTemplateRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLCreateContextManagementTemplateRequest
     */
    @VisibleForTesting
    MLCreateContextManagementTemplateRequest getRequest(RestRequest request) throws IOException {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException("Agent framework is disabled");
        }

        String templateName = request.param(PARAMETER_TEMPLATE_NAME);
        if (templateName == null || templateName.trim().isEmpty()) {
            throw new IllegalArgumentException("Template name is required");
        }

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        ContextManagementTemplate template = ContextManagementTemplate.parse(parser);

        // Set the template name from URL parameter
        template = template.toBuilder().name(templateName).build();

        return new MLCreateContextManagementTemplateRequest(templateName, template);
    }
}
