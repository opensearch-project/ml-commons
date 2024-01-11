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
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * This tool supports running any Agent.
 */
@Log4j2
@ToolAnnotation(AgentTool.TYPE)
public class AgentTool implements Tool {
    public static final String TYPE = "AgentTool";
    private final Client client;

    private String agentId;
    @Setter
    @Getter
    private String name = TYPE;

    // VisibleForTesting
    static String DEFAULT_DESCRIPTION = "Use this tool to run any agent.";
    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;

    public AgentTool(Client client, String agentId) {
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
    public String getType() {
        return TYPE;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String s) {
        this.name = s;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return true;
    }

    public static class Factory implements Tool.Factory<AgentTool> {
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

        public void init(Client client) {
            this.client = client;
        }

        @Override
        public AgentTool create(Map<String, Object> map) {
            return new AgentTool(client, (String) map.get("agent_id"));
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }

        @Override
        public String getDefaultType() {
            return TYPE;
        }

        @Override
        public String getDefaultVersion() {
            return null;
        }
    }
}
