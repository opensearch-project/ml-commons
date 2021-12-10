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

package org.opensearch.ml.action.execute;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.task.MLExecuteTaskRunner;
import org.opensearch.ml.task.MLTaskRunner;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TransportExecuteTaskAction extends HandledTransportAction<ActionRequest, MLExecuteTaskResponse> {
    MLTaskRunner<MLExecuteTaskRequest, MLExecuteTaskResponse> mlExecuteTaskRunner;
    TransportService transportService;

    @Inject
    public TransportExecuteTaskAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLExecuteTaskRunner mlExecuteTaskRunner
    ) {
        super(MLExecuteTaskAction.NAME, transportService, actionFilters, MLExecuteTaskRequest::new);
        this.mlExecuteTaskRunner = mlExecuteTaskRunner;
        this.transportService = transportService;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLExecuteTaskResponse> listener) {
        MLExecuteTaskRequest mlPredictionTaskRequest = MLExecuteTaskRequest.fromActionRequest(request);
        mlExecuteTaskRunner.run(mlPredictionTaskRequest, transportService, listener);
    }
}
