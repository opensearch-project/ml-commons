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
import static org.opensearch.ml.common.output.model.ModelTensorOutput.INFERENCE_RESULT_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.common.utils.MLTaskUtils.updateMLTaskDirectly;

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
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.agent.AgentInput;
import org.opensearch.ml.common.agent.AgentInputProcessor;
import org.opensearch.ml.common.agent.ContentBlock;
import org.opensearch.ml.common.agent.ContentType;
import org.opensearch.ml.common.agent.InputType;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.Message;
import org.opensearch.ml.common.agent.ModelProvider;
import org.opensearch.ml.common.agent.ModelProviderFactory;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.MLTaskOutput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.settings.SettingsChangeListener;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.Executable;
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
                                            && memoryFactoryMap.containsKey(memorySpec.getType())
                                            && (memoryId == null || parentInteractionId == null)) {
                                            ConversationIndexMemory.Factory conversationIndexMemoryFactory =
                                                (ConversationIndexMemory.Factory) memoryFactoryMap.get(memorySpec.getType());
                                            conversationIndexMemoryFactory
                                                .create(question, memoryId, appType, ActionListener.wrap(memory -> {
                                                    inputDataSet.getParameters().put(MEMORY_ID, memory.getConversationId());
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
                                                                        mlAgent,
                                                                        channel,
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
                                                            memory,
                                                            inputDataSet,
                                                            mlTask,
                                                            isAsync,
                                                            outputs,
                                                            modelTensors,
                                                            mlAgent,
                                                            channel,
                                                            agentMLInput
                                                        );
                                                    }
                                                }, ex -> {
                                                    log.error("Failed to read conversation memory", ex);
                                                    listener.onFailure(ex);
                                                }));
                                        } else {
                                            // For existing conversations, create memory instance using factory
                                            if (memorySpec != null && memorySpec.getType() != null) {
                                                ConversationIndexMemory.Factory factory = (ConversationIndexMemory.Factory) memoryFactoryMap
                                                    .get(memorySpec.getType());
                                                if (factory != null) {
                                                    // memoryId exists, so create returns an object with existing memory, therefore name can
                                                    // be null
                                                    factory
                                                        .create(
                                                            null,
                                                            memoryId,
                                                            appType,
                                                            ActionListener
                                                                .wrap(
                                                                    createdMemory -> executeAgent(
                                                                        inputDataSet,
                                                                        mlTask,
                                                                        isAsync,
                                                                        memoryId,
                                                                        mlAgent,
                                                                        outputs,
                                                                        modelTensors,
                                                                        listener,
                                                                        createdMemory,
                                                                        channel
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
                                                channel
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
     * @param listener callback listener
     * @param memory memory instance
     * @param inputDataSet input
     * @param mlAgent agent to run
     * @param agentMLInput the original agent input
     */
    private void saveRootInteractionAndExecute(
        ActionListener<Output> listener,
        ConversationIndexMemory memory,
        RemoteInferenceInputDataSet inputDataSet,
        MLTask mlTask,
        boolean isAsync,
        List<ModelTensors> outputs,
        List<ModelTensor> modelTensors,
        MLAgent mlAgent,
        TransportChannel channel,
        AgentMLInput agentMLInput
    ) {
        String appType = mlAgent.getAppType();
        String question = inputDataSet.getParameters().get(QUESTION);
        String regenerateInteractionId = inputDataSet.getParameters().get(REGENERATE_INTERACTION_ID);

        // Store message pairs if input is of type MESSAGES
        if (agentMLInput.getAgentInput() != null && agentMLInput.getAgentInput().getInputType() == InputType.MESSAGES) {
            storeMessagesInMemory(memory, (List<Message>) agentMLInput.getAgentInput().getInput(), appType);
        }

        // Create root interaction ID for the current question
        ConversationIndexMessage msg = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .type(appType)
            .question(question)
            .response("")
            .finalAnswer(true)
            .sessionId(memory.getConversationId())
            .build();
        memory.save(msg, null, null, null, ActionListener.<CreateInteractionResponse>wrap(interaction -> {
            log.info("Created parent interaction ID: {}", interaction.getId());
            inputDataSet.getParameters().put(PARENT_INTERACTION_ID, interaction.getId());
            // only delete previous interaction when new interaction created
            if (regenerateInteractionId != null) {
                memory
                    .getMemoryManager()
                    .deleteInteractionAndTrace(
                        regenerateInteractionId,
                        ActionListener
                            .wrap(
                                deleted -> executeAgent(
                                    inputDataSet,
                                    mlTask,
                                    isAsync,
                                    memory.getConversationId(),
                                    mlAgent,
                                    outputs,
                                    modelTensors,
                                    listener,
                                    memory,
                                    channel
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
                    memory.getConversationId(),
                    mlAgent,
                    outputs,
                    modelTensors,
                    listener,
                    memory,
                    channel
                );
            }
        }, ex -> {
            log.error("Failed to create parent interaction", ex);
            listener.onFailure(ex);
        }));
    }

    private void storeMessagesInMemory(ConversationIndexMemory memory, List<Message> messages, String appType) {
        // Process messages in pairs, excluding the last one
        for (int i = 0; i < messages.size() - 1; i += 2) {
            if (i + 1 < messages.size()) {
                Message userMessage = messages.get(i);
                Message assistantMessage = messages.get(i + 1);

                // Extract text from content blocks
                String userText = extractTextFromMessage(userMessage);
                String assistantText = extractTextFromMessage(assistantMessage);

                // Create completed interaction
                ConversationIndexMessage msg = ConversationIndexMessage
                    .conversationIndexMessageBuilder()
                    .type(appType)
                    .question(userText)
                    .response(assistantText)
                    .finalAnswer(true)
                    .sessionId(memory.getConversationId())
                    .build();

                // Save to memory
                memory
                    .save(
                        msg,
                        null,
                        null,
                        null,
                        ActionListener
                            .<CreateInteractionResponse>wrap(
                                interaction -> log.debug("Stored message pair in memory with interaction ID: {}", interaction.getId()),
                                ex -> log.error("Failed to store message pair in memory", ex)
                            )
                    );
            }
        }
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
        ConversationIndexMemory memory,
        TransportChannel channel
    ) {
        String mcpConnectorConfigJSON = (mlAgent.getParameters() != null) ? mlAgent.getParameters().get(MCP_CONNECTORS_FIELD) : null;
        if (mcpConnectorConfigJSON != null && !mlFeatureEnabledSetting.isMcpConnectorEnabled()) {
            // MCP connector provided as tools but MCP feature is disabled, so abort.
            listener.onFailure(new OpenSearchException(ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE));
            return;
        }

        MLAgentRunner mlAgentRunner = getAgentRunner(mlAgent);
        String parentInteractionId = inputDataSet.getParameters().get(PARENT_INTERACTION_ID);

        // If async is true, index ML task and return the taskID. Also add memoryID to the task if it exists
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
        ConversationIndexMemory memory
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
        ConversationIndexMemory memory
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
    protected MLAgentRunner getAgentRunner(MLAgent mlAgent) {
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
                    encryptor
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
                    encryptor
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

    private void updateInteractionWithFailure(String interactionId, ConversationIndexMemory memory, String errorMessage) {
        if (interactionId != null && memory != null) {
            String failureMessage = "Agent execution failed: " + errorMessage;
            Map<String, Object> updateContent = new HashMap<>();
            updateContent.put(RESPONSE_FIELD, failureMessage);

            memory
                .getMemoryManager()
                .updateInteraction(
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

    /**
     * Helper method to extract text from a message's content blocks.
     */
    private String extractTextFromMessage(Message message) {
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
