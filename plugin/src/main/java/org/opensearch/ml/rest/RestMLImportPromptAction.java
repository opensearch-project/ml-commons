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
import org.opensearch.ml.common.transport.prompt.MLImportPromptAction;
import org.opensearch.ml.common.transport.prompt.MLImportPromptInput;
import org.opensearch.ml.common.transport.prompt.MLImportPromptRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLImportPromptAction extends BaseRestHandler {
    private static final String ML_IMPORT_PROMPT_ACTION = "ml_import_prompt_actio";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLImportPromptAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_IMPORT_PROMPT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/prompts/_import", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLImportPromptRequest mlImportPromptRequest = getRequest(request);
        return channel -> client.execute(MLImportPromptAction.INSTANCE, mlImportPromptRequest, new RestToXContentListener<>(channel));
    }

    @VisibleForTesting
    MLImportPromptRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new IOException("Import Prompt request has empty body");
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLImportPromptInput mlImportPromptInput = MLImportPromptInput.parse(parser);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        mlImportPromptInput.setTenantId(tenantId);
        return new MLImportPromptRequest(mlImportPromptInput);
    }
}
