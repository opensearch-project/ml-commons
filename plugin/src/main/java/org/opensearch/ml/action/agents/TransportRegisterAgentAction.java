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

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.action.agent.MLAgentRegistrationValidator;
import org.opensearch.ml.action.contextmanagement.ContextManagementTemplateService;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.MLMemoryType;
import org.opensearch.ml.common.agent.AgentModelService;
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
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
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

        // Validate tenant id before any metadata search so a missing tenant fails fast in
        // multi-tenant mode, rather than leaking existence via a 409 from a cross-tenant search.
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, mlAgent.getTenantId(), listener)) {
            return;
        }

        validateAgentNameUniqueness(mlAgent, ActionListener.wrap(unused -> {
            // Check if this agent needs model creation
            if (mlAgent.usesUnifiedInterface()) {
                createModelAndRegisterAgent(mlAgent, listener);
                return;
            }
            registerAgent(mlAgent, listener);
        }, listener::onFailure));
    }

    /**
     * When {@code plugins.ml_commons.agent_name_uniqueness_enabled} is enabled, reject the
     * registration if an agent with the same name already exists in the same tenant. When the
     * setting is disabled (default), this is a no-op so existing clusters remain backward compatible.
     *
     * <p>For PLAN_EXECUTE_AND_REFLECT agents without a pre-existing executor-agent-id, an internal
     * "{@code <name> (ReAct)}" executor agent is auto-created; this method also checks that
     * derived name so the auto-created agent cannot collide with an existing one.
     *
     * <p>Note: this is a best-effort check, not a transactional guard. Two concurrent register
     * requests with the same name can both pass this check before either write is visible.
     * See the PR description for a follow-up plan if stricter semantics are required.
     */
    private void validateAgentNameUniqueness(MLAgent mlAgent, ActionListener<Void> listener) {
        if (!mlFeatureEnabledSetting.isAgentNameUniquenessEnabled()) {
            listener.onResponse(null);
            return;
        }

        // MLAgent.validate() already rejects null/blank/over-length names upstream, so we rely on
        // that invariant here and don't re-check.
        String name = mlAgent.getName();
        String tenantId = mlAgent.getTenantId();
        checkAgentNameAvailable(name, tenantId, ActionListener.wrap(unused -> {
            // If this is a PLAN_EXECUTE_AND_REFLECT registration that will auto-create an
            // executor agent named "<name> (ReAct)", validate that derived name too.
            if (MLAgentType.from(mlAgent.getType()) == MLAgentType.PLAN_EXECUTE_AND_REFLECT
                && mlAgent.getParameters() != null
                && !mlAgent.getParameters().containsKey(MLPlanExecuteAndReflectAgentRunner.EXECUTOR_AGENT_ID_FIELD)) {
                checkAgentNameAvailable(name + " (ReAct)", tenantId, listener);
            } else {
                listener.onResponse(null);
            }
        }, listener::onFailure));
    }

    private void checkAgentNameAvailable(String name, String tenantId, ActionListener<Void> listener) {
        BoolQueryBuilder query = new BoolQueryBuilder().filter(new TermQueryBuilder("name.keyword", name));
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(query).size(1).fetchSource(false);
        SearchRequest searchRequest = new SearchRequest(ML_AGENT_INDEX).source(sourceBuilder);
        SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
            .builder()
            .indices(searchRequest.indices())
            .searchSourceBuilder(searchRequest.source())
            .tenantId(tenantId)
            .build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient.searchDataObjectAsync(searchDataObjectRequest).whenComplete((r, throwable) -> {
                context.restore();
                if (throwable != null) {
                    if (ExceptionsHelper.unwrap(throwable, IndexNotFoundException.class) != null) {
                        // Index not yet created - no duplicate possible
                        listener.onResponse(null);
                        return;
                    }
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    log.error("Failed to search ML agent index for name uniqueness check", cause);
                    listener.onFailure(cause);
                    return;
                }
                try {
                    long totalHits = r.searchResponse().getHits().getTotalHits() == null
                        ? 0
                        : r.searchResponse().getHits().getTotalHits().value();
                    if (totalHits > 0) {
                        listener
                            .onFailure(
                                new OpenSearchStatusException(
                                    "An agent with name [" + name + "] already exists. Agent names must be unique.",
                                    RestStatus.CONFLICT
                                )
                            );
                    } else {
                        listener.onResponse(null);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse search response for agent name uniqueness check", e);
                    listener.onFailure(e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to execute agent name uniqueness check", e);
            listener.onFailure(e);
        }
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

                String llmInterface = AgentModelService.inferLLMInterface(mlAgent.getModel().getModelProvider());
                if (llmInterface != null) {
                    parameters.put(LLM_INTERFACE, llmInterface);
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
        // Use the comprehensive validator that includes V2 memory validation
        agentRegistrationValidator.validateAgentForRegistration(agent, ActionListener.wrap(isValid -> {
            if (Boolean.TRUE.equals(isValid)) {
                listener.onResponse(agent);
            } else {
                listener.onFailure(new IllegalArgumentException("Agent validation failed"));
            }
        }, listener::onFailure));
    }

    private void proceedWithAgentRegistration(MLAgent agent, ActionListener<MLRegisterAgentResponse> listener) {
        String mcpConnectorConfigJSON = (agent.getParameters() != null) ? agent.getParameters().get(MCP_CONNECTORS_FIELD) : null;
        if (mcpConnectorConfigJSON != null && !mlFeatureEnabledSetting.isMcpConnectorEnabled()) {
            // MCP connector provided as tools but MCP feature is disabled, so abort.
            listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE));
            return;
        }

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

        Instant now = Instant.now();
        boolean isHiddenAgent = RestActionUtils.isSuperAdminUser(clusterService, client);
        MLAgent mlAgent = agent.toBuilder().createdTime(now).lastUpdateTime(now).isHidden(isHiddenAgent).build();
        String tenantId = agent.getTenantId();

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
