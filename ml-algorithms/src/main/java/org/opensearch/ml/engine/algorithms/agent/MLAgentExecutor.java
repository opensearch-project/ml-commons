/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.MCP_CONNECTORS_FIELD;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.MLTask.RESPONSE_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTask.TASK_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.output.model.ModelTensorOutput.INFERENCE_RESULT_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.common.utils.MLTaskUtils.updateMLTaskDirectly;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.createMemoryParams;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.MLMemoryType;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.agent.AgentInput;
import org.opensearch.ml.common.agent.AgentInputProcessor;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.ModelProvider;
import org.opensearch.ml.common.agent.ModelProviderFactory;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.hooks.HookRegistry;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.output.MLTaskOutput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.settings.SettingsChangeListener;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.Executable;
import org.opensearch.ml.engine.algorithms.contextmanager.SlidingWindowManager;
import org.opensearch.ml.engine.algorithms.contextmanager.SummarizationManager;
import org.opensearch.ml.engine.algorithms.contextmanager.ToolsOutputTruncateManager;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.ConversationIndexMessage;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.memory.action.conversation.GetInteractionAction;
import org.opensearch.ml.memory.action.conversation.GetInteractionRequest;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Data
@NoArgsConstructor
@Function(FunctionName.AGENT)
public class MLAgentExecutor implements Executable, SettingsChangeListener {

    public static final String MEMORY_ID = "memory_id";
    public static final String QUESTION = "question";
    public static final String PARENT_INTERACTION_ID = "parent_interaction_id";
    public static final String REGENERATE_INTERACTION_ID = "regenerate_interaction_id";
    public static final String MESSAGE_HISTORY_LIMIT = "message_history_limit";
    public static final String ERROR_MESSAGE = "error_message";

    private Client client;
    private SdkClient sdkClient;
    private Settings settings;
    private ClusterService clusterService;
    private NamedXContentRegistry xContentRegistry;
    private Map<String, Tool.Factory> toolFactories;
    private Map<String, Memory.Factory> memoryFactoryMap;
    private volatile Boolean isMultiTenancyEnabled;
    private Encryptor encryptor;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public MLAgentExecutor(
        Client client,
        SdkClient sdkClient,
        Settings settings,
        ClusterService clusterService,
        NamedXContentRegistry xContentRegistry,
        Map<String, Tool.Factory> toolFactories,
        Map<String, Memory.Factory> memoryFactoryMap,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Encryptor encryptor
    ) {
        this.client = client;
        this.sdkClient = sdkClient;
        this.settings = settings;
        this.clusterService = clusterService;
        this.xContentRegistry = xContentRegistry;
        this.toolFactories = toolFactories;
        this.memoryFactoryMap = memoryFactoryMap;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.encryptor = encryptor;
        this.isMultiTenancyEnabled = mlFeatureEnabledSetting.isMultiTenancyEnabled();
    }

    @Override
    public void onMultiTenancyEnabledChanged(boolean isEnabled) {
        this.isMultiTenancyEnabled = isEnabled;
    }

    @Override
    public void execute(Input input, ActionListener<Output> listener, TransportChannel channel) {
        if (!(input instanceof AgentMLInput agentMLInput)) {
            throw new IllegalArgumentException("wrong input");
        }
        String agentId = agentMLInput.getAgentId();
        String tenantId = agentMLInput.getTenantId();
        Boolean isAsync = agentMLInput.getIsAsync();

        if (agentMLInput.getInputDataset() == null && !agentMLInput.hasStandardInput()) {
            throw new IllegalArgumentException("Agent input data can not be empty.");
        }

        if (isMultiTenancyEnabled && tenantId == null) {
            throw new OpenSearchStatusException("You don't have permission to access this resource", RestStatus.FORBIDDEN);
        }

        List<ModelTensors> outputs = new ArrayList<>();
        List<ModelTensor> modelTensors = new ArrayList<>();
        outputs.add(ModelTensors.builder().mlModelTensors(modelTensors).build());

        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_AGENT_INDEX)
            .id(agentId)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();

