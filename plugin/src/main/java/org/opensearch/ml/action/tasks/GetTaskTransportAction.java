/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.indices.MLIndicesHandler.ML_TASK_INDEX;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.parameter.MLTask;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
public class GetTaskTransportAction extends HandledTransportAction<ActionRequest, MLTaskGetResponse> {

    Client client;
    NamedXContentRegistry xContentRegistry;

    @Inject
    public GetTaskTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry
    ) {
        super(MLTaskGetAction.NAME, transportService, actionFilters, MLTaskGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskGetResponse> actionListener) {
        MLTaskGetRequest mlTaskGetRequest = MLTaskGetRequest.fromActionRequest(request);
        String taskId = mlTaskGetRequest.getTaskId();
        GetRequest getRequest = new GetRequest(ML_TASK_INDEX).id(taskId);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.get(getRequest, ActionListener.wrap(r -> {
                log.info("Completed Get Task Request, id:{}", taskId);

                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLTask mlTask = MLTask.parse(parser);
                        actionListener.onResponse(MLTaskGetResponse.builder().mlTask(mlTask).build());
                    } catch (Exception e) {
                        log.error("Failed to parse ml task" + r.getId(), e);
                        actionListener.onFailure(e);
                    }
                } else {
                    actionListener.onFailure(new MLResourceNotFoundException("Fail to find task " + taskId));
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    actionListener.onFailure(new MLResourceNotFoundException("Fail to find task " + taskId));
                } else {
                    log.error("Failed to get ML task " + taskId, e);
                    actionListener.onFailure(e);
                }
            }));
        } catch (Exception e) {
            log.error("Failed to get ML task " + taskId, e);
            actionListener.onFailure(e);
        }

    }
}
