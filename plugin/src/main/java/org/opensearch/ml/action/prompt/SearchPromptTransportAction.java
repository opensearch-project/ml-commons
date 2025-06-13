/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prompt;

import static org.opensearch.ml.utils.RestActionUtils.wrapListenerToHandleSearchIndexNotFound;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.prompt.MLPromptSearchAction;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Transport Action class that handles received validated ActionRequest from Rest Layer and
 * executes the actual operation of searching prompts based on the request parameters.
 */
@Log4j2
public class SearchPromptTransportAction extends HandledTransportAction<MLSearchActionRequest, SearchResponse> {
    private final Client client;
    private final SdkClient sdkClient;

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public SearchPromptTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLPromptSearchAction.NAME, transportService, actionFilters, MLSearchActionRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    /**
     * Executes the received request by searching prompts based on the request parameters.
     * Notify the listener with the SearchResponse if the operation is successful. Otherwise, failure exception
     * is notified to the listener.
     *
     * @param task The task
     * @param mlSearchActionRequest MLSearchActionRequest that contains search parameters
     * @param actionListener a listener to be notified of the response
     */
    @Override
    protected void doExecute(Task task, MLSearchActionRequest mlSearchActionRequest, ActionListener<SearchResponse> actionListener) {
        String tenantId = mlSearchActionRequest.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            final ActionListener<SearchResponse> wrappedListener = ActionListener
                .runBefore(
                    ActionListener.wrap(actionListener::onResponse, e -> wrapListenerToHandleSearchIndexNotFound(e, actionListener)),
                    context::restore
                );

            SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
                .builder()
                .indices(mlSearchActionRequest.indices())
                .searchSourceBuilder(mlSearchActionRequest.source())
                .tenantId(tenantId)
                .build();

            sdkClient.searchDataObjectAsync(searchDataObjectRequest).whenComplete(SdkClientUtils.wrapSearchCompletion(wrappedListener));
        } catch (Exception e) {
            log.error("Failed to search ML Prompt", e);
            actionListener.onFailure(e);
        }
    }
}
