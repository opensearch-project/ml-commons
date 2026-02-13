/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ENDPOINT_FIELD;
import static org.opensearch.ml.common.CommonValue.MCP_CONNECTORS_FIELD;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.MLTask.RESPONSE_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTask.TASK_ID_FIELD;
import static org.opensearch.ml.common.agent.MLAgent.CONTEXT_MANAGEMENT_NAME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.output.model.ModelTensorOutput.INFERENCE_RESULT_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_REMOTE_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.common.utils.MLTaskUtils.updateMLTaskDirectly;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.MEMORY_CONFIGURATION_FIELD;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.createMemoryParams;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.sanitizeForLogging;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
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
import org.opensearch.common.io.stream.BytesStreamOutput;
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
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.common.contextmanager.ContextManager;
import org.opensearch.ml.common.contextmanager.ContextManagerHookProvider;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.hooks.HookRegistry;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.agent.AgentInput;
import org.opensearch.ml.common.input.execute.agent.AgentInputProcessor;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.InputType;
import org.opensearch.ml.common.input.execute.agent.Message;
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
import org.opensearch.ml.engine.algorithms.contextmanager.ContextManagerFactory;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.engine.memory.ConversationIndexMessage;
import org.opensearch.ml.engine.agents.models.ModelProvider;
import org.opensearch.ml.engine.agents.models.ModelProviderFactory;
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
    public static final String CONTEXT_MANAGEMENT_PROCESSED = "context_management_processed";

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
        if (!(input instanceof AgentMLInput)) {
            throw new IllegalArgumentException("wrong input");
        }
        AgentMLInput agentMLInput = (AgentMLInput) input;
        String agentId = agentMLInput.getAgentId();
        String tenantId = agentMLInput.getTenantId();
        Boolean isAsync = agentMLInput.getIsAsync();

        if (agentMLInput.getInputDataset() == null && !agentMLInput.hasStandardInput()) {
            throw new IllegalArgumentException("Agent input data can not be empty.");
        }

        RemoteInferenceInputDataSet dataSet = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        if ((dataSet == null || dataSet.getParameters() == null) && !agentMLInput.hasStandardInput()) {
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
                                        Map<String, String> requestParameters = inputDataSet.getParameters();

                                        mlAgent = applyMemoryContainerOverride(mlAgent, inputDataSet, agentId);
                                        final MLAgent finalMlAgent = mlAgent;

                                        MLMemorySpec memorySpec = mlAgent.getMemory();
                                        if (!mlFeatureEnabledSetting.isRemoteAgenticMemoryEnabled()) {
                                            boolean usesRemoteMemory = memorySpec != null
                                                && MLMemoryType.REMOTE_AGENTIC_MEMORY.name().equalsIgnoreCase(memorySpec.getType());
                                            String memoryConfig = requestParameters != null
                                                ? requestParameters.get(MEMORY_CONFIGURATION_FIELD)
                                                : null;
                                            if (usesRemoteMemory || !Strings.isNullOrEmpty(memoryConfig)) {
                                                listener
                                                    .onFailure(
                                                        new OpenSearchStatusException(
                                                            ML_COMMONS_REMOTE_AGENTIC_MEMORY_DISABLED_MESSAGE,
                                                            RestStatus.FORBIDDEN
                                                        )
                                                    );
                                                return;
                                            }
                                        }
                                        String memoryId = inputDataSet.getParameters().get(MEMORY_ID);
                                        String parentInteractionId = inputDataSet.getParameters().get(PARENT_INTERACTION_ID);
                                        String regenerateInteractionId = inputDataSet.getParameters().get(REGENERATE_INTERACTION_ID);
                                        String appType = finalMlAgent.getAppType();
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
                                            && memoryFactoryMap != null
                                            && memoryFactoryMap.containsKey(MLMemoryType.from(memorySpec.getType()).name())
                                            && (memoryId == null || parentInteractionId == null)) {
                                            Map<String, Object> memoryParams = createMemoryParams(
                                                question,
                                                memoryId,
                                                appType,
                                                finalMlAgent,
                                                requestParameters
                                            );
                                            log.debug("MLAgentExecutor creating new memory, params: {}", sanitizeForLogging(memoryParams));
                                            // Check if inline connector metadata is present to use RemoteAgenticConversationMemory
                                            Memory.Factory<Memory<?, ?, ?>> memoryFactory;
                                            if (memoryParams != null && memoryParams.containsKey(ENDPOINT_FIELD)) {
                                                // Use RemoteAgenticConversationMemory when inline connector metadata is detected
                                                memoryFactory = memoryFactoryMap.get(MLMemoryType.REMOTE_AGENTIC_MEMORY.name());
                                                log.info("Detected inline connector metadata, using RemoteAgenticConversationMemory");
                                            } else {
                                                // Use the originally specified memory factory
                                                memoryFactory = memoryFactoryMap.get(MLMemoryType.from(memorySpec.getType()).name());
                                            }
                                            if (memoryFactory == null) {
                                                listener
                                                    .onFailure(
                                                        new IllegalArgumentException(
                                                            "Memory factory not found for type: "
                                                                + (memoryParams != null && memoryParams.containsKey(ENDPOINT_FIELD)
                                                                    ? MLMemoryType.REMOTE_AGENTIC_MEMORY.name()
                                                                    : MLMemoryType.from(memorySpec.getType()).name())
                                                        )
                                                    );
                                                return;
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
                                                                    tenantId,
                                                                    memory,
                                                                    inputDataSet,
                                                                    mlTask,
                                                                    isAsync,
                                                                    outputs,
                                                                    modelTensors,
                                                                    finalMlAgent,
                                                                    channel,
                                                                    hookRegistry,
                                                                    agentMLInput
                                                                );
                                                            }, e -> {
                                                                log.error("Failed to get existing interaction for regeneration", e);
                                                                listener.onFailure(e);
                                                            })
                                                        );
                                                } else {
                                                    saveRootInteractionAndExecute(
                                                        listener,
                                                        tenantId,
                                                        memory,
                                                        inputDataSet,
                                                        mlTask,
                                                        isAsync,
                                                        outputs,
                                                        modelTensors,
                                                        finalMlAgent,
                                                        channel,
                                                        hookRegistry,
                                                        agentMLInput
                                                    );
                                                }
                                            }, ex -> {
                                                log.error("Failed to read conversation memory", ex);
                                                listener.onFailure(ex);
                                            }));
                                        } else {
                                            Map<String, Object> memoryParams = createMemoryParams(
                                                question,
                                                memoryId,
                                                appType,
                                                finalMlAgent,
                                                requestParameters
                                            );
                                            log
                                                .debug(
                                                    "MLAgentExecutor loading existing memory, params: {}",
                                                    sanitizeForLogging(memoryParams)
                                                );
                                            // For existing conversations, create memory instance using factory
                                            if (memorySpec != null && memorySpec.getType() != null) {
                                                Memory.Factory<Memory<?, ?, ?>> memoryFactory;
                                                if (memoryParams != null && memoryParams.containsKey(ENDPOINT_FIELD)) {
                                                    // Use RemoteAgenticConversationMemory when inline connector metadata is detected
                                                    memoryFactory = memoryFactoryMap.get(MLMemoryType.REMOTE_AGENTIC_MEMORY.name());
                                                    log.info("Detected inline connector metadata, using RemoteAgenticConversationMemory");
                                                } else {
                                                    // Use the originally specified memory factory
                                                    memoryFactory = memoryFactoryMap.get(MLMemoryType.from(memorySpec.getType()).name());
                                                }

                                                if (memoryFactory != null) {
                                                    // memoryId exists, so create returns an object with existing
                                                    // memory, therefore name can
                                                    // be null

                                                    memoryFactory
                                                        .create(
                                                            memoryParams,
                                                            ActionListener
                                                                .wrap(
                                                                    createdMemory -> executeAgent(
                                                                        inputDataSet,
                                                                        tenantId,
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
                                                tenantId,
                                                mlTask,
                                                isAsync,
                                                memoryId,
                                                finalMlAgent,
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
     * @param mlAgent agent to run
     * @param agentMLInput the original agent input
     */
    private void saveRootInteractionAndExecute(
        ActionListener<Output> listener,
        String tenantId,
        Memory memory,
        RemoteInferenceInputDataSet inputDataSet,
        MLTask mlTask,
        boolean isAsync,
        List<ModelTensors> outputs,
        List<ModelTensor> modelTensors,
        MLAgent mlAgent,
        TransportChannel channel,
        HookRegistry hookRegistry,
        AgentMLInput agentMLInput
    ) {
        String appType = mlAgent.getAppType();
        String question = inputDataSet.getParameters().get(QUESTION);
        String regenerateInteractionId = inputDataSet.getParameters().get(REGENERATE_INTERACTION_ID);

        // Store message pairs if input is of type MESSAGES, then create parent interaction
        if (agentMLInput.getAgentInput() != null && agentMLInput.getAgentInput().getInputType() == InputType.MESSAGES) {
            storeMessagesInMemory(
                memory,
                (List<Message>) agentMLInput.getAgentInput().getInput(),
                appType,
                ActionListener
                    .<Void>wrap(
                        v -> createParentInteractionAndExecute(
                            tenantId,
                            memory,
                            appType,
                            question,
                            regenerateInteractionId,
                            inputDataSet,
                            mlTask,
                            isAsync,
                            mlAgent,
                            outputs,
                            modelTensors,
                            listener,
                            channel,
                            hookRegistry
                        ),
                        ex -> {
                            log.error("Failed to store message pairs in memory", ex);
                            listener.onFailure(ex);
                        }
                    )
            );
        } else {
            // No messages to store, proceed directly to create parent interaction
            createParentInteractionAndExecute(
                tenantId,
                memory,
                appType,
                question,
                regenerateInteractionId,
                inputDataSet,
                mlTask,
                isAsync,
                mlAgent,
                outputs,
                modelTensors,
                listener,
                channel,
                hookRegistry
            );
        }
    }

    /**
     * Creates parent interaction and executes the agent.
     */
    private void createParentInteractionAndExecute(
        String tenantId,
        Memory memory,
        String appType,
        String question,
        String regenerateInteractionId,
        RemoteInferenceInputDataSet inputDataSet,
        MLTask mlTask,
        boolean isAsync,
        MLAgent mlAgent,
        List<ModelTensors> outputs,
        List<ModelTensor> modelTensors,
        ActionListener<Output> listener,
        TransportChannel channel,
        HookRegistry hookRegistry
    ) {
        // Create root interaction ID for the current question
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
                                    tenantId,
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
                    tenantId,
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
            // Check if context management was already processed (by MLExecuteTaskRunner for runtime overrides)
            String contextManagementProcessed = inputDataSet.getParameters().get(CONTEXT_MANAGEMENT_PROCESSED);
            if ("true".equals(contextManagementProcessed)) {
                log.info("Context management already processed by MLExecuteTaskRunner, skipping agent's stored config");
                return;
            }

            // Check if context_management is already specified in runtime parameters
            String runtimeContextManagement = inputDataSet.getParameters().get(CONTEXT_MANAGEMENT_NAME_FIELD);
            if (runtimeContextManagement != null && !runtimeContextManagement.trim().isEmpty()) {
                log.info("Using runtime context management parameter: {}", runtimeContextManagement);
                return; // Runtime parameter takes precedence, let MLExecuteTaskRunner handle it
            }

            ContextManagementTemplate template = null;
            String templateName = null;

            if (mlAgent.hasContextManagementTemplate()) {
                // Template reference - would need to be resolved from template service
                templateName = mlAgent.getContextManagementTemplateName();
                log.info("Agent '{}' has context management template reference: {}", mlAgent.getName(), templateName);
                // For now, we'll pass the template name to parameters for MLExecuteTaskRunner
                // to handle
                inputDataSet.getParameters().put(CONTEXT_MANAGEMENT_NAME_FIELD, templateName);
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

                inputDataSet.getParameters().put(CONTEXT_MANAGEMENT_PROCESSED, "true");
                inputDataSet.getParameters().put(CONTEXT_MANAGEMENT_NAME_FIELD, templateName);
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
            List<ContextManager> contextManagers = ContextManagerFactory.createContextManagers(template, client);

            if (!contextManagers.isEmpty()) {
                // Create hook provider with template configuration and register with hook registry
                ContextManagerHookProvider hookProvider = new ContextManagerHookProvider(contextManagers, template.getHooks());

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

    @VisibleForTesting
    void storeMessagesInMemory(Memory memory, List<Message> messages, String appType, ActionListener<Void> listener) {
        // Parse backwards to extract historical conversation pairs, skipping trailing user messages
        // (which will be stored later with the agent's response)
        List<ConversationIndexMessage> messagePairs = new ArrayList<>();
        StringBuilder userTextBuilder = new StringBuilder();
        StringBuilder assistantTextBuilder = new StringBuilder();
        boolean skippingTrailingUsers = true;
        String currentRole = null;

        // Process messages backwards
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);

            if (message == null || message.getRole() == null) {
                continue;
            }

            String role = message.getRole().toLowerCase();

            // Skip non-user/assistant roles
            if (!role.equals("user") && !role.equals("assistant")) {
                continue;
            }

            // Skip trailing user messages (they'll be stored with agent's response)
            if (skippingTrailingUsers && role.equals("user")) {
                continue;
            }

            // Once we hit an assistant, stop skipping
            if (skippingTrailingUsers && role.equals("assistant")) {
                skippingTrailingUsers = false;
                log.info("Stopped skipping at assistant message at index {}", i);
            }

            // Detect role change from user to assistant (going backwards)
            if (currentRole != null && currentRole.equals("user") && role.equals("assistant")) {
                // Save the accumulated pair
                String userText = userTextBuilder.toString().trim();
                String assistantText = assistantTextBuilder.toString().trim();

                if (!userText.isEmpty() && !assistantText.isEmpty()) {
                    ConversationIndexMessage msg = ConversationIndexMessage
                        .conversationIndexMessageBuilder()
                        .type(appType)
                        .question(userText)
                        .response(assistantText)
                        .finalAnswer(true)
                        .sessionId(memory.getId())
                        .build();

                    messagePairs.add(msg);
                }

                // Clear buffers for next pair
                userTextBuilder.setLength(0);
                assistantTextBuilder.setLength(0);
            }

            // Accumulate text based on role (prepending since we're going backwards)
            if (role.equals("user")) {
                String text = extractTextFromMessage(message);
                if (!text.isEmpty()) {
                    if (userTextBuilder.length() > 0) {
                        userTextBuilder.insert(0, "\n");
                    }
                    userTextBuilder.insert(0, text);
                }
            } else if (role.equals("assistant")) {
                String text = extractTextFromMessage(message);
                if (!text.isEmpty()) {
                    if (assistantTextBuilder.length() > 0) {
                        assistantTextBuilder.insert(0, "\n");
                    }
                    assistantTextBuilder.insert(0, text);
                }
            }

            currentRole = role;
        }

        // Save any remaining pair
        String userText = userTextBuilder.toString().trim();
        String assistantText = assistantTextBuilder.toString().trim();

        if (!userText.isEmpty() && !assistantText.isEmpty()) {
            ConversationIndexMessage msg = ConversationIndexMessage
                .conversationIndexMessageBuilder()
                .type(appType)
                .question(userText)
                .response(assistantText)
                .finalAnswer(true)
                .sessionId(memory.getId())
                .build();

            messagePairs.add(msg);
        }

        // Reverse the list to maintain chronological order
        Collections.reverse(messagePairs);

        // If no pairs to save, complete immediately
        if (messagePairs.isEmpty()) {
            listener.onResponse(null);
            return;
        }

        // Save all pairs to memory sequentially using iterative approach
        saveMessagePairsSequentially(memory, messagePairs, listener);
    }

    /**
     * Iteratively saves message pairs to memory in sequence.
     * Each save waits for the previous one to complete before proceeding.
     */
    @VisibleForTesting
    void saveMessagePairsSequentially(Memory memory, List<ConversationIndexMessage> messagePairs, ActionListener<Void> finalListener) {
        saveNextMessagePair(memory, messagePairs, 0, finalListener);
    }

    /**
     * Helper method to save a single message pair and chain to the next one.
     * This creates a chain of listeners without recursion by building each listener
     * at the point of invocation.
     */
    @VisibleForTesting
    void saveNextMessagePair(Memory memory, List<ConversationIndexMessage> messagePairs, int index, ActionListener<Void> finalListener) {
        if (index >= messagePairs.size()) {
            // All pairs saved, complete
            finalListener.onResponse(null);
            return;
        }

        ConversationIndexMessage msg = messagePairs.get(index);

        // Create the listener for this save operation
        ActionListener<CreateInteractionResponse> saveListener = ActionListener.wrap(interaction -> {
            log.info("Stored message pair {} of {} in memory with interaction ID: {}", index + 1, messagePairs.size(), interaction.getId());
            // Save next pair
            saveNextMessagePair(memory, messagePairs, index + 1, finalListener);
        }, ex -> {
            log.error("Failed to store message pair {} of {} in memory", index + 1, messagePairs.size(), ex);
            // Continue with next pair even on failure
            saveNextMessagePair(memory, messagePairs, index + 1, finalListener);
        });

        // Execute the save operation with the listener
        memory.save(msg, null, null, null, saveListener);
    }

    private void executeAgent(
        RemoteInferenceInputDataSet inputDataSet,
        String tenantId,
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
                    tenantId,
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
                // For V2 agents, return clean structured output instead of wrapping in ModelTensor
                if (MLAgentType.CONVERSATIONAL_V2.name().equalsIgnoreCase(agentType) && output instanceof Map) {
                    Map<String, Object> responseMap = (Map<String, Object>) output;
                    listener.onResponse(org.opensearch.ml.common.output.agent.MLAgentV2Output.fromMap(responseMap));
                } else {
                    processOutput(output, modelTensors);
                    listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(outputs).build());
                }
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
        String tenantId,
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
                tenantId,
                updatedTask,
                client,
                sdkClient,
                ActionListener
                    .wrap(
                        response -> log.info("Updated ML task {} with agent execution results", taskId),
                        e -> log.error("Failed to update ML task {} with agent execution results: {}", taskId, e.getMessage(), e)
                    )
            );
        }, ex -> {
            agentResponse.put(ERROR_MESSAGE, ex.getMessage());

            updatedTask.put(RESPONSE_FIELD, agentResponse);
            updatedTask.put(STATE_FIELD, MLTaskState.FAILED);
            mlTask.setResponse(agentResponse);

            updateMLTaskDirectly(
                taskId,
                tenantId,
                updatedTask,
                client,
                sdkClient,
                ActionListener
                    .wrap(
                        response -> log.info("Updated ML task {} with agent execution failed reason", taskId),
                        e -> log.error("Failed to update ML task {} with agent execution results: {}", taskId, e.getMessage(), e)
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
            case AG_UI:
                return new MLAGUIAgentRunner(
                    client,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryFactoryMap,
                    sdkClient,
                    encryptor
                );
            case CONVERSATIONAL_V2:
                return new MLChatAgentRunnerV2(
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
                throw new IllegalArgumentException("Unsupported agent type");
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
    void processAgentInput(AgentMLInput agentMLInput, MLAgent mlAgent) {
        MLAgentType agentType = MLAgentType.from(mlAgent.getType());

        // old style agent registration, except AG_UI agent
        if (mlAgent.getModel() == null && agentType != MLAgentType.AG_UI) {
            return;
        }

        if (agentMLInput.getAgentInput() != null
            && agentMLInput.getAgentInput().getInputType() == InputType.MESSAGES
            && MLAgentType.from(mlAgent.getType()) == MLAgentType.PLAN_EXECUTE_AND_REFLECT) {
            throw new IllegalArgumentException("Messages input is not supported for Plan Execute and Reflect Agent.");
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

            // create input dataset if it doesn't exist
            if (agentMLInput.getInputDataset() == null) {
                agentMLInput.setInputDataset(new RemoteInferenceInputDataSet(new HashMap<>()));
            }

            // Set parameters to processed params
            RemoteInferenceInputDataSet remoteDataSet = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();

            // For agent with revamped interface, use ModelProvider to map the entire AgentInput
            if (mlAgent.getModel() != null) {
                // Get model provider from model spec or parameters (V2 agents may have it in parameters)
                String modelProviderStr = mlAgent.getModel().getModelProvider();
                if (modelProviderStr == null && mlAgent.getParameters() != null) {
                    modelProviderStr = mlAgent.getParameters().get("model_provider");
                }

                if (modelProviderStr == null) {
                    throw new IllegalArgumentException(
                        "Model provider not configured. Please specify 'model_provider' in model configuration or agent parameters."
                    );
                }

                ModelProvider modelProvider = ModelProviderFactory.getProvider(modelProviderStr);
                Map<String, String> parameters = modelProvider.mapAgentInput(agentMLInput.getAgentInput(), agentType);

                parameters.put(QUESTION, question);

                // For V2 agents, also pass the original AgentInput for Strands format storage
                if (agentType == MLAgentType.CONVERSATIONAL_V2 && agentMLInput.getAgentInput() != null) {
                    try {
                        // Serialize AgentInput using Writeable interface and Base64 encode
                        BytesStreamOutput out = new BytesStreamOutput();
                        agentMLInput.getAgentInput().writeTo(out);
                        String encoded = Base64.getEncoder().encodeToString(out.bytes().toBytesRef().bytes);
                        parameters.put("__agent_input_bytes__", encoded);
                    } catch (Exception e) {
                        log.warn("Failed to serialize AgentInput for V2 agent", e);
                    }
                }

                remoteDataSet.getParameters().putAll(parameters);
            } else {
                // For old-style AG_UI agents without model field
                remoteDataSet.getParameters().putIfAbsent(QUESTION, question);
            }
        } catch (Exception e) {
            log.error("Failed to process standardized input for agent {}", mlAgent.getName(), e);
            throw new IllegalArgumentException("Failed to process standardized agent input: " + e.getMessage(), e);
        }
    }

    /**
     * Applies memory container ID override from request parameters if provided.
     *
     * This method allows runtime override of the memory container ID that an agent uses,
     * enabling dynamic memory context switching. If a memory_container_id parameter is
     * provided in the request and differs from the agent's current container ID, the
     * agent's memory configuration is updated to use the override value.
     *
     * @param mlAgent The agent whose memory container may be overridden
     * @param inputDataSet The input dataset containing request parameters
     * @param agentId The agent ID for logging purposes
     * @return Updated MLAgent with overridden memory container if applicable, or original agent
     * @throws IllegalArgumentException if memory_container_id override is requested but agent has no memory configured
     */
    private MLAgent applyMemoryContainerOverride(MLAgent mlAgent, RemoteInferenceInputDataSet inputDataSet, String agentId) {
        Map<String, String> requestParameters = inputDataSet.getParameters();
        MLMemorySpec memorySpec = mlAgent.getMemory();

        // Extract memory_container_id override from request parameters if present
        String containerOverride = null;
        if (requestParameters != null && requestParameters.containsKey(MEMORY_CONTAINER_ID_FIELD)) {
            String containerParam = requestParameters.get(MEMORY_CONTAINER_ID_FIELD);
            if (!Strings.isNullOrEmpty(containerParam)) {
                containerOverride = containerParam;
            }
        }

        // Apply override if provided
        if (containerOverride != null) {
            // Validate that agent has memory configured
            if (memorySpec == null) {
                throw new IllegalArgumentException("memory_container_id override requires the agent to be configured with memory");
            }

            // Only update if override differs from current container ID
            String currentContainerId = memorySpec.getMemoryContainerId();
            if (!containerOverride.equals(currentContainerId)) {
                MLMemorySpec updatedSpec = memorySpec.toBuilder().memoryContainerId(containerOverride).build();
                mlAgent = mlAgent.toBuilder().memory(updatedSpec).build();

                log.debug("Agent {} overriding memory container from {} to {}", agentId, currentContainerId, containerOverride);
            }
        }

        return mlAgent;
    }

    /**
     * Helper method to extract text from a message's content blocks.
     */
    @VisibleForTesting
    String extractTextFromMessage(Message message) {
        if (message == null || message.getContent() == null) {
            return "";
        }

        StringBuilder textBuilder = new StringBuilder();
        for (ContentBlock block : message.getContent()) {
            if (block.getType() == ContentType.TEXT && block.getText() != null) {
                textBuilder.append(block.getText().trim());
                textBuilder.append("\n");
            }
        }

        return textBuilder.toString().trim();
    }
}
