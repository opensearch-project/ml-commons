package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.REMOTE_INFERENCE_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorRequest;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptAction;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptInput;
import org.opensearch.ml.common.transport.prompt.MLCreatePromptRequest;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLCreatePromptAction extends BaseRestHandler {
    private static final String ML_CREATE_PROMPT_ACTION = "ml_create_prompt_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     * @param mlFeatureEnabledSetting
     */
    public RestMLCreatePromptAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_CREATE_PROMPT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/prompts/_create", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLCreatePromptRequest mlCreatePromptRequest = getRequest(request);
        return channel -> client.execute(MLCreatePromptAction.INSTANCE, mlCreatePromptRequest, new RestToXContentListener<>(channel));
    }

    /**
     * * Creates a MLCreatePromptRequest from a RestRequest
     * @param request
     * @return MLCreatePromptRequest
     * @throws IOException
     */
    @VisibleForTesting
    MLCreatePromptRequest getRequest(RestRequest request) throws IOException {
        if (!mlFeatureEnabledSetting.isRemoteInferenceEnabled()) {
            throw new IllegalStateException(REMOTE_INFERENCE_DISABLED_ERR_MSG);
        }
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
