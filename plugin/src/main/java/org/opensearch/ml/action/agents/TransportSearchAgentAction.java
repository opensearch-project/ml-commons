/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.opensearch.ml.action.handler.MLSearchHandler.wrapRestActionListener;

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
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.transport.agent.MLSearchAgentAction;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportSearchAgentAction extends HandledTransportAction<MLSearchActionRequest, SearchResponse> {
    private final Client client;
    private final SdkClient sdkClient;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportSearchAgentAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLSearchAgentAction.NAME, transportService, actionFilters, MLSearchActionRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, MLSearchActionRequest request, ActionListener<SearchResponse> actionListener) {
        request.indices(CommonValue.ML_AGENT_INDEX);
        String tenantId = request.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }
        search(request, tenantId, actionListener);
    }

    private void search(SearchRequest request, String tenantId, ActionListener<SearchResponse> actionListener) {
        ActionListener<SearchResponse> listener = wrapRestActionListener(actionListener, "Fail to search agent");
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<SearchResponse> wrappedListener = ActionListener.runBefore(listener, context::restore);
            // Check if the original query is not null before adding it to the must clause
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
            if (request.source().query() != null) {
                queryBuilder.must(request.source().query());
            }

            // Create a BoolQueryBuilder for the should clauses
            BoolQueryBuilder shouldQuery = QueryBuilders.boolQuery();

            // Add a should clause to include documents where IS_HIDDEN_FIELD is false
            shouldQuery.should(QueryBuilders.termQuery(MLAgent.IS_HIDDEN_FIELD, false));

            // Add a should clause to include documents where IS_HIDDEN_FIELD does not exist or is null
            shouldQuery.should(QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(MLAgent.IS_HIDDEN_FIELD)));

            // Set minimum should match to 1 to ensure at least one of the should conditions is met
            shouldQuery.minimumShouldMatch(1);

            // Add the shouldQuery to the main queryBuilder
            queryBuilder.filter(shouldQuery);

            request.source().query(queryBuilder);
            SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
                .builder()
                .indices(request.indices())
                .searchSourceBuilder(request.source())
                .tenantId(tenantId)
                .build();

            sdkClient.searchDataObjectAsync(searchDataObjectRequest).whenComplete((r, throwable) -> {
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable, OpenSearchStatusException.class);
                    log.error("Failed to search agent", cause);
                    wrappedListener.onFailure(cause);
                } else {
                    try {
                        SearchResponse searchResponse = SearchResponse.fromXContent(r.parser());
                        log.info("Agent search complete: {}", searchResponse.getHits().getTotalHits());
                        wrappedListener.onResponse(searchResponse);
                    } catch (Exception e) {
                        log.error("Failed to parse model search response", e);
                        wrappedListener
                            .onFailure(
                                new OpenSearchStatusException("Failed to parse model search response", RestStatus.INTERNAL_SERVER_ERROR)
                            );
                    }
                }
            });
        } catch (Exception e) {
            log.error("failed to search the agent index", e);
            actionListener.onFailure(e);
        }
    }
}
