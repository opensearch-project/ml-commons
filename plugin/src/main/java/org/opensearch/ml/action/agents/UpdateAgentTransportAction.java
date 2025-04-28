/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import lombok.extern.log4j.Log4j2;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateAction;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateRequest;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateResponse;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import java.time.Instant;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;

@Log4j2
public class UpdateAgentTransportAction extends HandledTransportAction<ActionRequest, MLAgentUpdateResponse> {

    Client client;
    SdkClient sdkClient;
    NamedXContentRegistry xContentRegistry;
    ClusterService clusterService;

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public UpdateAgentTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLAgentUpdateAction.NAME, transportService, actionFilters, MLAgentUpdateRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLAgentUpdateResponse> listener) {
        MLAgentUpdateRequest mlAgentUpdateRequest = MLAgentUpdateRequest.fromActionRequest(request);
        String agentId = mlAgentUpdateRequest.getAgentId();
        MLAgent mlAgent = mlAgentUpdateRequest.getMlAgent();
        updateAgent(agentId, mlAgent, listener);
    }

    private void updateAgent(String agentId, MLAgent agent, ActionListener<MLAgentUpdateResponse> listener) {
        Instant now = Instant.now();
        MLAgent inputAgent = agent.toBuilder().lastUpdateTime(now).build();
        String tenantId = agent.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, listener)) {
            return;
        }
        boolean isSuperAdmin = RestActionUtils.isSuperAdminUser(clusterService, client);

        UpdateDataObjectRequest updateDataObjectRequest = UpdateDataObjectRequest
                .builder()
                .index(ML_AGENT_INDEX)
                .id(agentId)
                .tenantId(tenantId)
                .dataObject(inputAgent)
                .build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient.updateDataObjectAsync(updateDataObjectRequest).whenComplete((r, throwable) -> {
                context.restore();
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    log.error("Failed to update ML Agent {}", agentId, cause);
                    listener.onFailure(cause);
                } else {
                    try {
                        UpdateResponse ur = r.updateResponse();
                        if (ur != null && ur.status() == RestStatus.CREATED) {
                            try (
                                    XContentParser parser = jsonXContent
                                            .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, ur.getGetResult().sourceAsString())
                            ) {
                                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                MLAgent outputAgent = MLAgent.parse(parser);
                                if (TenantAwareHelper.validateTenantResource(mlFeatureEnabledSetting, tenantId, outputAgent.getTenantId(), listener)) {
                                    if (inputAgent.getIsHidden() && !isSuperAdmin) {
                                        listener.onFailure(
                                                new OpenSearchStatusException("User does not have privilege to update this agent", RestStatus.FORBIDDEN)
                                        );
                                    } else {
                                        listener.onResponse(MLAgentUpdateResponse.builder().mlAgent(outputAgent).build());
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse update response for ML agent {}", agentId, e);
                        listener.onFailure(e);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to update ML agent {}", agentId, e);
            listener.onFailure(e);
        }
    }
}
