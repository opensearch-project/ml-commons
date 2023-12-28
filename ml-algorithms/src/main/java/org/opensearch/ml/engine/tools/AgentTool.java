/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.spi.tools.AbstractTool;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

/**
 * This tool supports running any Agent.
 */
@Log4j2
@ToolAnnotation(AgentTool.TYPE)
public class AgentTool extends AbstractTool {
    public static final String TYPE = "AgentTool";
    private final Client client;

    private String agentId;

    @VisibleForTesting
    static String DEFAULT_DESCRIPTION = "Use this tool to run any agent.";

    public AgentTool(@NonNull Client client, @NonNull String agentId) {
        super(TYPE, DEFAULT_DESCRIPTION);
        this.client = client;
        this.agentId = agentId;
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        AgentMLInput agentMLInput = AgentMLInput
            .AgentMLInputBuilder()
            .agentId(agentId)
            .functionName(FunctionName.AGENT)
            .inputDataset(RemoteInferenceInputDataSet.builder().parameters(parameters).build())
            .build();
        ActionRequest request = new MLExecuteTaskRequest(FunctionName.AGENT, agentMLInput, false);
        client.execute(MLExecuteTaskAction.INSTANCE, request, ActionListener.wrap(r -> {
            ModelTensorOutput output = (ModelTensorOutput) r.getOutput();
            listener.onResponse((T) output);
        }, e -> {
            log.error("Failed to run agent " + agentId, e);
            listener.onFailure(e);
        }));

    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return true;
    }

    public static class Factory implements Tool.Factory<AgentTool> {
        public static final String AGENT_ID = "agent_id";
        private Client client;

        private static Factory INSTANCE;

        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (AgentTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new Factory();
                return INSTANCE;
            }
        }

        public void init(@NonNull Client client) {
            this.client = client;
        }

        @Override
        public AgentTool create(@NonNull Map<String, Object> params) {
            if (!params.containsKey(AGENT_ID) || params.get(AGENT_ID) == null) {
                throw new IllegalArgumentException("Agent ID is required");
            }
            return new AgentTool(client, (String) params.get(AGENT_ID));
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }
}
