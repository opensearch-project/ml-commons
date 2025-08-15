/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.opensearch.ml.action.handler.MLSearchHandler.wrapRestActionListener;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.utils.RestActionUtils.wrapListenerToHandleSearchIndexNotFound;

import java.util.Collections;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.model_group.MLModelGroupSearchAction;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.resources.MLResourceSharingExtension;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.security.spi.resources.client.ResourceSharingClient;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class SearchModelGroupTransportAction extends HandledTransportAction<MLSearchActionRequest, SearchResponse> {
    Client client;
    SdkClient sdkClient;
    ClusterService clusterService;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    ModelAccessControlHelper modelAccessControlHelper;

    @Inject(optional = true)
    public MLResourceSharingExtension mlResourceSharingExtension;

    @Inject
    public SearchModelGroupTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLModelGroupSearchAction.NAME, transportService, actionFilters, MLSearchActionRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, MLSearchActionRequest request, ActionListener<SearchResponse> actionListener) {
        User user = RestActionUtils.getUserContext(client);
        ActionListener<SearchResponse> listener = wrapRestActionListener(actionListener, "Fail to search");
        String tenantId = request.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }
        preProcessRoleAndPerformSearch(request, tenantId, user, listener);
    }

    private void preProcessRoleAndPerformSearch(
        SearchRequest request,
        String tenantId,
        User user,
        ActionListener<SearchResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<SearchResponse> wrappedListener = ActionListener.runBefore(listener, context::restore);

            final ActionListener<SearchResponse> doubleWrappedListener = ActionListener
                .wrap(wrappedListener::onResponse, e -> wrapListenerToHandleSearchIndexNotFound(e, wrappedListener));

            // If resource-sharing feature is enabled, we fetch accessible model-groups and restrict the search to those model-groups only.
            if (mlResourceSharingExtension != null && mlResourceSharingExtension.getResourceSharingClient() != null) {
                // If a model-group is shared, then it will have been shared at-least at read access, hence the final result is guaranteed
                // to only contain model-groups that the user at-least has read access to.
                addAccessibleModelGroupsFilterAndSearch(tenantId, request, doubleWrappedListener);
                return;
            }
            if (!modelAccessControlHelper.skipModelAccessControl(user)) {
                // Security is enabled, filter is enabled and user isn't admin
                modelAccessControlHelper.addUserBackendRolesFilter(user, request.source());
                log.debug("Filtering result by {}", user.getBackendRoles());
            }
            search(tenantId, request, doubleWrappedListener);
        } catch (Exception e) {
            log.error("Failed to search", e);
            listener.onFailure(e);
        }
    }

    private void addAccessibleModelGroupsFilterAndSearch(
        String tenantId,
        SearchRequest request,
        ActionListener<SearchResponse> wrappedListener
    ) {
        SearchSourceBuilder sourceBuilder = request.source() != null ? request.source() : new SearchSourceBuilder();
        ResourceSharingClient rsc = mlResourceSharingExtension.getResourceSharingClient();
        // filter by accessible model-groups
        rsc.getAccessibleResourceIds(ML_MODEL_GROUP_INDEX, ActionListener.wrap(ids -> {
            sourceBuilder.query(modelAccessControlHelper.mergeWithAccessFilter(sourceBuilder.query(), ids));
            request.source(sourceBuilder);
            search(tenantId, request, wrappedListener);
        }, e -> {
            // Fail-safe: deny-all and still return a response
            sourceBuilder.query(modelAccessControlHelper.mergeWithAccessFilter(sourceBuilder.query(), Collections.emptySet()));
            request.source(sourceBuilder);
            search(tenantId, request, wrappedListener);
        }));
    }

    private void search(String tenantId, SearchRequest request, ActionListener<SearchResponse> listener) {
        SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
            .builder()
            .indices(request.indices())
            .searchSourceBuilder(request.source())
            .tenantId(tenantId)
            .build();
        sdkClient.searchDataObjectAsync(searchDataObjectRequest).whenComplete(SdkClientUtils.wrapSearchCompletion(listener));
    }
}
