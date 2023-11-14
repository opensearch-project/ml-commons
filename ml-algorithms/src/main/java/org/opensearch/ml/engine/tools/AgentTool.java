/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import lombok.extern.log4j.Log4j2;
import org.opensearch.action.ActionRequest;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.spi.tools.AbstractTool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;

import java.util.Map;

/**
 * This tool supports running any Agent.
 */
@Log4j2
@ToolAnnotation(AgentTool.TYPE)
public class AgentTool extends AbstractTool {
    public static final String TYPE = "AgentTool";
    private static String DEFAULT_DESCRIPTION = "Use this tool to run any agent.";
    private final Client client;

    private String agentId;

    public AgentTool(Client client, String agentId) {
        super(TYPE, DEFAULT_DESCRIPTION);
        this.client = client;
        this.agentId = agentId;
    }

    @Override
    public <T> void run(Map<String, String> toolSpec, Map<String, String> parameters, ActionListener<T> listener) {
        AgentMLInput agentMLInput = AgentMLInput.AgentMLInputBuilder().agentId(agentId).functionName(FunctionName.AGENT)
                .inputDataset(RemoteInferenceInputDataSet.builder().parameters(parameters).build()).build();
        ActionRequest request = new MLExecuteTaskRequest(FunctionName.AGENT, agentMLInput, false);
        client.execute(MLExecuteTaskAction.INSTANCE, request, ActionListener.wrap(r->{
            ModelTensorOutput output = (ModelTensorOutput) r.getOutput();
            listener.onResponse((T)output);
        }, e->{
            log.error("Failed to run agent " + agentId, e);
            listener.onFailure(e);
        }));

    }

    @Override
    public boolean validate(Map<String, String> toolSpec, Map<String, String> parameters) {
        return true;
    }
}