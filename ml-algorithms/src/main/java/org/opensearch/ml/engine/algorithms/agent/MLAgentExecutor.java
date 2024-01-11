/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.get.GetRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.Executable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.ConversationIndexMessage;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.memory.action.conversation.GetInteractionAction;
import org.opensearch.ml.memory.action.conversation.GetInteractionRequest;

import com.google.gson.Gson;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Data
@NoArgsConstructor
@Function(FunctionName.AGENT)
public class MLAgentExecutor implements Executable {

    public static final String MEMORY_ID = "memory_id";
    public static final String QUESTION = "question";
    public static final String PARENT_INTERACTION_ID = "parent_interaction_id";
    public static final String REGENERATE_INTERACTION_ID = "regenerate_interaction_id";

    private Client client;
    private Settings settings;
    private ClusterService clusterService;
    private NamedXContentRegistry xContentRegistry;
    private Map<String, Tool.Factory> toolFactories;
    private Map<String, Memory.Factory> memoryFactoryMap;

    public MLAgentExecutor(
        Client client,
        Settings settings,
        ClusterService clusterService,
        NamedXContentRegistry xContentRegistry,
        Map<String, Tool.Factory> toolFactories,
        Map<String, Memory.Factory> memoryFactoryMap
    ) {
        this.client = client;
        this.settings = settings;
        this.clusterService = clusterService;
        this.xContentRegistry = xContentRegistry;
        this.toolFactories = toolFactories;
        this.memoryFactoryMap = memoryFactoryMap;
    }

    @Override
    public void execute(Input input, ActionListener<Output> listener) {
        if (!(input instanceof AgentMLInput)) {
            throw new IllegalArgumentException("wrong input");
        }
        AgentMLInput agentMLInput = (AgentMLInput) input;
        String agentId = agentMLInput.getAgentId();
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        if (inputDataSet.getParameters() == null) {
            throw new IllegalArgumentException("wrong input");
        }

        List<ModelTensors> outputs = new ArrayList<>();
        List<ModelTensor> modelTensors = new ArrayList<>();
        outputs.add(ModelTensors.builder().mlModelTensors(modelTensors).build());

        if (clusterService.state().metadata().hasIndex(ML_AGENT_INDEX)) {
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                GetRequest getRequest = new GetRequest(ML_AGENT_INDEX).id(agentId);
                client.get(getRequest, ActionListener.runBefore(ActionListener.wrap(r -> {
                    if (r.isExists()) {
                        try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                            MLAgent mlAgent = MLAgent.parse(parser);
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
                                conversationIndexMemoryFactory.create(question, memoryId, appType, ActionListener.wrap(memory -> {
                                    inputDataSet.getParameters().put(MEMORY_ID, memory.getConversationId());
                                    ActionListener<Object> agentActionListener = createAgentActionListener(listener, outputs, modelTensors);
                                    // get question for regenerate
                                    if (regenerateInteractionId != null) {
                                        log.info("Regenerate for existing interaction {}", regenerateInteractionId);
                                        client
                                            .execute(
                                                GetInteractionAction.INSTANCE,
                                                new GetInteractionRequest(memoryId, regenerateInteractionId),
                                                ActionListener.wrap(interactionRes -> {
                                                    inputDataSet
                                                        .getParameters()
                                                        .putIfAbsent(QUESTION, interactionRes.getInteraction().getInput());
                                                    saveRootInteractionAndExecute(agentActionListener, memory, inputDataSet, mlAgent);
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
                                ActionListener<Object> agentActionListener = createAgentActionListener(listener, outputs, modelTensors);
                                executeAgent(inputDataSet, mlAgent, agentActionListener);
                            }
                        }
                    } else {
                        listener.onFailure(new ResourceNotFoundException("Agent not found"));
                    }
                }, e -> {
                    log.error("Failed to get agent", e);
                    listener.onFailure(e);
                }), context::restore));
            }
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
            log.info("Created parent interaction ID: " + interaction.getId());
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

    private ActionListener<Object> createAgentActionListener(
        ActionListener<Output> listener,
        List<ModelTensors> outputs,
        List<ModelTensor> modelTensors
    ) {
        return ActionListener.wrap(output -> {
            if (output != null) {
                Gson gson = new Gson();
                if (output instanceof ModelTensorOutput) {
                    ModelTensorOutput modelTensorOutput = (ModelTensorOutput) output;
                    modelTensorOutput.getMlModelOutputs().forEach(outs -> {
                        for (ModelTensor mlModelTensor : outs.getMlModelTensors()) {
                            modelTensors.add(mlModelTensor);
                        }
                    });
                } else if (output instanceof ModelTensor) {
                    modelTensors.add((ModelTensor) output);
                } else if (output instanceof List) {
                    if (((List) output).get(0) instanceof ModelTensor) {
                        ((List<ModelTensor>) output).forEach(mlModelTensor -> modelTensors.add(mlModelTensor));
                    } else if (((List) output).get(0) instanceof ModelTensors) {
                        ((List<ModelTensors>) output).forEach(outs -> {
                            for (ModelTensor mlModelTensor : outs.getMlModelTensors()) {
                                modelTensors.add(mlModelTensor);
                            }
                        });
                    } else {
                        String result = output instanceof String
                            ? (String) output
                            : AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> gson.toJson(output));
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
            log.error("Failed to run flow agent", ex);
            listener.onFailure(ex);
        });
    }

    // VisibleForTesting
    protected MLAgentRunner getAgentRunner(MLAgent mlAgent) {
        switch (mlAgent.getType()) {
            case "flow":
                return new MLFlowAgentRunner(client, settings, clusterService, xContentRegistry, toolFactories, memoryFactoryMap);
            case "conversational":
                return new MLChatAgentRunner(client, settings, clusterService, xContentRegistry, toolFactories, memoryFactoryMap);
            default:
                throw new IllegalArgumentException("Unsupported agent type: " + mlAgent.getType());
        }
    }

    public XContentParser createXContentParserFromRegistry(NamedXContentRegistry xContentRegistry, BytesReference bytesReference)
        throws IOException {
        return XContentHelper.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, bytesReference, XContentType.JSON);
    }
}
