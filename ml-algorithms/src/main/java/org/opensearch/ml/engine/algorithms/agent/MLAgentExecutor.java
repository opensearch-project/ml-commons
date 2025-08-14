/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.client.Client;
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
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.SettingsChangeListener;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.Executable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.ConversationIndexMessage;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.memory.action.conversation.GetInteractionAction;
import org.opensearch.ml.memory.action.conversation.GetInteractionRequest;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;

import com.google.common.annotations.VisibleForTesting;
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

    private Client client;
    private SdkClient sdkClient;
    private Settings settings;
    private ClusterService clusterService;
    private NamedXContentRegistry xContentRegistry;
    private Map<String, Tool.Factory> toolFactories;
    private Map<String, Memory.Factory> memoryFactoryMap;
    private volatile Boolean isMultiTenancyEnabled;

    public MLAgentExecutor(
        Client client,
        SdkClient sdkClient,
        Settings settings,
        ClusterService clusterService,
        NamedXContentRegistry xContentRegistry,
        Map<String, Tool.Factory> toolFactories,
        Map<String, Memory.Factory> memoryFactoryMap,
        Boolean isMultiTenancyEnabled
    ) {
        this.client = client;
        this.sdkClient = sdkClient;
        this.settings = settings;
        this.clusterService = clusterService;
        this.xContentRegistry = xContentRegistry;
        this.toolFactories = toolFactories;
        this.memoryFactoryMap = memoryFactoryMap;
        this.isMultiTenancyEnabled = isMultiTenancyEnabled;
    }

    @Override
    public void onMultiTenancyEnabledChanged(boolean isEnabled) {
        this.isMultiTenancyEnabled = isEnabled;
    }

    @Override
    public void execute(Input input, ActionListener<Output> listener) {
        if (!(input instanceof AgentMLInput)) {
            throw new IllegalArgumentException("wrong input");
        }
        AgentMLInput agentMLInput = (AgentMLInput) input;
        String agentId = agentMLInput.getAgentId();
        String tenantId = agentMLInput.getTenantId();

        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        if (inputDataSet == null || inputDataSet.getParameters() == null) {
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

        if (MLIndicesHandler.doesMultiTenantIndexExist(clusterService, isMultiTenancyEnabled, ML_AGENT_INDEX)) {
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
                                        MLMemorySpec memorySpec = mlAgent.getMemory();
                                        String memoryId = inputDataSet.getParameters().get(MEMORY_ID);
                                        String parentInteractionId = inputDataSet.getParameters().get(PARENT_INTERACTION_ID);
                                        String regenerateInteractionId = inputDataSet.getParameters().get(REGENERATE_INTERACTION_ID);
                                        String appType = mlAgent.getAppType();
                                        String question = inputDataSet.getParameters().get(QUESTION);

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
                                                    ActionListener<Object> agentActionListener = createAgentActionListener(
                                                        listener,
                                                        outputs,
                                                        modelTensors,
                                                        mlAgent.getType()
                                                    );
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
                                                                        agentActionListener,
                                                                        memory,
                                                                        inputDataSet,
                                                                        mlAgent
                                                                    );
                                                                }, e -> {
                                                                    log.error("Failed to get existing interaction for regeneration", e);
                                                                    listener.onFailure(e);
                                                                })
                                                            );
                                                    } else {
                                                        saveRootInteractionAndExecute(agentActionListener, memory, inputDataSet, mlAgent);
                                                    }
                                                }, ex -> {
                                                    log.error("Failed to read conversation memory", ex);
                                                    listener.onFailure(ex);
                                                }));
                                        } else {
                                            ActionListener<Object> agentActionListener = createAgentActionListener(
                                                listener,
                                                outputs,
                                                modelTensors,
                                                mlAgent.getType()
                                            );
                                            executeAgent(inputDataSet, mlAgent, agentActionListener);
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
     */
    private void saveRootInteractionAndExecute(
        ActionListener<Object> listener,
        ConversationIndexMemory memory,
        RemoteInferenceInputDataSet inputDataSet,
        MLAgent mlAgent
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
                        ActionListener.wrap(deleted -> executeAgent(inputDataSet, mlAgent, listener), e -> {
                            log.error("Failed to regenerate for interaction {}", regenerateInteractionId, e);
                            listener.onFailure(e);
                        })
                    );
            } else {
                executeAgent(inputDataSet, mlAgent, listener);
            }
        }, ex -> {
            log.error("Failed to create parent interaction", ex);
            listener.onFailure(ex);
        }));
    }

    private void executeAgent(RemoteInferenceInputDataSet inputDataSet, MLAgent mlAgent, ActionListener<Object> agentActionListener) {
        MLAgentRunner mlAgentRunner = getAgentRunner(mlAgent);
        mlAgentRunner.run(mlAgent, inputDataSet.getParameters(), agentActionListener);
    }

    @SuppressWarnings("removal")
    private ActionListener<Object> createAgentActionListener(
        ActionListener<Output> listener,
        List<ModelTensors> outputs,
        List<ModelTensor> modelTensors,
        String agentType
    ) {
        return ActionListener.wrap(output -> {
            if (output != null) {
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
                listener.onResponse(ModelTensorOutput.builder().mlModelOutputs(outputs).build());
            } else {
                listener.onResponse(null);
            }
        }, ex -> {
            log.error("Failed to run {} agent", agentType, ex);
            listener.onFailure(ex);
        });
    }

    @VisibleForTesting
    protected MLAgentRunner getAgentRunner(MLAgent mlAgent) {
        final MLAgentType agentType = MLAgentType.from(mlAgent.getType().toUpperCase(Locale.ROOT));
        switch (agentType) {
            case FLOW:
                return new MLFlowAgentRunner(client, settings, clusterService, xContentRegistry, toolFactories, memoryFactoryMap);
            case CONVERSATIONAL_FLOW:
                return new MLConversationalFlowAgentRunner(
                    client,
                    settings,
                    clusterService,
                    xContentRegistry,
                    toolFactories,
                    memoryFactoryMap
                );
            case CONVERSATIONAL:
                return new MLChatAgentRunner(client, settings, clusterService, xContentRegistry, toolFactories, memoryFactoryMap);
            default:
                throw new IllegalArgumentException("Unsupported agent type: " + mlAgent.getType());
        }
    }
}
