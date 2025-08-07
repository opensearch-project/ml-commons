/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;

import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.transport.client.Client;

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

    @Setter
    private String agentId;
    @Setter
    @Getter
    private String name = TYPE;

    @VisibleForTesting
    static String DEFAULT_DESCRIPTION = "Use this tool to run any agent.";
    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;

    @Getter
    @Setter
    private Map<String, Object> attributes;

    public AgentTool(Client client, String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("Agent ID cannot be null or empty");
        }

        this.client = client;
        this.agentId = agentId;
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        try {
            if (agentId == null || agentId.isBlank()) {
                throw new IllegalArgumentException("Agent ID not registered in tool");
            }
            Map<String, String> extractedParameters = ToolUtils.extractInputParameters(parameters, attributes);
            String tenantId = parameters.get(TENANT_ID_FIELD);
            AgentMLInput agentMLInput = AgentMLInput
                .AgentMLInputBuilder()
                .agentId(agentId)
                .tenantId(tenantId)
                .functionName(FunctionName.AGENT)
                .inputDataset(RemoteInferenceInputDataSet.builder().parameters(extractedParameters).build())
                .build();
            ActionRequest request = new MLExecuteTaskRequest(FunctionName.AGENT, agentMLInput, false);
            client.execute(MLExecuteTaskAction.INSTANCE, request, ActionListener.wrap(r -> {
                ModelTensorOutput output = (ModelTensorOutput) r.getOutput();
                listener.onResponse((T) output);
            }, e -> {
                log.error("Failed to run agent " + agentId, e);
                listener.onFailure(e);
            }));
        } catch (Exception e) {
            log.error("Failed to run AgentTool with agent: {}", agentId, e);
            listener.onFailure(e);
        }
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
