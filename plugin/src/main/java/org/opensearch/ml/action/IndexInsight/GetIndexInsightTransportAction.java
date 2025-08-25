/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.IndexInsight;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_INDEX_NAME;
import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_CONFIG_INDEX;
import static org.opensearch.ml.engine.encryptor.EncryptorImpl.DEFAULT_TENANT_ID;

import java.time.Instant;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.indexInsight.FieldDescriptionTask;
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
import org.opensearch.ml.engine.indices.MLIndicesHandler;
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
    private Client client;
    private SdkClient sdkClient;
    private NamedXContentRegistry xContentRegistry;
    private MLIndicesHandler mlIndicesHandler;
    private ClusterService clusterService;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public GetIndexInsightTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Client client,
        SdkClient sdkClient,
        MLIndicesHandler mlIndicesHandler,
        ClusterService clusterService
    ) {
        super(MLIndexInsightGetAction.NAME, transportService, actionFilters, MLIndexInsightGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.mlIndicesHandler = mlIndicesHandler;
        this.clusterService = clusterService;
        this.sdkClient = sdkClient;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLIndexInsightGetResponse> actionListener) {
        MLIndexInsightGetRequest mlIndexInsightGetRequest = MLIndexInsightGetRequest.fromActionRequest(request);
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, mlIndexInsightGetRequest.getTenantId(), actionListener)) {
            return;
        }
        String indexName = mlIndexInsightGetRequest.getIndexName();
        String docId = mlIndexInsightGetRequest.getTenantId();
        if (docId == null) {
            docId = DEFAULT_TENANT_ID;
        }
        String finalDocId = docId;
        ActionListener<Boolean> actionAfterDryRun = ActionListener.wrap(r -> {
            try (ThreadContext.StoredContext getContext = client.threadPool().getThreadContext().stashContext()) {
                sdkClient
                    .getDataObjectAsync(
                        GetDataObjectRequest
                            .builder()
                            .tenantId(mlIndexInsightGetRequest.getTenantId())
                            .id(finalDocId)
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
                                    if (!isEnable) {
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
                                            INDEX_INSIGHT_INDEX_NAME,
                                            mlIndexInsightGetRequest.getTenantId(),
                                            wrappedListener
                                        );
                                    } catch (Exception e) {
                                        log.error("fail to get index insight", e);
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
        String storageIndex,
        String tenantId,
        ActionListener<MLIndexInsightGetResponse> listener
    ) {
        if (request.getTargetIndexInsight() == MLIndexInsightType.ALL) {
            executeAllTasks(request, storageIndex, tenantId, listener);
        } else {
            IndexInsightTask task = createTask(request);
            task.execute(storageIndex, tenantId, ActionListener.wrap(insight -> {
                // Task completed, return result directly
                listener.onResponse(MLIndexInsightGetResponse.builder().indexInsight(insight).build());
            }, listener::onFailure));
        }
    }

    private void executeAllTasks(
        MLIndexInsightGetRequest request,
        String storageIndex,
        String tenantId,
        ActionListener<MLIndexInsightGetResponse> listener
    ) {
        String indexName = request.getIndexName();
        StringBuilder combinedContent = new StringBuilder();

        // Create requests for each task type
        MLIndexInsightGetRequest statsRequest = MLIndexInsightGetRequest
            .builder()
            .indexName(indexName)
            .targetIndexInsight(MLIndexInsightType.STATISTICAL_DATA)
            .tenantId(tenantId)
            .build();
        MLIndexInsightGetRequest fieldRequest = MLIndexInsightGetRequest
            .builder()
            .indexName(indexName)
            .targetIndexInsight(MLIndexInsightType.FIELD_DESCRIPTION)
            .tenantId(tenantId)
            .build();
        MLIndexInsightGetRequest logRequest = MLIndexInsightGetRequest
            .builder()
            .indexName(indexName)
            .targetIndexInsight(MLIndexInsightType.LOG_RELATED_INDEX_CHECK)
            .tenantId(tenantId)
            .build();

        // Execute STATISTICAL_DATA first
        createTask(statsRequest).execute(storageIndex, tenantId, ActionListener.wrap(insight1 -> {
            combinedContent.append("STATISTICAL_DATA:\n").append(insight1.getContent());

            // Execute FIELD_DESCRIPTION second
            createTask(fieldRequest).execute(storageIndex, tenantId, ActionListener.wrap(insight2 -> {
                combinedContent.append("\n\nFIELD_DESCRIPTION:\n").append(insight2.getContent());

                // Execute LOG_RELATED_INDEX_CHECK third
                createTask(logRequest).execute(storageIndex, tenantId, ActionListener.wrap(insight3 -> {
                    combinedContent.append("\n\nLOG_RELATED_INDEX_CHECK:\n").append(insight3.getContent());

                    // Create combined insight
                    IndexInsight combinedInsight = IndexInsight
                        .builder()
                        .index(indexName)
                        .taskType(MLIndexInsightType.ALL)
                        .content(combinedContent.toString())
                        .status(IndexInsightTaskStatus.COMPLETED)
                        .lastUpdatedTime(Instant.now())
                        .build();

                    listener.onResponse(MLIndexInsightGetResponse.builder().indexInsight(combinedInsight).build());
                }, e -> {
                    // LOG_RELATED_INDEX_CHECK failed, return partial result
                    IndexInsight partialInsight = IndexInsight
                        .builder()
                        .index(indexName)
                        .taskType(MLIndexInsightType.ALL)
                        .content(combinedContent.toString())
                        .status(IndexInsightTaskStatus.COMPLETED)
                        .lastUpdatedTime(Instant.now())
                        .build();
                    listener.onResponse(MLIndexInsightGetResponse.builder().indexInsight(partialInsight).build());
                }));
            }, e -> {
                // FIELD_DESCRIPTION failed, try LOG_RELATED_INDEX_CHECK
                createTask(logRequest).execute(storageIndex, tenantId, ActionListener.wrap(insight3 -> {
                    combinedContent.append("\n\nLOG_RELATED_INDEX_CHECK:\n").append(insight3.getContent());
                    IndexInsight partialInsight = IndexInsight
                        .builder()
                        .index(indexName)
                        .taskType(MLIndexInsightType.ALL)
                        .content(combinedContent.toString())
                        .status(IndexInsightTaskStatus.COMPLETED)
                        .lastUpdatedTime(Instant.now())
                        .build();
                    listener.onResponse(MLIndexInsightGetResponse.builder().indexInsight(partialInsight).build());
                }, e2 -> {
                    // Both FIELD_DESCRIPTION and LOG_RELATED_INDEX_CHECK failed, return STATISTICAL_DATA only
                    IndexInsight partialInsight = IndexInsight
                        .builder()
                        .index(indexName)
                        .taskType(MLIndexInsightType.ALL)
                        .content(combinedContent.toString())
                        .status(IndexInsightTaskStatus.COMPLETED)
                        .lastUpdatedTime(Instant.now())
                        .build();
                    listener.onResponse(MLIndexInsightGetResponse.builder().indexInsight(partialInsight).build());
                }));
            }));
        }, e -> {
            // STATISTICAL_DATA failed, skip FIELD_DESCRIPTION and only try LOG_RELATED_INDEX_CHECK
            createTask(logRequest).execute(storageIndex, tenantId, ActionListener.wrap(insight3 -> {
                IndexInsight partialInsight = IndexInsight
                    .builder()
                    .index(indexName)
                    .taskType(MLIndexInsightType.ALL)
                    .content("LOG_RELATED_INDEX_CHECK:\n" + insight3.getContent())
                    .status(IndexInsightTaskStatus.COMPLETED)
                    .lastUpdatedTime(Instant.now())
                    .build();
                listener.onResponse(MLIndexInsightGetResponse.builder().indexInsight(partialInsight).build());
            }, e2 -> {
                // All tasks failed
                listener.onFailure(new RuntimeException("All index insight tasks failed"));
            }));
        }));
    }

    IndexInsightTask createTask(MLIndexInsightGetRequest request) {
        switch (request.getTargetIndexInsight()) {
            case STATISTICAL_DATA:
                return new StatisticalDataTask(request.getIndexName(), client);
            case FIELD_DESCRIPTION:
                return new FieldDescriptionTask(request.getIndexName(), client);
            case LOG_RELATED_INDEX_CHECK:
                return new LogRelatedIndexCheckTask(request.getIndexName(), client);
            default:
                throw new IllegalArgumentException("Unsupported task type: " + request.getTargetIndexInsight());
        }
    }
}
