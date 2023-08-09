/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.searchpipelines.questionanswering.generative;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.search.SearchExtBuilder;

import java.util.Optional;

/**
 * Utility class for extracting generative QA search pipeline parameters from search requests.
 */
public class GenerativeQAParamUtil {

    public static GenerativeQAParameters getGenerativeQAParameters(SearchRequest request) {
        GenerativeQAParamExtBuilder builder = null;
        if (request.source() != null && request.source().ext() != null && !request.source().ext().isEmpty()) {
            Optional<SearchExtBuilder> b = request.source().ext().stream().filter(bldr -> GenerativeQAParamExtBuilder.NAME.equals(bldr.getWriteableName())).findFirst();
            if (b.isPresent()) {
                builder = (GenerativeQAParamExtBuilder) b.get();
            }
        }

        GenerativeQAParameters params = null;
        if (builder != null) {
            params = builder.getParams();
        }

        return params;
    }
}
