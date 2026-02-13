/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.opensearch.ml.common.CommonValue.MCP_CONNECTORS_FIELD;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_REMOTE_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.action.agent.MLAgentRegistrationValidator;
import org.opensearch.ml.action.contextmanagement.ContextManagementTemplateService;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.MLMemoryType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLAgentModelSpec;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentAction;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentRequest;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;
import org.opensearch.ml.common.transport.register.MLRegisterModelAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelRequest;
import org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner;
import org.opensearch.ml.engine.function_calling.FunctionCallingFactory;
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

        if (mlAgent.getMemory() != null
            && MLMemoryType.REMOTE_AGENTIC_MEMORY.name().equalsIgnoreCase(mlAgent.getMemory().getType())
            && !mlFeatureEnabledSetting.isRemoteAgenticMemoryEnabled()) {
            listener.onFailure(new OpenSearchStatusException(ML_COMMONS_REMOTE_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        // Check if this agent needs model creation
        if (mlAgent.getModel() != null) {
            createModelAndRegisterAgent(mlAgent, listener);
            return;
        }

        registerAgent(mlAgent, listener);
    }

    private void createModelAndRegisterAgent(MLAgent mlAgent, ActionListener<MLRegisterAgentResponse> listener) {
        try {
            MLRegisterModelInput modelInput = AgentModelService.createModelFromSpec(mlAgent.getModel());
            MLRegisterModelRequest modelRequest = new MLRegisterModelRequest(modelInput);

            client.execute(MLRegisterModelAction.INSTANCE, modelRequest, ActionListener.wrap(modelResponse -> {
                String modelId = modelResponse.getModelId();

                Map<String, String> parameters = new HashMap<>();
                if (mlAgent.getParameters() != null) {
                    parameters.putAll(mlAgent.getParameters());
                }

                // For V2 agents, configure model_provider instead of _llm_interface
                MLAgentType agentType = MLAgentType.from(mlAgent.getType());
                if (agentType == MLAgentType.CONVERSATIONAL_V2) {
                    String modelProvider = mlAgent.getModel().getModelProvider();
                    if (modelProvider != null && !modelProvider.trim().isEmpty()) {
                        parameters.put("model_provider", modelProvider);
                        log.debug("Configured model_provider='{}' for V2 agent '{}'", modelProvider, mlAgent.getName());
                    }
                } else {
                    // For non-V2 agents, use legacy _llm_interface
                    String llmInterface = AgentModelService.inferLLMInterface(mlAgent.getModel().getModelProvider());
                    if (llmInterface != null) {
                        parameters.put(LLM_INTERFACE, llmInterface);
                    }
                }

                LLMSpec llmSpec = LLMSpec.builder().modelId(modelId).parameters(mlAgent.getModel().getModelParameters()).build();

                // Create sanitized model spec without credentials and model parameters
                // (stored in the model document and LLMSpec respectively)
                MLAgentModelSpec sanitizedModelSpec = mlAgent.getModel().toBuilder().modelParameters(null).credential(null).build();
                // ToDo: store model details within agent to prevent creating a new model document
                MLAgent agent = mlAgent.toBuilder().llm(llmSpec).model(sanitizedModelSpec).parameters(parameters).build();
                registerAgent(agent, listener);
            }, listener::onFailure));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private void registerAgent(MLAgent agent, ActionListener<MLRegisterAgentResponse> listener) {
        validateAgent(agent, ActionListener.wrap(validatedAgent -> {
            // All validations passed, proceed with registration
            proceedWithAgentRegistration(validatedAgent, listener);
        }, listener::onFailure));
    }

    private void validateAgent(MLAgent agent, ActionListener<MLAgent> listener) {
        // Validate V2 agent requirements
        try {
            validateV2AgentRequirements(agent);
        } catch (Exception e) {
            listener.onFailure(e);
            return;
        }

        // Validate context management configuration
        if (agent.hasContextManagementTemplate()) {
            // Validate context management template access
            String templateName = agent.getContextManagementTemplateName();
            agentRegistrationValidator.validateContextManagementTemplateAccess(templateName, ActionListener.wrap(hasAccess -> {
                if (Boolean.TRUE.equals(hasAccess)) {
                    listener.onResponse(agent);
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
            // Validate inline context management configuration
            try {
                validateInlineContextManagement(agent);
                listener.onResponse(agent);
            } catch (Exception e) {
                listener.onFailure(e);
            }
        } else {
            // No context management configuration - that's fine
            listener.onResponse(agent);
        }
    }

    private void validateV2AgentRequirements(MLAgent agent) {
        MLAgentType agentType = MLAgentType.from(agent.getType());
        if (agentType != MLAgentType.CONVERSATIONAL_V2) {
            return; // Not a V2 agent, skip validation
        }

        // V2 agents must have agentic_memory
        if (agent.getMemory() == null) {
            throw new IllegalArgumentException(
                "Agent type 'conversational/v2' requires memory configuration. " + "Please specify memory: {\"type\": \"agentic_memory\"}"
            );
        }

        String memoryType = agent.getMemory().getType();
        if (memoryType == null || memoryType.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Agent type 'conversational/v2' requires memory type to be specified. "
                    + "Please specify memory: {\"type\": \"agentic_memory\"}"
            );
        }

        // Must use agentic_memory
        if (!MLMemoryType.AGENTIC_MEMORY.name().equalsIgnoreCase(memoryType)) {
            if (MLMemoryType.CONVERSATION_INDEX.name().equalsIgnoreCase(memoryType)) {
                throw new IllegalArgumentException(
                    "Agent type 'conversational/v2' does not support 'conversation_index' memory. "
                        + "Use 'agentic_memory' instead for full multi-modal support."
                );
            } else if (MLMemoryType.REMOTE_AGENTIC_MEMORY.name().equalsIgnoreCase(memoryType)) {
                throw new IllegalArgumentException(
                    "Agent type 'conversational/v2' does not support 'remote_agentic_memory'. " + "Use 'agentic_memory' instead."
                );
            } else {
                throw new IllegalArgumentException(
                    "Agent type 'conversational/v2' requires memory type 'agentic_memory'. " + "Current type: " + memoryType
                );
            }
        }

        // Log deprecation warning if using old parameter style
        if (agent.getParameters() != null && agent.getParameters().containsKey(LLM_INTERFACE)) {
            log
                .warn(
                    "Agent '{}' uses deprecated '_llm_interface' parameter. "
                        + "This is auto-configured for v2 agents based on model provider.",
                    agent.getName()
                );
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

    private void proceedWithAgentRegistration(MLAgent agent, ActionListener<MLRegisterAgentResponse> listener) {
        String mcpConnectorConfigJSON = (agent.getParameters() != null) ? agent.getParameters().get(MCP_CONNECTORS_FIELD) : null;
        if (mcpConnectorConfigJSON != null && !mlFeatureEnabledSetting.isMcpConnectorEnabled()) {
            // MCP connector provided as tools but MCP feature is disabled, so abort.
            listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE));
            return;
        }

        // V2 agents don't use _llm_interface, so skip this validation
        MLAgentType agentType = MLAgentType.from(agent.getType());
        if (agentType != MLAgentType.CONVERSATIONAL_V2) {
            String llmInterface = (agent.getParameters() != null) ? agent.getParameters().get(LLM_INTERFACE) : null;
            if (llmInterface != null) {
                if (llmInterface.trim().isEmpty()) {
                    listener.onFailure(new IllegalArgumentException("_llm_interface cannot be blank or empty"));
                    return;
                }

                try {
                    FunctionCallingFactory.create(llmInterface);
                } catch (Exception e) {
                    listener.onFailure(new IllegalArgumentException("Invalid _llm_interface"));
                    return;
                }
            }
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
