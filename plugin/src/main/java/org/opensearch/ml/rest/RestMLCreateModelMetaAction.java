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
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaAction;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaInput;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLCreateModelMetaAction extends BaseRestHandler {
    private static final String ML_CREATE_MODEL_META_ACTION = "ml_create_model_meta_action";

    /**
     * Constructor
     */
    public RestMLCreateModelMetaAction() {}

    @Override
    public String getName() {
        return ML_CREATE_MODEL_META_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/models/meta", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLCreateModelMetaRequest mlCreateModelMetaRequest = getRequest(request);
        return channel -> client.execute(MLCreateModelMetaAction.INSTANCE, mlCreateModelMetaRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLUploadModelMetaRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLUploadModelMetaRequest
     */
    @VisibleForTesting
    MLCreateModelMetaRequest getRequest(RestRequest request) throws IOException {
        boolean hasContent = request.hasContent();
        if (!hasContent) {
            throw new IOException("Model meta request has empty body");
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLCreateModelMetaInput mlInput = MLCreateModelMetaInput.parse(parser);
        return new MLCreateModelMetaRequest(mlInput);
    }
}
