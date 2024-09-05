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
import org.opensearch.ml.common.transport.task.MLCancelBatchJobAction;
import org.opensearch.ml.common.transport.task.MLCancelBatchJobRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLCancelBatchJobAction extends BaseRestHandler {
    private static final String ML_CANCEL_BATCH_ACTION = "ml_cancel_batch_action";

    /**
     * Constructor
     */
    public RestMLCancelBatchJobAction() {}

    @Override
    public String getName() {
        return ML_CANCEL_BATCH_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(
                    RestRequest.Method.POST,
                    String.format(Locale.ROOT, "%s/tasks/{%s}/_cancel_batch", ML_BASE_URI, PARAMETER_TASK_ID)
                )
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLCancelBatchJobRequest mlCancelBatchJobRequest = getRequest(request);
        return channel -> client.execute(MLCancelBatchJobAction.INSTANCE, mlCancelBatchJobRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLCancelBatchJobRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLCancelBatchJobRequest
     */
    @VisibleForTesting
    MLCancelBatchJobRequest getRequest(RestRequest request) throws IOException {
        String taskId = getParameterId(request, PARAMETER_TASK_ID);

        return new MLCancelBatchJobRequest(taskId);
    }
}
