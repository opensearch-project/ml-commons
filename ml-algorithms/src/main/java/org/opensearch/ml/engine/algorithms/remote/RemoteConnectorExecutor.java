/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.script.ScriptService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processRemoteInput;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processTextDocsInput;

public interface RemoteConnectorExecutor {

    default ModelTensorOutput executePredict(MLInput mlInput) {
        List<ModelTensors> tensorOutputs = new ArrayList<>();
        Connector connector = getConnector();
        Optional<ConnectorAction> predictAction = connector.findPredictAction();
        if (predictAction.isEmpty()) {
            throw new IllegalArgumentException("no predict action found");
        }
        Map<String, String> parameters = new HashMap<>();
        if (connector.getParameters() != null) {
            parameters.putAll(connector.getParameters());
        }
        MLInputDataset inputDataset = mlInput.getInputDataset();
        if (inputDataset instanceof RemoteInferenceInputDataSet) {
            processRemoteInputDataSetInvocation(mlInput, parameters, connector, tensorOutputs);
        } else if (inputDataset instanceof TextDocsInputDataSet) {
            processTextDocsInputDataSetInvocation(mlInput, parameters, connector, tensorOutputs);
        } else {
            throw new IllegalArgumentException("Wrong input type");
        }
        return new ModelTensorOutput(tensorOutputs);
    }
    default void setScriptService(ScriptService scriptService){}
    ScriptService getScriptService();
    Connector getConnector();
    default void setClient(Client client){}
    default void setXContentRegistry(NamedXContentRegistry xContentRegistry){}
    default void setClusterService(ClusterService clusterService){}

    private void processTextDocsInputDataSetInvocation(MLInput mlInput, Map<String, String> parameters, Connector connector, List<ModelTensors> tensorOutputs) {
        if (((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs() == null || ((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs().isEmpty()) {
            throw new IllegalArgumentException("Input text docs size is empty, can not invoke remote model");
        }
        String preProcessFunction = connector.findPredictAction()
            .map(ConnectorAction::getPreProcessFunction)
            .orElse(MLPreProcessFunction.TEXT_DOCS_TO_DEFAULT_EMBEDDING_INPUT);
        Boolean batchEmbeddingSupportFlag = MLPreProcessFunction.getBatchEmbeddingSupportFlag(preProcessFunction);
        if (Boolean.FALSE.equals(batchEmbeddingSupportFlag)){
            TextDocsInputDataSet textDocsInputDataSet = (TextDocsInputDataSet) mlInput.getInputDataset();
            int size = Optional.ofNullable(textDocsInputDataSet).map(TextDocsInputDataSet::getDocs).map(List::size).orElse(0);
            for (int i = 0; i < size; i++) {
                TextDocsInputDataSet singleDocTextDocInputDataSet =
                    TextDocsInputDataSet.builder().docs(List.of(textDocsInputDataSet.getDocs().get(i))).build();
                RemoteInferenceInputDataSet inputData = processTextDocsInput(singleDocTextDocInputDataSet, preProcessFunction, parameters, getScriptService());
                String payload = createPayload(inputData, connector, parameters);
                invokeRemoteModel(mlInput, parameters, payload, tensorOutputs);
            }
        } else { // two cases: 1. build-in preprocess function but batch embedding not support; 2. non build-in preprocess function, process with user defined painless script.
            RemoteInferenceInputDataSet inputData = processTextDocsInput((TextDocsInputDataSet) mlInput.getInputDataset(), preProcessFunction, parameters, getScriptService());
            String payload = createPayload(inputData, connector, parameters);
            invokeRemoteModel(mlInput, parameters, payload, tensorOutputs);
        }
    }

    private void processRemoteInputDataSetInvocation(MLInput mlInput, Map<String, String> parameters, Connector connector, List<ModelTensors> tensorOutputs) {
        RemoteInferenceInputDataSet remoteInferenceInputDataSet = processRemoteInput(mlInput);
        if (remoteInferenceInputDataSet.getParameters() != null) {
            parameters.putAll(remoteInferenceInputDataSet.getParameters());
        }
        String payload = createPayload(remoteInferenceInputDataSet, connector, parameters);
        invokeRemoteModel(mlInput, parameters, payload, tensorOutputs);
    }

    private String createPayload(RemoteInferenceInputDataSet inputData, Connector connector, Map<String, String> parameters) {
        if (inputData.getParameters() != null) {
            parameters.putAll(inputData.getParameters());
        }
        String payload = connector.createPredictPayload(parameters);
        connector.validatePayload(payload);
        return payload;
    }

    void invokeRemoteModel(MLInput mlInput, Map<String, String> parameters, String payload, List<ModelTensors> tensorOutputs);


}
