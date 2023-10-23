/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MEMORY_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableList;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLGetMemoryAction extends BaseRestHandler {
    private static final String ML_GET_MEMORY_ACTION = "ml_get_memory_action";

    /**
     * Constructor
     */
    public RestMLGetMemoryAction() {}

    @Override
    public String getName() {
        return ML_GET_MEMORY_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/memory/{%s}", ML_BASE_URI, PARAMETER_MEMORY_ID)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLTaskGetRequest mlTaskGetRequest = getRequest(request);
        return channel -> client.execute(MLTaskGetAction.INSTANCE, mlTaskGetRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLTaskGetRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLTaskGetRequest
     */
    @VisibleForTesting
    MLTaskGetRequest getRequest(RestRequest request) throws IOException {
        String memoryId = getParameterId(request, PARAMETER_MEMORY_ID);

        return new MLTaskGetRequest(memoryId);
    }
}
