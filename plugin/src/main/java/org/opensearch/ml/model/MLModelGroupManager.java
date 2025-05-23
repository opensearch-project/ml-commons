/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED;
import static org.opensearch.security.spi.resources.FeatureConfigConstants.OPENSEARCH_RESOURCE_SHARING_ENABLED;
import static org.opensearch.security.spi.resources.FeatureConfigConstants.OPENSEARCH_RESOURCE_SHARING_ENABLED_DEFAULT;
import static org.opensearch.security.spi.resources.ResourceAccessLevels.PLACE_HOLDER;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.ResourceSharingClientAccessor;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.security.spi.resources.client.ResourceSharingClient;
import org.opensearch.security.spi.resources.sharing.Recipient;
import org.opensearch.security.spi.resources.sharing.Recipients;
import org.opensearch.security.spi.resources.sharing.ShareWith;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLModelGroupManager {
    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;
    private final Settings settings;
    private final SdkClient sdkClient;
    ClusterService clusterService;

    ModelAccessControlHelper modelAccessControlHelper;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public MLModelGroupManager(
        MLIndicesHandler mlIndicesHandler,
        Client client,
        Settings settings,
        SdkClient sdkClient,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
        this.settings = settings;
        this.sdkClient = sdkClient;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    public void createModelGroup(MLRegisterModelGroupInput input, ActionListener<String> listener) {
        try {
            String modelName = input.getName();
            User user = RestActionUtils.getUserContext(client);
            // Create a recipient sharing list
            AtomicReference<Map<Recipient, Set<String>>> recipientMap = new AtomicReference<>();
            boolean isResourceSharingFeatureEnabled = ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED.get(settings)
                && this.settings.getAsBoolean(OPENSEARCH_RESOURCE_SHARING_ENABLED, OPENSEARCH_RESOURCE_SHARING_ENABLED_DEFAULT);

            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<String> wrappedListener = ActionListener.runBefore(listener, context::restore);
                validateUniqueModelGroupName(input.getName(), input.getTenantId(), ActionListener.wrap(modelGroups -> {
                    if (modelGroups != null
                        && modelGroups.getHits().getTotalHits() != null
                        && modelGroups.getHits().getTotalHits().value() != 0) {
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

                        // TODO: Remove security-related entries from MLModelGroup builder
                        if (modelAccessControlHelper.isSecurityEnabledAndModelAccessControlEnabled(user)) {
                            validateRequestForAccessControl(input, user);
                            builder = builder.access(input.getModelAccessMode().getValue());

                            // share with current user's backend-roles
                            // TODO: check if resource should be shared with user's backend roles by default
                            recipientMap.set(Map.of(Recipient.BACKEND_ROLES, Set.copyOf(user.getBackendRoles())));

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

                            // TODO: Check if following line is actually required since by default the model will be pass-through when sec
                            // is disabled
                            recipientMap
                                .set(
                                    Map.of(Recipient.USERS, Set.of("*"), Recipient.ROLES, Set.of("*"), Recipient.BACKEND_ROLES, Set.of("*"))
                                );

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
                                    PutDataObjectRequest
                                        .builder()
                                        .tenantId(mlModelGroup.getTenantId())
                                        .index(ML_MODEL_GROUP_INDEX)
                                        .dataObject(mlModelGroup)
                                        .build()
                                )
                                .whenComplete((r, throwable) -> {
                                    if (throwable != null) {
                                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                                        log.error("Failed to index model group", cause);
                                        wrappedListener.onFailure(cause);
                                    } else {
                                        try {
                                            IndexResponse indexResponse = r.indexResponse();
                                            log
                                                .info(
                                                    "Model group creation result: {}, model group id: {}",
                                                    indexResponse.getResult(),
                                                    indexResponse.getId()
                                                );

                                            // TODO: Remove this feature flag check once feature is GA, as it will be enabled by default
                                            if (isResourceSharingFeatureEnabled) {
                                                // Create an entry in resource-sharing index
                                                String modelGroupId = indexResponse.getId();
                                                String modelGroupIndex = indexResponse.getIndex();
                                                ShareWith shareWith = new ShareWith(
                                                    Map.of(PLACE_HOLDER, new Recipients(recipientMap.get()))
                                                );

                                                ResourceSharingClient resourceSharingClient = ResourceSharingClientAccessor
                                                    .getInstance()
                                                    .getResourceSharingClient();

                                                resourceSharingClient
                                                    .share(
                                                        modelGroupId,
                                                        modelGroupIndex,
                                                        shareWith,
                                                        ActionListener.wrap(resourceSharing -> {
                                                            log
                                                                .debug(
                                                                    "Successfully shared ml-model-group: {} with entities: {}",
                                                                    modelName,
                                                                    recipientMap
                                                                );

                                                            wrappedListener.onResponse(r.id());
                                                        }, listener::onFailure)
                                                    );
                                            } else {
                                                wrappedListener.onResponse(r.id());
                                            }

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

    public void validateUniqueModelGroupName(String name, String tenantId, ActionListener<SearchResponse> listener)
        throws IllegalArgumentException {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            BoolQueryBuilder query = new BoolQueryBuilder();
            query.filter(new TermQueryBuilder(MLRegisterModelGroupInput.NAME_FIELD + ".keyword", name));

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(query);
            SearchRequest searchRequest = new SearchRequest(ML_MODEL_GROUP_INDEX).source(searchSourceBuilder);

            SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
                .builder()
                .indices(searchRequest.indices())
                .searchSourceBuilder(searchRequest.source())
                .tenantId(tenantId)
                .build();

            sdkClient.searchDataObjectAsync(searchDataObjectRequest).whenComplete((r, throwable) -> {
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    if (ExceptionsHelper.unwrap(throwable, IndexNotFoundException.class) != null) {
                        log.debug("Model group index does not exist");
                        listener.onResponse(null);
                    } else {
                        log.error("Failed to search model group index", cause);
                        listener.onFailure(cause);
                    }
                } else {
                    try {
                        SearchResponse searchResponse = r.searchResponse();
                        // Parsing failure would cause NPE on next line
                        log.info("Model group search complete: {}", searchResponse.getHits().getTotalHits());
                        listener.onResponse(searchResponse);
                    } catch (Exception e) {
                        log.error("Failed to parse search response", e);
                        listener
                            .onFailure(new OpenSearchStatusException("Failed to parse search response", RestStatus.INTERNAL_SERVER_ERROR));
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
     * @param sdkClient    SDK client
     * @param modelGroupId Model group ID
     * @param listener     Action listener
     */
    public void getModelGroupResponse(SdkClient sdkClient, String modelGroupId, ActionListener<GetResponse> listener) {
        GetDataObjectRequest getRequest = buildGetModelGroupRequest(modelGroupId);

        sdkClient.getDataObjectAsync(getRequest).whenComplete((response, throwable) -> {
            if (throwable != null) {
                handleError(throwable, listener);
                return;
            }

            processModelGroupResponse(response, modelGroupId, listener);
        });
    }

    private GetDataObjectRequest buildGetModelGroupRequest(String modelGroupId) {
        return GetDataObjectRequest.builder().index(ML_MODEL_GROUP_INDEX).id(modelGroupId).build();
    }

    private void handleError(Throwable throwable, ActionListener<GetResponse> listener) {
        Exception exception = SdkClientUtils.unwrapAndConvertToException(throwable);
        listener.onFailure(exception);
    }

    private void processModelGroupResponse(GetDataObjectResponse response, String modelGroupId, ActionListener<GetResponse> listener) {
        try {
            GetResponse getResponse = response.getResponse();
            if (getResponse == null || !getResponse.isExists()) {
                listener.onFailure(new MLResourceNotFoundException("Failed to find model group with ID: " + modelGroupId));
                return;
            }

            parseAndRespond(getResponse, listener);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private void parseAndRespond(GetResponse getResponse, ActionListener<GetResponse> listener) {
        try (
            XContentParser parser = jsonXContent
                .createParser(
                    NamedXContentRegistry.EMPTY,
                    LoggingDeprecationHandler.INSTANCE,
                    Strings.toString(MediaTypeRegistry.JSON, getResponse)
                )
        ) {
            listener.onResponse(GetResponse.fromXContent(parser));
        } catch (Exception e) {
            log.error("Failed to parse model group response: {}", getResponse.getId(), e);
            listener.onFailure(e);
        }
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
