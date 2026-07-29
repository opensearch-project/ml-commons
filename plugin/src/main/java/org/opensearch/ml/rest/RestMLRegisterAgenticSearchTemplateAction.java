/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agenticsearch.MLRegisterAgenticSearchTemplateAction;
import org.opensearch.ml.common.transport.agenticsearch.MLRegisterAgenticSearchTemplateRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * {@code POST /_plugins/_ml/agentic_search_templates} — register a search template
 * for filling. The customer sends only the link (§4.5): {@code template_name},
 * {@code index}, and an optional {@code description}; the server derives + stores
 * the param-schema and echoes back the {@code template_id}.
 */
public class RestMLRegisterAgenticSearchTemplateAction extends BaseRestHandler {
    private static final String ACTION_NAME = "ml_register_agentic_search_template_action";
    private static final String TEMPLATE_NAME_FIELD = "template_name";
    private static final String INDEX_FIELD = "index";
    private static final String DESCRIPTION_FIELD = "description";

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLRegisterAgenticSearchTemplateAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/agentic_search_templates", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLRegisterAgenticSearchTemplateRequest registerRequest = getRequest(request);
        return channel -> client
            .execute(MLRegisterAgenticSearchTemplateAction.INSTANCE, registerRequest, new RestToXContentListener<>(channel));
    }

    @VisibleForTesting
    MLRegisterAgenticSearchTemplateRequest getRequest(RestRequest request) throws IOException {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException("Agent framework is disabled");
        }
        if (!request.hasContent()) {
            throw new IllegalArgumentException("Request body is required");
        }

        String templateName = null;
        String index = null;
        String description = null;

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case TEMPLATE_NAME_FIELD:
                    templateName = parser.text();
                    break;
                case INDEX_FIELD:
                    index = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        if (templateName == null || templateName.trim().isEmpty()) {
            throw new IllegalArgumentException("'template_name' is required");
        }
        if (index == null || index.trim().isEmpty()) {
            throw new IllegalArgumentException("'index' is required");
        }
        return new MLRegisterAgenticSearchTemplateRequest(templateName, index, description);
    }
}
