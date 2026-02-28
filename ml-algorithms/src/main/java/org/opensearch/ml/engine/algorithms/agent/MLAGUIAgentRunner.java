/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.CommonValue.ENDPOINT_FIELD;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_LOAD_CHAT_HISTORY;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.createMemoryParams;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMessageHistoryLimit;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.sanitizeForLogging;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;

import java.util.List;
import java.util.Map;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLMemoryType;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.hooks.HookRegistry;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.function_calling.FunctionCalling;
import org.opensearch.ml.engine.function_calling.FunctionCallingFactory;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLAGUIAgentRunner implements MLAgentRunner {

    private final Client client;
    private final Settings settings;
    private final ClusterService clusterService;
    private final NamedXContentRegistry xContentRegistry;
    private final Map<String, Tool.Factory> toolFactories;
    private final Map<String, Memory.Factory> memoryFactoryMap;
    private final SdkClient sdkClient;
    private final Encryptor encryptor;
    private final HookRegistry hookRegistry;

    public MLAGUIAgentRunner(
        Client client,
        Settings settings,
        ClusterService clusterService,
        NamedXContentRegistry xContentRegistry,
        Map<String, Tool.Factory> toolFactories,
        Map<String, Memory.Factory> memoryFactoryMap,
        SdkClient sdkClient,
        Encryptor encryptor
    ) {
        this(client, settings, clusterService, xContentRegistry, toolFactories, memoryFactoryMap, sdkClient, encryptor, null);
    }

    public MLAGUIAgentRunner(
        Client client,
        Settings settings,
        ClusterService clusterService,
        NamedXContentRegistry xContentRegistry,
        Map<String, Tool.Factory> toolFactories,
        Map<String, Memory.Factory> memoryFactoryMap,
        SdkClient sdkClient,
        Encryptor encryptor,
        HookRegistry hookRegistry
    ) {
        this.client = client;
        this.settings = settings;
        this.clusterService = clusterService;
        this.xContentRegistry = xContentRegistry;
        this.toolFactories = toolFactories;
        this.memoryFactoryMap = memoryFactoryMap;
        this.sdkClient = sdkClient;
        this.encryptor = encryptor;
        this.hookRegistry = hookRegistry;
    }

    @Override
    public void run(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener, TransportChannel channel) {
        run(mlAgent, params, listener, channel, null);
    }

    @Override
    public void run(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener, TransportChannel channel, Memory memory) {
        try {
            String llmInterface = params.get(LLM_INTERFACE);
            if (llmInterface == null && mlAgent.getParameters() != null) {
                llmInterface = mlAgent.getParameters().get(LLM_INTERFACE);
            }

            FunctionCalling functionCalling = FunctionCallingFactory.create(llmInterface);
            if (functionCalling != null) {
                functionCalling.configure(params);
            }

            params.put("agent_type", "ag_ui");

            // Handle AGUI history-load: empty messages means "load previous conversation"
            if ("true".equals(params.get(AGUI_PARAM_LOAD_CHAT_HISTORY))) {
                handleHistoryLoad(mlAgent, params, listener, channel);
                return;
            }

            MLAgentRunner conversationalRunner = new MLChatAgentRunner(
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

            // Execute with streaming - events are generated in RestMLExecuteStreamAction
            conversationalRunner.run(mlAgent, params, listener, channel, memory);

        } catch (Exception e) {
            log.error("Error starting AG-UI agent execution", e);
            listener.onFailure(e);
        }
    }

    private void handleHistoryLoad(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener, TransportChannel channel) {
        StreamingWrapper streamingWrapper = new StreamingWrapper(channel, client, params);

        if (mlAgent.getMemory() == null || memoryFactoryMap == null || memoryFactoryMap.isEmpty()) {
            // No memory configured, send empty snapshot
            streamingWrapper.sendMessagesSnapshot(List.of(), null, listener);
            return;
        }

        int messageHistoryLimit = getMessageHistoryLimit(params);
        String memoryType = MLMemoryType.from(mlAgent.getMemory().getType()).name();
        String memoryId = params.get(MLAgentExecutor.MEMORY_ID);
        String appType = mlAgent.getAppType();
        String title = params.get(MLAgentExecutor.QUESTION);

        Map<String, Object> memoryParams = createMemoryParams(title, memoryId, appType, mlAgent, params);
        log.debug("MLAGUIAgentRunner history-load setting up memory, params: {}", sanitizeForLogging(memoryParams));

        Memory.Factory<Memory<Interaction, ?, ?>> memoryFactory;
        if (memoryParams != null && memoryParams.containsKey(ENDPOINT_FIELD)) {
            memoryFactory = memoryFactoryMap.get(MLMemoryType.REMOTE_AGENTIC_MEMORY.name());
        } else {
            memoryFactory = memoryFactoryMap.get(memoryType);
        }

        if (memoryFactory == null) {
            listener.onFailure(new IllegalArgumentException("Memory factory not found for type: " + memoryType));
            return;
        }

        memoryFactory.create(memoryParams, ActionListener.wrap(memory -> {
            memory.getStructuredMessages(ActionListener.wrap(allMessages -> {
                List<Message> history = messageHistoryLimit > 0 && allMessages.size() > messageHistoryLimit
                    ? allMessages.subList(allMessages.size() - messageHistoryLimit, allMessages.size())
                    : allMessages;
                streamingWrapper.sendMessagesSnapshot(history, memory.getId(), listener);
            }, e -> {
                log.error("Failed to load history for AGUI snapshot", e);
                listener.onFailure(e);
            }));
        }, listener::onFailure));
    }
}
