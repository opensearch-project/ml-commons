/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.opensearch.ml.action.handler.MLSearchHandler.wrapRestActionListener;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_VALIDATE_BACKEND_ROLES;
import static org.opensearch.ml.utils.SecurityUtils.addUserBackendRolesFilter;
import static org.opensearch.ml.utils.SecurityUtils.isAdmin;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.ml.action.handler.MLSearchHandler;
import org.opensearch.ml.common.transport.model_group.MLModelGroupSearchAction;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
public class SearchModelGroupTransportAction extends HandledTransportAction<SearchRequest, SearchResponse> {
    private MLSearchHandler mlSearchHandler;

    Client client;
    ClusterService clusterService;

    private volatile boolean filterByEnabled;

    @Inject
    public SearchModelGroupTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLSearchHandler mlSearchHandler,
        Client client,
        Settings settings,
        ClusterService clusterService
    ) {
        super(MLModelGroupSearchAction.NAME, transportService, actionFilters, SearchRequest::new);
        this.mlSearchHandler = mlSearchHandler;
        this.client = client;
        this.clusterService = clusterService;
        filterByEnabled = ML_COMMONS_VALIDATE_BACKEND_ROLES.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_VALIDATE_BACKEND_ROLES, it -> filterByEnabled = it);
    }

    @Override
    protected void doExecute(Task task, SearchRequest request, ActionListener<SearchResponse> actionListener) {
        User user = RestActionUtils.getUserContext(client);
        ActionListener<SearchResponse> listener = wrapRestActionListener(actionListener, "Fail to search");
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            validateRole(request, user, listener);
        } catch (Exception e) {
            log.error("Failed to search", e);
            listener.onFailure(e);
        }
    }

    private void validateRole(SearchRequest request, User user, ActionListener<SearchResponse> listener) {
        if (user == null || !filterByEnabled || isAdmin(user)) {
            // Case 1: user == null when 1. Security is disabled. 2. When user is super-admin
            // Case 2: If Security is enabled and filter is disabled, proceed with search as
            // user is already authenticated to hit this API.
            // case 3: user is admin which means we don't have to check backend role filtering
            client.search(request, listener);
        } else {
            // Security is enabled, filter is enabled and user isn't admin
            try {
                addUserBackendRolesFilter(user, request.source());
                log.debug("Filtering result by " + user.getBackendRoles());
                client.search(request, listener);
            } catch (Exception e) {
                listener.onFailure(e);
            }
        }
    }

}
