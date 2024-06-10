/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.execute;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.task.MLExecuteTaskRunner;
import org.opensearch.ml.task.MLTaskRunner;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

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
        MLExecuteTaskRequest mlExecuteTaskRequest = MLExecuteTaskRequest.fromActionRequest(request);
        FunctionName functionName = mlExecuteTaskRequest.getFunctionName();
        mlExecuteTaskRunner.run(functionName, mlExecuteTaskRequest, transportService, listener);
    }
}
