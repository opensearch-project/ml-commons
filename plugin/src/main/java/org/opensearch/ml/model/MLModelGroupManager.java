/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;

import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;

import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLModelGroupManager {
    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;
    ClusterService clusterService;

    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public MLModelGroupManager(
        MLIndicesHandler mlIndicesHandler,
        Client client,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    public void createModelGroup(MLRegisterModelGroupInput input, ActionListener<String> listener) {
        try {
            String modelName = input.getName();
            User user = RestActionUtils.getUserContext(client);
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<String> wrappedListener = ActionListener.runBefore(listener, () -> context.restore());
                validateUniqueModelGroupName(input.getName(), ActionListener.wrap(modelGroups -> {
                    if (modelGroups != null
                        && modelGroups.getHits().getTotalHits() != null
                        && modelGroups.getHits().getTotalHits().value != 0) {
                        Iterator<SearchHit> iterator = modelGroups.getHits().iterator();
                        while (iterator.hasNext()) {
                            String id = iterator.next().getId();
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
                                .build();
                        } else {
                            validateSecurityDisabledOrModelAccessControlDisabled(input);
                            mlModelGroup = builder
                                .name(modelName)
                                .description(input.getDescription())
                                .access(AccessMode.PUBLIC.getValue())
                                .createdTime(Instant.now())
                                .lastUpdatedTime(Instant.now())
                                .build();
                        }

                        mlIndicesHandler.initModelGroupIndexIfAbsent(ActionListener.wrap(res -> {
                            IndexRequest indexRequest = new IndexRequest(ML_MODEL_GROUP_INDEX);
                            indexRequest
                                .source(
                                    mlModelGroup.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS)
                                );
                            indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

                            client.index(indexRequest, ActionListener.wrap(r -> {
                                log.debug("Indexed model group doc successfully {}", modelName);
                                wrappedListener.onResponse(r.getId());
                            }, e -> {
                                log.error("Failed to index model group doc", e);
                                wrappedListener.onFailure(e);
                            }));
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
        BoolQueryBuilder query = new BoolQueryBuilder();
        query.filter(new TermQueryBuilder(MLRegisterModelGroupInput.NAME_FIELD + ".keyword", name));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(query);
        SearchRequest searchRequest = new SearchRequest(ML_MODEL_GROUP_INDEX).source(searchSourceBuilder);

        client.search(searchRequest, ActionListener.wrap(modelGroups -> { listener.onResponse(modelGroups); }, e -> {
            if (e instanceof IndexNotFoundException) {
                listener.onResponse(null);
            } else {
                log.error("Failed to search model group index", e);
                listener.onFailure(e);
            }
        }));
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
