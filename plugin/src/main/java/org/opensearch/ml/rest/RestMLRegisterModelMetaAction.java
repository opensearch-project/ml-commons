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
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaAction;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaInput;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLRegisterModelMetaAction extends BaseRestHandler {
    private static final String ML_REGISTER_MODEL_META_ACTION = "ml_register_model_meta_action";

    /**
     * Constructor
     */
    public RestMLRegisterModelMetaAction() {}

    @Override
    public String getName() {
        return ML_REGISTER_MODEL_META_ACTION;
    }

    @Override
    public List<ReplacedRoute> replacedRoutes() {
        return ImmutableList
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
    @VisibleForTesting
    MLRegisterModelMetaRequest getRequest(RestRequest request) throws IOException {
        boolean hasContent = request.hasContent();
        if (!hasContent) {
            throw new IOException("Model meta request has empty body");
        }
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLRegisterModelMetaInput mlInput = MLRegisterModelMetaInput.parse(parser);
        return new MLRegisterModelMetaRequest(mlInput);
    }
}
