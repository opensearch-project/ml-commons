package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.Strings;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.search.RestSearchAction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    List<MLParameter> getMLParameters(RestRequest request) {
        String parametersStr = request.param(ML_PARAMETERS);
        List<MLParameter> parameters = new ArrayList<>();
        if (Strings.isNullOrEmpty(parametersStr)) {
            return parameters;
        }

        if (parametersStr.charAt(0) != '{' && parametersStr.charAt(parametersStr.length() - 1) != '}') {
            parametersStr = "{" + parametersStr + "}";
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            // Convert Map to JSON
            Map<String, Object> map = mapper.readValue(parametersStr, new TypeReference<Map<String, Object>>() {
            });
            parameters = map.entrySet().stream().map(e -> new MLParameter(e.getKey(), e.getValue())).collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid ml_parameter: expected key=\"value\" or key=value [" + parametersStr + "]", e);
        }

        return parameters;
    }
}
