/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TASK_ID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.transport.task.MLTaskDeleteAction;
import org.opensearch.ml.common.transport.task.MLTaskDeleteRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLDeleteTaskAction extends BaseRestHandler {
    private static final String ML_DELETE_TASK_ACTION = "ml_delete_task_action";

    public void RestMLDeleteTaskAction() {}

    @Override
    public String getName() {
        return ML_DELETE_TASK_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(RestRequest.Method.DELETE, String.format(Locale.ROOT, "%s/tasks/{%s}", ML_BASE_URI, PARAMETER_TASK_ID)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient nodeClient) throws IOException {
        String taskId = restRequest.param(PARAMETER_TASK_ID);

        MLTaskDeleteRequest mlModelDeleteRequest = new MLTaskDeleteRequest(taskId);
        return channel -> nodeClient.execute(MLTaskDeleteAction.INSTANCE, mlModelDeleteRequest, new RestToXContentListener<>(channel));
    }
}
