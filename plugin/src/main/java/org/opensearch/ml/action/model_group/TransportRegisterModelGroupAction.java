/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;

import java.time.Instant;
import java.util.HashSet;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.CollectionUtils;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.MLModelGroup.MLModelGroupBuilder;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupRequest;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportRegisterModelGroupAction extends HandledTransportAction<ActionRequest, MLRegisterModelGroupResponse> {

    private final TransportService transportService;
    private final ActionFilters actionFilters;
    private final MLIndicesHandler mlIndicesHandler;
    private final ThreadPool threadPool;
    private final Client client;
    ClusterService clusterService;

    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public TransportRegisterModelGroupAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLIndicesHandler mlIndicesHandler,
        ThreadPool threadPool,
        Client client,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        super(MLRegisterModelGroupAction.NAME, transportService, actionFilters, MLRegisterModelGroupRequest::new);
        this.transportService = transportService;
        this.actionFilters = actionFilters;
        this.mlIndicesHandler = mlIndicesHandler;
        this.threadPool = threadPool;
        this.client = client;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLRegisterModelGroupResponse> listener) {
        MLRegisterModelGroupRequest createModelGroupRequest = MLRegisterModelGroupRequest.fromActionRequest(request);
        MLRegisterModelGroupInput createModelGroupInput = createModelGroupRequest.getRegisterModelGroupInput();
        createModelGroup(createModelGroupInput, ActionListener.wrap(modelGroupId -> {
            listener.onResponse(new MLRegisterModelGroupResponse(modelGroupId, MLTaskState.CREATED.name()));
        }, ex -> {
            log.error("Failed to init model group index", ex);
            listener.onFailure(ex);
        }));
    }

    public void createModelGroup(MLRegisterModelGroupInput input, ActionListener<String> listener) {
        try {
            String modelName = input.getName();
            User user = RestActionUtils.getUserContext(client);
            MLModelGroupBuilder builder = MLModelGroup.builder();
            MLModelGroup mlModelGroup;
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
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
                        .source(mlModelGroup.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
                    indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

                    client.index(indexRequest, ActionListener.wrap(r -> {
                        log.debug("Indexed model group doc successfully {}", modelName);
                        listener.onResponse(r.getId());
                    }, e -> {
                        log.error("Failed to index model group doc", e);
                        listener.onFailure(e);
                    }));
                }, ex -> {
                    log.error("Failed to init model group index", ex);
                    listener.onFailure(ex);
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
            if (!Boolean.TRUE.equals(isAddAllBackendRoles) && CollectionUtils.isEmpty(input.getBackendRoles())) {
                throw new IllegalArgumentException(
                    "You must specify at least one backend role or make the model group public/private for registering it."
                );
            } else {
                input.setModelAccessMode(AccessMode.RESTRICTED);
                modelAccessMode = AccessMode.RESTRICTED;
            }
        }
        if ((AccessMode.PUBLIC == modelAccessMode || AccessMode.PRIVATE == modelAccessMode)
            && (!CollectionUtils.isEmpty(input.getBackendRoles()) || Boolean.TRUE.equals(isAddAllBackendRoles))) {
            throw new IllegalArgumentException("You can specify backend roles only for a model group with the restricted access mode.");
        } else if (AccessMode.RESTRICTED == modelAccessMode) {
            if (modelAccessControlHelper.isAdmin(user) && Boolean.TRUE.equals(isAddAllBackendRoles)) {
                throw new IllegalArgumentException("Admin users cannot add all backend roles to a model group.");
            }
            if (CollectionUtils.isEmpty(user.getBackendRoles())) {
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
                && !CollectionUtils.isEmpty(input.getBackendRoles())
                && !new HashSet<>(user.getBackendRoles()).containsAll(input.getBackendRoles())) {
                throw new IllegalArgumentException("You don't have the backend roles specified.");
            }
        }
    }

    private void validateSecurityDisabledOrModelAccessControlDisabled(MLRegisterModelGroupInput input) {
        if (input.getModelAccessMode() != null || input.getIsAddAllBackendRoles() != null || input.getBackendRoles() != null) {
            throw new IllegalArgumentException(
                "You cannot specify model access control parameters because the Security plugin or model access control is disabled on your cluster."
            );
        }
    }
}
