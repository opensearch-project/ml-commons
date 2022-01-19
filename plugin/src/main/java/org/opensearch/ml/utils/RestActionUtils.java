package org.opensearch.ml.utils;

import static org.opensearch.rest.action.search.RestSearchAction.parseSearchRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.IntConsumer;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.Strings;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.rest.RestRequest;

public class RestActionUtils {

    public static final String PARAMETER_ALGORITHM = "algorithm";
    public static final String PARAMETER_ASYNC = "async";
    public static final String PARAMETER_MODEL_ID = "model_id";

    public static String getAlgorithm(RestRequest request) {
        String algorithm = request.param(PARAMETER_ALGORITHM);
        if (Strings.isNullOrEmpty(algorithm)) {
            throw new IllegalArgumentException("Request should contain algorithm!");
        }
        return algorithm.toUpperCase(Locale.ROOT);
    }

    public static boolean isAsync(RestRequest request) {
        return request.paramAsBoolean(PARAMETER_ASYNC, false);
    }

    /**
     * Get the model id from a RestRequest
     *
     * @param request RestRequest
     * @return model id
     */
    public static String getModelId(RestRequest request) {
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
     * @param client node client
     * @return SearchQueryInputDataset with indices and search source
     * @throws IOException throw IOException when fail to parse search request
     */
    public static SearchQueryInputDataset buildSearchQueryInput(RestRequest request, NodeClient client) throws IOException {
        SearchRequest searchRequest = new SearchRequest();
        IntConsumer setSize = size -> searchRequest.source().size(size);
        request
            .withContentOrSourceParamParserOrNull(
                parser -> parseSearchRequest(searchRequest, request, parser, client.getNamedWriteableRegistry(), setSize)
            );
        return new SearchQueryInputDataset(Arrays.asList(searchRequest.indices()), searchRequest.source());
    }
}
