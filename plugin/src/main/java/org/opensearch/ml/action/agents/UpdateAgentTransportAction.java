/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;

import java.time.Instant;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.core.xcontent.XContentParserUtils;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateAction;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateRequest;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class UpdateAgentTransportAction extends HandledTransportAction<ActionRequest, UpdateResponse> {

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
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> actionListener) {
        MLAgentUpdateRequest mlAgentUpdateRequest = MLAgentUpdateRequest.fromActionRequest(request);
        String agentId = mlAgentUpdateRequest.getAgentId();
        MLAgent mlAgent = mlAgentUpdateRequest.getMlAgent();
        String tenantId = mlAgent.getTenantId();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);

        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_AGENT_INDEX)
            .id(agentId)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<UpdateResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
                log.debug("Completed Get Agent request for Agent ID {}", agentId);
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    log.error("Failed to get ML Agent {}", agentId, cause);
                    wrappedListener.onFailure(cause);
                } else {
                    try {
                        GetResponse getResponse = r.getResponse();
                        assert getResponse != null : "Failed to get Agent";
                        XContentParser parser = JsonXContent.jsonXContent
                            .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString());
                        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLAgent retrievedAgent = MLAgent.parse(parser);

                        if (TenantAwareHelper
                            .validateTenantResource(mlFeatureEnabledSetting, tenantId, retrievedAgent.getTenantId(), wrappedListener)) {
                            if (retrievedAgent.getIsHidden() && !isSuperAdmin) {
                                wrappedListener
                                    .onFailure(
                                        new OpenSearchStatusException(
                                            "User does not have privilege to perform this operation on this agent",
                                            RestStatus.FORBIDDEN
                                        )
                                    );
                            } else {
                                updateAgent(agentId, mlAgent, wrappedListener);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to get ML agent {}", agentId);
                        wrappedListener.onFailure(e);
                    }
                }
            });
        }
    }

    private void updateAgent(String agentId, MLAgent agent, ActionListener<UpdateResponse> wrappedListener) {
        Instant now = Instant.now();
        MLAgent mlAgent = agent.toBuilder().lastUpdateTime(now).build();
        String tenantId = agent.getTenantId();

        UpdateDataObjectRequest updateDataObjectRequest = UpdateDataObjectRequest
            .builder()
            .index(ML_AGENT_INDEX)
            .id(agentId)
            .tenantId(tenantId)
            .dataObject(mlAgent)
            .build();

        sdkClient.updateDataObjectAsync(updateDataObjectRequest).whenComplete((r, throwable) -> {
            log.debug("Completed Update Agent request for Agent ID {}", agentId);
            if (throwable != null) {
                Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                log.error("Failed to update ML Agent {}", agentId, cause);
                wrappedListener.onFailure(cause);
            } else {
                try {
                    UpdateResponse updateResponse = r.updateResponse();
                    assert updateResponse != null : "Failed to update Agent";
                    if (updateResponse.getResult() == DocWriteResponse.Result.UPDATED) {
                        log.info("Successfully updated ");
                        wrappedListener.onResponse(updateResponse);
                    }
                } catch (Exception e) {
                    log.error("Failed to update ML agent {}", agentId, e);
                    wrappedListener.onFailure(e);
                }
            }
        });
    }

    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }
}
