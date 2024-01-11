/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ALLOW_LOCAL_FILE_UPLOAD;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkAction;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkInput;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLUploadModelChunkAction extends BaseRestHandler {
    private static final String ML_UPLOAD_MODEL_CHUNK_ACTION = "ml_upload_model_chunk_action";
    private volatile boolean isLocalFileUploadAllowed;

    /**
     * Constructor
     */
    public RestMLUploadModelChunkAction(ClusterService clusterService, Settings settings) {
        isLocalFileUploadAllowed = ML_COMMONS_ALLOW_LOCAL_FILE_UPLOAD.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_ALLOW_LOCAL_FILE_UPLOAD, it -> isLocalFileUploadAllowed = it);
    }

    @Override
    public String getName() {
        return ML_UPLOAD_MODEL_CHUNK_ACTION;
    }

    @Override
    public List<ReplacedRoute> replacedRoutes() {
        return List
            .of(
                new ReplacedRoute(
                    RestRequest.Method.POST,
                    String.format(Locale.ROOT, "%s/models/{%s}/upload_chunk/{%s}", ML_BASE_URI, "model_id", "chunk_number"),// new url
                    RestRequest.Method.POST,
                    String.format(Locale.ROOT, "%s/models/{%s}/chunk/{%s}", ML_BASE_URI, "model_id", "chunk_number")// old url
                )
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLUploadModelChunkRequest mlUploadModelRequest = getRequest(request);
        return channel -> client.execute(MLUploadModelChunkAction.INSTANCE, mlUploadModelRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLUploadModelChunkRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLUploadModelChunkRequest
     */
    // VisibleForTesting
    MLUploadModelChunkRequest getRequest(RestRequest request) throws IOException {
        final String modelId = request.param("model_id");
        String chunk_number = request.param("chunk_number");
        byte[] content = request.content().streamInput().readAllBytes();
        if (!isLocalFileUploadAllowed) {
            throw new IllegalArgumentException(
                "To upload custom model from local file, user needs to enable allow_registering_model_via_local_file settings. Otherwise please use opensearch pre-trained models."
            );
        }
        MLUploadModelChunkInput mlInput = new MLUploadModelChunkInput(modelId, Integer.parseInt(chunk_number), content);
        return new MLUploadModelChunkRequest(mlInput);
    }
}
