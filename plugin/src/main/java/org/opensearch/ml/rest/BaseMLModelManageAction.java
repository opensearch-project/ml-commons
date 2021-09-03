/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;

import org.opensearch.client.node.NodeClient;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * This is the base class to handle custom model management related requests in ML, including upload and search.
 * This base class extends the BaseRestHandler to build the SearchRequest, and it has the common code to parse
 * the upload request and search request.
 */
public class BaseMLModelManageAction extends BaseRestHandler {
    private static final String BASE_ML_MODEL_MANAGE_ACTION = "base_ml_model_manage_action";

    protected static final String PARAMETER_MODEL_ID = "model_id";
    protected static final String PARAMETER_NAME = "name";
    protected static final String PARAMETER_FORMAT = "format";
    protected static final String PARAMETER_ALGORITHM = "algorithm";
    protected static final String PARAMETER_BODY = "body";

    public BaseMLModelManageAction() {}

    @Override
    public String getName() {
        return BASE_ML_MODEL_MANAGE_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of();
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient nodeClient) throws IOException {
        return null;
    }

    /**
     * Get the model id from a RestRequest
     *
     * @param request RestRequest
     * @return model id, or null
     */
    @VisibleForTesting
    String getModelId(RestRequest request) {
        return request.param(PARAMETER_MODEL_ID);
    }

    /**
     * Get the model name from a RestRequest
     *
     * @param request RestRequest
     * @return model name, or null
     */
    @VisibleForTesting
    String getName(RestRequest request) {
        return request.param(PARAMETER_NAME);
    }

    /**
     * Get the model format from a RestRequest
     *
     * @param request RestRequest
     * @return model format, or null
     */
    @VisibleForTesting
    String getFormat(RestRequest request) {
        return request.param(PARAMETER_FORMAT);
    }

    /**
     * Get the algorithm name from a RestRequest
     *
     * @param request RestRequest
     * @return algorithm name, or null
     */
    @VisibleForTesting
    String getAlgorithm(RestRequest request) {
        return request.param(PARAMETER_ALGORITHM);
    }
}