        if (MLIndicesHandler.doesMultiTenantIndexExist(clusterService, mlFeatureEnabledSetting.isMultiTenancyEnabled(), ML_AGENT_INDEX)) {
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                sdkClient
                    .getDataObjectAsync(getDataObjectRequest, client.threadPool().executor("opensearch_ml_general"))
                    .whenComplete((response, throwable) -> {
                        context.restore();
                        log.debug("Completed Get Agent Request, Agent id:{}", agentId);
                        if (throwable != null) {
                            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                            if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
                                log.error("Failed to get Agent index", cause);
                                listener.onFailure(new OpenSearchStatusException("Failed to get agent index", RestStatus.NOT_FOUND));
                            } else {
                                log.error("Failed to get ML Agent {}", agentId, cause);
                                listener.onFailure(cause);
                            }
                        } else {
                            try {
                                GetResponse getAgentResponse = response.parser() == null
                                    ? null
                                    : GetResponse.fromXContent(response.parser());
                                if (getAgentResponse != null && getAgentResponse.isExists()) {
                                    try (
                                        XContentParser parser = jsonXContent
                                            .createParser(
                                                xContentRegistry,
                                                LoggingDeprecationHandler.INSTANCE,
                                                getAgentResponse.getSourceAsString()
                                            )
                                    ) {
                                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                        MLAgent mlAgent = MLAgent.parse(parser);
                                        // Use existing HookRegistry from AgentMLInput if available (set by MLExecuteTaskRunner for template
                                        // references)
                                        // Otherwise create a fresh HookRegistry for agent execution
                                        final HookRegistry hookRegistry = agentMLInput.getHookRegistry() != null
                                            ? agentMLInput.getHookRegistry()
                                            : new HookRegistry();
                                        if (isMultiTenancyEnabled && !Objects.equals(tenantId, mlAgent.getTenantId())) {
                                            listener
                                                .onFailure(
                                                    new OpenSearchStatusException(
                                                        "You don't have permission to access this resource",
                                                        RestStatus.FORBIDDEN
                                                    )
                                                );
                                        }

                                        processAgentInput(agentMLInput, mlAgent);

                                        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) agentMLInput
                                            .getInputDataset();
                                        MLMemorySpec memorySpec = mlAgent.getMemory();
                                        Map<String, String> requestParameters = inputDataSet.getParameters();
                                        String containerOverride = null;
                                        if (requestParameters != null && requestParameters.containsKey(MEMORY_CONTAINER_ID_FIELD)) {
                                            String containerParam = requestParameters.get(MEMORY_CONTAINER_ID_FIELD);
                                            if (!Strings.isNullOrEmpty(containerParam)) {
                                                containerOverride = containerParam;
                                            }
                                        }
                                        if (containerOverride != null) {
                                            if (memorySpec == null) {
                                                throw new IllegalArgumentException(
                                                    "memory_container_id override requires the agent to be configured with memory"
                                                );
                                            }
                                            String currentContainerId = memorySpec.getMemoryContainerId();
                                            if (!containerOverride.equals(currentContainerId)) {
                                                MLMemorySpec updatedSpec = memorySpec
                                                    .toBuilder()
                                                    .memoryContainerId(containerOverride)
                                                    .build();
                                                mlAgent = mlAgent.toBuilder().memory(updatedSpec).build();
                                                memorySpec = updatedSpec;
                                                log
                                                    .debug(
                                                        "Agent {} overriding memory container from {} to {}",
                                                        agentId,
                                                        currentContainerId,
                                                        containerOverride
                                                    );
                                            }
                                        }
                                        final MLAgent finalMlAgent = mlAgent;
                                        String memoryId = inputDataSet.getParameters().get(MEMORY_ID);
                                        String parentInteractionId = inputDataSet.getParameters().get(PARENT_INTERACTION_ID);
                                        String regenerateInteractionId = inputDataSet.getParameters().get(REGENERATE_INTERACTION_ID);
                                        String appType = mlAgent.getAppType();
                                        String question = inputDataSet.getParameters().get(QUESTION);

                                        if (parentInteractionId != null && regenerateInteractionId != null) {
                                            throw new IllegalArgumentException(
                                                "Provide either `parent_interaction_id` to update an existing interaction, or `regenerate_interaction_id` to create a new one."
                                            );
                                        }

                                        MLTask mlTask = MLTask
                                            .builder()
                                            .taskType(MLTaskType.AGENT_EXECUTION)
                                            .functionName(FunctionName.AGENT)
                                            .state(MLTaskState.CREATED)
                                            .workerNodes(ImmutableList.of(clusterService.localNode().getId()))
                                            .createTime(Instant.now())
                                            .lastUpdateTime(Instant.now())
                                            .async(false)
                                            .tenantId(tenantId)
                                            .build();

                                        if (memoryId == null && regenerateInteractionId != null) {
                                            throw new IllegalArgumentException("A memory ID must be provided to regenerate.");
                                        }
                                        if (memorySpec != null
                                            && memorySpec.getType() != null
                                            && memoryFactoryMap.containsKey(MLMemoryType.from(memorySpec.getType()).name())
                                            && (memoryId == null || parentInteractionId == null)) {
                                            Map<String, Object> memoryParams = createMemoryParams(
                                                question,
                                                memoryId,
                                                appType,
                                                mlAgent,
                                                requestParameters
                                            );

                                            // Check if inline connector metadata is present to use RemoteAgenticConversationMemory
                                            Memory.Factory<Memory<?, ?, ?>> memoryFactory;
                                            if (memoryParams != null && memoryParams.containsKey("endpoint")) {
                                                // Use RemoteAgenticConversationMemory when inline connector metadata is detected
                                                memoryFactory = memoryFactoryMap.get(MLMemoryType.REMOTE_AGENTIC_MEMORY.name());
                                                log.info("Detected inline connector metadata, using RemoteAgenticConversationMemory");
                                            } else {
                                                // Use the originally specified memory factory
                                                memoryFactory = memoryFactoryMap.get(MLMemoryType.from(memorySpec.getType()).name());
                                            }
                                            memoryFactory.create(memoryParams, ActionListener.wrap(memory -> {
                                                inputDataSet.getParameters().put(MEMORY_ID, memory.getId());
                                                // get question for regenerate
                                                if (regenerateInteractionId != null) {
                                                    log.info("Regenerate for existing interaction {}", regenerateInteractionId);
                                                    client
                                                        .execute(
                                                            GetInteractionAction.INSTANCE,
                                                            new GetInteractionRequest(regenerateInteractionId),
                                                            ActionListener.wrap(interactionRes -> {
                                                                inputDataSet
                                                                    .getParameters()
                                                                    .putIfAbsent(QUESTION, interactionRes.getInteraction().getInput());
                                                                saveRootInteractionAndExecute(
                                                                    listener,
                                                                    memory,
                                                                    inputDataSet,
                                                                    mlTask,
                                                                    isAsync,
                                                                    outputs,
                                                                    modelTensors,
                                                                    finalMlAgent,
                                                                    channel,
                                                                    hookRegistry
                                                                );
                                                            }, e -> {
                                                                log.error("Failed to get existing interaction for regeneration", e);
                                                                listener.onFailure(e);
                                                            })
                                                        );
                                                } else {
                                                    saveRootInteractionAndExecute(
                                                        listener,
                                                        memory,
                                                        inputDataSet,
                                                        mlTask,
                                                        isAsync,
                                                        outputs,
                                                        modelTensors,
                                                        finalMlAgent,
                                                        channel,
                                                        hookRegistry
                                                    );
                                                }
                                            }, ex -> {
                                                log.error("Failed to read conversation memory", ex);
                                                listener.onFailure(ex);
                                            }));
                                        } else {
                                            // For existing conversations, create memory instance using factory
                                            if (memorySpec != null && memorySpec.getType() != null) {
                                                String memoryType = MLMemoryType.from(memorySpec.getType()).name();
                                                Memory.Factory<Memory<?, ?, ?>> memoryFactory = memoryFactoryMap.get(memoryType);

                                                ConversationIndexMemory.Factory factory = (ConversationIndexMemory.Factory) memoryFactoryMap
                                                    .get(memorySpec.getType());
                                                if (factory != null) {
                                                    // memoryId exists, so create returns an object with existing
                                                    // memory, therefore name can
                                                    // be null
                                                    Map<String, Object> memoryParams = createMemoryParams(
                                                        question,
                                                        memoryId,
                                                        appType,
                                                        finalMlAgent,
                                                        requestParameters
                                                    );

                                                    factory
                                                        .create(
                                                            memoryParams,
                                                            ActionListener
                                                                .wrap(
                                                                    createdMemory -> executeAgent(
                                                                        inputDataSet,
                                                                        mlTask,
                                                                        isAsync,
                                                                        memoryId,
                                                                        finalMlAgent,
                                                                        outputs,
                                                                        modelTensors,
                                                                        listener,
                                                                        createdMemory,
                                                                        channel,
                                                                        hookRegistry
                                                                    ),
                                                                    ex -> {
                                                                        log.error("Failed to find memory with memory_id: {}", memoryId, ex);
                                                                        listener.onFailure(ex);
                                                                    }
                                                                )
                                                        );
                                                    return;
                                                }
                                            }
                                            executeAgent(
                                                inputDataSet,
                                                mlTask,
                                                isAsync,
                                                memoryId,
                                                mlAgent,
                                                outputs,
                                                modelTensors,
                                                listener,
                                                null,
                                                channel,
                                                hookRegistry
                                            );
                                        }
                                    } catch (Exception e) {
                                        log.error("Failed to parse ml agent {}", agentId, e);
                                        listener.onFailure(e);
                                    }
                                } else {
                                    listener
                                        .onFailure(
                                            new OpenSearchStatusException(
                                                "Failed to find agent with the provided agent id: " + agentId,
                                                RestStatus.NOT_FOUND
                                            )
                                        );
                                }
                            } catch (Exception e) {
                                log.error("Failed to get agent", e);
                                listener.onFailure(e);
                            }
                        }
                    });
            }
        } else {
            listener.onFailure(new ResourceNotFoundException("Agent index not found"));
        }
    }

    /**
     * save root interaction and start execute the agent
     * 
     * @param listener     callback listener
     * @param memory       memory instance
     * @param inputDataSet input
     * @param mlAgent      agent to run
     */
    private void saveRootInteractionAndExecute(
        ActionListener<Output> listener,
        Memory memory,
        RemoteInferenceInputDataSet inputDataSet,
        MLTask mlTask,
        boolean isAsync,
        List<ModelTensors> outputs,
        List<ModelTensor> modelTensors,
        MLAgent mlAgent,
        TransportChannel channel,
        HookRegistry hookRegistry
    ) {
        String appType = mlAgent.getAppType();
        String question = inputDataSet.getParameters().get(QUESTION);
        String regenerateInteractionId = inputDataSet.getParameters().get(REGENERATE_INTERACTION_ID);
        // Create root interaction ID
        ConversationIndexMessage msg = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .type(appType)
            .question(question)
            .response("")
            .finalAnswer(true)
            .sessionId(memory.getId())
            .build();
        memory.save(msg, null, null, null, ActionListener.<CreateInteractionResponse>wrap(interaction -> {
            log.info("Created parent interaction ID: {}", interaction.getId());
            inputDataSet.getParameters().put(PARENT_INTERACTION_ID, interaction.getId());
            // only delete previous interaction when new interaction created
            if (regenerateInteractionId != null) {
                memory
                    .deleteInteractionAndTrace(
                        regenerateInteractionId,
                        ActionListener
                            .wrap(
                                deleted -> executeAgent(
                                    inputDataSet,
                                    mlTask,
                                    isAsync,
                                    memory.getId(),
                                    mlAgent,
                                    outputs,
                                    modelTensors,
                                    listener,
                                    memory,
                                    channel,
                                    hookRegistry
                                ),
                                e -> {
                                    log.error("Failed to regenerate for interaction {}", regenerateInteractionId, e);
                                    listener.onFailure(e);
                                }
                            )
                    );
            } else {
                executeAgent(
                    inputDataSet,
                    mlTask,
                    isAsync,
                    memory.getId(),
                    mlAgent,
                    outputs,
                    modelTensors,
                    listener,
                    memory,
                    channel,
                    hookRegistry
                );
            }
        }, ex -> {
            log.error("Failed to create parent interaction", ex);
            listener.onFailure(ex);
        }));
    }

    /**
     * Process context management configuration and register context managers in
     * hook registry
     * 
     * @param mlAgent      the ML agent with context management configuration
     * @param hookRegistry the hook registry to register context managers with
     * @param inputDataSet the input dataset to update with context management info
     */
    private void processContextManagement(MLAgent mlAgent, HookRegistry hookRegistry, RemoteInferenceInputDataSet inputDataSet) {
        try {
            // Check if context_management is already specified in runtime parameters
            String runtimeContextManagement = inputDataSet.getParameters().get("context_management");
            if (runtimeContextManagement != null && !runtimeContextManagement.trim().isEmpty()) {
                log.info("Using runtime context management parameter: {}", runtimeContextManagement);
                return; // Runtime parameter takes precedence, let MLExecuteTaskRunner handle it
            }

            // Check if already processed to avoid duplicate registrations
            if ("true".equals(inputDataSet.getParameters().get("context_management_processed"))) {
                log.debug("Context management already processed for this execution, skipping");
                return;
            }

            // Check if HookRegistry already has callbacks (from previous runtime setup)
            // Don't override with inline configuration if runtime config is already active
            if (hookRegistry.getCallbackCount(org.opensearch.ml.common.hooks.EnhancedPostToolEvent.class) > 0
                || hookRegistry.getCallbackCount(org.opensearch.ml.common.hooks.PreLLMEvent.class) > 0) {
                log.info("HookRegistry already has active configuration, skipping inline context management");
                return;
            }

            ContextManagementTemplate template = null;
            String templateName = null;

            if (mlAgent.hasContextManagementTemplate()) {
                // Template reference - would need to be resolved from template service
                templateName = mlAgent.getContextManagementTemplateName();
                log.info("Agent '{}' has context management template reference: {}", mlAgent.getName(), templateName);
                // For now, we'll pass the template name to parameters for MLExecuteTaskRunner
                // to handle
                inputDataSet.getParameters().put("context_management", templateName);
                return; // Let MLExecuteTaskRunner handle template resolution
            } else if (mlAgent.getInlineContextManagement() != null) {
                // Inline template - process directly
                template = mlAgent.getInlineContextManagement();
                templateName = template.getName();
                log.info("Agent '{}' has inline context management configuration: {}", mlAgent.getName(), templateName);
            }

            if (template != null) {
                // Process inline context management template
                processInlineContextManagement(template, hookRegistry);
                // Mark as processed to prevent MLExecuteTaskRunner from processing it again
                inputDataSet.getParameters().put("context_management_processed", "true");
                inputDataSet.getParameters().put("context_management", templateName);
            }
        } catch (Exception e) {
            log.error("Failed to process context management for agent '{}': {}", mlAgent.getName(), e.getMessage(), e);
            // Don't fail the entire execution, just log the error
        }
    }

    /**
     * Process inline context management template and register context managers
     * 
     * @param template     the context management template
     * @param hookRegistry the hook registry to register with
     */
    private void processInlineContextManagement(ContextManagementTemplate template, HookRegistry hookRegistry) {
        try {
            log.debug("Processing inline context management template: {}", template.getName());

            // Fresh HookRegistry ensures no duplicate registrations

            // Create context managers from template configuration
            List<org.opensearch.ml.common.contextmanager.ContextManager> contextManagers = createContextManagers(template);

            if (!contextManagers.isEmpty()) {
                // Create hook provider and register with hook registry
                org.opensearch.ml.common.contextmanager.ContextManagerHookProvider hookProvider =
                    new org.opensearch.ml.common.contextmanager.ContextManagerHookProvider(contextManagers);

                // Update hook configuration based on template
                hookProvider.updateHookConfiguration(template.getHooks());

                // Register hooks with the registry
                hookProvider.registerHooks(hookRegistry);

                log.info("Successfully registered {} context managers from template '{}'", contextManagers.size(), template.getName());
            } else {
                log.warn("No context managers created from template '{}'", template.getName());
            }
        } catch (Exception e) {
            log.error("Failed to process inline context management template '{}': {}", template.getName(), e.getMessage(), e);
        }
    }

    /**
     * Create context managers from template configuration
     * 
     * @param template the context management template
     * @return list of created context managers
     */
    private List<org.opensearch.ml.common.contextmanager.ContextManager> createContextManagers(ContextManagementTemplate template) {
        List<org.opensearch.ml.common.contextmanager.ContextManager> managers = new ArrayList<>();

        try {
            // Iterate through all hooks and their configurations
            for (Map.Entry<String, List<org.opensearch.ml.common.contextmanager.ContextManagerConfig>> entry : template
                .getHooks()
                .entrySet()) {
                String hookName = entry.getKey();
                List<org.opensearch.ml.common.contextmanager.ContextManagerConfig> configs = entry.getValue();

                log.debug("Processing hook '{}' with {} configurations", hookName, configs.size());

                for (org.opensearch.ml.common.contextmanager.ContextManagerConfig config : configs) {
                    try {
                        org.opensearch.ml.common.contextmanager.ContextManager manager = createContextManager(config);
                        if (manager != null) {
                            managers.add(manager);
                            log.debug("Created context manager: {} for hook: {}", config.getType(), hookName);
                        }
                    } catch (Exception e) {
                        log
                            .error(
                                "Failed to create context manager of type '{}' for hook '{}': {}",
                                config.getType(),
                                hookName,
                                e.getMessage(),
                                e
                            );
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to create context managers from template: {}", e.getMessage(), e);
        }

        return managers;
    }

    /**
     * Create a single context manager from configuration
     * 
     * @param config the context manager configuration
     * @return the created context manager or null if creation failed
     */
    private org.opensearch.ml.common.contextmanager.ContextManager createContextManager(
        org.opensearch.ml.common.contextmanager.ContextManagerConfig config
    ) {
        try {
            String type = config.getType();
            Map<String, Object> managerConfig = config.getConfig();

            log.debug("Creating context manager of type: {}", type);

            // Create context manager based on type
            switch (type) {
                case "ToolsOutputTruncateManager":
                    return createToolsOutputTruncateManager(managerConfig);
                case "SummarizationManager":
                case "SummarizingManager":
                    return createSummarizationManager(managerConfig);
                case "MemoryManager":
                    return createMemoryManager(managerConfig);
                case "ConversationManager":
                    return createConversationManager(managerConfig);
                default:
                    log.warn("Unknown context manager type: {}", type);
                    return null;
            }
        } catch (Exception e) {
            log.error("Failed to create context manager: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Create ToolsOutputTruncateManager
     */
    private org.opensearch.ml.common.contextmanager.ContextManager createToolsOutputTruncateManager(Map<String, Object> config) {
        log.debug("Creating ToolsOutputTruncateManager with config: {}", config);
        ToolsOutputTruncateManager manager = new ToolsOutputTruncateManager();
        manager.initialize(config != null ? config : new HashMap<>());
        return manager;
    }

    /**
     * Create SummarizationManager
     */
    private org.opensearch.ml.common.contextmanager.ContextManager createSummarizationManager(Map<String, Object> config) {
        log.debug("Creating SummarizationManager with config: {}", config);
        SummarizationManager manager = new SummarizationManager(client);
        manager.initialize(config != null ? config : new HashMap<>());
        return manager;
    }

    /**
     * Create SlidingWindowManager (used for MemoryManager type)
     */
    private org.opensearch.ml.common.contextmanager.ContextManager createMemoryManager(Map<String, Object> config) {
        log.debug("Creating SlidingWindowManager (MemoryManager) with config: {}", config);
        SlidingWindowManager manager = new SlidingWindowManager();
        manager.initialize(config != null ? config : new HashMap<>());
        return manager;
    }

    /**
     * Create ConversationManager (placeholder - using SummarizationManager for now)
     */
    private org.opensearch.ml.common.contextmanager.ContextManager createConversationManager(Map<String, Object> config) {
        log.debug("Creating ConversationManager (using SummarizationManager as placeholder) with config: {}", config);
        SummarizationManager manager = new SummarizationManager(client);
        manager.initialize(config != null ? config : new HashMap<>());
        return manager;
    }

    private void executeAgent(
        RemoteInferenceInputDataSet inputDataSet,
        MLTask mlTask,
        boolean isAsync,
        String memoryId,
        MLAgent mlAgent,
        List<ModelTensors> outputs,
        List<ModelTensor> modelTensors,
        ActionListener<Output> listener,
        Memory memory,
        TransportChannel channel,
        HookRegistry hookRegistry
    ) {
        String mcpConnectorConfigJSON = (mlAgent.getParameters() != null) ? mlAgent.getParameters().get(MCP_CONNECTORS_FIELD) : null;
        if (mcpConnectorConfigJSON != null && !mlFeatureEnabledSetting.isMcpConnectorEnabled()) {
            // MCP connector provided as tools but MCP feature is disabled, so abort.
            listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE));
            return;
        }

        // Check for agent-level context management configuration (following connector
        // pattern)
        if (mlAgent.hasContextManagement()) {
            processContextManagement(mlAgent, hookRegistry, inputDataSet);
        }

        MLAgentRunner mlAgentRunner = getAgentRunner(mlAgent, hookRegistry);
        String parentInteractionId = inputDataSet.getParameters().get(PARENT_INTERACTION_ID);

        // If async is true, index ML task and return the taskID. Also add memoryID to
        // the task if it exists
        if (isAsync) {
            Map<String, Object> agentResponse = new HashMap<>();
            if (memoryId != null && !memoryId.isEmpty()) {
                agentResponse.put(MEMORY_ID, memoryId);
            }

            if (parentInteractionId != null && !parentInteractionId.isEmpty()) {
                agentResponse.put(PARENT_INTERACTION_ID, parentInteractionId);
            }
            mlTask.setResponse(agentResponse);
            mlTask.setAsync(true);

            indexMLTask(mlTask, ActionListener.wrap(indexResponse -> {
                String taskId = indexResponse.getId();
                mlTask.setTaskId(taskId);

                MLTaskOutput outputBuilder = MLTaskOutput.builder().taskId(taskId).status(MLTaskState.RUNNING.toString()).build();

                if (memoryId != null && !memoryId.isEmpty()) {
                    outputBuilder.setResponse(agentResponse);
                }
                listener.onResponse(outputBuilder);
                ActionListener<Object> agentActionListener = createAsyncTaskUpdater(
                    mlTask,
                    outputs,
                    modelTensors,
                    parentInteractionId,
                    memory
                );
                inputDataSet.getParameters().put(TASK_ID_FIELD, taskId);
                try {
                    mlAgentRunner.run(mlAgent, inputDataSet.getParameters(), agentActionListener, channel);
                } catch (Exception e) {
                    log.error("Failed to run agent", e);
                    agentActionListener.onFailure(e);
                }
            }, e -> {
                log.error("Failed to create task for agent async execution", e);
                listener.onFailure(e);
            }));
        } else {
            ActionListener<Object> agentActionListener = createAgentActionListener(
                listener,
                outputs,
                modelTensors,
                mlAgent.getType(),
                parentInteractionId,
                memory
            );
            try {
                mlAgentRunner.run(mlAgent, inputDataSet.getParameters(), agentActionListener, channel);
            } catch (Exception e) {
                log.error("Failed to run agent", e);
                agentActionListener.onFailure(e);
            }
        }
    }

    @SuppressWarnings("removal")
    private ActionListener<Object> createAgentActionListener(
        ActionListener<Output> listener,
        List<ModelTensors> outputs,
        List<ModelTensor> modelTensors,
        String agentType,
        String parentInteractionId,
        Memory memory
    ) {
        return ActionListener.wrap(output -> {
            if (output != null) {
                processOutput(output, modelTensors);
                listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(outputs).build());
            } else {
                listener.onResponse(null);
            }
        }, ex -> {
            log.error("Failed to run {} agent", agentType, ex);
            updateInteractionWithFailure(parentInteractionId, memory, ex.getMessage());
            listener.onFailure(ex);
        });
    }

    private ActionListener<Object> createAsyncTaskUpdater(
        MLTask mlTask,
        List<ModelTensors> outputs,
        List<ModelTensor> modelTensors,
        String parentInteractionId,
        Memory memory
    ) {
        String taskId = mlTask.getTaskId();
        Map<String, Object> agentResponse = new HashMap<>();
        Map<String, Object> updatedTask = new HashMap<>();

        return ActionListener.wrap(output -> {
            if (output != null) {
                processOutput(output, modelTensors);
                agentResponse.put(INFERENCE_RESULT_FIELD, outputs);
            } else {
                agentResponse.put(ERROR_MESSAGE, "No output found from agent execution");
            }

            mlTask.setResponse(agentResponse);
            updatedTask.put(RESPONSE_FIELD, agentResponse);
            updatedTask.put(STATE_FIELD, MLTaskState.COMPLETED);
            updateMLTaskDirectly(
                taskId,
                updatedTask,
                client,
                ActionListener
                    .wrap(
                        response -> log.info("Updated ML task {} with agent execution results", taskId),
                        e -> log.error("Failed to update ML task {} with agent execution results", taskId)
                    )
            );
        }, ex -> {
            agentResponse.put(ERROR_MESSAGE, ex.getMessage());

            updatedTask.put(RESPONSE_FIELD, agentResponse);
            updatedTask.put(STATE_FIELD, MLTaskState.FAILED);
            mlTask.setResponse(agentResponse);

            updateMLTaskDirectly(
                taskId,
                updatedTask,
                client,
                ActionListener
                    .wrap(
                        response -> log.info("Updated ML task {} with agent execution failed reason", taskId),
                        e -> log.error("Failed to update ML task {} with agent execution results", taskId)
                    )
            );

            updateInteractionWithFailure(parentInteractionId, memory, ex.getMessage());
        });
    }

    @VisibleForTesting
    protected MLAgentRunner getAgentRunner(MLAgent mlAgent, HookRegistry hookRegistry) {
        final MLAgentType agentType = MLAgentType.from(mlAgent.getType().toUpperCase(Locale.ROOT));
        switch (agentType) {
            case FLOW:
                return new MLFlowAgentRunner(
                    client,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryFactoryMap,
                    sdkClient,
                    encryptor
                );
            case CONVERSATIONAL_FLOW:
                return new MLConversationalFlowAgentRunner(
                    client,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryFactoryMap,
                    sdkClient,
                    encryptor
                );
            case CONVERSATIONAL:
                return new MLChatAgentRunner(
                    client,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryFactoryMap,
                    sdkClient,
                    encryptor,
                    hookRegistry
                );
            case PLAN_EXECUTE_AND_REFLECT:
                return new MLPlanExecuteAndReflectAgentRunner(
                    client,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryFactoryMap,
                    sdkClient,
                    encryptor,
                    hookRegistry
                );
            default:
                throw new IllegalArgumentException("Unsupported agent type: " + mlAgent.getType());
        }
    }

    @SuppressWarnings("removal")
    public void processOutput(Object output, List<ModelTensor> modelTensors) throws PrivilegedActionException {
        Gson gson = new Gson();
        if (output instanceof ModelTensorOutput) {
            ModelTensorOutput modelTensorOutput = (ModelTensorOutput) output;
            modelTensorOutput.getMlModelOutputs().forEach(outs -> { modelTensors.addAll(outs.getMlModelTensors()); });
        } else if (output instanceof ModelTensor) {
            modelTensors.add((ModelTensor) output);
        } else if (output instanceof List) {
            if (((List<?>) output).get(0) instanceof ModelTensor) {
                modelTensors.addAll(((List<ModelTensor>) output));
            } else if (((List<?>) output).get(0) instanceof ModelTensors) {
                ((List<ModelTensors>) output).forEach(outs -> { modelTensors.addAll(outs.getMlModelTensors()); });
            } else {
                String result = AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> gson.toJson(output));
                modelTensors.add(ModelTensor.builder().name("response").result(result).build());
            }
        } else {
            String result = output instanceof String
                ? (String) output
                : AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> gson.toJson(output));
            modelTensors.add(ModelTensor.builder().name("response").result(result).build());
        }
    }

    public void indexMLTask(MLTask mlTask, ActionListener<IndexResponse> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient
                .putDataObjectAsync(
                    PutDataObjectRequest.builder().index(ML_TASK_INDEX).tenantId(mlTask.getTenantId()).dataObject(mlTask).build()
                )
                .whenComplete((r, throwable) -> {
                    context.restore();
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        log.error("Failed to index ML task", cause);
                        listener.onFailure(cause);
                    } else {
                        try {
                            IndexResponse indexResponse = IndexResponse.fromXContent(r.parser());
                            log.info("Task creation result: {}, Task id: {}", indexResponse.getResult(), indexResponse.getId());
                            listener.onResponse(indexResponse);
                        } catch (Exception e) {
                            listener.onFailure(e);
                        }
                    }
                });
        } catch (Exception e) {
            log.error("Failed to create ML task for {}, {}", mlTask.getFunctionName(), mlTask.getTaskType(), e);
            listener.onFailure(e);
        }
    }

    private void updateInteractionWithFailure(String interactionId, Memory memory, String errorMessage) {
        if (interactionId != null && memory != null) {
            String failureMessage = "Agent execution failed: " + errorMessage;
            Map<String, Object> updateContent = new HashMap<>();
            updateContent.put(RESPONSE_FIELD, failureMessage);

            memory
                .update(
                    interactionId,
                    updateContent,
                    ActionListener
                        .wrap(
                            res -> log.info("Updated interaction {} with failure message", interactionId),
                            e -> log.warn("Failed to update interaction {} with failure message", interactionId, e)
                        )
                );
        }
    }

    /**
     * Processes standardized input if present in AgentMLInput.
     * This method handles the conversion from AgentInput to parameters that can be used
     * by the existing agent execution logic.
     */
    private void processAgentInput(AgentMLInput agentMLInput, MLAgent mlAgent) {
        // old style agent registration
        if (mlAgent.getModel() == null) {
            return;
        }

        // If legacy question input is provided, parse to new standard input
        if (agentMLInput.getInputDataset() != null) {
            RemoteInferenceInputDataSet remoteInferenceInputDataSet = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
            if (remoteInferenceInputDataSet.getParameters().containsKey(QUESTION)) {
                AgentInput standardInput = new AgentInput(remoteInferenceInputDataSet.getParameters().get(QUESTION));
                agentMLInput.setAgentInput(standardInput);
            }
        }

        try {
            // Extract the question text for prompt template and memory storage
            String question = AgentInputProcessor.extractQuestionText(agentMLInput.getAgentInput());
            ModelProvider modelProvider = ModelProviderFactory.getProvider(mlAgent.getModel().getModelProvider());

            // create input dataset if it doesn't exist
            if (agentMLInput.getInputDataset() == null) {
                agentMLInput.setInputDataset(new RemoteInferenceInputDataSet(new HashMap<>()));
            }

            // Set parameters to processed params
            RemoteInferenceInputDataSet remoteDataSet = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
            Map<String, String> parameters = modelProvider.mapAgentInput(agentMLInput.getAgentInput());
            // set question to questionText for memory
            parameters.put(QUESTION, question);
            remoteDataSet.getParameters().putAll(parameters);
        } catch (Exception e) {
            log.error("Failed to process standardized input for agent {}", mlAgent.getName(), e);
            throw new IllegalArgumentException("Failed to process standardized agent input: " + e.getMessage(), e);
        }
    }
}
