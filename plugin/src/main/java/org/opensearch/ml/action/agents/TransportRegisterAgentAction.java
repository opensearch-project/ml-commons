/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.opensearch.ml.common.CommonValue.MCP_CONNECTORS_FIELD;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_ENABLED;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentAction;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentRequest;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;
import org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportRegisterAgentAction extends HandledTransportAction<ActionRequest, MLRegisterAgentResponse> {
    MLIndicesHandler mlIndicesHandler;
    Client client;
    SdkClient sdkClient;
    ClusterService clusterService;

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private volatile boolean mcpConnectorIsEnabled;

    @Inject
    public TransportRegisterAgentAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        MLIndicesHandler mlIndicesHandler,
        ClusterService clusterService,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLRegisterAgentAction.NAME, transportService, actionFilters, MLRegisterAgentRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlIndicesHandler = mlIndicesHandler;
        this.clusterService = clusterService;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.mcpConnectorIsEnabled = ML_COMMONS_MCP_CONNECTOR_ENABLED.get(clusterService.getSettings());
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MCP_CONNECTOR_ENABLED, it -> mcpConnectorIsEnabled = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLRegisterAgentResponse> listener) {
        User user = RestActionUtils.getUserContext(client);// TODO: check access
        MLRegisterAgentRequest registerAgentRequest = MLRegisterAgentRequest.fromActionRequest(request);
        MLAgent mlAgent = registerAgentRequest.getMlAgent();
        registerAgent(mlAgent, listener);
    }

    private void registerAgent(MLAgent agent, ActionListener<MLRegisterAgentResponse> listener) {
        String mcpConnectorConfigJSON = (agent.getParameters() != null) ? agent.getParameters().get(MCP_CONNECTORS_FIELD) : null;
        if (mcpConnectorConfigJSON != null && !mcpConnectorIsEnabled) {
            // MCP connector provided as tools but MCP feature is disabled, so abort.
            listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE));
            return;
        }
        Instant now = Instant.now();
        boolean isHiddenAgent = RestActionUtils.isSuperAdminUser(clusterService, client);
        MLAgent mlAgent = agent.toBuilder().createdTime(now).lastUpdateTime(now).isHidden(isHiddenAgent).build();
        String tenantId = agent.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, listener)) {
            return;
        }

        // If the agent is a PLAN_EXECUTE_AND_REFLECT agent and does not have an executor agent id, create an executor (reAct) agent
        if (MLAgentType.from(mlAgent.getType()) == MLAgentType.PLAN_EXECUTE_AND_REFLECT
            && !mlAgent.getParameters().containsKey(MLPlanExecuteAndReflectAgentRunner.EXECUTOR_AGENT_ID_FIELD)) {
            createConversationAgent(mlAgent, tenantId, ActionListener.wrap(conversationAgentId -> {
                Map<String, String> parameters = new HashMap<>(mlAgent.getParameters());
                parameters.put(MLPlanExecuteAndReflectAgentRunner.EXECUTOR_AGENT_ID_FIELD, conversationAgentId);
                MLAgent updatedAgent = mlAgent.toBuilder().parameters(parameters).build();
                registerAgentToIndex(updatedAgent, tenantId, listener);
            }, listener::onFailure));
        } else {
            registerAgentToIndex(mlAgent, tenantId, listener);
        }
    }

    private void createConversationAgent(MLAgent planExecuteReflectAgent, String tenantId, ActionListener<String> listener) {
        Instant now = Instant.now();
        boolean isHiddenAgent = RestActionUtils.isSuperAdminUser(clusterService, client);

        // Create CONVERSATION agent with same configuration but different type and name
        MLAgent conversationAgent = planExecuteReflectAgent
            .toBuilder()
            .name(planExecuteReflectAgent.getName() + " (ReAct)")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("Execution Agent for Plan Execute Reflect - " + planExecuteReflectAgent.getName())
            .createdTime(now)
            .lastUpdateTime(now)
            .isHidden(isHiddenAgent)
            .build();

        registerAgentToIndex(
            conversationAgent,
            tenantId,
            ActionListener.wrap(response -> { listener.onResponse(response.getAgentId()); }, listener::onFailure)
        );
    }

    private void registerAgentToIndex(MLAgent mlAgent, String tenantId, ActionListener<MLRegisterAgentResponse> listener) {
        mlIndicesHandler.initMLAgentIndex(ActionListener.wrap(result -> {
            if (result) {
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    sdkClient
                        .putDataObjectAsync(
                            PutDataObjectRequest.builder().index(ML_AGENT_INDEX).tenantId(tenantId).dataObject(mlAgent).build()
                        )
                        .whenComplete((r, throwable) -> {
                            context.restore();
                            if (throwable != null) {
                                Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                                log.error("Failed to index ML agent", cause);
                                listener.onFailure(cause);
                            } else {
                                try {
                                    IndexResponse indexResponse = r.indexResponse();
                                    log.info("Agent creation result: {}, Agent id: {}", indexResponse.getResult(), indexResponse.getId());
                                    MLRegisterAgentResponse response = new MLRegisterAgentResponse(r.id());
                                    listener.onResponse(response);
                                } catch (Exception e) {
                                    listener.onFailure(e);
                                }
                            }
                        });
                } catch (Exception e) {
                    log.error("Failed to index ML agent", e);
                    listener.onFailure(e);
                }
            } else {
                log.error("Failed to create ML agent index");
                listener.onFailure(new OpenSearchException("Failed to create ML agent index"));
            }
        }, e -> {
            log.error("Failed to create ML agent index", e);
            listener.onFailure(e);
        }));
    }
}
