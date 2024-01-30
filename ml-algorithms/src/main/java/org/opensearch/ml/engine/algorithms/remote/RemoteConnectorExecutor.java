/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processInput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.script.ScriptService;

public interface RemoteConnectorExecutor {

    default ModelTensorOutput executePredict(MLInput mlInput) {
        List<ModelTensors> tensorOutputs = new ArrayList<>();

        if (mlInput.getInputDataset() instanceof TextDocsInputDataSet) {
            TextDocsInputDataSet textDocsInputDataSet = (TextDocsInputDataSet) mlInput.getInputDataset();
            int processedDocs = 0;
            while (processedDocs < textDocsInputDataSet.getDocs().size()) {
                List<String> textDocs = textDocsInputDataSet.getDocs().subList(processedDocs, textDocsInputDataSet.getDocs().size());
                List<ModelTensors> tempTensorOutputs = new ArrayList<>();
                preparePayloadAndInvokeRemoteModel(
                    MLInput
                        .builder()
                        .algorithm(FunctionName.TEXT_EMBEDDING)
                        .inputDataset(TextDocsInputDataSet.builder().docs(textDocs).build())
                        .build(),
                    tempTensorOutputs
                );
                int tensorCount = 0;
                if (tempTensorOutputs.size() > 0 && tempTensorOutputs.get(0).getMlModelTensors() != null) {
                    tensorCount = tempTensorOutputs.get(0).getMlModelTensors().size();
                }
                // This is to support some model which takes N text docs and embedding size is less than N.
                // We need to tell executor what's the step size for each model run.
                Map<String, String> parameters = getConnector().getParameters();
                if (parameters != null && parameters.containsKey("input_docs_processed_step_size")) {
                    int stepSize = Integer.parseInt(parameters.get("input_docs_processed_step_size"));
                    // We need to check the parameter on runtime as parameter can be passed into predict request
                    if (stepSize <= 0) {
                        throw new IllegalArgumentException(
                            "Invalid parameter: input_docs_processed_step_size. It must be positive integer."
                        );
                    }
                    processedDocs += stepSize;
                } else {
                    processedDocs += Math.max(tensorCount, 1);
                }
                tensorOutputs.addAll(tempTensorOutputs);
            }
        } else {
            preparePayloadAndInvokeRemoteModel(mlInput, tensorOutputs);
        }
        return new ModelTensorOutput(tensorOutputs);
    }

    default void setScriptService(ScriptService scriptService) {}

    ScriptService getScriptService();

    Connector getConnector();

    TokenBucket getRateLimiter();

    Map<String, TokenBucket> getUserRateLimiterMap();

    Client getClient();

    default void setClient(Client client) {}

    default void setXContentRegistry(NamedXContentRegistry xContentRegistry) {}

    default void setClusterService(ClusterService clusterService) {}

    default void setRateLimiter(TokenBucket rateLimiter) {}

    default void setUserRateLimiterMap(Map<String, TokenBucket> userRateLimiterMap) {}

    default void preparePayloadAndInvokeRemoteModel(MLInput mlInput, List<ModelTensors> tensorOutputs) {
        Connector connector = getConnector();

        Map<String, String> parameters = new HashMap<>();
        if (connector.getParameters() != null) {
            parameters.putAll(connector.getParameters());
        }
        MLInputDataset inputDataset = mlInput.getInputDataset();
        Map<String, String> inputParameters = new HashMap<>();
        if (inputDataset instanceof RemoteInferenceInputDataSet && ((RemoteInferenceInputDataSet) inputDataset).getParameters() != null) {
            inputParameters.putAll(((RemoteInferenceInputDataSet) inputDataset).getParameters());
        }
        parameters.putAll(inputParameters);
        RemoteInferenceInputDataSet inputData = processInput(mlInput, connector, parameters, getScriptService());
        if (inputData.getParameters() != null) {
            parameters.putAll(inputData.getParameters());
        }
        // override again to always prioritize the input parameter
        parameters.putAll(inputParameters);
        String payload = connector.createPredictPayload(parameters);
        connector.validatePayload(payload);
        String userStr = getClient()
            .threadPool()
            .getThreadContext()
            .getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
        User user = User.parse(userStr);
        if (getRateLimiter() != null && !getRateLimiter().request()) {
            throw new OpenSearchStatusException("Request is throttled at model level.", RestStatus.TOO_MANY_REQUESTS);
        } else if (user != null
            && getUserRateLimiterMap() != null
            && getUserRateLimiterMap().get(user.getName()) != null
            && !getUserRateLimiterMap().get(user.getName()).request()) {
            throw new OpenSearchStatusException(
                "Request is throttled at user level. If you think there's an issue, please contact your cluster admin.",
                RestStatus.TOO_MANY_REQUESTS
            );
        } else {
            invokeRemoteModel(mlInput, parameters, payload, tensorOutputs);
        }
    }

    void invokeRemoteModel(MLInput mlInput, Map<String, String> parameters, String payload, List<ModelTensors> tensorOutputs);

}
