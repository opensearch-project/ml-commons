/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.LOCAL_MODEL_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.REMOTE_INFERENCE_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_DEPLOY_MODEL;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_VERSION;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.register.MLRegisterModelAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestRequestFilter;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLRegisterModelAction extends BaseRestHandler implements RestRequestFilter {
    private static final String ML_REGISTER_MODEL_ACTION = "ml_register_model_action";
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
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_REGISTER_MODEL_ACTION;
    }

    @Override
    public List<ReplacedRoute> replacedRoutes() {
        return ImmutableList
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
    @VisibleForTesting
    MLRegisterModelRequest getRequest(RestRequest request) throws IOException {
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        boolean loadModel = request.paramAsBoolean(PARAMETER_DEPLOY_MODEL, false);
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLRegisterModelInput mlInput = MLRegisterModelInput.parse(parser, loadModel);
        mlInput.setTenantId(tenantId);
        if (mlInput.getFunctionName() == FunctionName.REMOTE && !mlFeatureEnabledSetting.isRemoteInferenceEnabled()) {
            throw new IllegalStateException(REMOTE_INFERENCE_DISABLED_ERR_MSG);
        } else if (FunctionName.isDLModel(mlInput.getFunctionName()) && !mlFeatureEnabledSetting.isLocalModelEnabled()) {
            throw new OpenSearchStatusException(LOCAL_MODEL_DISABLED_ERR_MSG, RestStatus.BAD_REQUEST);
        }
        return new MLRegisterModelRequest(mlInput);
    }

    @Override
    public Set<String> getFilteredFields() {
        return Set.of("connector.credential", "*.Authorization");
    }
}
