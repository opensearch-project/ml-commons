/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.action.handler.MLSearchHandler;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class SearchModelTransportAction extends HandledTransportAction<SearchRequest, SearchResponse> {
    private final MLSearchHandler mlSearchHandler;
    private final SdkClient sdkClient;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public SearchModelTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        SdkClient sdkClient,
        MLSearchHandler mlSearchHandler,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLModelSearchAction.NAME, transportService, actionFilters, SearchRequest::new);
        this.sdkClient = sdkClient;
        this.mlSearchHandler = mlSearchHandler;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, SearchRequest request, ActionListener<SearchResponse> actionListener) {
        request.indices(CommonValue.ML_MODEL_INDEX);
        if (mlFeatureEnabledSetting.isMultiTenancyEnabled() && !TenantAwareHelper.isTenantFilteringEnabled(request)) {
            actionListener
                .onFailure(
                    new OpenSearchStatusException("Failed to get the tenant ID from the search request", RestStatus.INTERNAL_SERVER_ERROR)
                );
            return;
        }
        mlSearchHandler.search(sdkClient, request, actionListener);
    }
}
