/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.remote;

import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.transport.connector.MLConnectorSearchAction;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class SearchConnectorTransportAction extends HandledTransportAction<SearchRequest, SearchResponse> {

    private final Client client;

    private final ConnectorAccessControlHelper connectorAccessControlHelper;

    @Inject
    public SearchConnectorTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ConnectorAccessControlHelper connectorAccessControlHelper
    ) {
        super(MLConnectorSearchAction.NAME, transportService, actionFilters, SearchRequest::new);
        this.client = client;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, SearchRequest request, ActionListener<SearchResponse> actionListener) {
        request.indices(CommonValue.ML_CONNECTOR_INDEX);
        search(request, actionListener);
    }

    private void search(SearchRequest request, ActionListener<SearchResponse> actionListener) {
        User user = RestActionUtils.getUserContext(client);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            if (connectorAccessControlHelper.skipConnectorAccessControl(user)) {
                client.search(request, actionListener);
            } else {
                SearchSourceBuilder sourceBuilder = connectorAccessControlHelper.addUserBackendRolesFilter(user, request.source());
                request.source(sourceBuilder);
                client.search(request, actionListener);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            actionListener.onFailure(e);
        }
    }
}
