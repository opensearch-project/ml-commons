/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.ml.common.MLModel.MODEL_CONTENT_FIELD;
import static org.opensearch.ml.common.MLModel.OLD_MODEL_CONTENT_FIELD;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.ArrayUtils;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.Strings;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestStatus;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;

import com.google.common.annotations.VisibleForTesting;

public class RestActionUtils {

    public static final String PARAMETER_ALGORITHM = "algorithm";
    public static final String PARAMETER_ASYNC = "async";
    public static final String PARAMETER_RETURN_CONTENT = "return_content";
    public static final String PARAMETER_MODEL_ID = "model_id";
    public static final String PARAMETER_TASK_ID = "task_id";
    public static final String PARAMETER_LOAD_MODEL = "load";
    public static final String PARAMETER_VERSION = "version";
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

    public static boolean returnContent(RestRequest request) {
        return request.paramAsBoolean(PARAMETER_RETURN_CONTENT, false);
    }

    /**
     * Get the Model or Task id from a RestRequest
     *
     * @param request RestRequest
     * @param idName  ID name for example "model_id"
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
     * @param searchSourceBuilder instance of searchSourceBuilder to fetch includes and excludes
     * @return instance of {@link org.opensearch.search.fetch.subphase.FetchSourceContext}
     */
    public static FetchSourceContext getSourceContext(RestRequest request, SearchSourceBuilder searchSourceBuilder) {
        String userAgent = Strings.coalesceToEmpty(request.header("User-Agent"));
        if (searchSourceBuilder.fetchSource() != null) {
            final String[] includes = searchSourceBuilder.fetchSource().includes();
            final String[] excludes = searchSourceBuilder.fetchSource().excludes();
            if (!ArrayUtils.contains(includes, MODEL_CONTENT_FIELD)) {
                ArrayUtils.add(excludes, MODEL_CONTENT_FIELD);
            }
            if (!ArrayUtils.contains(includes, OLD_MODEL_CONTENT_FIELD)) {
                ArrayUtils.add(excludes, OLD_MODEL_CONTENT_FIELD);
            }
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
        } else {
            // When user does not set the _source field in search model api request, searchSourceBuilder.fetchSource becomes null
            String[] excludes = new String[] { OLD_MODEL_CONTENT_FIELD, MODEL_CONTENT_FIELD };
            if (!userAgent.contains(OPENSEARCH_DASHBOARDS_USER_AGENT)) {
                return new FetchSourceContext(true, Strings.EMPTY_ARRAY, ArrayUtils.add(excludes, "ui_metadata"));
            } else {
                return new FetchSourceContext(true, Strings.EMPTY_ARRAY, excludes);
            }
        }
    }

    /**
     * Return FetchSourceContext
     * @param returnModelContent if the model content should be returned
     */
    public static FetchSourceContext getFetchSourceContext(boolean returnModelContent) {
        if (!returnModelContent) {
            return new FetchSourceContext(true, Strings.EMPTY_ARRAY, new String[] { OLD_MODEL_CONTENT_FIELD, MODEL_CONTENT_FIELD });
        }
        return new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
    }

    /**
     * Return all nodes in the cluster
     * @param clusterService the cluster service
     */
    public static String[] getAllNodes(ClusterService clusterService) {
        Iterator<DiscoveryNode> iterator = clusterService.state().nodes().iterator();
        List<String> nodeIds = new ArrayList<>();
        while (iterator.hasNext()) {
            nodeIds.add(iterator.next().getId());
        }
        return nodeIds.toArray(new String[0]);
    }

    /**
     *
     * @param channel RestChannel
     * @param status RestStatus enums
     * @param errorMessage Error messages
     * @param exception Reported Exception
     */
    public static void onFailure(RestChannel channel, RestStatus status, String errorMessage, Exception exception) {
        BytesRestResponse bytesRestResponse;
        try {
            bytesRestResponse = new BytesRestResponse(channel, exception);
        } catch (Exception e) {
            bytesRestResponse = new BytesRestResponse(status, errorMessage);
        }
        channel.sendResponse(bytesRestResponse);
    }

    @VisibleForTesting
    public static Optional<String[]> splitCommaSeparatedParam(RestRequest request, String paramName) {
        return Optional.ofNullable(request.param(paramName)).map(s -> s.split(","));
    }

    public static Optional<String> getStringParam(RestRequest request, String paramName) {
        return Optional.ofNullable(request.param(paramName));
    }
}
