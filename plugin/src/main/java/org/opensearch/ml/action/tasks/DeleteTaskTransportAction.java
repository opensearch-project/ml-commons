/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;

import java.io.IOException;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.task.MLTaskDeleteAction;
import org.opensearch.ml.common.transport.task.MLTaskDeleteRequest;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.DeleteDataObjectRequest;
import org.opensearch.remote.metadata.client.DeleteDataObjectResponse;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeleteTaskTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    private final Client client;
    private final SdkClient sdkClient;
    private final NamedXContentRegistry xContentRegistry;
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
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_TASK_INDEX)
            .id(taskId)
            .fetchSourceContext(fetchSourceContext)
            .tenantId(tenantId)
            .build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<DeleteResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);

            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((getDataObjectResponse, throwable) -> {
                if (throwable != null) {
                    handleGetError(throwable, taskId, wrappedListener);
                    return;
                }
                processGetDataObjectResponse(getDataObjectResponse, taskId, tenantId, wrappedListener);
            });
        } catch (Exception e) {
            log.error("Failed to delete ML task {}", taskId, e);
            actionListener.onFailure(e);
        }
    }

    private void handleGetError(Throwable throwable, String taskId, ActionListener<DeleteResponse> actionListener) {
        Exception rootCause = SdkClientUtils.unwrapAndConvertToException(throwable);

        if (ExceptionsHelper.unwrap(rootCause, IndexNotFoundException.class) != null) {
            log.error("Failed to find task index", rootCause);
            actionListener.onFailure(new OpenSearchStatusException("Failed to find task", RestStatus.NOT_FOUND, rootCause));
        } else {
            log.error("Failed to get ML task {}", taskId, rootCause);
            actionListener.onFailure(rootCause);
        }
    }

    private void processGetDataObjectResponse(
        GetDataObjectResponse getDataObjectResponse,
        String taskId,
        String tenantId,
        ActionListener<DeleteResponse> actionListener
    ) {
        try {
            GetResponse getResponse = getDataObjectResponse.parser() == null
                ? null
                : GetResponse.fromXContent(getDataObjectResponse.parser());

            if (getResponse == null || !getResponse.isExists()) {
                actionListener.onFailure(new OpenSearchStatusException("Failed to find task", RestStatus.NOT_FOUND));
                return;
            }

            processTask(getResponse, taskId, tenantId, actionListener);
        } catch (Exception e) {
            log.error("Failed to parse GetDataObjectResponse for task {}", taskId, e);
            actionListener.onFailure(e);
        }
    }

    private void processTask(GetResponse getResponse, String taskId, String tenantId, ActionListener<DeleteResponse> actionListener) {
        try (
            XContentParser parser = jsonXContent
                .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString())
        ) {

            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            MLTask mlTask = MLTask.parse(parser);

            if (!TenantAwareHelper.validateTenantResource(mlFeatureEnabledSetting, tenantId, mlTask.getTenantId(), actionListener)) {
                return;
            }

            if (mlTask.getState() == MLTaskState.RUNNING) {
                actionListener.onFailure(new Exception("Task cannot be deleted in running state. Try after some time."));
            } else {
                executeDelete(taskId, tenantId, actionListener);
            }
        } catch (Exception e) {
            log.error("Failed to parse ML task {}", taskId, e);
            actionListener.onFailure(e);
        }
    }

    private void executeDelete(String taskId, String tenantId, ActionListener<DeleteResponse> actionListener) {
        DeleteRequest deleteRequest = new DeleteRequest(ML_TASK_INDEX, taskId);

        try {
            sdkClient
                .deleteDataObjectAsync(
                    DeleteDataObjectRequest.builder().index(deleteRequest.index()).id(deleteRequest.id()).tenantId(tenantId).build()
                )
                .whenComplete((deleteDataObjectResponse, throwable) -> {
                    if (throwable != null) {
                        handleDeleteError(throwable, taskId, actionListener);
                        return;
                    }
                    processDeleteResponse(deleteDataObjectResponse, actionListener);
                });
        } catch (Exception e) {
            log.error("Failed to delete ML task: {}", taskId, e);
            actionListener.onFailure(e);
        }
    }

    private void handleDeleteError(Throwable throwable, String taskId, ActionListener<DeleteResponse> actionListener) {
        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
        log.error("Failed to delete ML task: {}", taskId, cause);
        actionListener.onFailure(cause);
    }

    private void processDeleteResponse(DeleteDataObjectResponse deleteDataObjectResponse, ActionListener<DeleteResponse> actionListener) {
        try {
            DeleteResponse deleteResponse = DeleteResponse.fromXContent(deleteDataObjectResponse.parser());
            log.info("Task deletion result: {}, task id: {}", deleteResponse.getResult(), deleteResponse.getId());
            actionListener.onResponse(deleteResponse);
        } catch (IOException e) {
            actionListener.onFailure(e);
        }
    }
}
