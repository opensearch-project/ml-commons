/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_RESOURCE_TYPE;
import static org.opensearch.ml.helper.ModelAccessControlHelper.shouldUseResourceAuthz;
import static org.opensearch.ml.utils.RestActionUtils.wrapListenerToHandleSearchIndexNotFound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.opensearch.ExceptionsHelper;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.Nullable;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.ResourceSharingClientAccessor;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLConnectorSearchAction;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class SearchConnectorTransportAction extends HandledTransportAction<MLSearchActionRequest, SearchResponse> {

    private final Client client;
    private final SdkClient sdkClient;

    private final ConnectorAccessControlHelper connectorAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public SearchConnectorTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLConnectorSearchAction.NAME, transportService, actionFilters, MLSearchActionRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, MLSearchActionRequest request, ActionListener<SearchResponse> actionListener) {
        request.indices(CommonValue.ML_CONNECTOR_INDEX);

        String tenantId = request.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }
        search(request, tenantId, actionListener);
    }

    private void search(SearchRequest request, String tenantId, ActionListener<SearchResponse> actionListener) {
        User user = RestActionUtils.getUserContext(client);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<SearchResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            List<String> excludes = Optional
                .ofNullable(request.source())
                .map(SearchSourceBuilder::fetchSource)
                .map(FetchSourceContext::excludes)
                .map(x -> Arrays.stream(x).collect(Collectors.toList()))
                .orElse(new ArrayList<>());
            excludes.add(HttpConnector.CREDENTIAL_FIELD);
            FetchSourceContext rebuiltFetchSourceContext = new FetchSourceContext(
                Optional
                    .ofNullable(request.source())
                    .map(SearchSourceBuilder::fetchSource)
                    .map(FetchSourceContext::fetchSource)
                    .orElse(true),
                Optional.ofNullable(request.source()).map(SearchSourceBuilder::fetchSource).map(FetchSourceContext::includes).orElse(null),
                excludes.toArray(new String[0])
            );
            request.source().fetchSource(rebuiltFetchSourceContext);

            final ActionListener<SearchResponse> doubleWrappedListener = ActionListener
                .wrap(wrappedListener::onResponse, e -> wrapListenerToHandleSearchIndexNotFound(e, wrappedListener));

            // When connectors are protected in their own right, search has to admit exactly what a per-connector
            // permission check would admit: the connectors whose own sharing record grants the caller. Without this the
            // point check denies an unshared connector while search still returns it.
            //
            // This deliberately does not lean on the security plugin's DLS filter. That filter only engages for a search
            // the security plugin sees as an internal request, and this search does not reach it that way - the same was
            // verified end to end for model search. Filtering here keeps it fail-closed regardless.
            if (shouldUseResourceAuthz(ML_CONNECTOR_RESOURCE_TYPE) && user != null) {
                var resourceSharingClient = ResourceSharingClientAccessor.getInstance().getResourceSharingClient();
                resourceSharingClient.getAccessibleResourceIds(ML_CONNECTOR_RESOURCE_TYPE, ActionListener.wrap(accessibleIds -> {
                    SearchSourceBuilder gated = Optional.ofNullable(request.source()).orElseGet(SearchSourceBuilder::new);
                    gated.query(restrictToAccessible(gated.query(), accessibleIds));
                    request.source(gated);
                    runSearch(request, tenantId, doubleWrappedListener);
                }, e -> {
                    log.error("Failed to resolve accessible ml-connector ids", e);
                    wrappedListener.onFailure(e);
                }));
                return;
            }

            if (!connectorAccessControlHelper.skipConnectorAccessControl(user)) {
                SearchSourceBuilder sourceBuilder = connectorAccessControlHelper.addUserBackendRolesFilter(user, request.source());
                request.source(sourceBuilder);
            }

            runSearch(request, tenantId, doubleWrappedListener);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            actionListener.onFailure(e);
        }
    }

    @VisibleForTesting
    public static void wrapListenerToHandleConnectorIndexNotFound(Exception e, ActionListener<SearchResponse> listener) {
        if (ExceptionsHelper.unwrapCause(e) instanceof IndexNotFoundException) {
            log.debug("Connectors index not created yet, therefore we will swallow the exception and return an empty search result");
            final InternalSearchResponse internalSearchResponse = InternalSearchResponse.empty();
            final SearchResponse emptySearchResponse = new SearchResponse(
                internalSearchResponse,
                null,
                0,
                0,
                0,
                0,
                new ShardSearchFailure[] {},
                SearchResponse.Clusters.EMPTY,
                null
            );
            listener.onResponse(emptySearchResponse);
        } else {
            listener.onFailure(e);
        }
    }

    private void runSearch(SearchRequest request, String tenantId, ActionListener<SearchResponse> listener) {
        SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
            .builder()
            .indices(request.indices())
            .searchSourceBuilder(request.source())
            .tenantId(tenantId)
            .build();
        sdkClient.searchDataObjectAsync(searchDataObjectRequest).whenComplete(SdkClientUtils.wrapSearchCompletion(listener));
    }

    /**
     * Narrows a search to the connectors the caller may reach. An empty set denies everything rather than matching
     * everything, so a caller with no accessible connector sees none instead of all of them.
     */
    static QueryBuilder restrictToAccessible(QueryBuilder existing, @Nullable Collection<String> accessibleConnectorIds) {
        final QueryBuilder gate = (accessibleConnectorIds == null || accessibleConnectorIds.isEmpty())
            ? QueryBuilders.boolQuery().mustNot(QueryBuilders.matchAllQuery())
            : QueryBuilders.idsQuery().addIds(accessibleConnectorIds.toArray(new String[0]));

        if (existing == null) {
            return gate;
        } else if (existing instanceof BoolQueryBuilder) {
            ((BoolQueryBuilder) existing).filter(gate);
            return existing;
        } else {
            return QueryBuilders.boolQuery().must(existing).filter(gate);
        }
    }
}
