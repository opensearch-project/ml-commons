/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.transport.task.MLTaskDeleteAction;
import org.opensearch.ml.common.transport.task.MLTaskDeleteRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeleteTaskTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    Client client;

    NamedXContentRegistry xContentRegistry;

    @Inject
    public DeleteTaskTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry
    ) {
        super(MLTaskDeleteAction.NAME, transportService, actionFilters, MLTaskDeleteRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLTaskDeleteRequest mlTaskDeleteRequest = MLTaskDeleteRequest.fromActionRequest(request);
        String taskId = mlTaskDeleteRequest.getTaskId();
        GetRequest getRequest = new GetRequest(ML_TASK_INDEX).id(taskId);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.get(getRequest, ActionListener.wrap(r -> {

                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLTask mlTask = MLTask.parse(parser);
                        MLTaskState mlTaskState = mlTask.getState();
                        if (mlTaskState.equals(MLTaskState.RUNNING)) {
                            actionListener.onFailure(new Exception("Task cannot be deleted in running state. Try after sometime"));
                        } else {
                            DeleteRequest deleteRequest = new DeleteRequest(ML_TASK_INDEX, taskId);
                            client.delete(deleteRequest, new ActionListener<DeleteResponse>() {
                                @Override
                                public void onResponse(DeleteResponse deleteResponse) {
                                    log.debug("Completed Delete Task Request, task id:{} deleted", taskId);
                                    actionListener.onResponse(deleteResponse);
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    log.error("Failed to delete ML Task " + taskId, e);
                                    actionListener.onFailure(e);
                                }
                            });
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse ML task " + taskId, e);
                        actionListener.onFailure(e);
                    }
                } else {
                    actionListener.onFailure(new MLResourceNotFoundException("Fail to find task"));
                }
            }, e -> { actionListener.onFailure(new MLResourceNotFoundException("Fail to find task")); }));
        } catch (Exception e) {
            log.error("Failed to delete ml task " + taskId, e);
            actionListener.onFailure(e);
        }
    }
}
