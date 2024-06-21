/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
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
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetResponse;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetTaskTransportAction extends HandledTransportAction<ActionRequest, MLTaskGetResponse> {

    Client client;
    SdkClient sdkClient;
    NamedXContentRegistry xContentRegistry;

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public GetTaskTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLTaskGetAction.NAME, transportService, actionFilters, MLTaskGetRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskGetResponse> actionListener) {
        MLTaskGetRequest mlTaskGetRequest = MLTaskGetRequest.fromActionRequest(request);
        String taskId = mlTaskGetRequest.getTaskId();
        String tenantId = mlTaskGetRequest.getTenantId();
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

            sdkClient
                .getDataObjectAsync(getDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                .whenComplete((r, throwable) -> {
                    context.restore();
                    log.debug("Completed Get task Request, id:{}", taskId);
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        if (cause instanceof IndexNotFoundException) {
                            log.error("Failed to get task index", cause);
                            actionListener.onFailure(new OpenSearchStatusException("Failed to find task", RestStatus.NOT_FOUND));
                        } else {
                            log.error("Failed to get ML task {}", taskId, cause);
                            actionListener.onFailure(cause);
                        }
                    } else {
                        try {
                            GetResponse gr = GetResponse.fromXContent(r.parser());
                            if (gr != null && gr.isExists()) {
                                try (
                                    XContentParser parser = jsonXContent
                                        .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, gr.getSourceAsString())
                                ) {
                                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                    MLTask mlTask = MLTask.parse(parser);
                                    if (!TenantAwareHelper
                                        .validateTenantResource(mlFeatureEnabledSetting, tenantId, mlTask.getTenantId(), actionListener)) {
                                        return;
                                    }
                                    actionListener.onResponse(MLTaskGetResponse.builder().mlTask(mlTask).build());
                                } catch (Exception e) {
                                    log.error("Failed to parse ml task {}", r.id(), e);
                                    actionListener.onFailure(e);
                                }
                            } else {
                                actionListener.onFailure(new OpenSearchStatusException("Fail to find task", RestStatus.NOT_FOUND));
                            }
                        } catch (Exception e) {
                            actionListener.onFailure(e);
                        }
                    }
                });
        } catch (Exception e) {
            log.error("Failed to get ML task {}", taskId, e);
            actionListener.onFailure(e);
        }

    }
}
