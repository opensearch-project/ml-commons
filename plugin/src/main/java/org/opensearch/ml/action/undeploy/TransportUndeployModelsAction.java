/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.undeploy;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
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
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.sdk.SearchDataObjectRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

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
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, listener)) {
            return;
        }

        if (modelIds == null) {
            listener.onFailure(new IllegalArgumentException("Must set specific model ids to undeploy"));
            return;
        }
        if (modelIds.length == 1) {
            String modelId = modelIds[0];
            validateAccess(modelId, tenantId, ActionListener.wrap(hasPermissionToUndeploy -> {
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
            listener.onResponse(new MLUndeployModelsResponse(r));
        }, listener::onFailure));
    }

    private void validateAccess(String modelId, String tenantId, ActionListener<Boolean> listener) {
        User user = RestActionUtils.getUserContext(client);
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);
        String[] excludes = new String[] { MLModel.MODEL_CONTENT_FIELD, MLModel.OLD_MODEL_CONTENT_FIELD };
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            mlModelManager.getModel(modelId, null, excludes, ActionListener.runBefore(ActionListener.wrap(mlModel -> {
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

            SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
                .builder()
                .indices(searchRequest.indices())
                .searchSourceBuilder(searchRequest.source())
                .build();

            sdkClient
                .searchDataObjectAsync(searchDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                .whenComplete((r, throwable) -> {
                    context.restore();
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        log.error("Failed to search model index", cause);
                        if (cause instanceof IndexNotFoundException) {
                            listener.onResponse(null);
                        } else {
                            listener.onFailure(cause);
                        }
                    } else {
                        try {
                            SearchResponse searchResponse = SearchResponse.fromXContent(r.parser());
                            log.info("Model Index search complete: {}", searchResponse.getHits().getTotalHits());
                            listener.onResponse(searchResponse);
                        } catch (Exception e) {
                            log.error("Failed to parse search response", e);
                            listener
                                .onFailure(
                                    new OpenSearchStatusException("Failed to parse search response", RestStatus.INTERNAL_SERVER_ERROR)
                                );
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
