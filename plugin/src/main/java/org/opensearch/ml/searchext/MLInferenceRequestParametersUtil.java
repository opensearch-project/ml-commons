/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.searchext;

import java.util.List;
import java.util.stream.Collectors;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.search.SearchExtBuilder;

/**
 * Utility class for handling ML Inference Request Parameters.
 *
 * This class provides utility methods to extract ML Inference Request Parameters
 * from a SearchRequest. It is designed to work with the OpenSearch ML plugin
 * and facilitates the retrieval of ML-specific parameters that are embedded
 * within search requests.
 *
 */
public class MLInferenceRequestParametersUtil {
    /**
     * Extracts ML Inference Request Parameters from a SearchRequest.
     *
     * This method examines the provided SearchRequest for ML-inference parameters
     * that are embedded within the request's extensions. It specifically looks for
     * the MLInferenceRequestParametersExtBuilder and extracts the ML Inference
     * Request Parameters if present.
     * */
    public static MLInferenceRequestParameters getMLInferenceRequestParameters(SearchRequest searchRequest) {
        MLInferenceRequestParametersExtBuilder mLInferenceRequestParametersExtBuilder = null;
        if (searchRequest.source() != null && searchRequest.source().ext() != null && !searchRequest.source().ext().isEmpty()) {
            List<SearchExtBuilder> extBuilders = searchRequest
                .source()
                .ext()
                .stream()
                .filter(extBuilder -> MLInferenceRequestParametersExtBuilder.NAME.equals(extBuilder.getWriteableName()))
                .collect(Collectors.toList());

            if (!extBuilders.isEmpty()) {
                mLInferenceRequestParametersExtBuilder = (MLInferenceRequestParametersExtBuilder) extBuilders.get(0);
                MLInferenceRequestParameters mlInferenceRequestParameters = null;
                if (mLInferenceRequestParametersExtBuilder != null) {
                    mlInferenceRequestParameters = mLInferenceRequestParametersExtBuilder.getRequestParameters();
                }
                return mlInferenceRequestParameters;
            }

        }
        return null;
    }
}
