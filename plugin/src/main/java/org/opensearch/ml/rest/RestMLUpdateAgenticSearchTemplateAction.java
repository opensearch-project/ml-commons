/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TEMPLATE_ID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.agenticsearch.AgenticSearchTemplate;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agenticsearch.MLUpdateAgenticSearchTemplateAction;
import org.opensearch.ml.common.transport.agenticsearch.MLUpdateAgenticSearchTemplateRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * {@code PUT /_plugins/_ml/agentic_search_templates/{template_id}} — edit a
 * registered schema (§4.5): descriptions, enum tightening. Only the fields in the
 * body are merged into the stored doc. Uses PUT (with partial-merge semantics) to
 * match ml-commons' other update APIs (model, connector, agent, context-management),
 * which all expose partial updates under PUT rather than HTTP PATCH.
 */
public class RestMLUpdateAgenticSearchTemplateAction extends BaseRestHandler {
    private static final String ACTION_NAME = "ml_update_agentic_search_template_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLUpdateAgenticSearchTemplateAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public List<Route> routes() {
        String route = String.format(Locale.ROOT, "%s/agentic_search_templates/{%s}", ML_BASE_URI, PARAMETER_TEMPLATE_ID);
        return ImmutableList.of(new Route(RestRequest.Method.PUT, route));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLUpdateAgenticSearchTemplateRequest updateRequest = getRequest(request);
        return channel -> client
            .execute(MLUpdateAgenticSearchTemplateAction.INSTANCE, updateRequest, new RestToXContentListener<>(channel));
    }

    @VisibleForTesting
    MLUpdateAgenticSearchTemplateRequest getRequest(RestRequest request) throws IOException {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException("Agent framework is disabled");
        }
        String templateId = request.param(PARAMETER_TEMPLATE_ID);
        if (templateId == null || templateId.trim().isEmpty()) {
            throw new IllegalArgumentException("Template id is required");
        }
        if (!request.hasContent()) {
            throw new IllegalArgumentException("Update body is required");
        }

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        AgenticSearchTemplate patch = AgenticSearchTemplate.parse(parser);
        // The URL id is authoritative; a body template_id (if any) is overridden.
        patch = patch.toBuilder().templateId(templateId).build();

        return new MLUpdateAgenticSearchTemplateRequest(templateId, patch);
    }
}
