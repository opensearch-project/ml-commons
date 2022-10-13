/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.transport.model.upload_chunk.MLUploadModelMetaAction;
import org.opensearch.ml.common.transport.model.upload_chunk.MLUploadModelMetaInput;
import org.opensearch.ml.common.transport.model.upload_chunk.MLUploadModelMetaRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLUploadModelMetaAction extends BaseRestHandler {
    private static final String ML_UPLOAD_MODEL_META_ACTION = "ml_upload_model__meta_action";

    /**
     * Constructor
     */
    public RestMLUploadModelMetaAction() {}

    @Override
    public String getName() {
        return ML_UPLOAD_MODEL_META_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/models/meta", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLUploadModelMetaRequest mlUploadModelMetaRequest = getRequest(request);
        return channel -> client.execute(MLUploadModelMetaAction.INSTANCE, mlUploadModelMetaRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLTrainingTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLTrainingTaskRequest
     */
    @VisibleForTesting
    MLUploadModelMetaRequest getRequest(RestRequest request) throws IOException {
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLUploadModelMetaInput mlInput = MLUploadModelMetaInput.parse(parser);
        if (mlInput.getTotalChunks() == null) {
            throw new IllegalArgumentException("total chunks is null");
        }

        return new MLUploadModelMetaRequest(mlInput);
    }
}
