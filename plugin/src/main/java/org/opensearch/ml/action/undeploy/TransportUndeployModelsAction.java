/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.undeploy;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.UNDEPLOYED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.Client;
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
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodeResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportUndeployModelsAction extends HandledTransportAction<ActionRequest, MLUndeployModelsResponse> {
    TransportService transportService;
    ModelHelper modelHelper;
    MLTaskManager mlTaskManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;

    Settings settings;
    NamedXContentRegistry xContentRegistry;
    DiscoveryNodeHelper nodeFilter;
    MLTaskDispatcher mlTaskDispatcher;
    MLModelManager mlModelManager;
    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public TransportUndeployModelsAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ModelHelper modelHelper,
        MLTaskManager mlTaskManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        Settings settings,
        NamedXContentRegistry xContentRegistry,
        DiscoveryNodeHelper nodeFilter,
        MLTaskDispatcher mlTaskDispatcher,
        MLModelManager mlModelManager,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        super(MLUndeployModelsAction.NAME, transportService, actionFilters, MLDeployModelRequest::new);
        this.transportService = transportService;
        this.modelHelper = modelHelper;
        this.mlTaskManager = mlTaskManager;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.nodeFilter = nodeFilter;
        this.mlTaskDispatcher = mlTaskDispatcher;
        this.mlModelManager = mlModelManager;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.settings = settings;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLUndeployModelsResponse> listener) {
        MLUndeployModelsRequest undeployModelsRequest = MLUndeployModelsRequest.fromActionRequest(request);
        String[] modelIds = undeployModelsRequest.getModelIds();
        String[] targetNodeIds = undeployModelsRequest.getNodeIds();

        if (modelIds == null) {
            listener.onFailure(new IllegalArgumentException("Must set specific model ids to undeploy"));
            return;
        }
        if (modelIds.length == 1) {
            String modelId = modelIds[0];
            validateAccess(modelId, ActionListener.wrap(hasPermissionToUndeploy -> {
                if (hasPermissionToUndeploy) {
                    undeployModels(targetNodeIds, modelIds, listener);
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
                        && hiddenModels.getHits().getTotalHits().value != 0
                        && !isSuperAdminUserWrapper(clusterService, client)) {
                        List<String> hiddenModelIds = Arrays
                            .stream(hiddenModels.getHits().getHits())
                            .map(SearchHit::getId)
                            .collect(Collectors.toList());

                        String[] modelsIDsToUndeploy = Arrays
                            .stream(modelIds)
                            .filter(modelId -> !hiddenModelIds.contains(modelId))
                            .toArray(String[]::new);

                        undeployModels(targetNodeIds, modelsIDsToUndeploy, listener);
                    } else {
                        undeployModels(targetNodeIds, modelIds, listener);
                    }
                }, e -> {
                    log.error("Failed to search model index", e);
                    listener.onFailure(e);
                }));
            }
        }
    }

    private void undeployModels(String[] targetNodeIds, String[] modelIds, ActionListener<MLUndeployModelsResponse> listener) {
        MLUndeployModelNodesRequest mlUndeployModelNodesRequest = new MLUndeployModelNodesRequest(targetNodeIds, modelIds);

        client.execute(MLUndeployModelAction.INSTANCE, mlUndeployModelNodesRequest, ActionListener.wrap(r -> {
            if (r.getNodes() != null) {
                List<MLUndeployModelNodeResponse> responses = r.getNodes();
                Map<String, List<String>> actualRemovedNodesMap = new HashMap<>();
                Map<String, String[]> modelWorkNodesBeforeRemoval = new HashMap<>();
                responses.forEach(response -> {
                    Map<String, String[]> nodeCounts = response.getModelWorkerNodeBeforeRemoval();

                    if (nodeCounts != null) {
                        for (Map.Entry<String, String[]> entry : nodeCounts.entrySet()) {
                            // when undeploy a undeployed model, the entry.getvalue() is null
                            if (entry.getValue() != null
                                && (!modelWorkNodesBeforeRemoval.containsKey(entry.getKey())
                                    || modelWorkNodesBeforeRemoval.get(entry.getKey()).length < entry.getValue().length)) {
                                modelWorkNodesBeforeRemoval.put(entry.getKey(), entry.getValue());
                            }
                        }
                    }

                    Map<String, String> modelUndeployStatus = response.getModelUndeployStatus();
                    for (Map.Entry<String, String> entry : modelUndeployStatus.entrySet()) {
                        String status = entry.getValue();
                        if (UNDEPLOYED.equals(status)) {
                            String modelId = entry.getKey();
                            if (!actualRemovedNodesMap.containsKey(modelId)) {
                                actualRemovedNodesMap.put(modelId, new ArrayList<>());
                            }
                            actualRemovedNodesMap.get(modelId).add(response.getNode().getId());
                        }
                    }
                });

                MLSyncUpInput syncUpInput = MLSyncUpInput
                    .builder()
                    .removedWorkerNodes(covertRemoveNodesMapForSyncUp(actualRemovedNodesMap))
                    .build();

                MLSyncUpNodesRequest syncUpRequest = new MLSyncUpNodesRequest(nodeFilter.getAllNodes(), syncUpInput);
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    ActionListener<MLUndeployModelsResponse> wrappedListener = ActionListener.runBefore(listener, context::restore);
                    if (actualRemovedNodesMap.size() > 0) {
                        BulkRequest bulkRequest = new BulkRequest();
                        Map<String, Boolean> deployToAllNodes = new HashMap<>();
                        for (String modelId : actualRemovedNodesMap.keySet()) {
                            UpdateRequest updateRequest = new UpdateRequest();
                            List<String> removedNodes = actualRemovedNodesMap.get(modelId);
                            int removedNodeCount = removedNodes.size();
                            /**
                             *  If allow custom deploy is false, user can only undeploy all nodes and status is undeployed.
                             *  If allow custom deploy is true, user can undeploy all nodes and status is undeployed,
                             *  or undeploy partial nodes, and status is deployed, this case means user created a new deployment plan, and
                             *  we need to update both planning worker nodes (count) and current worker nodes (count)
                             *  and deployToAllNodes value in model index.
                             */
                            Map<String, Object> updateDocument = new HashMap<>();
                            if (modelWorkNodesBeforeRemoval.get(modelId).length == removedNodeCount) { // undeploy all nodes.
                                updateDocument.put(MLModel.PLANNING_WORKER_NODES_FIELD, ImmutableList.of());
                                updateDocument.put(MLModel.PLANNING_WORKER_NODE_COUNT_FIELD, 0);
                                updateDocument.put(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD, 0);
                                updateDocument.put(MLModel.MODEL_STATE_FIELD, MLModelState.UNDEPLOYED);
                            } else { // undeploy partial nodes.
                                // TODO (to fix) when undeploy partial nodes, the original model status could be partially_deployed,
                                // and the user could be undeploying not running model nodes, and we should update model status to deployed.
                                updateDocument.put(MLModel.DEPLOY_TO_ALL_NODES_FIELD, false);
                                List<String> newPlanningWorkerNodes = Arrays
                                    .stream(modelWorkNodesBeforeRemoval.get(modelId))
                                    .filter(x -> !removedNodes.contains(x))
                                    .collect(Collectors.toList());
                                updateDocument.put(MLModel.PLANNING_WORKER_NODES_FIELD, newPlanningWorkerNodes);
                                updateDocument.put(MLModel.PLANNING_WORKER_NODE_COUNT_FIELD, newPlanningWorkerNodes.size());
                                updateDocument.put(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD, newPlanningWorkerNodes.size());
                                deployToAllNodes.put(modelId, false);
                            }
                            updateRequest.index(ML_MODEL_INDEX).id(modelId).doc(updateDocument);
                            bulkRequest.add(updateRequest).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                        }
                        syncUpInput.setDeployToAllNodes(deployToAllNodes);
                        client.bulk(bulkRequest, ActionListener.wrap(bulkResponse -> {
                            log
                                .debug(
                                    "updated model state as undeployed for : {}",
                                    Arrays.toString(actualRemovedNodesMap.keySet().toArray(new String[0]))
                                );
                            syncUpUndeployedModels(syncUpRequest, ActionListener.wrap(syncUpNodesResponse -> {
                                wrappedListener.onResponse(new MLUndeployModelsResponse(r));
                            }, wrappedListener::onFailure));
                        }, e -> {
                            log.error("Failed to update model state as undeployed", e);
                            wrappedListener.onFailure(e);
                        }));
                    } else {
                        syncUpUndeployedModels(syncUpRequest, ActionListener.wrap(syncUpNodesResponse -> {
                            wrappedListener.onResponse(new MLUndeployModelsResponse(r));
                        }, wrappedListener::onFailure));
                    }
                } catch (Exception e) {
                    log.error("Failed to sync up removed node", e);
                    listener.onFailure(e);
                }
            } else {
                log.warn("MLUndeployModelNodesResponse has null MLUndeployModelNodeResponse");
                listener.onResponse(new MLUndeployModelsResponse(r));
            }
        }, listener::onFailure));
    }

    private void validateAccess(String modelId, ActionListener<Boolean> listener) {
        User user = RestActionUtils.getUserContext(client);
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            mlModelManager.getModel(modelId, null, excludes, ActionListener.runBefore(ActionListener.wrap(mlModel -> {
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
                    modelAccessControlHelper.validateModelGroupAccess(user, mlModel.getModelGroupId(), client, listener);
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

            client.search(searchRequest, ActionListener.runBefore(ActionListener.wrap(models -> { listener.onResponse(models); }, e -> {
                if (e instanceof IndexNotFoundException) {
                    listener.onResponse(null);
                } else {
                    log.error("Failed to search model index", e);
                    listener.onFailure(e);
                }
            }), () -> context.restore()));
        } catch (Exception e) {
            log.error("Failed to search model index", e);
            listener.onFailure(e);
        }
    }

    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }

    private Map<String, String[]> covertRemoveNodesMapForSyncUp(Map<String, List<String>> actualRemovedNodesMap) {
        Map<String, String[]> removedNodesMap = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : actualRemovedNodesMap.entrySet()) {
            removedNodesMap.put(entry.getKey(), entry.getValue().toArray(new String[0]));
            log.debug("removed node for model: {}, {}", entry.getKey(), Arrays.toString(entry.getValue().toArray(new String[0])));
        }
        return removedNodesMap;
    }

    private void syncUpUndeployedModels(MLSyncUpNodesRequest syncUpRequest, ActionListener<MLSyncUpNodesResponse> listener) {
        client.execute(MLSyncUpAction.INSTANCE, syncUpRequest, ActionListener.wrap(r -> {
            listener.onResponse(r);
            log.debug("Sync up removed nodes successfully");
        }, e -> {
            listener.onFailure(e);
            log.error("Failed to sync up removed node", e);
        }));
    }
}
