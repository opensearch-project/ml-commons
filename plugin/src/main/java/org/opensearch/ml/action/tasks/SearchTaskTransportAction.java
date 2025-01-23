/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.utils.RestActionUtils.wrapListenerToHandleSearchIndexNotFound;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.common.transport.task.MLTaskSearchAction;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SdkClient;
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

            // Modify the query to include tenant ID filtering
            if (tenantId != null) {
                BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

                // Preserve existing query if present
                if (request.source().query() != null) {
                    queryBuilder.must(request.source().query());
                }
                // Add tenancy filter
                queryBuilder.filter(QueryBuilders.termQuery(TENANT_ID_FIELD, tenantId)); // Replace 'tenant_id_field' with actual field name

                // Update the request's source with the new query
                request.source().query(queryBuilder);
            }
            client.search(request, ActionListener.runBefore(wrappedListener, context::restore));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            actionListener.onFailure(e);
        }
    }
}
