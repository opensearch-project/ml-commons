/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import java.util.Locale;

import org.opensearch.common.Strings;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;

public class RestActionUtils {

    public static final String PARAMETER_ALGORITHM = "algorithm";
    public static final String PARAMETER_ASYNC = "async";
    public static final String PARAMETER_MODEL_ID = "model_id";
    public static final String PARAMETER_TASK_ID = "task_id";
    public static final String OPENSEARCH_DASHBOARDS_USER_AGENT = "OpenSearch Dashboards";
    public static final String[] UI_METADATA_EXCLUDE = new String[] { "ui_metadata" };

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
     * Get the Model or Task id from a RestRequest
     *
     * @param request RestRequest
     * @param idName ID name for example "model_id"
     * @return id for model or task
     */
    public static String getParameterId(RestRequest request, String idName) {
        String id = request.param(idName);
        if (Strings.isNullOrEmpty(id)) {
            throw new IllegalArgumentException("Request should contain " + idName);
        }
        return id;
    }

    /**
     * Checks to see if the request came from OpenSearch Dashboards, if so we want to return the UI Metadata from the document.
     * If the request came from the client then we exclude the UI Metadata from the search result.
     *
     * @param request rest request
     * @return instance of {@link org.opensearch.search.fetch.subphase.FetchSourceContext}
     */
    public static FetchSourceContext getSourceContext(RestRequest request, SearchSourceBuilder searchSourceBuilder) {
        String userAgent = Strings.coalesceToEmpty(request.header("User-Agent"));
        if (searchSourceBuilder.fetchSource() != null) {
            String[] includes = searchSourceBuilder.fetchSource().includes();
            String[] excludes = searchSourceBuilder.fetchSource().excludes();
            String[] metadataExcludes = new String[excludes.length + 1];
            if (!userAgent.contains(OPENSEARCH_DASHBOARDS_USER_AGENT)) {
                if (excludes.length == 0) {
                    return new FetchSourceContext(true, includes, UI_METADATA_EXCLUDE);
                } else {
                    System.arraycopy(excludes, 0, metadataExcludes, 0, excludes.length);
                    metadataExcludes[metadataExcludes.length - 1] = "ui_metadata";
                    return new FetchSourceContext(true, includes, metadataExcludes);
                }
            } else {
                return new FetchSourceContext(true, includes, excludes);
            }
        }
        return new FetchSourceContext(true, Strings.EMPTY_ARRAY, UI_METADATA_EXCLUDE);
    }
}
