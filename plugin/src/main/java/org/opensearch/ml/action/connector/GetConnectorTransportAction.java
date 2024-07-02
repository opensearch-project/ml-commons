/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.utils.RestActionUtils.getFetchSourceContext;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.transport.connector.MLConnectorGetAction;
import org.opensearch.ml.common.transport.connector.MLConnectorGetRequest;
import org.opensearch.ml.common.transport.connector.MLConnectorGetResponse;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.SdkClient;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GetConnectorTransportAction extends HandledTransportAction<ActionRequest, MLConnectorGetResponse> {

    Client client;
    SdkClient sdkClient;

    ConnectorAccessControlHelper connectorAccessControlHelper;

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public GetConnectorTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLConnectorGetAction.NAME, transportService, actionFilters, MLConnectorGetRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLConnectorGetResponse> actionListener) {
        MLConnectorGetRequest mlConnectorGetRequest = MLConnectorGetRequest.fromActionRequest(request);
        String connectorId = mlConnectorGetRequest.getConnectorId();
        String tenantId = mlConnectorGetRequest.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }
        FetchSourceContext fetchSourceContext = getFetchSourceContext(mlConnectorGetRequest.isReturnContent());
        GetDataObjectRequest getDataObjectRequest = new GetDataObjectRequest.Builder()
            .index(ML_CONNECTOR_INDEX)
            .id(connectorId)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();
        User user = RestActionUtils.getUserContext(client);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            connectorAccessControlHelper
                .getConnector(
                    sdkClient,
                    client,
                    context,
                    getDataObjectRequest,
                    connectorId,
                    ActionListener
                        .wrap(
                            connector -> handleConnectorAccessValidation(user, tenantId, connector, actionListener),
                            e -> handleConnectorAccessValidationFailure(connectorId, e, actionListener)
                        )
                );
        } catch (Exception e) {
            log.error("Failed to get ML connector {}", connectorId, e);
            actionListener.onFailure(e);
        }
    }

    private void handleConnectorAccessValidation(
        User user,
        String tenantId,
        Connector mlConnector,
        ActionListener<MLConnectorGetResponse> actionListener
    ) {
        if (TenantAwareHelper.validateTenantResource(mlFeatureEnabledSetting, tenantId, mlConnector.getTenantId(), actionListener)) {
            if (connectorAccessControlHelper.hasPermission(user, mlConnector)) {
                actionListener.onResponse(MLConnectorGetResponse.builder().mlConnector(mlConnector).build());
            } else {
                actionListener
                    .onFailure(new OpenSearchStatusException("You don't have permission to access this connector", RestStatus.FORBIDDEN));
            }
        }
    }

    private void handleConnectorAccessValidationFailure(
        String connectorId,
        Exception e,
        ActionListener<MLConnectorGetResponse> actionListener
    ) {
        log.error("Failed to get ML connector: {}", connectorId, e);
        actionListener.onFailure(e);
    }
}
