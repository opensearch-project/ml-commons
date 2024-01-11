/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ALLOW_MODEL_URL;
import static org.opensearch.ml.utils.MLExceptionUtils.REMOTE_INFERENCE_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_DEPLOY_MODEL;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_VERSION;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.transport.register.MLRegisterModelAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelRequest;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLRegisterModelAction extends BaseRestHandler {
    private static final String ML_REGISTER_MODEL_ACTION = "ml_register_model_action";
    private volatile boolean isModelUrlAllowed;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLRegisterModelAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    /**
     * Constructor
     * @param clusterService cluster service
     * @param settings settings
     */
    public RestMLRegisterModelAction(ClusterService clusterService, Settings settings, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        isModelUrlAllowed = ML_COMMONS_ALLOW_MODEL_URL.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_ALLOW_MODEL_URL, it -> isModelUrlAllowed = it);
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_REGISTER_MODEL_ACTION;
    }

    @Override
    public List<ReplacedRoute> replacedRoutes() {
        return List
            .of(
                new ReplacedRoute(
                    RestRequest.Method.POST,
                    String.format(Locale.ROOT, "%s/models/_register", ML_BASE_URI),// new url
                    RestRequest.Method.POST,
                    String.format(Locale.ROOT, "%s/models/_upload", ML_BASE_URI)// old url
                ),
                new ReplacedRoute(
                    RestRequest.Method.POST,
                    // new url
                    String.format(Locale.ROOT, "%s/models/{%s}/{%s}/_register", ML_BASE_URI, PARAMETER_MODEL_ID, PARAMETER_VERSION),
                    RestRequest.Method.POST,
                    // old url
                    String.format(Locale.ROOT, "%s/models/{%s}/{%s}/_upload", ML_BASE_URI, PARAMETER_MODEL_ID, PARAMETER_VERSION)
                )
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLRegisterModelRequest mlRegisterModelRequest = getRequest(request);
        return channel -> client.execute(MLRegisterModelAction.INSTANCE, mlRegisterModelRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLTrainingTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLTrainingTaskRequest
     */
    // VisibleForTesting
    MLRegisterModelRequest getRequest(RestRequest request) throws IOException {
        boolean loadModel = request.paramAsBoolean(PARAMETER_DEPLOY_MODEL, false);
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLRegisterModelInput mlInput = MLRegisterModelInput.parse(parser, loadModel);
        if (mlInput.getFunctionName() == FunctionName.REMOTE && !mlFeatureEnabledSetting.isRemoteInferenceEnabled()) {
            throw new IllegalStateException(REMOTE_INFERENCE_DISABLED_ERR_MSG);
        }
        if (mlInput.getUrl() != null && !isModelUrlAllowed) {
            throw new IllegalArgumentException(
                "To upload custom model user needs to enable allow_registering_model_via_url settings. Otherwise please use opensearch pre-trained models."
            );
        }
        return new MLRegisterModelRequest(mlInput);
    }
}
