/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.action.handler.MLSearchHandler;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class SearchModelTransportAction extends HandledTransportAction<MLSearchActionRequest, SearchResponse> {
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
        super(MLModelSearchAction.NAME, transportService, actionFilters, MLSearchActionRequest::new);
        this.sdkClient = sdkClient;
        this.mlSearchHandler = mlSearchHandler;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, MLSearchActionRequest request, ActionListener<SearchResponse> actionListener) {

        String tenantId = request.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }
        mlSearchHandler.search(sdkClient, request, tenantId, actionListener);
    }
}
