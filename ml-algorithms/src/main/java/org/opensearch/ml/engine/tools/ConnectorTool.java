/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import java.util.List;
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.transport.connector.MLExecuteConnectorAction;
import org.opensearch.ml.common.transport.connector.MLExecuteConnectorRequest;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * This tool supports running connector.
 */
@Log4j2
@ToolAnnotation(ConnectorTool.TYPE)
public class ConnectorTool implements Tool {
    public static final String TYPE = "ConnectorTool";
    public static final String CONNECTOR_ID = "connector_id";
    public static final String CONNECTOR_ACTION = "connector_action";

    @Setter
    @Getter
    private String name = ConnectorTool.TYPE;
    @Getter
    @Setter
    private String description = Factory.DEFAULT_DESCRIPTION;
    @Getter
    private String version;
    @Setter
    private Parser inputParser;
    @Setter
    private Parser outputParser;

    private Client client;
    private String connectorId;

    public ConnectorTool(Client client, String connectorId) {
        this.client = client;
        if (connectorId == null) {
            throw new IllegalArgumentException("connector_id can't be null");
        }
        this.connectorId = connectorId;

        outputParser = new Parser() {
            @Override
            public Object parse(Object o) {
                List<ModelTensors> mlModelOutputs = (List<ModelTensors>) o;
                return mlModelOutputs.get(0).getMlModelTensors().get(0).getDataAsMap().get("response");
            }
        };
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        MLInput mlInput = RemoteInferenceMLInput.builder().algorithm(FunctionName.CONNECTOR).inputDataset(inputDataSet).build();
        ActionRequest request = new MLExecuteConnectorRequest(connectorId, mlInput);

        client.execute(MLExecuteConnectorAction.INSTANCE, request, ActionListener.wrap(r -> {
            ModelTensorOutput modelTensorOutput = (ModelTensorOutput) r.getOutput();
            modelTensorOutput.getMlModelOutputs();
            if (outputParser == null) {
                listener.onResponse((T) modelTensorOutput.getMlModelOutputs());
            } else {
                listener.onResponse((T) outputParser.parse(modelTensorOutput.getMlModelOutputs()));
            }
        }, e -> {
            log.error("Failed to run model " + connectorId, e);
            listener.onFailure(e);
        }));
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        if (parameters == null || parameters.size() == 0) {
            return false;
        }
        return true;
    }

    public static class Factory implements Tool.Factory<ConnectorTool> {
        public static final String TYPE = "ConnectorTool";
        public static final String DEFAULT_DESCRIPTION = "This tool will invoke external service.";
        private Client client;
        private static Factory INSTANCE;

        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (ConnectorTool.class) {
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
        public ConnectorTool create(Map<String, Object> map) {
            return new ConnectorTool(client, (String) map.get(CONNECTOR_ID));
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
