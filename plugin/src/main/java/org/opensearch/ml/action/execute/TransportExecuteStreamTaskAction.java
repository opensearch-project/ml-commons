/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.execute;

import static org.opensearch.ml.plugin.MachineLearningPlugin.STREAM_EXECUTE_THREAD_POOL;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.Nullable;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.transport.execute.MLExecuteStreamTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.task.MLExecuteTaskRunner;
import org.opensearch.ml.task.MLTaskRunner;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.StreamTransportService;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportException;
import org.opensearch.transport.TransportResponseHandler;
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
    private static StreamTransportService streamTransportServiceInstance;

    @Inject
    public TransportExecuteStreamTaskAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLExecuteTaskRunner mlExecuteTaskRunner,
        @Nullable StreamTransportService streamTransportService
    ) {
        super(MLExecuteStreamTaskAction.NAME, transportService, actionFilters, MLExecuteTaskRequest::new);
        this.mlExecuteTaskRunner = mlExecuteTaskRunner;
        this.transportService = transportService;
        if (streamTransportServiceInstance == null) {
            streamTransportServiceInstance = streamTransportService;
        }
        this.streamTransportService = streamTransportServiceInstance;

        if (streamTransportService != null) {
            streamTransportService
                .registerRequestHandler(
                    MLExecuteStreamTaskAction.NAME,
                    STREAM_EXECUTE_THREAD_POOL,
                    MLExecuteTaskRequest::new,
                    this::messageReceived
                );
        } else {
            log.warn("StreamTransportService is not available.");
        }
    }

    public static StreamTransportService getStreamTransportService() {
        return streamTransportService;
    }

    public void messageReceived(MLExecuteTaskRequest request, TransportChannel channel, Task task) {
        request.setStreamingChannel(channel);
        transportService
            .sendRequest(
                transportService.getLocalNode(),
                MLExecuteStreamTaskAction.NAME,
                request,
                new TransportResponseHandler<MLExecuteTaskResponse>() {
                    public MLExecuteTaskResponse read(StreamInput in) throws IOException {
                        return new MLExecuteTaskResponse(in);
                    }

                    public void handleResponse(MLExecuteTaskResponse response) {}

                    public void handleException(TransportException exp) {
                        try {
                            channel.sendResponse(exp);
                        } catch (Exception e) {
                            log.error("Failed to send error response", e);
                        }
                    }

                    public String executor() {
                        return ThreadPool.Names.SAME;
                    }
                }
            );
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLExecuteTaskResponse> listener) {
        TransportChannel channel = ((MLExecuteTaskRequest) request).getStreamingChannel();
        if (channel != null) {
            doExecute(task, request, listener, channel);
        } else {
            listener.onFailure(new UnsupportedOperationException("Use doExecute with TransportChannel for streaming requests"));
        }
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
