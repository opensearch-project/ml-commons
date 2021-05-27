/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.action.prediction;

import org.opensearch.ml.task.MLTaskRunner;
import org.opensearch.ml.action.prediction.MLPredictionTaskRemoteExecutionAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskResponse;
import org.opensearch.action.ActionListener;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class MLPredictionTaskRemoteExecutionTransportAction extends HandledTransportAction<MLPredictionTaskRequest, MLPredictionTaskResponse> {
    private final MLTaskRunner mlTaskRunner;
    private final TransportService transportService;

    @Inject
    public MLPredictionTaskRemoteExecutionTransportAction(
            ActionFilters actionFilters,
            TransportService transportService,
            MLTaskRunner mlTaskRunner
    ) {
        super(MLPredictionTaskRemoteExecutionAction.NAME, transportService, actionFilters, MLPredictionTaskRequest::new);
        this.mlTaskRunner = mlTaskRunner;
        this.transportService = transportService;
    }

    @Override
    protected void doExecute(Task task, MLPredictionTaskRequest request, ActionListener<MLPredictionTaskResponse> listener) {
        mlTaskRunner.startPredictionTask(request, listener);
    }
}
