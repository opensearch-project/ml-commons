/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.action.stats.MLStatsNodesAction;
import org.opensearch.ml.action.stats.MLStatsNodesRequest;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

//TODO: add IT
public class RestStatsMLAction extends BaseRestHandler {
    private static final String STATS_ML_ACTION = "stats_ml";
    private MLStats mlStats;

    /**
     * Constructor
     *
     * @param mlStats MLStats object
     */
    public RestStatsMLAction(MLStats mlStats) {
        this.mlStats = mlStats;
    }

    @Override
    public String getName() {
        return STATS_ML_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(RestRequest.Method.GET, ML_BASE_URI + "/{nodeId}/stats/"),
                new Route(RestRequest.Method.GET, ML_BASE_URI + "/{nodeId}/stats/{stat}"),
                new Route(RestRequest.Method.GET, ML_BASE_URI + "/stats/"),
                new Route(RestRequest.Method.GET, ML_BASE_URI + "/stats/{stat}")
            );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) {
        MLStatsNodesRequest mlStatsNodesRequest = getRequest(request);
        return channel -> client.execute(MLStatsNodesAction.INSTANCE, mlStatsNodesRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLStatsNodesRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLStatsNodesRequest
     */
    @VisibleForTesting
    MLStatsNodesRequest getRequest(RestRequest request) {
        // todo: add logic to triage request based on node type(ML node or data node)
        MLStatsNodesRequest mlStatsRequest = new MLStatsNodesRequest(splitCommaSeparatedParam(request, "nodeId").orElse(null));
        mlStatsRequest.timeout(request.param("timeout"));

        List<String> requestedStats = splitCommaSeparatedParam(request, "stat").map(Arrays::asList).orElseGet(Collections::emptyList);

        Set<String> validStats = mlStats.getStats().keySet();
        if (isAllStatsRequested(requestedStats)) {
            mlStatsRequest.setRetrieveAllStats(true);
        } else {
            mlStatsRequest.addAll(getStatsToBeRetrieved(request, validStats, requestedStats));
        }

        return mlStatsRequest;
    }

    @VisibleForTesting
    Set<String> getStatsToBeRetrieved(RestRequest request, Set<String> validStats, List<String> requestedStats) {
        if (requestedStats.contains(MLStatsNodesRequest.ALL_STATS_KEY)) {
            throw new IllegalArgumentException(
                String
                    .format(
                        Locale.ROOT,
                        "Request %s contains both %s and individual stats",
                        request.path(),
                        MLStatsNodesRequest.ALL_STATS_KEY
                    )
            );
        }

        Set<String> invalidStats = requestedStats.stream().filter(s -> !validStats.contains(s)).collect(Collectors.toSet());

        if (!invalidStats.isEmpty()) {
            throw new IllegalArgumentException(unrecognized(request, invalidStats, new HashSet<>(requestedStats), "stat"));
        }
        return new HashSet<>(requestedStats);
    }

    @VisibleForTesting
    boolean isAllStatsRequested(List<String> requestedStats) {
        return requestedStats.isEmpty() || (requestedStats.size() == 1 && requestedStats.contains(MLStatsNodesRequest.ALL_STATS_KEY));
    }

    @VisibleForTesting
    Optional<String[]> splitCommaSeparatedParam(RestRequest request, String paramName) {
        return Optional.ofNullable(request.param(paramName)).map(s -> s.split(","));
    }
}
