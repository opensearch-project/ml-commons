/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.AGENT_ID_LOG_FIELD;
import static org.opensearch.ml.common.CommonValue.ENDPOINT_FIELD;
import static org.opensearch.ml.common.CommonValue.MCP_CONNECTORS_FIELD;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.MLTask.RESPONSE_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTask.TASK_ID_FIELD;
import static org.opensearch.ml.common.agent.MLAgent.CONTEXT_MANAGEMENT_NAME_FIELD;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_CONTEXT;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_LOAD_CHAT_HISTORY;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.output.model.ModelTensorOutput.INFERENCE_RESULT_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_REMOTE_AGENTIC_MEMORY_DISABLED_MESSAGE;
import static org.opensearch.ml.common.utils.MLTaskUtils.updateMLTaskDirectly;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.MEMORY_CONFIGURATION_FIELD;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.createMemoryParams;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.extractStatusCode;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMessageHistoryLimit;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.logAgentExecutionFailure;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.logAgentExecutionLatency;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.sanitizeForLogging;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.NEW_CHAT_HISTORY;

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
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agui.AGUIInputConverter;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.common.contextmanager.ContextManager;
import org.opensearch.ml.common.contextmanager.ContextManagerContext;
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
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.model.ModelProviderFactory;
import org.opensearch.ml.common.output.MLTaskOutput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.execute.agent.AgentV2Output;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.settings.SettingsChangeListener;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.utils.ToolUtils;
import org.opensearch.ml.engine.Executable;
import org.opensearch.ml.engine.agents.AgentContextUtil;
import org.opensearch.ml.engine.algorithms.contextmanager.ContextManagerFactory;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

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
    public static final String USES_UNIFIED_INTERFACE = "uses_unified_interface";

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
                                log.error("Failed to get Agent index. agentId={}, tenantId={}", agentId, tenantId, cause);
                                logAgentExecutionFailure("unknown", agentId, tenantId, 0, String.valueOf(RestStatus.NOT_FOUND.getStatus()));
                                listener.onFailure(new OpenSearchStatusException("Failed to get agent index", RestStatus.NOT_FOUND));
                            } else {
                                log.error("Failed to get ML Agent. agentId={}, tenantId={}", agentId, tenantId, cause);
                                logAgentExecutionFailure("unknown", agentId, tenantId, 0, extractStatusCode(cause));
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
                                            logAgentExecutionFailure(
                                                mlAgent.getType(),
                                                agentId,
                                                tenantId,
                                                0,
                                                String.valueOf(RestStatus.FORBIDDEN.getStatus())
                                            );
                                            listener
                                                .onFailure(
                                                    new OpenSearchStatusException(
                                                        "You don't have permission to access this resource",
                                                        RestStatus.FORBIDDEN
                                                    )
                                                );
                                            return;
                                        }

                                        processAgentInput(agentMLInput, mlAgent);

                                        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) agentMLInput
                                            .getInputDataset();
                                        Map<String, String> requestParameters = inputDataSet.getParameters();

                                        // Add agentId to parameters for logging in agent runners.
                                        // Uses agent_id_log to avoid colliding with the "agent_id" tool parameter
                                        // used by AgentTool to specify which sub-agent to invoke.
                                        inputDataSet.getParameters().put(AGENT_ID_LOG_FIELD, agentId);

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
                                                logAgentExecutionFailure(
                                                    finalMlAgent.getType(),
                                                    agentId,
                                                    tenantId,
                                                    0,
                                                    String.valueOf(RestStatus.FORBIDDEN.getStatus())
                                                );
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
                                                logAgentExecutionFailure(
                                                    finalMlAgent.getType(),
                                                    agentId,
                                                    tenantId,
                                                    0,
                                                    String.valueOf(RestStatus.BAD_REQUEST.getStatus())
                                                );
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
                                                                log
                                                                    .error(
                                                                        "Failed to get existing interaction for regeneration. agentId={}, tenantId={}",
                                                                        agentId,
                                                                        tenantId,
                                                                        e
                                                                    );
                                                                logAgentExecutionFailure(
                                                                    finalMlAgent.getType(),
                                                                    agentId,
                                                                    tenantId,
                                                                    0,
                                                                    extractStatusCode(e)
                                                                );
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
                                                log
                                                    .error(
                                                        "Failed to read conversation memory. agentId={}, tenantId={}",
                                                        agentId,
                                                        tenantId,
                                                        ex
                                                    );
                                                logAgentExecutionFailure(
                                                    finalMlAgent.getType(),
                                                    agentId,
                                                    tenantId,
                                                    0,
                                                    extractStatusCode(ex)
                                                );
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
                                                                        hookRegistry,
                                                                        null
                                                                    ),
                                                                    ex -> {
                                                                        log
                                                                            .error(
                                                                                "Failed to find memory with memory_id: {}. agentId={}, tenantId={}",
                                                                                memoryId,
                                                                                agentId,
                                                                                tenantId,
                                                                                ex
                                                                            );
                                                                        logAgentExecutionFailure(
                                                                            finalMlAgent.getType(),
                                                                            agentId,
                                                                            tenantId,
                                                                            0,
                                                                            extractStatusCode(ex)
                                                                        );
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
                                                hookRegistry,
                                                null
                                            );
                                        }
                                    } catch (Exception e) {
                                        log.error("Failed to parse ml agent. agentId={}, tenantId={}", agentId, tenantId, e);
                                        logAgentExecutionFailure("unknown", agentId, tenantId, 0, extractStatusCode(e));
                                        listener.onFailure(e);
                                    }
                                } else {
                                    logAgentExecutionFailure(
                                        "unknown",
                                        agentId,
                                        tenantId,
                                        0,
                                        String.valueOf(RestStatus.NOT_FOUND.getStatus())
                                    );
                                    listener
                                        .onFailure(
                                            new OpenSearchStatusException(
                                                "Failed to find agent with the provided agent id: " + agentId,
                                                RestStatus.NOT_FOUND
                                            )
                                        );
                                }
                            } catch (Exception e) {
                                log.error("Failed to get agent. agentId={}, tenantId={}", agentId, tenantId, e);
                                logAgentExecutionFailure("unknown", agentId, tenantId, 0, extractStatusCode(e));
                                listener.onFailure(e);
                            }
                        }
                    });
            }
        } else {
            logAgentExecutionFailure("unknown", agentId, tenantId, 0, String.valueOf(RestStatus.NOT_FOUND.getStatus()));
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

        // Convert all unified interface input types to List<Message>
        List<Message> inputMessages = null;
        if (agentMLInput.getAgentInput() != null) {
            InputType inputType = agentMLInput.getAgentInput().getInputType();
            switch (inputType) {
                case MESSAGES:
                    @SuppressWarnings("unchecked")
                    List<Message> messages = (List<Message>) agentMLInput.getAgentInput().getInput();
                    inputMessages = messages;
                    break;
                case CONTENT_BLOCKS:
                    @SuppressWarnings("unchecked")
                    List<ContentBlock> blocks = (List<ContentBlock>) agentMLInput.getAgentInput().getInput();
                    inputMessages = List.of(new Message("user", blocks));
                    break;
                case TEXT:
                    String text = (String) agentMLInput.getAgentInput().getInput();
                    ContentBlock textBlock = new ContentBlock();
                    textBlock.setType(ContentType.TEXT);
                    textBlock.setText(text);
                    inputMessages = List.of(new Message("user", List.of(textBlock)));
                    break;
            }
        }

        // For unified interface requests with structured message support, or AGUI history-load,
        // skip parent interaction creation. The pair-based memory format handles ordering internally.
        MLAgentType agentType = MLAgentType.from(mlAgent.getType());
        if ((inputMessages != null && supportsStructuredMessages(mlAgent)) || (agentType == MLAgentType.AG_UI && inputMessages == null)) {
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
                hookRegistry,
                inputMessages
            );
            return;
        }

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
            hookRegistry,
            inputMessages
        );
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
        HookRegistry hookRegistry,
        List<Message> inputMessages
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
                                    hookRegistry,
                                    inputMessages
                                ),
                                e -> {
                                    log
                                        .error(
                                            "Failed to regenerate for interaction {}. agentId={}, tenantId={}",
                                            regenerateInteractionId,
                                            inputDataSet.getParameters().get(AGENT_ID_LOG_FIELD),
                                            tenantId,
                                            e
                                        );
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
                    hookRegistry,
                    inputMessages
                );
            }
        }, ex -> {
            log
                .error(
                    "Failed to create parent interaction. agentId={}, tenantId={}",
                    inputDataSet.getParameters().get(AGENT_ID_LOG_FIELD),
                    tenantId,
                    ex
                );
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

    /**
     * Performs initial memory operations for unified interface agents:
     * 1. Gets structured message history
     * 2. Applies POST_MEMORY hook for context management (summarization, sliding window, etc.)
     * 3. Saves input messages
     * 4. Appends AGUI context if applicable
     * 5. Formats history for the LLM
     * 6. Invokes continuation when done
     */
    @VisibleForTesting
    void performInitialMemoryOperations(
        Memory memory,
        List<Message> inputMessages,
        Map<String, String> params,
        MLAgent mlAgent,
        ActionListener<?> listener,
        Runnable continuation,
        HookRegistry hookRegistry
    ) {
        int messageHistoryLimit = getMessageHistoryLimit(params);

        // TODO: Scalability improvement - getStructuredMessages fetches ALL messages then slices in-memory.
        // For conversations with thousands of messages, this is inefficient. Should add Memory.getStructuredMessages(limit)
        // to push limit down to database query level (e.g., OpenSearch query with size parameter).
        memory.getStructuredMessages(ActionListener.wrap(result -> {
            @SuppressWarnings("unchecked")
            List<Message> allMessages = (List<Message>) result;

            // Apply history limit
            List<Message> limitedHistory = messageHistoryLimit > 0 && allMessages.size() > messageHistoryLimit
                ? allMessages.subList(allMessages.size() - messageHistoryLimit, allMessages.size())
                : allMessages;

            AgentContextUtil.ensureLlmModelId(mlAgent, params);

            // Emit POST_MEMORY hook to allow context managers to modify retrieved structured history
            List<Message> history = processPostStructuredMemoryHook(params, limitedHistory, memory, hookRegistry);

            // Save input messages — memory auto-resolves the next message ID
            memory.saveStructuredMessages(inputMessages, ActionListener.wrap(v -> {
                try {
                    ModelProvider modelProvider = ModelProviderFactory.getProvider(mlAgent.getModel().getModelProvider());
                    MLAgentType agentType = MLAgentType.from(mlAgent.getType());

                    // Append AGUI context to current input for this LLM call (not persisted in memory)
                    if (agentType == MLAgentType.AG_UI) {
                        String contextJson = params.get(AGUI_PARAM_CONTEXT);
                        if (contextJson != null) {
                            JsonArray contextArray = JsonParser.parseString(contextJson).getAsJsonArray();
                            AGUIInputConverter.appendContextToLatestUserMessage(inputMessages, contextArray);
                            // Re-format current input with context included
                            String updatedBody = modelProvider.mapMessages(inputMessages, agentType).get("body");
                            if (updatedBody != null) {
                                params.put("body", updatedBody);
                            }
                        }
                    }

                    if (!history.isEmpty()) {
                        // Format history messages using the model provider for API-compatible output
                        Map<String, String> historyParams = modelProvider.mapMessages(history, agentType);
                        String formattedHistory = historyParams.get("body");
                        if (formattedHistory != null && !formattedHistory.isEmpty()) {
                            params.put(NEW_CHAT_HISTORY, formattedHistory + ", ");
                        }
                        // Copy NO_ESCAPE_PARAMS to ensure _chat_history is not double-escaped
                        if (historyParams.containsKey(ToolUtils.NO_ESCAPE_PARAMS)) {
                            String existingNoEscape = params.get(ToolUtils.NO_ESCAPE_PARAMS);
                            String historyNoEscape = historyParams.get(ToolUtils.NO_ESCAPE_PARAMS);
                            if (existingNoEscape != null && !existingNoEscape.isEmpty()) {
                                // Merge with existing NO_ESCAPE_PARAMS (avoid duplicates)
                                String merged = existingNoEscape + "," + historyNoEscape;
                                params.put(ToolUtils.NO_ESCAPE_PARAMS, merged);
                            } else {
                                params.put(ToolUtils.NO_ESCAPE_PARAMS, historyNoEscape);
                            }
                        }
                    }

                    continuation.run();
                } catch (Exception ex) {
                    log
                        .error(
                            "Failed during memory post-processing. agentId={}, tenantId={}",
                            params.get(AGENT_ID_LOG_FIELD),
                            mlAgent.getTenantId(),
                            ex
                        );
                    listener.onFailure(ex);
                }
            }, e -> {
                log
                    .error(
                        "Failed to save input messages. agentId={}, tenantId={}",
                        params.get(AGENT_ID_LOG_FIELD),
                        mlAgent.getTenantId(),
                        e
                    );
                listener.onFailure(e);
            }));
        }, e -> {
            log.error("Failed to get history. agentId={}, tenantId={}", params.get(AGENT_ID_LOG_FIELD), mlAgent.getTenantId(), e);
            listener.onFailure(e);
        }));
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
        HookRegistry hookRegistry,
        List<Message> inputMessages
    ) {
        MLAgentType agentType = MLAgentType.from(mlAgent.getType());

        String mcpConnectorConfigJSON = (mlAgent.getParameters() != null) ? mlAgent.getParameters().get(MCP_CONNECTORS_FIELD) : null;
        if (mcpConnectorConfigJSON != null && !mlFeatureEnabledSetting.isMcpConnectorEnabled()) {
            // MCP connector provided as tools but MCP feature is disabled, so abort.
            logAgentExecutionFailure(
                mlAgent.getType(),
                inputDataSet.getParameters().get(AGENT_ID_LOG_FIELD),
                tenantId,
                0,
                String.valueOf(RestStatus.FORBIDDEN.getStatus())
            );
            listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE));
            return;
        }

        // V2 agents follow pure message-centric execution path
        // TODO: Refactor to separate MLAgentExecutorV2 class for cleaner separation
        if (agentType.isV2() && inputMessages != null && memory != null) {
            executeV2Agent(inputDataSet, tenantId, mlTask, isAsync, mlAgent, listener, memory, channel, hookRegistry, inputMessages);
            return;
        }

        // Check for agent-level context management configuration (following connector
        // pattern)
        if (mlAgent.hasContextManagement()) {
            processContextManagement(mlAgent, hookRegistry, inputDataSet);
        }

        MLAgentRunner mlAgentRunner = getAgentRunner(mlAgent, hookRegistry);
        String parentInteractionId = inputDataSet.getParameters().get(PARENT_INTERACTION_ID);

        boolean usesUnifiedInterface = "true".equals(inputDataSet.getParameters().get(USES_UNIFIED_INTERFACE));

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
                    mlAgent.getType(),
                    inputDataSet.getParameters().get(AGENT_ID_LOG_FIELD),
                    tenantId,
                    outputs,
                    modelTensors,
                    parentInteractionId,
                    memory
                );
                inputDataSet.getParameters().put(TASK_ID_FIELD, taskId);
                try {
                    if (usesUnifiedInterface && inputMessages != null && memory != null && supportsStructuredMessages(mlAgent)) {
                        performInitialMemoryOperations(
                            memory,
                            inputMessages,
                            inputDataSet.getParameters(),
                            mlAgent,
                            agentActionListener,
                            () -> {
                                mlAgentRunner.run(mlAgent, inputDataSet.getParameters(), agentActionListener, channel, memory);
                            },
                            hookRegistry
                        );
                    } else {
                        mlAgentRunner.run(mlAgent, inputDataSet.getParameters(), agentActionListener, channel);
                    }
                } catch (Exception e) {
                    log
                        .error(
                            "Failed to run agent. agentId={}, tenantId={}, statusCode={}",
                            inputDataSet.getParameters().get(AGENT_ID_LOG_FIELD),
                            tenantId,
                            extractStatusCode(e),
                            e
                        );
                    agentActionListener.onFailure(e);
                }
            }, e -> {
                log
                    .error(
                        "Failed to create task for agent async execution. agentId={}, tenantId={}",
                        inputDataSet.getParameters().get(AGENT_ID_LOG_FIELD),
                        tenantId,
                        e
                    );
                logAgentExecutionFailure(
                    mlAgent.getType(),
                    inputDataSet.getParameters().get(AGENT_ID_LOG_FIELD),
                    tenantId,
                    0,
                    extractStatusCode(e)
                );
                listener.onFailure(e);
            }));
        } else {
            ActionListener<Object> agentActionListener = createAgentActionListener(
                listener,
                outputs,
                modelTensors,
                mlAgent.getType(),
                inputDataSet.getParameters().get(AGENT_ID_LOG_FIELD),
                tenantId,
                parentInteractionId,
                memory
            );
            try {
                if (usesUnifiedInterface && inputMessages != null && memory != null && supportsStructuredMessages(mlAgent)) {
                    performInitialMemoryOperations(
                        memory,
                        inputMessages,
                        inputDataSet.getParameters(),
                        mlAgent,
                        agentActionListener,
                        () -> {
                            mlAgentRunner.run(mlAgent, inputDataSet.getParameters(), agentActionListener, channel, memory);
                        },
                        hookRegistry
                    );
                } else {
                    mlAgentRunner.run(mlAgent, inputDataSet.getParameters(), agentActionListener, channel);
                }
            } catch (Exception e) {
                log
                    .error(
                        "Failed to run agent. agentId={}, tenantId={}, statusCode={}",
                        inputDataSet.getParameters().get(AGENT_ID_LOG_FIELD),
                        tenantId,
                        extractStatusCode(e),
                        e
                    );
                agentActionListener.onFailure(e);
            }
        }
    }

    /**
     * Execute V2 agent with pure message-centric flow.
     * V2 agents work exclusively with List<Message> objects.
     * Executor handles memory operations (fetch history, save input, apply limits).
     * Runner receives complete conversation and executes agent logic.
     *
     * TODO: Extract to separate MLAgentExecutorV2 class for cleaner separation
     */
    private void executeV2Agent(
        RemoteInferenceInputDataSet inputDataSet,
        String tenantId,
        MLTask mlTask,
        boolean isAsync,
        MLAgent mlAgent,
        ActionListener<Output> listener,
        Memory memory,
        TransportChannel channel,
        HookRegistry hookRegistry,
        List<Message> inputMessages
    ) {
        String agentId = inputDataSet.getParameters().get(AGENT_ID_LOG_FIELD);
        Map<String, String> params = new HashMap<>(inputDataSet.getParameters());

        // Get runner (routes to MLChatAgentRunnerV2Refactored for CONVERSATIONAL_V2)
        MLAgentRunner mlAgentRunner = getAgentRunner(mlAgent, hookRegistry);

        // Executor handles memory operations (fetch history, save input, apply limits)
        // Then passes complete conversation to runner for execution
        int messageHistoryLimit = getMessageHistoryLimit(params);

        // TODO: Scalability improvement - getStructuredMessages fetches ALL messages then slices in-memory.
        // For conversations with thousands of messages, this is inefficient. Should add Memory.getStructuredMessages(limit)
        // to push limit down to database query level (e.g., OpenSearch query with size parameter).
        memory.getStructuredMessages(ActionListener.wrap(historyObj -> {
            @SuppressWarnings("unchecked")
            List<Message> allHistory = (List<Message>) historyObj;

            // Apply history limit (window-based: keep last N messages)
            List<Message> history = messageHistoryLimit > 0 && allHistory.size() > messageHistoryLimit
                ? allHistory.subList(allHistory.size() - messageHistoryLimit, allHistory.size())
                : allHistory;

            log.debug("Retrieved {} messages from history (limit={}). agentId={}", history.size(), messageHistoryLimit, agentId);

            // Save input messages to memory
            memory.saveStructuredMessages(inputMessages, ActionListener.wrap(v -> {
                log.debug("Saved {} input messages to memory. agentId={}", inputMessages.size(), agentId);

                // Build full conversation (history + new input)
                List<Message> fullConversation = new ArrayList<>(history);
                fullConversation.addAll(inputMessages);

                log.debug("Full conversation has {} messages. agentId={}", fullConversation.size(), agentId);

                if (isAsync) {
                    // Async execution: index task, run in background
                    Map<String, Object> agentResponse = new HashMap<>();
                    agentResponse.put(MEMORY_ID, memory.getId());
                    mlTask.setResponse(agentResponse);
                    mlTask.setAsync(true);

                    indexMLTask(mlTask, ActionListener.wrap(indexResponse -> {
                        String taskId = indexResponse.getId();
                        mlTask.setTaskId(taskId);
                        params.put(TASK_ID_FIELD, taskId);

                        MLTaskOutput outputBuilder = MLTaskOutput
                            .builder()
                            .taskId(taskId)
                            .status(MLTaskState.RUNNING.toString())
                            .response(agentResponse)
                            .build();
                        listener.onResponse(outputBuilder);

                        ActionListener<Object> taskUpdater = createV2AsyncTaskUpdater(mlTask, mlAgent.getType(), agentId, tenantId);

                        try {
                            mlAgentRunner.runV2(mlAgent, params, taskUpdater, channel, memory, fullConversation);
                        } catch (Exception e) {
                            log.error("Agent execution failed. agentId={}, tenantId={}", agentId, tenantId, e);
                            taskUpdater.onFailure(e);
                        }
                    }, listener::onFailure));
                } else {
                    // Sync execution
                    ActionListener<Object> agentActionListener = createV2AgentActionListener(
                        listener,
                        mlAgent.getType(),
                        agentId,
                        tenantId
                    );

                    try {
                        mlAgentRunner.runV2(mlAgent, params, agentActionListener, channel, memory, fullConversation);
                    } catch (Exception e) {
                        log.error("Agent execution failed. agentId={}, tenantId={}", agentId, tenantId, e);
                        agentActionListener.onFailure(e);
                    }
                }
            }, e -> {
                log.error("Failed to save input messages. agentId={}, tenantId={}", agentId, tenantId, e);
                listener.onFailure(e);
            }));
        }, e -> {
            log.error("Failed to retrieve message history. agentId={}, tenantId={}", agentId, tenantId, e);
            listener.onFailure(e);
        }));
    }

    @SuppressWarnings("removal")
    private ActionListener<Object> createAgentActionListener(
        ActionListener<Output> listener,
        List<ModelTensors> outputs,
        List<ModelTensor> modelTensors,
        String agentType,
        String agentId,
        String tenantId,
        String parentInteractionId,
        Memory memory
    ) {
        long startTime = System.currentTimeMillis();
        return ActionListener.wrap(output -> {
            long latencyMs = System.currentTimeMillis() - startTime;
            log
                .info(
                    "Agent execution completed successfully. agentType={}, agentId={}, tenantId={}, latencyMs={}",
                    agentType,
                    agentId,
                    tenantId,
                    latencyMs
                );
            logAgentExecutionLatency(agentType, agentId, tenantId, latencyMs);
            if (output != null) {
                if (output instanceof AgentV2Output) {
                    listener.onResponse((AgentV2Output) output);
                } else {
                    processOutput(output, modelTensors);
                    listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(outputs).build());
                }
            } else {
                log.debug("Agent output is null, returning null response");
                listener.onResponse(null);
            }
        }, ex -> {
            long latencyMs = System.currentTimeMillis() - startTime;
            String statusCode = extractStatusCode(ex);
            // CENTRAL AGENT FAILURE LOG - All agent execution failures flow through here
            log
                .error(
                    "Agent execution failed. agentType={}, agentId={}, tenantId={}, latencyMs={}, statusCode={}, error={}",
                    agentType,
                    agentId,
                    tenantId,
                    latencyMs,
                    statusCode,
                    ex.getMessage(),
                    ex
                );
            logAgentExecutionFailure(agentType, agentId, tenantId, latencyMs, statusCode);
            updateInteractionWithFailure(parentInteractionId, memory, ex.getMessage());
            listener.onFailure(ex);
        });
    }

    /**
     * Create action listener for V2 agents (sync execution).
     * V2 agents return AgentV2Output directly without ModelTensorOutput wrapping.
     */
    private ActionListener<Object> createV2AgentActionListener(
        ActionListener<Output> listener,
        String agentType,
        String agentId,
        String tenantId
    ) {
        long startTime = System.currentTimeMillis();
        return ActionListener.wrap(output -> {
            long latencyMs = System.currentTimeMillis() - startTime;
            log
                .info(
                    "Agent execution completed. agentType={}, agentId={}, tenantId={}, latencyMs={}",
                    agentType,
                    agentId,
                    tenantId,
                    latencyMs
                );

            if (output instanceof AgentV2Output) {
                listener.onResponse((AgentV2Output) output);
            } else {
                String errorMsg = "V2 agent must return AgentV2Output but returned: "
                    + (output != null ? output.getClass().getName() : "null");
                log.error(errorMsg);
                listener.onFailure(new IllegalStateException(errorMsg));
            }
        }, ex -> {
            long latencyMs = System.currentTimeMillis() - startTime;
            String statusCode = extractStatusCode(ex);
            log
                .error(
                    "Agent execution failed. agentType={}, agentId={}, tenantId={}, latencyMs={}, statusCode={}",
                    agentType,
                    agentId,
                    tenantId,
                    latencyMs,
                    statusCode,
                    ex
                );
            listener.onFailure(ex);
        });
    }

    private ActionListener<Object> createAsyncTaskUpdater(
        MLTask mlTask,
        String agentType,
        String agentId,
        String tenantId,
        List<ModelTensors> outputs,
        List<ModelTensor> modelTensors,
        String parentInteractionId,
        Memory memory
    ) {
        long startTime = System.currentTimeMillis();
        String taskId = mlTask.getTaskId();
        Map<String, Object> agentResponse = new HashMap<>();
        Map<String, Object> updatedTask = new HashMap<>();

        return ActionListener.wrap(output -> {
            long latencyMs = System.currentTimeMillis() - startTime;
            log
                .info(
                    "Agent execution completed successfully. agentType={}, agentId={}, tenantId={}, latencyMs={}, taskId={}",
                    agentType,
                    agentId,
                    tenantId,
                    latencyMs,
                    taskId
                );
            logAgentExecutionLatency(agentType, agentId, tenantId, latencyMs, taskId);
            if (output != null) {
                if (output instanceof AgentV2Output) {
                    AgentV2Output v2Output = (AgentV2Output) output;
                    if (v2Output.getStopReason() != null) {
                        agentResponse.put(AgentV2Output.STOP_REASON_FIELD, v2Output.getStopReason());
                    }
                    if (v2Output.getMessage() != null) {
                        agentResponse.put(AgentV2Output.MESSAGE_FIELD, v2Output.getMessage());
                    }
                    if (v2Output.getMemoryId() != null) {
                        agentResponse.put(AgentV2Output.MEMORY_ID_FIELD, v2Output.getMemoryId());
                    }
                    if (v2Output.getMetrics() != null && !v2Output.getMetrics().isEmpty()) {
                        agentResponse.put(AgentV2Output.METRICS_FIELD, v2Output.getMetrics());
                    }
                } else {
                    processOutput(output, modelTensors);
                    agentResponse.put(INFERENCE_RESULT_FIELD, outputs);
                }
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
            long latencyMs = System.currentTimeMillis() - startTime;
            String statusCode = extractStatusCode(ex);
            // CENTRAL AGENT FAILURE LOG - All async agent execution failures flow through here
            log
                .error(
                    "Agent execution failed. agentType={}, agentId={}, tenantId={}, latencyMs={}, statusCode={}, error={}",
                    agentType,
                    agentId,
                    tenantId,
                    latencyMs,
                    statusCode,
                    ex.getMessage(),
                    ex
                );
            logAgentExecutionFailure(agentType, agentId, tenantId, latencyMs, statusCode);
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

    /**
     * Create action listener for V2 agents (async execution).
     * Updates ML task with AgentV2Output when complete.
     */
    private ActionListener<Object> createV2AsyncTaskUpdater(MLTask mlTask, String agentType, String agentId, String tenantId) {
        long startTime = System.currentTimeMillis();
        String taskId = mlTask.getTaskId();
        Map<String, Object> agentResponse = new HashMap<>();
        Map<String, Object> updatedTask = new HashMap<>();

        return ActionListener.wrap(output -> {
            long latencyMs = System.currentTimeMillis() - startTime;
            log
                .info(
                    "Agent async execution completed. agentType={}, agentId={}, tenantId={}, latencyMs={}, taskId={}",
                    agentType,
                    agentId,
                    tenantId,
                    latencyMs,
                    taskId
                );

            if (output instanceof AgentV2Output) {
                AgentV2Output v2Output = (AgentV2Output) output;
                if (v2Output.getStopReason() != null) {
                    agentResponse.put(AgentV2Output.STOP_REASON_FIELD, v2Output.getStopReason());
                }
                if (v2Output.getMessage() != null) {
                    agentResponse.put(AgentV2Output.MESSAGE_FIELD, v2Output.getMessage());
                }
                if (v2Output.getMemoryId() != null) {
                    agentResponse.put(AgentV2Output.MEMORY_ID_FIELD, v2Output.getMemoryId());
                }
                if (v2Output.getMetrics() != null && !v2Output.getMetrics().isEmpty()) {
                    agentResponse.put(AgentV2Output.METRICS_FIELD, v2Output.getMetrics());
                }

                mlTask.setResponse(agentResponse);
                updatedTask.put(RESPONSE_FIELD, agentResponse);
                updatedTask.put(STATE_FIELD, MLTaskState.COMPLETED);
            } else {
                // Programming error: V2 agent must return AgentV2Output
                String errorMsg = "V2 agent must return AgentV2Output but returned: "
                    + (output != null ? output.getClass().getName() : "null");
                log.error(errorMsg);
                agentResponse.put(ERROR_MESSAGE, errorMsg);
                mlTask.setResponse(agentResponse);
                updatedTask.put(RESPONSE_FIELD, agentResponse);
                updatedTask.put(STATE_FIELD, MLTaskState.FAILED);
            }

            updateMLTaskDirectly(
                taskId,
                tenantId,
                updatedTask,
                client,
                sdkClient,
                ActionListener
                    .wrap(
                        response -> log.info("Updated ML task {} with agent results", taskId),
                        e -> log.error("Failed to update ML task {} with agent results", taskId, e)
                    )
            );
        }, ex -> {
            long latencyMs = System.currentTimeMillis() - startTime;
            String statusCode = extractStatusCode(ex);
            log
                .error(
                    "Agent async execution failed. agentType={}, agentId={}, tenantId={}, latencyMs={}, statusCode={}",
                    agentType,
                    agentId,
                    tenantId,
                    latencyMs,
                    statusCode,
                    ex
                );

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
                        response -> log.info("Updated ML task {} with agent failure", taskId),
                        e -> log.error("Failed to update ML task {} with failure", taskId, e)
                    )
            );
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
                    encryptor,
                    hookRegistry
                );
            case CONVERSATIONAL_V2:
                return new MLChatAgentRunnerV2(client, settings, clusterService, xContentRegistry, toolFactories, sdkClient, encryptor);
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
        if (!mlAgent.usesUnifiedInterface() && agentType != MLAgentType.AG_UI) {
            return;
        }

        if (agentMLInput.getInputDataset() == null) {
            agentMLInput.setInputDataset(new RemoteInferenceInputDataSet(new HashMap<>()));
        }

        // No processing required for V2, native support for Messages
        if (agentType.isV2()) {
            return;
        }

        // Validate V1 agents with unified interface only support TEXT input (fail early)
        if (agentMLInput.getAgentInput() != null) {
            validateV1MultiModalInput(mlAgent, agentMLInput.getAgentInput().getInputType());
        }

        // AGUI history-load: no question extraction needed, MLAGUIAgentRunner handles it
        if (agentMLInput.getInputDataset() instanceof RemoteInferenceInputDataSet) {
            RemoteInferenceInputDataSet ds = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
            if (ds.getParameters() != null && "true".equals(ds.getParameters().get(AGUI_PARAM_LOAD_CHAT_HISTORY))) {
                return;
            }
        }

        if (agentMLInput.getAgentInput() != null
            && agentMLInput.getAgentInput().getInputType() == InputType.MESSAGES
            && MLAgentType.from(mlAgent.getType()) == MLAgentType.PLAN_EXECUTE_AND_REFLECT) {
            throw new IllegalArgumentException("Messages input is not supported for Plan Execute and Reflect Agent.");
        }

        // If legacy question input is provided and no structured input exists, parse to new standard input
        if (agentMLInput.getAgentInput() == null && agentMLInput.getInputDataset() != null) {
            RemoteInferenceInputDataSet remoteInferenceInputDataSet = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
            if (remoteInferenceInputDataSet.getParameters().containsKey(QUESTION)) {
                AgentInput standardInput = new AgentInput(remoteInferenceInputDataSet.getParameters().get(QUESTION));
                agentMLInput.setAgentInput(standardInput);
            }
        }

        try {
            // Set parameters to processed params
            RemoteInferenceInputDataSet remoteDataSet = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();

            // For agent with revamped interface, use ModelProvider to map the entire AgentInput
            String question = null;
            if (mlAgent.usesUnifiedInterface()) {
                remoteDataSet.getParameters().put(USES_UNIFIED_INTERFACE, "true");
                ModelProvider modelProvider = ModelProviderFactory.getProvider(mlAgent.getModel().getModelProvider());
                Map<String, String> parameters = modelProvider.mapAgentInput(agentMLInput.getAgentInput(), agentType);

                // Extract question text for prompt template usage
                // Both V1 and V2 unified agents support all input types (TEXT, CONTENT_BLOCKS, MESSAGES)
                // through ModelProvider.mapAgentInput(), so use AgentInputProcessor to extract text
                question = AgentInputProcessor.extractQuestionText(agentMLInput.getAgentInput());
                parameters.put(QUESTION, question);

                remoteDataSet.getParameters().putAll(parameters);
            } else {
                // For old-style AG_UI agents without model field
                // Extract question text from AgentInput
                question = AgentInputProcessor.extractQuestionText(agentMLInput.getAgentInput());

                // Prepend context to question if available
                if (agentType == MLAgentType.AG_UI) {
                    String context = remoteDataSet.getParameters().get(AGUI_PARAM_CONTEXT);
                    if (context != null && !context.isEmpty()) {
                        question = "Context: " + context + "\nQuestion: " + question;
                    }
                }
                remoteDataSet.getParameters().putIfAbsent(QUESTION, question);
            }
        } catch (Exception e) {
            log.error("Failed to process standardized input for agent {}", mlAgent.getName(), e);
            throw new IllegalArgumentException("Failed to process standardized agent input: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that V1 agents using unified interface only support TEXT input.
     * V2 agents and AG_UI agents support all input types (TEXT, CONTENT_BLOCKS, MESSAGES).
     * This validation is performed early in processAgentInput() to fail fast.
     *
     * @param mlAgent the agent being executed
     * @param inputType the input type provided
     * @throws IllegalArgumentException if V1 agent (except AG_UI) with unified interface receives non-TEXT input
     */
    private void validateV1MultiModalInput(MLAgent mlAgent, InputType inputType) {
        MLAgentType agentType = MLAgentType.from(mlAgent.getType());

        // V2 agents support all input types
        if (agentType.isV2()) {
            return;
        }

        // Non-unified interface agents don't need validation
        if (!mlAgent.usesUnifiedInterface()) {
            return;
        }

        // AG_UI agents support all input types for UI interactions
        if (agentType == MLAgentType.AG_UI) {
            return;
        }

        // V1 agents (except AG_UI) with unified interface only support TEXT input
        if (inputType == InputType.CONTENT_BLOCKS || inputType == InputType.MESSAGES) {
            throw new IllegalArgumentException(
                String
                    .format(
                        "V1 agents with unified agent interface only support TEXT input type. "
                            + "Found input type: %s. Please use TEXT input (e.g., {\"input\": \"your text here\"}), "
                            + "or upgrade to CONVERSATIONAL_V2 agent type for multi-modal support (CONTENT_BLOCKS, MESSAGES).",
                        inputType
                    )
            );
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

    @VisibleForTesting
    boolean supportsStructuredMessages(MLAgent mlAgent) {
        MLAgentType agentType = MLAgentType.from(mlAgent.getType());
        return agentType == MLAgentType.CONVERSATIONAL || agentType == MLAgentType.CONVERSATIONAL_V2 || agentType == MLAgentType.AG_UI;
    }

    /**
     * Process POST_MEMORY hook for structured messages and return the (potentially modified) list.
     * Context managers like SlidingWindowManager or SummarizationManager can modify
     * the retrieved structured chat history before it is formatted into the prompt.
     */
    private List<Message> processPostStructuredMemoryHook(
        Map<String, String> params,
        List<Message> retrievedStructuredHistory,
        Memory memory,
        HookRegistry hookRegistry
    ) {
        log
            .debug(
                "processPostStructuredMemoryHook called with {} messages, hookRegistry: {}",
                retrievedStructuredHistory.size(),
                hookRegistry != null ? "present" : "null"
            );

        if (hookRegistry != null && !retrievedStructuredHistory.isEmpty()) {
            int originalSize = retrievedStructuredHistory.size();
            ContextManagerContext contextAfterEvent = AgentContextUtil
                .emitPostStructuredMemoryHook(params, retrievedStructuredHistory, null, hookRegistry);

            log.debug("POST_MEMORY hook emitted, estimated token count: {}", contextAfterEvent.getEstimatedTokenCount());

            List<Message> updatedHistory = contextAfterEvent.getStructuredChatHistory();
            // Use size comparison — more robust than List.equals() which relies on
            // Message.equals() and can miss in-place mutations of shared references.
            if (updatedHistory != null && updatedHistory.size() != originalSize) {
                log.info("POST_MEMORY hook modified structured history: {} -> {} messages", originalSize, updatedHistory.size());
                return updatedHistory;
            } else {
                log.debug("POST_MEMORY hook did not modify structured history");
            }
        }
        return retrievedStructuredHistory;
    }

}
