/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.execute;

import static org.opensearch.ml.plugin.MachineLearningPlugin.STREAM_EXECUTE_THREAD_POOL;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.transport.execute.MLExecuteStreamTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener;
import org.opensearch.ml.task.MLExecuteTaskRunner;
import org.opensearch.ml.task.MLTaskRunner;
import org.opensearch.tasks.Task;
import org.opensearch.transport.StreamTransportService;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TransportExecuteStreamTaskAction extends HandledTransportAction<ActionRequest, MLExecuteTaskResponse> {
    MLTaskRunner<MLExecuteTaskRequest, MLExecuteTaskResponse> mlExecuteTaskRunner;
    TransportService transportService;

    public static StreamTransportService streamTransportService;

    @Inject
    public TransportExecuteStreamTaskAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLExecuteTaskRunner mlExecuteTaskRunner,
        StreamTransportService streamTransportService
    ) {
        super(MLExecuteStreamTaskAction.NAME, transportService, actionFilters, MLExecuteTaskRequest::new);
        this.mlExecuteTaskRunner = mlExecuteTaskRunner;
        this.transportService = transportService;
        this.streamTransportService = streamTransportService;

        streamTransportService
            .registerRequestHandler(
                MLExecuteStreamTaskAction.NAME,
                STREAM_EXECUTE_THREAD_POOL,
                MLExecuteTaskRequest::new,
                this::messageReceived
            );
    }

    public void messageReceived(MLExecuteTaskRequest request, TransportChannel channel, Task task) {
        StreamPredictActionListener<MLExecuteTaskResponse, MLExecuteTaskRequest> streamListener = new StreamPredictActionListener<>(
            channel
        );
        doExecute(task, request, streamListener, channel);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLExecuteTaskResponse> listener) {
        // This should never be called for streaming action
        listener.onFailure(new UnsupportedOperationException("Use doExecute with TransportChannel for streaming requests"));
    }

    protected void doExecute(Task task, ActionRequest request, ActionListener<MLExecuteTaskResponse> listener, TransportChannel channel) {
        MLExecuteTaskRequest mlExecuteTaskRequest = MLExecuteTaskRequest.fromActionRequest(request);
        mlExecuteTaskRequest.setStreamingChannel(channel);

        if (mlExecuteTaskRequest.getStreamingChannel() != null) {
            mlExecuteTaskRequest.setDispatchTask(false);
        }

        FunctionName functionName = mlExecuteTaskRequest.getFunctionName();
        mlExecuteTaskRunner.run(functionName, mlExecuteTaskRequest, streamTransportService, listener);
    }
}
