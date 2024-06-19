/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.utils.MLExceptionUtils.logException;

import java.util.HashSet;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupRequest;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelGroupManager;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportUpdateModelGroupAction extends HandledTransportAction<ActionRequest, MLUpdateModelGroupResponse> {

    private final TransportService transportService;
    private final ActionFilters actionFilters;
    private Client client;
    final SdkClient sdkClient;
    private NamedXContentRegistry xContentRegistry;
    ClusterService clusterService;

    ModelAccessControlHelper modelAccessControlHelper;
    MLModelGroupManager mlModelGroupManager;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportUpdateModelGroupAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper,
        MLModelGroupManager mlModelGroupManager,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLUpdateModelGroupAction.NAME, transportService, actionFilters, MLUpdateModelGroupRequest::new);
        this.actionFilters = actionFilters;
        this.transportService = transportService;
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.mlModelGroupManager = mlModelGroupManager;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLUpdateModelGroupResponse> listener) {
        MLUpdateModelGroupRequest updateModelGroupRequest = MLUpdateModelGroupRequest.fromActionRequest(request);
        MLUpdateModelGroupInput updateModelGroupInput = updateModelGroupRequest.getUpdateModelGroupInput();
        String modelGroupId = updateModelGroupInput.getModelGroupID();
        String tenantId = updateModelGroupInput.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, listener)) {
            return;
        }
        User user = RestActionUtils.getUserContext(client);
        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = new GetDataObjectRequest.Builder()
            .index(ML_MODEL_GROUP_INDEX)
            .id(modelGroupId)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLUpdateModelGroupResponse> wrappedListener = ActionListener.runBefore(listener, context::restore);

            sdkClient
                .getDataObjectAsync(getDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                .whenComplete((r, throwable) -> {
                    log.debug("Completed Get Model group Request, id:{}", modelGroupId);
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        if (cause instanceof IndexNotFoundException) {
                            log.error("Failed to get model group index", cause);
                            wrappedListener.onFailure(new OpenSearchStatusException("Failed to find model group", RestStatus.NOT_FOUND));
                        } else {
                            log.error("Failed to get ML group {}", modelGroupId, cause);
                            wrappedListener.onFailure(cause);
                        }
                    } else {
                        if (r != null && r.parser().isPresent()) {
                            try {
                                XContentParser parser = r.parser().get();
                                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                MLModelGroup mlModelGroup = MLModelGroup.parse(parser);
                                if (TenantAwareHelper
                                    .validateTenantResource(
                                        mlFeatureEnabledSetting,
                                        tenantId,
                                        mlModelGroup.getTenantId(),
                                        wrappedListener
                                    )) {
                                    if (modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(user)) {
                                        validateRequestForAccessControl(updateModelGroupInput, user, mlModelGroup);
                                    } else {
                                        validateSecurityDisabledOrModelAccessControlDisabled(updateModelGroupInput);
                                    }
                                    updateModelGroup(modelGroupId, r.source(), updateModelGroupInput, wrappedListener, user);
                                }
                            } catch (Exception e) {
                                log.error("Failed to parse ml connector {}", r.id(), e);
                                wrappedListener.onFailure(e);
                            }
                        } else {
                            wrappedListener
                                .onFailure(
                                    new OpenSearchStatusException(
                                        "Failed to find model group with the provided model group id: " + modelGroupId,
                                        RestStatus.NOT_FOUND
                                    )
                                );
                        }
                    }
                });
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
        String modelGroupName = (String) source.get(MLModelGroup.MODEL_GROUP_NAME_FIELD);
        if (updateModelGroupInput.getModelAccessMode() != null) {
            source.put(MLModelGroup.ACCESS, updateModelGroupInput.getModelAccessMode().getValue());
            if (AccessMode.RESTRICTED != updateModelGroupInput.getModelAccessMode()) {
                source.put(MLModelGroup.BACKEND_ROLES_FIELD, ImmutableList.of());
            }
        } else if (updateModelGroupInput.getBackendRoles() != null
            || Boolean.TRUE.equals(updateModelGroupInput.getIsAddAllBackendRoles())) {
            source.put(MLModelGroup.ACCESS, AccessMode.RESTRICTED.getValue());
        }
        if (updateModelGroupInput.getBackendRoles() != null) {
            source.put(MLModelGroup.BACKEND_ROLES_FIELD, updateModelGroupInput.getBackendRoles());
        }
        if (Boolean.TRUE.equals(updateModelGroupInput.getIsAddAllBackendRoles())) {
            source.put(MLModelGroup.BACKEND_ROLES_FIELD, user.getBackendRoles());
        }
        if (StringUtils.isNotBlank(updateModelGroupInput.getDescription())) {
            source.put(MLModelGroup.DESCRIPTION_FIELD, updateModelGroupInput.getDescription());
        }
        if (StringUtils.isNotBlank(updateModelGroupInput.getName()) && !updateModelGroupInput.getName().equals(modelGroupName)) {
            mlModelGroupManager.validateUniqueModelGroupName(updateModelGroupInput.getName(), ActionListener.wrap(modelGroups -> {
                if (modelGroups != null
                    && modelGroups.getHits().getTotalHits() != null
                    && modelGroups.getHits().getTotalHits().value != 0) {
                    for (SearchHit documentFields : modelGroups.getHits()) {
                        String id = documentFields.getId();
                        listener
                            .onFailure(
                                new IllegalArgumentException(
                                    "The name you provided is already being used by another model with ID: "
                                        + id
                                        + ". Please provide a different name"
                                )
                            );
                    }
                } else {
                    source.put(MLModelGroup.MODEL_GROUP_NAME_FIELD, updateModelGroupInput.getName());
                    updateModelGroup(modelGroupId, source, listener);
                }
            }, e -> {
                log.error("Failed to search model group index", e);
                listener.onFailure(e);
            }));
        } else {
            updateModelGroup(modelGroupId, source, listener);
        }

    }

    private void updateModelGroup(String modelGroupId, Map<String, Object> source, ActionListener<MLUpdateModelGroupResponse> listener) {
        UpdateRequest updateModelGroupRequest = new UpdateRequest();
        updateModelGroupRequest.index(ML_MODEL_GROUP_INDEX).id(modelGroupId).doc(source);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLUpdateModelGroupResponse> wrappedListener = ActionListener.runBefore(listener, context::restore);
            client
                .update(
                    updateModelGroupRequest,
                    ActionListener.wrap(r -> { wrappedListener.onResponse(new MLUpdateModelGroupResponse("Updated")); }, e -> {
                        if (e instanceof IndexNotFoundException) {
                            wrappedListener.onFailure(new MLResourceNotFoundException("Fail to find model group"));
                        } else {
                            log.error("Failed to update model group {}", modelGroupId, e);
                            wrappedListener.onFailure(new MLValidationException("Failed to update Model Group"));
                        }
                    })
                );
        } catch (Exception e) {
            logException("Failed to Update model group ", e, log);
            listener.onFailure(e);
        }
    }

    private void validateRequestForAccessControl(MLUpdateModelGroupInput input, User user, MLModelGroup mlModelGroup) {
        if (hasAccessControlChange(input)) {
            if (!modelAccessControlHelper.isOwner(mlModelGroup.getOwner(), user) && !modelAccessControlHelper.isAdmin(user)) {
                throw new IllegalArgumentException("Only owner or admin can update access control data.");
            }
        }
        if (!modelAccessControlHelper.isAdmin(user)
            && !modelAccessControlHelper.isOwner(mlModelGroup.getOwner(), user)
            && !modelAccessControlHelper.isUserHasBackendRole(user, mlModelGroup)) {
            throw new IllegalArgumentException("You don't have permission to update this model group.");
        } else if (modelAccessControlHelper.isOwner(mlModelGroup.getOwner(), user)
            && !modelAccessControlHelper.isAdmin(user)
            && !modelAccessControlHelper.isOwnerStillHasPermission(user, mlModelGroup)) {
            throw new IllegalArgumentException(
                "You don't have the specified backend role to update this model group. For more information, contact your administrator."
            );
        }
        AccessMode accessMode = input.getModelAccessMode();
        if ((AccessMode.PUBLIC == accessMode || AccessMode.PRIVATE == accessMode)
            && (!CollectionUtils.isEmpty(input.getBackendRoles()) || Boolean.TRUE.equals(input.getIsAddAllBackendRoles()))) {
            throw new IllegalArgumentException("You can specify backend roles only for a model group with the restricted access mode.");
        } else if (accessMode == null || AccessMode.RESTRICTED == accessMode) {
            if (modelAccessControlHelper.isAdmin(user) && Boolean.TRUE.equals(input.getIsAddAllBackendRoles())) {
                throw new IllegalArgumentException("Admin users cannot add all backend roles to a model group.");
            }
            if (Boolean.TRUE.equals(input.getIsAddAllBackendRoles()) && CollectionUtils.isEmpty(user.getBackendRoles())) {
                throw new IllegalArgumentException("You don't have any backend roles.");
            }
            if (CollectionUtils.isEmpty(input.getBackendRoles()) && Boolean.FALSE.equals(input.getIsAddAllBackendRoles())) {
                throw new IllegalArgumentException("You have to specify backend roles when add all backend roles is set to false.");
            }
            if (!CollectionUtils.isEmpty(input.getBackendRoles()) && Boolean.TRUE.equals(input.getIsAddAllBackendRoles())) {
                throw new IllegalArgumentException("You cannot specify backend roles and add all backend roles at the same time.");
            }
            if (AccessMode.RESTRICTED == accessMode
                && CollectionUtils.isEmpty(input.getBackendRoles())
                && !Boolean.TRUE.equals(input.getIsAddAllBackendRoles())) {
                throw new IllegalArgumentException(
                    "You must specify one or more backend roles or add all backend roles to register a restricted model group."
                );
            }
            if (!modelAccessControlHelper.isAdmin(user)
                && !CollectionUtils.isEmpty(input.getBackendRoles())
                && !new HashSet<>(user.getBackendRoles()).containsAll(input.getBackendRoles())) {
                throw new IllegalArgumentException("You don't have the backend roles specified.");
            }
        }
    }

    private boolean hasAccessControlChange(MLUpdateModelGroupInput input) {
        return input.getModelAccessMode() != null || input.getIsAddAllBackendRoles() != null || input.getBackendRoles() != null;
    }

    private void validateSecurityDisabledOrModelAccessControlDisabled(MLUpdateModelGroupInput input) {
        if (input.getModelAccessMode() != null
            || input.getIsAddAllBackendRoles() != null
            || !CollectionUtils.isEmpty(input.getBackendRoles())) {
            throw new IllegalArgumentException(
                "You cannot specify model access control parameters because the Security plugin or model access control is disabled on your cluster."
            );
        }
    }

}
