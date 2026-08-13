/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENT_MAX_CALL_DEPTH;

import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.utils.ToolUtils;
import org.opensearch.ml.engine.tools.parser.ToolParser;
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

    // Tracks AgentTool hops on the current chain to bound nested agent recursion.
    public static final String AGENT_CALL_DEPTH_FIELD = "_agent_call_depth";
    // Fallback when no ClusterService is wired (e.g. in unit tests that use init(Client)).
    public static final int DEFAULT_MAX_AGENT_CALL_DEPTH = 1;

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

    @Setter
    private Parser outputParser;

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

            int maxDepth = Factory.getInstance().getMaxCallDepth();
            int currentDepth = readDepth(parameters);
            if (currentDepth >= maxDepth) {
                listener
                    .onFailure(
                        new OpenSearchStatusException(
                            "AgentTool nested call depth exceeded the maximum ("
                                + maxDepth
                                + "). Check that your agent's tools do not transitively reference the agent itself.",
                            RestStatus.BAD_REQUEST
                        )
                    );
                return;
            }

            Map<String, String> extractedParameters = ToolUtils.extractInputParameters(parameters, attributes);
            // Propagate incremented depth to the nested agent so subsequent hops can see it.
            extractedParameters.put(AGENT_CALL_DEPTH_FIELD, String.valueOf(currentDepth + 1));
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
                listener.onResponse((T) outputParser.parse(output));
            }, e -> {
                log.error("Failed to run agent " + agentId, e);
                listener.onFailure(e);
            }));
        } catch (Exception e) {
            log.error("Failed to run AgentTool with agent: {}", agentId, e);
            listener.onFailure(e);
        }
    }

    private static int readDepth(Map<String, String> parameters) {
        if (parameters == null) {
            return 0;
        }
        String raw = parameters.get(AGENT_CALL_DEPTH_FIELD);
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            int depth = Integer.parseInt(raw);
            // Defensive: a negative value would let an attacker reset the counter.
            return Math.max(depth, 0);
        } catch (NumberFormatException e) {
            log.warn("Ignoring non-numeric {} value: {}", AGENT_CALL_DEPTH_FIELD, raw);
            return 0;
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
        // volatile so a setting update on any thread is visible to executor threads reading depth.
        private volatile int maxCallDepth = DEFAULT_MAX_AGENT_CALL_DEPTH;

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

        public void init(Client client, ClusterService clusterService) {
            this.client = client;
            if (clusterService != null) {
                this.maxCallDepth = ML_COMMONS_AGENT_MAX_CALL_DEPTH.get(clusterService.getSettings());
                clusterService
                    .getClusterSettings()
                    .addSettingsUpdateConsumer(ML_COMMONS_AGENT_MAX_CALL_DEPTH, it -> this.maxCallDepth = it);
            }
        }

        public int getMaxCallDepth() {
            return maxCallDepth;
        }

        @VisibleForTesting
        void setMaxCallDepth(int maxCallDepth) {
            this.maxCallDepth = maxCallDepth;
        }

        @Override
        public AgentTool create(Map<String, Object> params) {
            AgentTool agentTool = new AgentTool(client, (String) params.get("agent_id"));
            agentTool.setOutputParser(ToolParser.createFromToolParams(params));
            return agentTool;
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
