/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.opensearch.ml.common.CommonValue.MCP_CONNECTORS_FIELD;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import org.opensearch.ml.action.agent.MLAgentRegistrationValidator;
import org.opensearch.ml.action.contextmanagement.ContextManagementTemplateService;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentAction;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentRequest;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;
import org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner;
import org.opensearch.ml.engine.function_calling.FunctionCallingFactory;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.engine.tools.QueryPlanningTool;
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
    private final MLAgentRegistrationValidator agentRegistrationValidator;

    @Inject
    public TransportRegisterAgentAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        MLIndicesHandler mlIndicesHandler,
        ClusterService clusterService,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        ContextManagementTemplateService contextManagementTemplateService
    ) {
        super(MLRegisterAgentAction.NAME, transportService, actionFilters, MLRegisterAgentRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlIndicesHandler = mlIndicesHandler;
        this.clusterService = clusterService;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.agentRegistrationValidator = new MLAgentRegistrationValidator(contextManagementTemplateService);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLRegisterAgentResponse> listener) {
        User user = RestActionUtils.getUserContext(client);// TODO: check access
        MLRegisterAgentRequest registerAgentRequest = MLRegisterAgentRequest.fromActionRequest(request);
        MLAgent mlAgent = registerAgentRequest.getMlAgent();
        registerAgent(mlAgent, listener);
    }

    private void registerAgent(MLAgent agent, ActionListener<MLRegisterAgentResponse> listener) {
        // Validate context management configuration (following connector pattern)
        if (agent.hasContextManagementTemplate()) {
            // Validate context management template access (similar to connector access validation)
            String templateName = agent.getContextManagementTemplateName();
            agentRegistrationValidator.validateContextManagementTemplateAccess(templateName, ActionListener.wrap(hasAccess -> {
                if (Boolean.TRUE.equals(hasAccess)) {
                    continueAgentRegistration(agent, listener);
                } else {
                    listener
                        .onFailure(
                            new IllegalArgumentException(
                                "You don't have permission to use the context management template provided, template name: " + templateName
                            )
                        );
                }
            }, e -> {
                log.error("You don't have permission to use the context management template provided, template name: {}", templateName, e);
                listener.onFailure(e);
            }));
        } else if (agent.getInlineContextManagement() != null) {
            // Validate inline context management configuration only if it exists (similar to inline connector validation)
            validateInlineContextManagement(agent);
            continueAgentRegistration(agent, listener);
        } else {
            // No context management configuration - that's fine, continue with registration
            continueAgentRegistration(agent, listener);
        }
    }

    private void validateInlineContextManagement(MLAgent agent) {
        if (agent.getInlineContextManagement() == null) {
            log
                .error(
                    "You must provide context management content when creating an agent without providing context management template name!"
                );
            throw new IllegalArgumentException(
                "You must provide context management content when creating an agent without context management template name!"
            );
        }

        // Validate inline context management configuration structure
        if (!agent.getInlineContextManagement().isValid()) {
            log
                .error(
                    "Invalid context management configuration: configuration must have a name and at least one hook with valid context manager configurations"
                );
            throw new IllegalArgumentException(
                "Invalid context management configuration: configuration must have a name and at least one hook with valid context manager configurations"
            );
        }
    }

    private void continueAgentRegistration(MLAgent agent, ActionListener<MLRegisterAgentResponse> listener) {
        String mcpConnectorConfigJSON = (agent.getParameters() != null) ? agent.getParameters().get(MCP_CONNECTORS_FIELD) : null;
        if (mcpConnectorConfigJSON != null && !mlFeatureEnabledSetting.isMcpConnectorEnabled()) {
            // MCP connector provided as tools but MCP feature is disabled, so abort.
            listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE));
            return;
        }

        // Update QueryPlanningTool to include model_id if missing
        List<MLToolSpec> updatedTools = processQueryPlannerTools(agent);

        String llmInterface = (agent.getParameters() != null) ? agent.getParameters().get(LLM_INTERFACE) : null;
        if (llmInterface != null) {
            if (llmInterface.trim().isEmpty()) {
                listener.onFailure(new IllegalArgumentException("_llm_interface cannot be blank or empty"));
                return;
            }

            try {
                FunctionCallingFactory.create(llmInterface);
            } catch (Exception e) {
                listener.onFailure(new IllegalArgumentException("Invalid _llm_interface: " + llmInterface));
                return;
            }
        }

        Instant now = Instant.now();
        boolean isHiddenAgent = RestActionUtils.isSuperAdminUser(clusterService, client);
        MLAgent mlAgent = agent.toBuilder().tools(updatedTools).createdTime(now).lastUpdateTime(now).isHidden(isHiddenAgent).build();
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

    private List<MLToolSpec> processQueryPlannerTools(MLAgent agent) {
        List<MLToolSpec> tools = agent.getTools();
        List<MLToolSpec> updatedTools = tools;
        if (tools != null) {
            // Update QueryPlanningTool with model_id if missing and LLM exists
            if (agent.getLlm() != null && agent.getLlm().getModelId() != null && !agent.getLlm().getModelId().isBlank()) {
                updatedTools = tools.stream().map(tool -> {
                    if (tool.getType().equals(QueryPlanningTool.TYPE)) {
                        Map<String, String> params = tool.getParameters() != null ? new HashMap<>(tool.getParameters()) : new HashMap<>();
                        params.putIfAbsent("model_id", agent.getLlm().getModelId());
                        return tool.toBuilder().parameters(params).build();
                    }
                    return tool;
                }).collect(Collectors.toList());
            }
        }
        return updatedTools;
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
