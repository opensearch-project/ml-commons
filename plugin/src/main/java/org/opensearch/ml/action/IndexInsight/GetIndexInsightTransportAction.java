/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.IndexInsight;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_INDEX_INSIGHT_FEATURE_ENABLED;

import java.time.Instant;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.indexInsight.FieldDescriptionTask;
import org.opensearch.ml.common.indexInsight.IndexInsight;
import org.opensearch.ml.common.indexInsight.IndexInsightAccessControllerHelper;
import org.opensearch.ml.common.indexInsight.IndexInsightTask;
import org.opensearch.ml.common.indexInsight.IndexInsightTaskStatus;
import org.opensearch.ml.common.indexInsight.LogRelatedIndexCheckTask;
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;
import org.opensearch.ml.common.indexInsight.StatisticalDataTask;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetIndexInsightTransportAction extends HandledTransportAction<ActionRequest, MLIndexInsightGetResponse> {
    private static final MLIndexInsightType[] ALL_TYPE_ORDER = {
        MLIndexInsightType.STATISTICAL_DATA,
        MLIndexInsightType.FIELD_DESCRIPTION,
        MLIndexInsightType.LOG_RELATED_INDEX_CHECK };

    private final Client client;
    private final SdkClient sdkClient;
    private final NamedXContentRegistry xContentRegistry;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MLIndicesHandler mlIndicesHandler;

    @Inject
    public GetIndexInsightTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Client client,
        SdkClient sdkClient,
        MLIndicesHandler mlIndicesHandler
    ) {
        super(MLIndexInsightGetAction.NAME, transportService, actionFilters, MLIndexInsightGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.sdkClient = sdkClient;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mlIndicesHandler = mlIndicesHandler;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLIndexInsightGetResponse> actionListener) {
        MLIndexInsightGetRequest mlIndexInsightGetRequest = MLIndexInsightGetRequest.fromActionRequest(request);
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, mlIndexInsightGetRequest.getTenantId(), actionListener)) {
            return;
        }
        if (!this.mlFeatureEnabledSetting.isIndexInsightEnabled()) {
            actionListener
                .onFailure(
                    new RuntimeException(
                        "Index insight feature is not enabled yet. To enable, please update the setting "
                            + ML_COMMONS_INDEX_INSIGHT_FEATURE_ENABLED.getKey()
                    )
                );
            return;
        }
        String indexName = mlIndexInsightGetRequest.getIndexName();
        mlIndicesHandler.initMLIndexIfAbsent(MLIndex.INDEX_INSIGHT_STORAGE, ActionListener.wrap(r2 -> {
            ActionListener<Boolean> actionAfterDryRun = ActionListener.wrap(r -> {
                executeTaskAndReturn(mlIndexInsightGetRequest, mlIndexInsightGetRequest.getTenantId(), actionListener);
            }, actionListener::onFailure);
            IndexInsightAccessControllerHelper.verifyAccessController(client, actionAfterDryRun, indexName);
        }, e -> {
            log.error("Failed to create index insight storage", e);
            actionListener.onFailure(e);
        }));
    }

    private void executeTaskAndReturn(
        MLIndexInsightGetRequest request,
        String tenantId,
        ActionListener<MLIndexInsightGetResponse> listener
    ) {
        if (request.getTargetIndexInsight() == MLIndexInsightType.ALL) {
            StringBuilder combinedContent = new StringBuilder();
            executeTaskChain(request.getIndexName(), tenantId, combinedContent, listener, 0, null);
        } else {
            IndexInsightTask task = createTask(request);
            task.execute(tenantId, ActionListener.wrap(insight -> {
                // Task completed, return result directly
                listener.onResponse(MLIndexInsightGetResponse.builder().indexInsight(insight).build());
            }, listener::onFailure));
        }
    }

    /**
     * Recursively executes index insight tasks in sequence and combines their results.
     */
    private void executeTaskChain(
        String indexName,
        String tenantId,
        StringBuilder combinedContent,
        ActionListener<MLIndexInsightGetResponse> listener,
        int taskIndex,
        Instant lastSuccessTime
    ) {
        if (taskIndex >= ALL_TYPE_ORDER.length) {
            // Check if all tasks failed
            if (combinedContent.length() == 0) {
                listener.onFailure(new RuntimeException("All index insight tasks failed"));
                return;
            }
            returnCombinedResult(indexName, combinedContent, lastSuccessTime, listener);
            return;
        }

        MLIndexInsightGetRequest taskRequest = MLIndexInsightGetRequest
            .builder()
            .indexName(indexName)
            .targetIndexInsight(ALL_TYPE_ORDER[taskIndex])
            .tenantId(tenantId)
            .build();
        executeTaskForAllType(
            taskRequest,
            tenantId,
            combinedContent,
            ActionListener
                .wrap(
                    time -> executeTaskChain(indexName, tenantId, combinedContent, listener, taskIndex + 1, time),
                    e -> executeTaskChain(indexName, tenantId, combinedContent, listener, taskIndex + 1, lastSuccessTime)
                )
        );
    }

    private void executeTaskForAllType(
        MLIndexInsightGetRequest request,
        String tenantId,
        StringBuilder combinedContent,
        ActionListener<Instant> listener
    ) {
        createTask(request).execute(tenantId, ActionListener.wrap(insight -> {
            if (combinedContent.length() > 0) {
                combinedContent.append("\n\n");
            }
            combinedContent.append(request.getTargetIndexInsight().name()).append(":\n").append(insight.getContent());
            listener.onResponse(insight.getLastUpdatedTime());
        }, listener::onFailure));
    }

    private void returnCombinedResult(
        String indexName,
        StringBuilder combinedContent,
        Instant lastUpdatedTime,
        ActionListener<MLIndexInsightGetResponse> listener
    ) {
        IndexInsight combinedInsight = IndexInsight
            .builder()
            .index(indexName)
            .taskType(MLIndexInsightType.ALL)
            .content(combinedContent.toString())
            .status(IndexInsightTaskStatus.COMPLETED)
            .lastUpdatedTime(lastUpdatedTime)
            .build();
        listener.onResponse(MLIndexInsightGetResponse.builder().indexInsight(combinedInsight).build());
    }

    IndexInsightTask createTask(MLIndexInsightGetRequest request) {
        switch (request.getTargetIndexInsight()) {
            case STATISTICAL_DATA:
                return new StatisticalDataTask(
                    request.getIndexName(),
                    client,
                    sdkClient,
                    request.getCmkRoleArn(),
                    request.getAssumeRoleArn()
                );
            case FIELD_DESCRIPTION:
                return new FieldDescriptionTask(
                    request.getIndexName(),
                    client,
                    sdkClient,
                    request.getCmkRoleArn(),
                    request.getAssumeRoleArn()
                );
            case LOG_RELATED_INDEX_CHECK:
                return new LogRelatedIndexCheckTask(
                    request.getIndexName(),
                    client,
                    sdkClient,
                    request.getCmkRoleArn(),
                    request.getAssumeRoleArn()
                );
            default:
                throw new IllegalArgumentException("Unsupported task type: " + request.getTargetIndexInsight());
        }
    }
}
