/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.utils.RestActionUtils.wrapListenerToHandleSearchIndexNotFound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.transport.connector.MLConnectorSearchAction;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.sdk.SearchDataObjectRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class SearchConnectorTransportAction extends HandledTransportAction<SearchRequest, SearchResponse> {

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
        super(MLConnectorSearchAction.NAME, transportService, actionFilters, SearchRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, SearchRequest request, ActionListener<SearchResponse> actionListener) {
        request.indices(CommonValue.ML_CONNECTOR_INDEX);
        if (mlFeatureEnabledSetting.isMultiTenancyEnabled() && !TenantAwareHelper.isTenantFilteringEnabled(request)) {
            actionListener
                .onFailure(
                    new OpenSearchStatusException("Failed to get the tenant ID from the search request", RestStatus.INTERNAL_SERVER_ERROR)
                );
            return;
        }
        search(request, actionListener);
    }

    private void search(SearchRequest request, ActionListener<SearchResponse> actionListener) {
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

            if (!connectorAccessControlHelper.skipConnectorAccessControl(user)) {
                SearchSourceBuilder sourceBuilder = connectorAccessControlHelper.addUserBackendRolesFilter(user, request.source());
                request.source(sourceBuilder);
            }
            SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
                .builder()
                .indices(request.indices())
                .searchSourceBuilder(request.source())
                .build();
            sdkClient
                .searchDataObjectAsync(searchDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                .whenComplete((r, throwable) -> {
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        log.error("Failed to search connector", cause);
                        doubleWrappedListener.onFailure(cause);
                    } else {
                        try {
                            SearchResponse searchResponse = SearchResponse.fromXContent(r.parser());
                            log.info("Connector search complete: {}", searchResponse.getHits().getTotalHits());
                            doubleWrappedListener.onResponse(searchResponse);
                        } catch (Exception e) {
                            log.error("Failed to parse search response", e);
                            doubleWrappedListener
                                .onFailure(
                                    new OpenSearchStatusException("Failed to parse search response", RestStatus.INTERNAL_SERVER_ERROR)
                                );
                        }
                    }
                });
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
}
