/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.Strings;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.search.RestSearchAction;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * This is the base class to handle ML requests, including training and prediction.
 * Basically,
 * 1. Training request will search the data, then train the model.
 * 2. Prediction request will search the data then run the model to predict based on the data.
 *
 * This base class extends the RestSearchAction to build the SearchRequest,
 * and it has the common code to parse the training request and prediction request.
 *
 */
public class BaseMLAction extends RestSearchAction {
    private static final String BASE_ML_SEARCH_ACTION = "base_ml_search_action";
    protected static final String PARAMETER_ALGORITHM = "algorithm";
    protected static final String PARAMETER_MODEL_ID = "model_id";

    public BaseMLAction() {}

    @Override
    public String getName() {
        return BASE_ML_SEARCH_ACTION;
    }

    public List<Route> routes() {
        return ImmutableList.of();
    }

    /**
     * Get the algorithm name from a RestRequest
     *
     * @param request RestRequest
     * @return algorithm name
     */
    @VisibleForTesting
    String getAlgorithm(RestRequest request) {
        String algorithm = request.param(PARAMETER_ALGORITHM);
        if (Strings.isNullOrEmpty(algorithm)) {
            throw new IllegalArgumentException("Request should contain algorithm!");
        }
        return algorithm.toUpperCase(Locale.ROOT);
    }

    /**
     * Get the model id from a RestRequest
     *
     * @param request RestRequest
     * @return model id
     */
    @VisibleForTesting
    String getModelId(RestRequest request) {
        String modelId = request.param(PARAMETER_MODEL_ID);
        if (Strings.isNullOrEmpty(modelId)) {
            throw new IllegalArgumentException("Request should contain model_id!");
        }
        return modelId;
    }

    /**
     * Create SearchQueryInputDataset from a RestRequest
     *
     * @param request RestRequest
     * @return SearchQueryInputDataset with indices and search source
     */
    @VisibleForTesting
    SearchQueryInputDataset buildSearchQueryInput(RestRequest request, NodeClient client) throws IOException {
        SearchRequest searchRequest = new SearchRequest();
        IntConsumer setSize = size -> searchRequest.source().size(size);
        request
            .withContentOrSourceParamParserOrNull(
                parser -> parseSearchRequest(searchRequest, request, parser, client.getNamedWriteableRegistry(), setSize)
            );
        return new SearchQueryInputDataset(Arrays.asList(searchRequest.indices()), searchRequest.source());
    }

}
