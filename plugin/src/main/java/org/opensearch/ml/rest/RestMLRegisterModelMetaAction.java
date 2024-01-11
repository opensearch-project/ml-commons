/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ALLOW_LOCAL_FILE_UPLOAD;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaAction;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaInput;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLRegisterModelMetaAction extends BaseRestHandler {
    private static final String ML_REGISTER_MODEL_META_ACTION = "ml_register_model_meta_action";

    private volatile boolean isLocalFileUploadAllowed;

    /**
     * Constructor
     * @param clusterService cluster service
     * @param settings settings
     */
    public RestMLRegisterModelMetaAction(ClusterService clusterService, Settings settings) {
        isLocalFileUploadAllowed = ML_COMMONS_ALLOW_LOCAL_FILE_UPLOAD.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_ALLOW_LOCAL_FILE_UPLOAD, it -> isLocalFileUploadAllowed = it);
    }

    @Override
    public String getName() {
        return ML_REGISTER_MODEL_META_ACTION;
    }

    @Override
    public List<ReplacedRoute> replacedRoutes() {
        return List
            .of(
                new ReplacedRoute(
                    RestRequest.Method.POST,
                    String.format(Locale.ROOT, "%s/models/_register_meta", ML_BASE_URI),// new url
                    RestRequest.Method.POST,
                    String.format(Locale.ROOT, "%s/models/meta", ML_BASE_URI)// old url
                )
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLRegisterModelMetaRequest mlRegisterModelMetaRequest = getRequest(request);
        return channel -> client
            .execute(MLRegisterModelMetaAction.INSTANCE, mlRegisterModelMetaRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLUploadModelMetaRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLUploadModelMetaRequest
     */
    // VisibleForTesting
    MLRegisterModelMetaRequest getRequest(RestRequest request) throws IOException {
        boolean hasContent = request.hasContent();
        if (!isLocalFileUploadAllowed) {
            throw new IllegalArgumentException(
                "To upload custom model from local file, user needs to enable allow_registering_model_via_local_file settings. Otherwise please use opensearch pre-trained models"
            );
        } else if (!hasContent) {
            throw new IOException("Model meta request has empty body");
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLRegisterModelMetaInput mlInput = MLRegisterModelMetaInput.parse(parser);
        return new MLRegisterModelMetaRequest(mlInput);
    }
}
