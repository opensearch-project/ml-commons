/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.opensearch.ml.action.handler.MLSearchHandler.wrapRestActionListener;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.utils.RestActionUtils.wrapListenerToHandleSearchIndexNotFound;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.transport.model_group.MLModelGroupSearchAction;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SdkClient;
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

            // Modify the query to include tenant ID filtering
            if (tenantId != null) {
                BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

                // Preserve existing query if present
                if (request.source().query() != null) {
                    queryBuilder.must(request.source().query());
                }
                // Add tenancy filter
                queryBuilder.filter(QueryBuilders.termQuery(TENANT_ID_FIELD, tenantId));

                // Update the request's source with the new query
                request.source().query(queryBuilder);
            }

            if (modelAccessControlHelper.skipModelAccessControl(user)) {
                client.search(request, doubleWrappedListener);
            } else {
                // Security is enabled, filter is enabled and user isn't admin
                modelAccessControlHelper.addUserBackendRolesFilter(user, request.source());
                log.debug("Filtering result by {}", user.getBackendRoles());
                client.search(request, doubleWrappedListener);
            }
        } catch (Exception e) {
            log.error("Failed to search", e);
            listener.onFailure(e);
        }
    }
}
