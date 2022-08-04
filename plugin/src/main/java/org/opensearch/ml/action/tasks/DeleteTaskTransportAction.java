/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.ml.common.transport.task.MLTaskDeleteAction;
import org.opensearch.ml.common.transport.task.MLTaskDeleteRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
public class DeleteTaskTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    Client client;

    @Inject
    public DeleteTaskTransportAction(TransportService transportService, ActionFilters actionFilters, Client client) {
        super(MLTaskDeleteAction.NAME, transportService, actionFilters, MLTaskDeleteRequest::new);
        this.client = client;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLTaskDeleteRequest mlTaskDeleteRequest = MLTaskDeleteRequest.fromActionRequest(request);
        String taskId = mlTaskDeleteRequest.getTaskId();

        DeleteRequest deleteRequest = new DeleteRequest(ML_TASK_INDEX, taskId);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.delete(deleteRequest, new ActionListener<DeleteResponse>() {
                @Override
                public void onResponse(DeleteResponse deleteResponse) {
                    log.info("Completed Delete Task Request, task id:{} deleted", taskId);
                    actionListener.onResponse(deleteResponse);
                }

                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to delete ML Task " + taskId, e);
                    actionListener.onFailure(e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to delete ML task " + taskId, e);
            actionListener.onFailure(e);
        }
    }
}
