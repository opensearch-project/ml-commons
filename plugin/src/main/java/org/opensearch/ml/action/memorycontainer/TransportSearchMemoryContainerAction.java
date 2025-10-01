/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.ml.action.handler.MLSearchHandler.wrapRestActionListener;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.utils.RestActionUtils.wrapListenerToHandleSearchIndexNotFound;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerSearchAction;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportSearchMemoryContainerAction extends HandledTransportAction<MLSearchActionRequest, SearchResponse> {

    final Client client;
    final SdkClient sdkClient;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MemoryContainerHelper memoryContainerHelper;

    @Inject
    public TransportSearchMemoryContainerAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper
    ) {
        super(MLMemoryContainerSearchAction.NAME, transportService, actionFilters, MLSearchActionRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
    }

    @Override
    protected void doExecute(Task task, MLSearchActionRequest request, ActionListener<SearchResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }
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

            if (!memoryContainerHelper.isAdminUser(user)) {
                memoryContainerHelper.addUserBackendRolesFilter(user, request.source());
                log.debug("Filtering result by {}", user.getBackendRoles());
            }
            SearchDataObjectRequest searchDataObjecRequest = SearchDataObjectRequest
                .builder()
                .indices(request.indices())
                .searchSourceBuilder(request.source())
                .tenantId(tenantId)
                .build();
            sdkClient
                .searchDataObjectAsync(searchDataObjecRequest)
                .whenComplete(SdkClientUtils.wrapSearchCompletion(doubleWrappedListener));
        } catch (Exception e) {
            log.error("Failed to search", e);
            listener.onFailure(e);
        }
    }

}
