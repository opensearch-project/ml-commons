/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.opensearch.ml.action.handler.MLSearchHandler.wrapRestActionListener;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.utils.RestActionUtils.wrapListenerToHandleSearchIndexNotFound;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.transport.model_group.MLModelGroupSearchAction;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.sdk.SearchDataObjectRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class SearchModelGroupTransportAction extends HandledTransportAction<SearchRequest, SearchResponse> {
    Client client;
    SdkClient sdkClient;
    ClusterService clusterService;

    ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public SearchModelGroupTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper
    ) {
        super(MLModelGroupSearchAction.NAME, transportService, actionFilters, SearchRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, SearchRequest request, ActionListener<SearchResponse> actionListener) {
        User user = RestActionUtils.getUserContext(client);
        ActionListener<SearchResponse> listener = wrapRestActionListener(actionListener, "Fail to search");
        request.indices(CommonValue.ML_MODEL_GROUP_INDEX);
        preProcessRoleAndPerformSearch(request, user, listener);
    }

    private void preProcessRoleAndPerformSearch(SearchRequest request, User user, ActionListener<SearchResponse> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<SearchResponse> wrappedListener = ActionListener.runBefore(listener, () -> context.restore());

            final ActionListener<SearchResponse> doubleWrappedListener = ActionListener
                .wrap(wrappedListener::onResponse, e -> wrapListenerToHandleSearchIndexNotFound(e, wrappedListener));

            if (!modelAccessControlHelper.skipModelAccessControl(user)) {
                // Security is enabled, filter is enabled and user isn't admin
                modelAccessControlHelper.addUserBackendRolesFilter(user, request.source());
                log.debug("Filtering result by " + user.getBackendRoles());
            }
            SearchDataObjectRequest searchDataObjecRequest = new SearchDataObjectRequest.Builder()
                .indices(request.indices())
                .searchSourceBuilder(request.source())
                .build();
            sdkClient
                .searchDataObjectAsync(searchDataObjecRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                .whenComplete((r, throwable) -> {
                    if (throwable == null) {
                        try {
                            SearchResponse searchResponse = SearchResponse.fromXContent(r.parser());
                            log.info("Model group search complete: {}", searchResponse.getHits().getTotalHits());
                            doubleWrappedListener.onResponse(searchResponse);
                        } catch (Exception e) {
                            log.error("Failed to parse search response", e);
                            doubleWrappedListener
                                .onFailure(
                                    new OpenSearchStatusException("Failed to parse search response", RestStatus.INTERNAL_SERVER_ERROR)
                                );
                        }
                    } else {
                        Exception e = SdkClientUtils.unwrapAndConvertToException(throwable);
                        log.error(e.getMessage(), e);
                        doubleWrappedListener.onFailure(e);
                    }
                });
        } catch (Exception e) {
            log.error("Failed to search", e);
            listener.onFailure(e);
        }
    }
}
