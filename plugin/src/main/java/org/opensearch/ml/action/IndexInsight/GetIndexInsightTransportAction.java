/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.IndexInsight;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_CONFIG_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_INDEX_INSIGHT_FEATURE_ENABLED;
import static org.opensearch.ml.engine.encryptor.EncryptorImpl.DEFAULT_TENANT_ID;

import java.time.Instant;
import java.util.Optional;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.indexInsight.FieldDescriptionTask;
import org.opensearch.ml.common.indexInsight.IndexCorrelationTask;
import org.opensearch.ml.common.indexInsight.IndexInsight;
import org.opensearch.ml.common.indexInsight.IndexInsightAccessControllerHelper;
import org.opensearch.ml.common.indexInsight.IndexInsightConfig;
import org.opensearch.ml.common.indexInsight.IndexInsightTask;
import org.opensearch.ml.common.indexInsight.IndexInsightTaskStatus;
import org.opensearch.ml.common.indexInsight.LogRelatedIndexCheckTask;
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;
import org.opensearch.ml.common.indexInsight.StatisticalDataTask;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetResponse;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetIndexInsightTransportAction extends HandledTransportAction<ActionRequest, MLIndexInsightGetResponse> {
    private static final MLIndexInsightType[] ALL_TYPE_ORDER = {
        MLIndexInsightType.STATISTICAL_DATA,
        MLIndexInsightType.FIELD_DESCRIPTION,
        MLIndexInsightType.LOG_RELATED_INDEX_CHECK,
        MLIndexInsightType.INDEX_CORRELATION };

    private final Client client;
    private final SdkClient sdkClient;
    private final NamedXContentRegistry xContentRegistry;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public GetIndexInsightTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Client client,
        SdkClient sdkClient
    ) {
        super(MLIndexInsightGetAction.NAME, transportService, actionFilters, MLIndexInsightGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.sdkClient = sdkClient;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
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
        String docId = Optional.ofNullable(mlIndexInsightGetRequest.getTenantId()).orElse(DEFAULT_TENANT_ID);
        ActionListener<Boolean> actionAfterDryRun = ActionListener.wrap(r -> {
            try (ThreadContext.StoredContext getContext = client.threadPool().getThreadContext().stashContext()) {
                sdkClient
                    .getDataObjectAsync(
                        GetDataObjectRequest
                            .builder()
                            .tenantId(mlIndexInsightGetRequest.getTenantId())
                            .id(docId)
                            .index(ML_INDEX_INSIGHT_CONFIG_INDEX)
                            .build()
                    )
                    .whenComplete((r1, throwable) -> {
                        getContext.restore();
                        if (throwable != null) {
                            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                            log.error("Failed to get index insight config", cause);
                            actionListener.onFailure(cause);
                        } else {
                            GetResponse getResponse = r1.getResponse();
                            if (getResponse.isExists()) {
                                try (
                                    XContentParser parser = jsonXContent
                                        .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString())
                                ) {
                                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                    IndexInsightConfig indexInsightConfig = IndexInsightConfig.parse(parser);
                                    Boolean isEnable = indexInsightConfig.getIsEnable();
                                    if (Boolean.FALSE.equals(isEnable)) {
                                        actionListener
                                            .onFailure(
                                                new RuntimeException(
                                                    "You are not enabled to use index insight yet, please firstly enable it."
                                                )
                                            );
                                        return;
                                    }
                                    try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                                        ActionListener<MLIndexInsightGetResponse> wrappedListener = ActionListener
                                            .runBefore(actionListener, () -> context.restore());
                                        executeTaskAndReturn(
                                            mlIndexInsightGetRequest,
                                            mlIndexInsightGetRequest.getTenantId(),
                                            wrappedListener
                                        );
                                    } catch (Exception e) {
                                        log.error("Failed to get index insight", e);
                                        actionListener.onFailure(e);
                                    }
                                } catch (Exception e) {
                                    actionListener.onFailure(e);
                                }
                            } else {
                                actionListener
                                    .onFailure(
                                        new RuntimeException("You are not enabled to use index insight yet, please firstly enable it.")
                                    );
                            }
                        }
                    });
            } catch (Exception e) {
                actionListener.onFailure(e);
            }
        }, actionListener::onFailure);
        IndexInsightAccessControllerHelper.verifyAccessController(client, actionAfterDryRun, indexName);
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
                return new StatisticalDataTask(request.getIndexName(), client, sdkClient);
            case FIELD_DESCRIPTION:
                return new FieldDescriptionTask(request.getIndexName(), client, sdkClient);
            case LOG_RELATED_INDEX_CHECK:
                return new LogRelatedIndexCheckTask(request.getIndexName(), client, sdkClient);
            case INDEX_CORRELATION:
                return new IndexCorrelationTask(request.getIndexName(), client, sdkClient);
            case PATTERN_TYPE_CACHE:
                throw new IllegalArgumentException("PATTERN_TYPE_CACHE is internal and cannot be queried directly");
            default:
                throw new IllegalArgumentException("Unsupported task type: " + request.getTargetIndexInsight());
        }
    }
}
