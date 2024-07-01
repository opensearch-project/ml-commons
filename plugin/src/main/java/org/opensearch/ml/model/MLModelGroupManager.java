/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;

import java.time.Instant;
import java.util.HashSet;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.sdk.PutDataObjectRequest;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.sdk.SearchDataObjectRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLModelGroupManager {
    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;
    private final SdkClient sdkClient;
    ClusterService clusterService;

    ModelAccessControlHelper modelAccessControlHelper;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public MLModelGroupManager(
        MLIndicesHandler mlIndicesHandler,
        Client client,
        SdkClient sdkClient,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
        this.sdkClient = sdkClient;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    public void createModelGroup(MLRegisterModelGroupInput input, ActionListener<String> listener) {
        try {
            String modelName = input.getName();
            User user = RestActionUtils.getUserContext(client);
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<String> wrappedListener = ActionListener.runBefore(listener, context::restore);
                validateUniqueModelGroupName(input.getName(), ActionListener.wrap(modelGroups -> {
                    if (modelGroups != null
                        && modelGroups.getHits().getTotalHits() != null
                        && modelGroups.getHits().getTotalHits().value != 0) {
                        for (SearchHit documentFields : modelGroups.getHits()) {
                            String id = documentFields.getId();
                            wrappedListener
                                .onFailure(
                                    new IllegalArgumentException(
                                        "The name you provided is already being used by a model group with ID: " + id + "."
                                    )
                                );
                        }
                    } else {
                        MLModelGroup.MLModelGroupBuilder builder = MLModelGroup.builder();
                        MLModelGroup mlModelGroup;
                        if (modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(user)) {
                            validateRequestForAccessControl(input, user);
                            builder = builder.access(input.getModelAccessMode().getValue());
                            if (Boolean.TRUE.equals(input.getIsAddAllBackendRoles())) {
                                input.setBackendRoles(user.getBackendRoles());
                            }
                            mlModelGroup = builder
                                .name(modelName)
                                .description(input.getDescription())
                                .backendRoles(input.getBackendRoles())
                                .owner(user)
                                .createdTime(Instant.now())
                                .lastUpdatedTime(Instant.now())
                                .tenantId(input.getTenantId())
                                .build();
                        } else {
                            validateSecurityDisabledOrModelAccessControlDisabled(input);
                            mlModelGroup = builder
                                .name(modelName)
                                .description(input.getDescription())
                                .access(AccessMode.PUBLIC.getValue())
                                .createdTime(Instant.now())
                                .lastUpdatedTime(Instant.now())
                                .tenantId(input.getTenantId())
                                .build();
                        }

                        mlIndicesHandler.initModelGroupIndexIfAbsent(ActionListener.wrap(res -> {
                            sdkClient
                                .putDataObjectAsync(
                                    new PutDataObjectRequest.Builder()
                                        .tenantId(mlModelGroup.getTenantId())
                                        .index(ML_MODEL_GROUP_INDEX)
                                        .dataObject(mlModelGroup)
                                        .build(),
                                    client.threadPool().executor(GENERAL_THREAD_POOL)
                                )
                                .whenComplete((r, throwable) -> {
                                    if (throwable != null) {
                                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                                        log.error("Failed to index model group", cause);
                                        wrappedListener.onFailure(cause);
                                    } else {
                                        try {
                                            IndexResponse indexResponse = IndexResponse.fromXContent(r.parser());
                                            log
                                                .info(
                                                    "Model group creation result: {}, model group id: {}",
                                                    indexResponse.getResult(),
                                                    indexResponse.getId()
                                                );
                                            wrappedListener.onResponse(r.id());
                                        } catch (Exception e) {
                                            wrappedListener.onFailure(e);
                                        }
                                    }
                                });

                        }, ex -> {
                            log.error("Failed to init model group index", ex);
                            wrappedListener.onFailure(ex);
                        }));
                    }
                }, e -> {
                    log.error("Failed to search model group index", e);
                    wrappedListener.onFailure(e);
                }));
            } catch (Exception e) {
                log.error("Failed to create model group doc", e);
                listener.onFailure(e);
            }
        } catch (final Exception e) {
            log.error("Failed to init model group index", e);
            listener.onFailure(e);
        }
    }

    private void validateRequestForAccessControl(MLRegisterModelGroupInput input, User user) {
        AccessMode modelAccessMode = input.getModelAccessMode();
        Boolean isAddAllBackendRoles = input.getIsAddAllBackendRoles();
        if (modelAccessMode == null) {
            if (!CollectionUtils.isEmpty(input.getBackendRoles()) && Boolean.TRUE.equals(isAddAllBackendRoles)) {
                throw new IllegalArgumentException("You cannot specify backend roles and add all backend roles at the same time.");
            } else if (Boolean.TRUE.equals(isAddAllBackendRoles) || !CollectionUtils.isEmpty(input.getBackendRoles())) {
                input.setModelAccessMode(AccessMode.RESTRICTED);
                modelAccessMode = AccessMode.RESTRICTED;
            } else {
                input.setModelAccessMode(AccessMode.PRIVATE);
            }
        }
        if ((AccessMode.PUBLIC == modelAccessMode || AccessMode.PRIVATE == modelAccessMode)
            && (!CollectionUtils.isEmpty(input.getBackendRoles()) || Boolean.TRUE.equals(isAddAllBackendRoles))) {
            throw new IllegalArgumentException("You can specify backend roles only for a model group with the restricted access mode.");
        } else if (AccessMode.RESTRICTED == modelAccessMode) {
            if (modelAccessControlHelper.isAdmin(user) && Boolean.TRUE.equals(isAddAllBackendRoles)) {
                throw new IllegalArgumentException("Admin users cannot add all backend roles to a model group.");
            }
            if (!modelAccessControlHelper.isAdmin(user) && CollectionUtils.isEmpty(user.getBackendRoles())) {
                throw new IllegalArgumentException("You must have at least one backend role to register a restricted model group.");
            }
            if (CollectionUtils.isEmpty(input.getBackendRoles()) && !Boolean.TRUE.equals(isAddAllBackendRoles)) {
                throw new IllegalArgumentException(
                    "You must specify one or more backend roles or add all backend roles to register a restricted model group."
                );
            }
            if (!CollectionUtils.isEmpty(input.getBackendRoles()) && Boolean.TRUE.equals(isAddAllBackendRoles)) {
                throw new IllegalArgumentException("You cannot specify backend roles and add all backend roles at the same time.");
            }
            if (!modelAccessControlHelper.isAdmin(user)
                && !Boolean.TRUE.equals(isAddAllBackendRoles)
                && !new HashSet<>(user.getBackendRoles()).containsAll(input.getBackendRoles())) {
                throw new IllegalArgumentException("You don't have the backend roles specified.");
            }
        }
    }

    public void validateUniqueModelGroupName(String name, ActionListener<SearchResponse> listener) throws IllegalArgumentException {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            BoolQueryBuilder query = new BoolQueryBuilder();
            query.filter(new TermQueryBuilder(MLRegisterModelGroupInput.NAME_FIELD + ".keyword", name));

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(query);
            SearchRequest searchRequest = new SearchRequest(ML_MODEL_GROUP_INDEX).source(searchSourceBuilder);

            SearchDataObjectRequest searchDataObjectRequest = new SearchDataObjectRequest.Builder()
                .indices(searchRequest.indices())
                .searchSourceBuilder(searchRequest.source())
                .build();

            sdkClient
                .searchDataObjectAsync(searchDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                .whenComplete((r, throwable) -> {
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        log.error("Failed to search model group index", cause);
                        if (cause instanceof IndexNotFoundException) {
                            listener.onResponse(null);
                        } else {
                            listener.onFailure(cause);
                        }
                    } else {
                        try {
                            SearchResponse searchResponse = SearchResponse.fromXContent(r.parser());
                            log.info("Model group search complete: {}", searchResponse.getHits().getTotalHits());
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
            log.error("Failed to search model group index", e);
            listener.onFailure(e);
        }
    }

    /**
     * Get model group from model group index.
     *
     * @param modelGroupId  model group id
     * @param listener action listener
     */
    public void getModelGroupResponse(String modelGroupId, ActionListener<GetResponse> listener) {
        GetRequest getRequest = new GetRequest();
        getRequest.index(ML_MODEL_GROUP_INDEX).id(modelGroupId);
        client.get(getRequest, ActionListener.wrap(r -> {
            if (r != null && r.isExists()) {
                listener.onResponse(r);
            } else {
                listener.onFailure(new MLResourceNotFoundException("Failed to find model group with ID: " + modelGroupId));
            }
        }, listener::onFailure));
    }

    private void validateSecurityDisabledOrModelAccessControlDisabled(MLRegisterModelGroupInput input) {
        if (input.getModelAccessMode() != null
            || input.getIsAddAllBackendRoles() != null
            || !CollectionUtils.isEmpty(input.getBackendRoles())) {
            throw new IllegalArgumentException(
                "You cannot specify model access control parameters because the Security plugin or model access control is disabled on your cluster."
            );
        }
    }
}
