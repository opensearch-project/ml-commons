/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.utils.MLExceptionUtils.logException;

import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.CollectionUtils;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupRequest;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.SecurityUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportUpdateModelGroupAction extends HandledTransportAction<ActionRequest, MLUpdateModelGroupResponse> {

    private final TransportService transportService;
    private final ActionFilters actionFilters;
    private Client client;
    private NamedXContentRegistry xContentRegistry;
    ClusterService clusterService;

    ModelAccessControlHelper modelAccessControlHelper;
    @Inject
    public TransportUpdateModelGroupAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        super(MLUpdateModelGroupAction.NAME, transportService, actionFilters, MLUpdateModelGroupRequest::new);
        this.actionFilters = actionFilters;
        this.transportService = transportService;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLUpdateModelGroupResponse> listener) {
        MLUpdateModelGroupRequest updateModelGroupRequest = MLUpdateModelGroupRequest.fromActionRequest(request);
        MLUpdateModelGroupInput updateModelGroupInput = updateModelGroupRequest.getUpdateModelGroupInput();

        String modelGroupId = updateModelGroupInput.getModelGroupID();
        GetRequest getModelGroupRequest = new GetRequest(ML_MODEL_GROUP_INDEX).id(modelGroupId);

        if (modelGroupId == null) {
            throw new IllegalArgumentException("Model Group ID cannot be empty/null");
        }

        User user = RestActionUtils.getUserContext(client);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.get(getModelGroupRequest, ActionListener.wrap(modelGroup -> {
                if (modelGroup.isExists()) {
                    Map<String, Object> source = modelGroup.getSourceAsMap();
                    Map<String, Object> owner = (Map) source.get(MLModelGroup.OWNER);

                    if (modelAccessControlHelper.skipModelAccessControl(user)) {
                        updateModelGroup(modelGroupId, source, updateModelGroupInput, listener, user);
                    } else if (user.getName().equals(owner.get("name"))) {
                        if (!CollectionUtils.isEmpty(updateModelGroupInput.getBackendRoles())) {
                            Boolean isRolePresent = updateModelGroupInput
                                .getBackendRoles()
                                .stream()
                                .allMatch(user.getBackendRoles().stream().collect(Collectors.toSet())::contains);

                            if (!isRolePresent) {
                                log.error("Invalid Backend Roles provided in the input");
                                throw new IllegalArgumentException("Invalid Backend Roles provided in the input");
                            }
                        }

                        updateModelGroup(modelGroupId, source, updateModelGroupInput, listener, user);
                    } else {
                        throw new IllegalArgumentException("User doesn't have valid privilege to perform this operation");
                    }
                } else {
                    listener.onFailure(new MLResourceNotFoundException("Fail to find model group"));
                }
            }, e -> {
                logException("Failed to Update model model", e, log);
                listener.onFailure(e);
            }));
        } catch (Exception e) {
            logException("Failed to Update model group", e, log);
            listener.onFailure(e);
        }
    }

    private void updateModelGroup(
        String modelGroupId,
        Map<String, Object> source,
        MLUpdateModelGroupInput updateModelGroupInput,
        ActionListener<MLUpdateModelGroupResponse> listener,
        User user
    ) {
        if (StringUtils.isNotBlank(updateModelGroupInput.getName())) {
            source.put(MLModelGroup.MODEL_GROUP_NAME_FIELD, updateModelGroupInput.getName());
        }
        if (StringUtils.isNotBlank(updateModelGroupInput.getDescription())) {
            source.put(MLModelGroup.DESCRIPTION_FIELD, updateModelGroupInput.getDescription());
        }
        if (StringUtils.isNotBlank(updateModelGroupInput.getDescription())) {
            source.put(MLModelGroup.TAGS_FIELD, updateModelGroupInput.getTags());
        }
        if (Boolean.TRUE.equals(updateModelGroupInput.getIsPublic())) {
            source.put(MLModelGroup.ACCESS, MLModelGroup.PUBLIC);
        } else if (Boolean.TRUE.equals(updateModelGroupInput.getIsAddAllBackendRoles())) {
            if (!SecurityUtils.isAdmin(user)) {
                source.put(MLModelGroup.BACKEND_ROLES_FIELD, user.getBackendRoles());
                source.put(MLModelGroup.ACCESS, null);
            } else
                throw new IllegalArgumentException("Admin cannot specify add all backend roles field in the request");
        } else if (!CollectionUtils.isEmpty(updateModelGroupInput.getBackendRoles())) {
            source.put(MLModelGroup.BACKEND_ROLES_FIELD, updateModelGroupInput.getBackendRoles());
            source.put(MLModelGroup.ACCESS, null);
        } else if (updateModelGroupInput.getBackendRoles() != null && updateModelGroupInput.getBackendRoles().isEmpty()) {
            source.put(MLModelGroup.ACCESS, MLModelGroup.PRIVATE);
        }

        UpdateRequest updateModelGroupRequest = new UpdateRequest();
        updateModelGroupRequest.index(ML_MODEL_GROUP_INDEX).id(modelGroupId).doc(source);
        client
            .update(
                updateModelGroupRequest,
                ActionListener.wrap(r -> { listener.onResponse(new MLUpdateModelGroupResponse("Updated")); }, e -> {
                    log.error("Failed to update Model Group", e);
                    throw new MLException("Failed to update Model Group", e);
                })
            );

    }

}
