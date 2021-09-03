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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.ParsingException;
import org.opensearch.common.Strings;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.search.RestSearchAction;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * This is the base class to handle search related ML requests, including training and prediction.
 * Basically,
 * 1. Training request will search the data, then train the model.
 * 2. Prediction request will search the data then run the model to predict based on the data.
 *
 * This base class extends the RestSearchAction to build the SearchRequest,
 * and it has the common code to parse the training request and prediction request.
 *
 */
public class BaseMLSearchAction extends RestSearchAction {
    private static final String BASE_ML_SEARCH_ACTION = "base_ml_search_action";
    protected static final String PARAMETER_ALGORITHM = "algorithm";
    protected static final String PARAMETER_MODEL_ID = "model_id";
    protected static final String ML_PARAMETERS = "ml_parameters";

    public BaseMLSearchAction() {}

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
        return algorithm;
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

    /**
     * Create MLParameter list from a RestRequest
     *
     * @param request RestRequest
     * @return MLParameter list
     */
    @VisibleForTesting
    List<MLParameter> getMLParameters(RestRequest request) throws IOException {
        List<MLParameter> parameters = new ArrayList<>();
        if (request.hasContent()) {
            XContentParser parser = request.contentParser();
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();
                if (ML_PARAMETERS.equals(fieldName)) {
                    Map<String, Object> uiMetadata = parser.map();
                    parameters = uiMetadata
                        .entrySet()
                        .stream()
                        .map(e -> new MLParameter(e.getKey(), e.getValue()))
                        .collect(Collectors.toList());
                    break;
                }
            }
        }

        return parameters;
    }

    private void ensureExpectedToken(XContentParser.Token expected, XContentParser.Token actual, XContentParser parser) {
        if (actual != expected) {
            throw new ParsingException(
                parser.getTokenLocation(),
                String.format(Locale.ROOT, "Failed to parse object: expecting token of type [%s] but found [%s]", expected, actual),
                new Object[0]
            );
        }
    }
}
