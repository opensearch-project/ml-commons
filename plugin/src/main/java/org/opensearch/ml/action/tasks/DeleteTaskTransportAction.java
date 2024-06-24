/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.task.MLTaskDeleteAction;
import org.opensearch.ml.common.transport.task.MLTaskDeleteRequest;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.DeleteDataObjectResponse;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeleteTaskTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    Client client;
    SdkClient sdkClient;

    NamedXContentRegistry xContentRegistry;

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public DeleteTaskTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLTaskDeleteAction.NAME, transportService, actionFilters, MLTaskDeleteRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLTaskDeleteRequest mlTaskDeleteRequest = MLTaskDeleteRequest.fromActionRequest(request);
        String taskId = mlTaskDeleteRequest.getTaskId();
        String tenantId = mlTaskDeleteRequest.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }
        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = new GetDataObjectRequest.Builder()
            .index(ML_TASK_INDEX)
            .id(taskId)
            .fetchSourceContext(fetchSourceContext)
            .build();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<DeleteResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            sdkClient
                .getDataObjectAsync(getDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                .whenComplete((r, throwable) -> {
                    log.debug("Completed Get task Request, id:{}", taskId);
                    if (throwable != null) {
                        Exception rootCause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        if (rootCause instanceof IndexNotFoundException) {
                            log.error("Failed to get task index", rootCause);
                            actionListener.onFailure(new OpenSearchStatusException("Failed to find task", RestStatus.NOT_FOUND, rootCause));
                        } else {
                            log.error("Failed to get ML task {}", taskId, rootCause);
                            actionListener.onFailure(rootCause);
                        }
                    } else {
                        if (r != null && r.parser().isPresent()) {
                            try {
                                XContentParser parser = r.parser().get();
                                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                MLTask mlTask = MLTask.parse(parser);
                                if (!TenantAwareHelper
                                    .validateTenantResource(mlFeatureEnabledSetting, tenantId, mlTask.getTenantId(), actionListener)) {
                                    return;
                                }
                                MLTaskState mlTaskState = mlTask.getState();
                                if (mlTaskState.equals(MLTaskState.RUNNING)) {
                                    actionListener.onFailure(new Exception("Task cannot be deleted in running state. Try after sometime"));
                                } else {
                                    DeleteRequest deleteRequest = new DeleteRequest(ML_TASK_INDEX, taskId);
                                    try {
                                        sdkClient
                                            .deleteDataObjectAsync(
                                                new DeleteDataObjectRequest.Builder()
                                                    .index(deleteRequest.index())
                                                    .id(deleteRequest.id())
                                                    .build(),
                                                client.threadPool().executor(GENERAL_THREAD_POOL)
                                            )
                                            .whenComplete(
                                                (response, delThrowable) -> handleDeleteResponse(
                                                    response,
                                                    delThrowable,
                                                    tenantId,
                                                    actionListener
                                                )
                                            );
                                    } catch (Exception e) {
                                        log.error("Failed to delete ML task: {}", taskId, e);
                                        actionListener.onFailure(e);
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Failed to parse ml task {}", r.id(), e);
                                wrappedListener.onFailure(e);
                            }
                        } else {
                            wrappedListener.onFailure(new OpenSearchStatusException("Fail to find task", RestStatus.NOT_FOUND));
                        }
                    }
                });

        } catch (Exception e) {
            log.error("Failed to delete ml task {}", taskId, e);
            actionListener.onFailure(e);
        }
    }

    private void handleDeleteResponse(
        DeleteDataObjectResponse response,
        Throwable throwable,
        String taskId,
        ActionListener<DeleteResponse> actionListener
    ) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            log.error("Failed to delete ML task: {}", taskId, cause);
            actionListener.onFailure(cause);
        } else {
            log.info("Task deletion result: {}, task id: {}", response.deleted(), response.id());
            DeleteResponse deleteResponse = new DeleteResponse(response.shardId(), response.id(), 0, 0, 0, response.deleted());
            deleteResponse.setShardInfo(response.shardInfo());
            actionListener.onResponse(deleteResponse);
        }
    }
}
