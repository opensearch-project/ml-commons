/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.script.ScriptService;

import java.util.ArrayList;
import java.util.List;

public interface RemoteConnectorExecutor {

    default ModelTensorOutput executePredict(MLInput mlInput) {
        List<ModelTensors> tensorOutputs = new ArrayList<>();

        if (mlInput.getInputDataset() instanceof TextDocsInputDataSet) {
            TextDocsInputDataSet textDocsInputDataSet = (TextDocsInputDataSet) mlInput.getInputDataset();
            List textDocs = new ArrayList(textDocsInputDataSet.getDocs());
            for (int i = 0; i < textDocsInputDataSet.getDocs().size(); i++) {
                List<ModelTensor> modelTensors = new ArrayList<>();
                invokeRemoteModel(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(TextDocsInputDataSet.builder().docs(textDocs).build()).build(), tensorOutputs, modelTensors);
                if (tensorOutputs.size() >= textDocsInputDataSet.getDocs().size()) {
                    break;
                }
                textDocs.remove(0);
            }
        } else {
            List<ModelTensor> modelTensors = new ArrayList<>();
            invokeRemoteModel(mlInput, tensorOutputs, modelTensors);
        }
        return new ModelTensorOutput(tensorOutputs);
    }
    default void setScriptService(ScriptService scriptService){}
    default void setClient(Client client){}
    default void setXContentRegistry(NamedXContentRegistry xContentRegistry){}
    default void setClusterService(ClusterService clusterService){}

    void invokeRemoteModel(MLInput mlInput, List<ModelTensors> tensorOutputs, List<ModelTensor> modelTensors);


}
