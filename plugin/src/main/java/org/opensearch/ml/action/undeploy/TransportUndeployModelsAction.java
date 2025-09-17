/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.undeploy;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.NOT_FOUND;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.BulkDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportUndeployModelsAction extends HandledTransportAction<ActionRequest, MLUndeployModelsResponse> {
    TransportService transportService;
    ModelHelper modelHelper;
    MLTaskManager mlTaskManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    SdkClient sdkClient;

    Settings settings;
    NamedXContentRegistry xContentRegistry;
    DiscoveryNodeHelper nodeFilter;
    MLTaskDispatcher mlTaskDispatcher;
    MLModelManager mlModelManager;
    ModelAccessControlHelper modelAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportUndeployModelsAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ModelHelper modelHelper,
        MLTaskManager mlTaskManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        SdkClient sdkClient,
        Settings settings,
        NamedXContentRegistry xContentRegistry,
        DiscoveryNodeHelper nodeFilter,
        MLTaskDispatcher mlTaskDispatcher,
        MLModelManager mlModelManager,
        ModelAccessControlHelper modelAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLUndeployModelsAction.NAME, transportService, actionFilters, MLDeployModelRequest::new);
        this.transportService = transportService;
        this.modelHelper = modelHelper;
        this.mlTaskManager = mlTaskManager;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.nodeFilter = nodeFilter;
        this.mlTaskDispatcher = mlTaskDispatcher;
        this.mlModelManager = mlModelManager;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.settings = settings;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLUndeployModelsResponse> listener) {
        MLUndeployModelsRequest undeployModelsRequest = MLUndeployModelsRequest.fromActionRequest(request);
        String[] modelIds = undeployModelsRequest.getModelIds();
        String tenantId = undeployModelsRequest.getTenantId();
        String[] targetNodeIds = undeployModelsRequest.getNodeIds();
        log.info("Executing undeploy model action for modelIds: {}", Arrays.toString(modelIds));

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, listener)) {
            return;
        }

        if (modelIds == null) {
            log.error("No modelIds provided in undeploy.");
            listener.onFailure(new IllegalArgumentException("Must set specific model ids to undeploy"));
            return;
        }
        if (modelIds.length == 1) {
            String modelId = modelIds[0];
            validateAccess(modelId, tenantId, ActionListener.wrap(hasPermissionToUndeploy -> {
                if (hasPermissionToUndeploy) {
                    undeployModels(targetNodeIds, modelIds, tenantId, listener);
                } else {
                    listener.onFailure(new IllegalArgumentException("No permission to undeploy model " + modelId));
                }
            }, listener::onFailure));
        } else {
            // Only allow user to undeploy one model if model access control enabled.
            // With multiple models, it is difficult to check to which models user has access to.
            if (modelAccessControlHelper.isModelAccessControlEnabled()) {
                throw new IllegalArgumentException("only support undeploy one model");
            } else {
                searchHiddenModels(modelIds, ActionListener.wrap(hiddenModels -> {
                    if (hiddenModels != null
                        && hiddenModels.getHits().getTotalHits() != null
                        && hiddenModels.getHits().getTotalHits().value() != 0
                        && !isSuperAdminUserWrapper(clusterService, client)) {
                        List<String> hiddenModelIds = Arrays
                            .stream(hiddenModels.getHits().getHits())
                            .map(SearchHit::getId)
                            .collect(Collectors.toList());

                        String[] modelsIDsToUndeploy = Arrays
                            .stream(modelIds)
                            .filter(modelId -> !hiddenModelIds.contains(modelId))
                            .toArray(String[]::new);

                        undeployModels(targetNodeIds, modelsIDsToUndeploy, tenantId, listener);
                    } else {
                        undeployModels(targetNodeIds, modelIds, tenantId, listener);
                    }
                }, e -> {
                    log.error("Failed to search model index", e);
                    listener.onFailure(e);
                }));
            }
        }
    }

    private void undeployModels(
        String[] targetNodeIds,
        String[] modelIds,
        String tenantId,
        ActionListener<MLUndeployModelsResponse> listener
    ) {
        log.debug("Initiating undeploy on nodes: {}, for modelIds: {}", Arrays.toString(targetNodeIds), Arrays.toString(modelIds));
        MLUndeployModelNodesRequest mlUndeployModelNodesRequest = new MLUndeployModelNodesRequest(targetNodeIds, modelIds);
        mlUndeployModelNodesRequest.setTenantId(tenantId);

        client.execute(MLUndeployModelAction.INSTANCE, mlUndeployModelNodesRequest, ActionListener.wrap(response -> {
            log.info("Undeploy response received from nodes");
            /*
             * The method TransportUndeployModelsAction.processUndeployModelResponseAndUpdate(...) performs
             * undeploy action of models by removing the models from the nodes cache and updating the index when it's able to find it.
             *
             * The problem becomes when the models index is incorrect and no node(s) are servicing the model. This results in
             * `{}` responses (on undeploy action), with no update to the model index thus, causing incorrect model state status.
             *
             * Having this change enables a check that this edge case occurs along with having access to the model id
             * allowing us to update the stale model index correctly to `UNDEPLOYED` since no nodes service the model.
             */
            boolean modelNotFoundInNodesCache = response.getNodes().stream().allMatch(nodeResponse -> {
                Map<String, String> status = nodeResponse.getModelUndeployStatus();
                if (status == null)
                    return false;
                // Stream is used to catch all models edge case but only one is ever undeployed
                boolean modelCacheMissForModelIds = Arrays.stream(modelIds).allMatch(modelId -> {
                    String modelStatus = status.get(modelId);
                    return modelStatus != null && modelStatus.equalsIgnoreCase(NOT_FOUND);
                });

                return modelCacheMissForModelIds;
            });
            if (response.getNodes().isEmpty() || modelNotFoundInNodesCache) {
                log.warn("No nodes service these models, performing manual `UNDEPLOY` write to model index");
                bulkSetModelIndexToUndeploy(modelIds, tenantId, listener, response);
                return;
            }
            log.info("Successfully undeployed model(s) from nodes: {}", Arrays.toString(modelIds));
            listener.onResponse(new MLUndeployModelsResponse(response));
        }, listener::onFailure));
    }

    private void bulkSetModelIndexToUndeploy(
        String[] modelIds,
        String tenantId,
        ActionListener<MLUndeployModelsResponse> listener,
        MLUndeployModelNodesResponse mlUndeployModelNodesResponse
    ) {
        BulkDataObjectRequest bulkRequest = BulkDataObjectRequest.builder().globalIndex(ML_MODEL_INDEX).build();

        for (String modelId : modelIds) {

            Map<String, Object> updateDocument = new HashMap<>();

            updateDocument.put(MLModel.MODEL_STATE_FIELD, MLModelState.UNDEPLOYED.name());
            updateDocument.put(MLModel.PLANNING_WORKER_NODES_FIELD, List.of());
            updateDocument.put(MLModel.PLANNING_WORKER_NODE_COUNT_FIELD, 0);
            updateDocument.put(MLModel.LAST_UPDATED_TIME_FIELD, Instant.now().toEpochMilli());
            updateDocument.put(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD, 0);

            UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
                .builder()
                .id(modelId)
                .tenantId(tenantId)
                .dataObject(updateDocument)
                .build();
            bulkRequest.add(updateRequest).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        }

        log.info("No nodes running these models: {}", Arrays.toString(modelIds));

        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLUndeployModelsResponse> listenerWithContextRestoration = ActionListener
                .runBefore(listener, () -> threadContext.restore());

            ActionListener<BulkResponse> bulkResponseListener = ActionListener.wrap(br -> {
                listenerWithContextRestoration.onResponse(new MLUndeployModelsResponse(mlUndeployModelNodesResponse));
            }, e -> {
                String modelsNotFoundMessage = String
                    .format("Failed to set the following modelId(s) to UNDEPLOY in index: %s", Arrays.toString(modelIds));
                log.error(modelsNotFoundMessage, e);

                OpenSearchStatusException exception = new OpenSearchStatusException(
                    modelsNotFoundMessage + e.getMessage(),
                    RestStatus.INTERNAL_SERVER_ERROR
                );
                listenerWithContextRestoration.onFailure(exception);
            });

            sdkClient.bulkDataObjectAsync(bulkRequest).whenComplete((response, exception) -> {
                if (exception != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(exception, OpenSearchStatusException.class);
                    bulkResponseListener.onFailure(cause);
                    return;
                }

                try {
                    BulkResponse bulkResponse = BulkResponse.fromXContent(response.parser());
                    log
                        .info(
                            "Executed {} bulk operations with {} failures, Took: {}",
                            bulkResponse.getItems().length,
                            bulkResponse.hasFailures()
                                ? Arrays.stream(bulkResponse.getItems()).filter(BulkItemResponse::isFailed).count()
                                : 0,
                            bulkResponse.getTook()
                        );
                    List<String> unemployedModelIds = Arrays
                        .stream(bulkResponse.getItems())
                        .filter(bulkItemResponse -> !bulkItemResponse.isFailed())
                        .map(BulkItemResponse::getId)
                        .collect(Collectors.toList());
                    log
                        .debug(
                            "Successfully set the following modelId(s) to UNDEPLOY in index: {}",
                            Arrays.toString(unemployedModelIds.toArray())
                        );

                    bulkResponseListener.onResponse(bulkResponse);
                } catch (Exception e) {
                    bulkResponseListener.onFailure(e);
                }
            });
        } catch (Exception e) {
            log.error("Unexpected error while setting the following modelId(s) to UNDEPLOY in index: {}", Arrays.toString(modelIds), e);
            listener.onFailure(e);
        }

    }

    private void validateAccess(String modelId, String tenantId, ActionListener<Boolean> listener) {
        User user = RestActionUtils.getUserContext(client);
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            mlModelManager.getModel(modelId, tenantId, null, excludes, ActionListener.runBefore(ActionListener.wrap(mlModel -> {
                if (!TenantAwareHelper.validateTenantResource(mlFeatureEnabledSetting, tenantId, mlModel.getTenantId(), listener)) {
                    return;
                }
                Boolean isHidden = mlModel.getIsHidden();
                if (isHidden != null && isHidden) {
                    if (isSuperAdmin) {
                        listener.onResponse(true);
                    } else {
                        listener
                            .onFailure(
                                new OpenSearchStatusException(
                                    "User doesn't have privilege to perform this operation on this model",
                                    RestStatus.FORBIDDEN
                                )
                            );
                    }
                } else {
                    modelAccessControlHelper
                        .validateModelGroupAccess(
                            user,
                            mlFeatureEnabledSetting,
                            tenantId,
                            mlModel.getModelGroupId(),
                            client,
                            sdkClient,
                            listener
                        );
                }
            }, e -> {
                log.error("Failed to find Model", e);
                listener.onFailure(e);
            }), context::restore));
        } catch (Exception e) {
            log.error("Failed to undeploy ML model");
            listener.onFailure(e);
        }
    }

    public void searchHiddenModels(String[] modelIds, ActionListener<SearchResponse> listener) throws IllegalArgumentException {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            // Create a TermsQueryBuilder for MODEL_ID_FIELD using the modelIds
            TermsQueryBuilder termsQuery = QueryBuilders.termsQuery("_id", modelIds);

            // Create a TermQueryBuilder for IS_HIDDEN_FIELD with value true
            TermQueryBuilder isHiddenQuery = QueryBuilders.termQuery(MLModel.IS_HIDDEN_FIELD, true);

            // Create an existsQuery to exclude model chunks
            // Combine the queries using a bool query with must and mustNot clause
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder
                .query(
                    QueryBuilders
                        .boolQuery()
                        .must(termsQuery)
                        .must(isHiddenQuery)
                        .mustNot(QueryBuilders.existsQuery(MLModel.CHUNK_NUMBER_FIELD))
                );

            SearchRequest searchRequest = new SearchRequest(ML_MODEL_INDEX).source(searchSourceBuilder);

            SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
                .builder()
                .indices(searchRequest.indices())
                .searchSourceBuilder(searchRequest.source())
                .build();

            sdkClient.searchDataObjectAsync(searchDataObjectRequest).whenComplete((r, throwable) -> {
                context.restore();
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    log.error("Failed to search model index", cause);
                    if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
                        listener.onResponse(null);
                    } else {
                        listener.onFailure(cause);
                    }
                } else {
                    try {
                        SearchResponse searchResponse = r.searchResponse();
                        // Parsing failure would cause NPE on next line
                        log.info("Model Index search complete: {}", searchResponse.getHits().getTotalHits());
                        listener.onResponse(searchResponse);
                    } catch (Exception e) {
                        log.error("Failed to parse search response", e);
                        listener
                            .onFailure(new OpenSearchStatusException("Failed to parse search response", RestStatus.INTERNAL_SERVER_ERROR));
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to search model index", e);
            listener.onFailure(e);
        }
    }

    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }

}
