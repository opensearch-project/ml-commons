/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.utils.RestActionUtils.wrapListenerToHandleSearchIndexNotFound;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.common.transport.task.MLTaskSearchAction;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.sdk.SearchDataObjectRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class SearchTaskTransportAction extends HandledTransportAction<MLSearchActionRequest, SearchResponse> {
    private Client client;
    private final SdkClient sdkClient;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public SearchTaskTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLTaskSearchAction.NAME, transportService, actionFilters, MLSearchActionRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, MLSearchActionRequest mlSearchActionRequest, ActionListener<SearchResponse> actionListener) {
        String tenantId = mlSearchActionRequest.getTenantId();
        SearchRequest request = mlSearchActionRequest.getSearchRequest();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            final ActionListener<SearchResponse> wrappedListener = ActionListener
                .wrap(actionListener::onResponse, e -> wrapListenerToHandleSearchIndexNotFound(e, actionListener));

            SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
                .builder()
                .indices(request.indices())
                .searchSourceBuilder(request.source())
                .tenantId(tenantId)
                .build();

            sdkClient
                .searchDataObjectAsync(searchDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                .whenComplete((r, throwable) -> {
                    context.restore();
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        log.error("Failed to search task", cause);
                        wrappedListener.onFailure(cause);
                    } else {
                        try {
                            SearchResponse searchResponse = SearchResponse.fromXContent(r.parser());
                            log.info("Task search complete: {}", searchResponse.getHits().getTotalHits());
                            wrappedListener.onResponse(searchResponse);
                        } catch (Exception e) {
                            log.error("Failed to parse task search response", e);
                            wrappedListener
                                .onFailure(
                                    new OpenSearchStatusException("Failed to parse task search response", RestStatus.INTERNAL_SERVER_ERROR)
                                );
                        }
                    }
                });
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            actionListener.onFailure(e);
        }
    }
}
