/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TASK_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLGetTaskAction extends BaseRestHandler {
    private static final String ML_GET_Task_ACTION = "ml_get_task_action";

    /**
     * Constructor
     */
    public RestMLGetTaskAction() {}

    @Override
    public String getName() {
        return ML_GET_Task_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/tasks/{%s}", ML_BASE_URI, PARAMETER_TASK_ID)));
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
    // VisibleForTesting
    MLTaskGetRequest getRequest(RestRequest request) throws IOException {
        String taskId = getParameterId(request, PARAMETER_TASK_ID);

        return new MLTaskGetRequest(taskId);
    }
}
