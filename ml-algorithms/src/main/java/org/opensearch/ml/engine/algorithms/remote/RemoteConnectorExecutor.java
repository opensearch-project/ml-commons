/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.escapeRemoteInferenceInputData;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.script.ScriptService;

public interface RemoteConnectorExecutor {
    int DEFAULT_BATCH_SIZE = -1;
    String MAX_BATCH_SIZE_KEY = "max_batch_size";
    String STEP_SIZE_KEY = "input_docs_processed_step_size";

    default void executePredict(MLInput mlInput, ActionListener<MLTaskResponse> actionListener) {
        ActionListener<List<ModelTensors>> tensorActionListener = ActionListener.wrap(r -> {
            actionListener.onResponse(new MLTaskResponse(new ModelTensorOutput(r)));
        }, actionListener::onFailure);
        try {
            Map<Integer, ModelTensors> modelTensors = new ConcurrentHashMap<>();
            AtomicReference<Exception> exceptionHolder = new AtomicReference<>();
            if (mlInput.getInputDataset() instanceof TextDocsInputDataSet) {
                TextDocsInputDataSet textDocsInputDataSet = (TextDocsInputDataSet) mlInput.getInputDataset();
                int maxBatchSize = getMaxBatchSize();
                Tuple<Integer, Integer> calculatedChunkSize = calculateChunkSize(textDocsInputDataSet, maxBatchSize);
                List<Integer> textDocOriginalOrder = Collections.emptyList();
                if (shouldSortBeforeCuttingBatches(maxBatchSize, calculatedChunkSize)) {
                    Tuple<TextDocsInputDataSet, List<Integer>> sortedData = sortTextDocsByTextLength(textDocsInputDataSet);
                    textDocsInputDataSet = sortedData.v1();
                    textDocOriginalOrder = Collections.unmodifiableList(sortedData.v2());
                }

                CountDownLatch countDownLatch = new CountDownLatch(calculatedChunkSize.v1());
                int sequence = 0;
                for (int processedDocs = 0; processedDocs < textDocsInputDataSet.getDocs().size(); processedDocs += calculatedChunkSize
                    .v2()) {
                    List<String> textDocs = textDocsInputDataSet
                        .getDocs()
                        .subList(processedDocs, Math.min(processedDocs + calculatedChunkSize.v2(), textDocsInputDataSet.getDocs().size()));
                    preparePayloadAndInvokeRemoteModel(
                        MLInput
                            .builder()
                            .algorithm(FunctionName.TEXT_EMBEDDING)
                            .inputDataset(TextDocsInputDataSet.builder().docs(textDocs).build())
                            .build(),
                        modelTensors,
                        new ExecutionContext(sequence++, countDownLatch, exceptionHolder, textDocOriginalOrder),
                        tensorActionListener
                    );
                }
            } else {
                preparePayloadAndInvokeRemoteModel(
                    mlInput,
                    modelTensors,
                    new ExecutionContext(0, new CountDownLatch(1), exceptionHolder, Collections.emptyList()),
                    tensorActionListener
                );
            }
        } catch (Exception e) {
            actionListener.onFailure(e);
        }
    }

    /**
     * Calculate the chunk size.
     * @param textDocsInputDataSet
     * @return Tuple of chunk size and step size.
     */
    private Tuple<Integer, Integer> calculateChunkSize(TextDocsInputDataSet textDocsInputDataSet, int maxBatchSize) {
        int textDocsLength = textDocsInputDataSet.getDocs().size();
        Map<String, String> parameters = getConnector().getParameters();
        if (parameters != null && parameters.containsKey(STEP_SIZE_KEY)) {
            int stepSize = Integer.parseInt(parameters.get(STEP_SIZE_KEY));
            // We need to check the parameter on runtime as parameter can be passed into predict request
            if (stepSize <= 0) {
                throw new IllegalArgumentException("Invalid parameter: input_docs_processed_step_size. It must be positive integer.");
            } else {
                return Tuple.tuple((int) Math.ceil((double) textDocsLength / stepSize), stepSize);
            }
        } else {
            Optional<ConnectorAction> predictAction = getConnector().findPredictAction();
            if (predictAction.isEmpty()) {
                throw new IllegalArgumentException("no predict action found");
            }
            String preProcessFunction = predictAction.get().getPreProcessFunction();
            if (preProcessFunction != null && !MLPreProcessFunction.contains(preProcessFunction)) {
                // user defined preprocess script, this case, the chunk size is always equals to text docs length.
                return Tuple.tuple(textDocsLength, 1);
            }

            if (maxBatchSize == DEFAULT_BATCH_SIZE || textDocsLength <= maxBatchSize) {
                return Tuple.tuple(1, textDocsLength);
            }
            return Tuple.tuple((int) Math.ceil((double) textDocsLength / maxBatchSize), maxBatchSize);
        }
    }

    /**
     * Get user configured max_batch_size parameter, throw IllegalArgumentException if it's invalid.
     * Return default value if it's not configured.
     * @return max batch size
     */
    private int getMaxBatchSize() {
        Map<String, String> parameters = getConnector().getParameters();
        if (parameters == null || !parameters.containsKey(MAX_BATCH_SIZE_KEY)) {
            return DEFAULT_BATCH_SIZE;
        }
        int maxBatchSize = Integer.parseInt(parameters.get(MAX_BATCH_SIZE_KEY));
        if (maxBatchSize <= 0) {
            throw new IllegalArgumentException("Invalid parameter: " + MAX_BATCH_SIZE_KEY + ". It must be positive integer.");
        }
        return maxBatchSize;
    }

    private boolean shouldSortBeforeCuttingBatches(int maxBatchSize, Tuple<Integer, Integer> calculatedChunkSize) {
        if (maxBatchSize <= 1 || calculatedChunkSize.v1() <= 1 || calculatedChunkSize.v2() <= 1) {
            return false;
        }
        // skip step size situation
        Map<String, String> parameters = getConnector().getParameters();
        if (parameters != null && parameters.containsKey(STEP_SIZE_KEY)) {
            return false;
        }
        return true;
    }

    private Tuple<TextDocsInputDataSet, List<Integer>> sortTextDocsByTextLength(TextDocsInputDataSet textDocsInputDataSet) {
        List<Tuple<Integer, String>> docsWithIndex = new ArrayList<>();
        for (int i = 0; i < textDocsInputDataSet.getDocs().size(); ++i) {
            docsWithIndex.add(Tuple.tuple(i, textDocsInputDataSet.getDocs().get(i)));
        }
        docsWithIndex.sort(Comparator.comparingInt(t -> t.v2().length()));
        List<String> sortedDocs = docsWithIndex.stream().map(Tuple::v2).collect(Collectors.toList());
        List<Integer> originalIndexOrder = docsWithIndex.stream().map(Tuple::v1).collect(Collectors.toList());
        TextDocsInputDataSet sortedTextDocsInputDataSet = TextDocsInputDataSet.builder().docs(sortedDocs).build();
        return Tuple.tuple(sortedTextDocsInputDataSet, originalIndexOrder);
    }

    default void setScriptService(ScriptService scriptService) {}

    ScriptService getScriptService();

    Connector getConnector();

    TokenBucket getRateLimiter();

    Map<String, TokenBucket> getUserRateLimiterMap();

    MLGuard getMlGuard();

    Client getClient();

    default void setClient(Client client) {}

    default void setXContentRegistry(NamedXContentRegistry xContentRegistry) {}

    default void setClusterService(ClusterService clusterService) {}

    default void setRateLimiter(TokenBucket rateLimiter) {}

    default void setUserRateLimiterMap(Map<String, TokenBucket> userRateLimiterMap) {}

    default void setMlGuard(MLGuard mlGuard) {}

    default void preparePayloadAndInvokeRemoteModel(
        MLInput mlInput,
        Map<Integer, ModelTensors> tensorOutputs,
        ExecutionContext countDownLatch,
        ActionListener<List<ModelTensors>> actionListener
    ) {
        Connector connector = getConnector();

        Map<String, String> parameters = new HashMap<>();
        if (connector.getParameters() != null) {
            parameters.putAll(connector.getParameters());
        }
        MLInputDataset inputDataset = mlInput.getInputDataset();
        Map<String, String> inputParameters = new HashMap<>();
        if (inputDataset instanceof RemoteInferenceInputDataSet && ((RemoteInferenceInputDataSet) inputDataset).getParameters() != null) {
            escapeRemoteInferenceInputData((RemoteInferenceInputDataSet) inputDataset);
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
            if (getMlGuard() != null && !getMlGuard().validate(payload, MLGuard.Type.INPUT)) {
                throw new IllegalArgumentException("guardrails triggered for user input");
            }
            invokeRemoteModel(mlInput, parameters, payload, tensorOutputs, countDownLatch, actionListener);
        }
    }

    void invokeRemoteModel(
        MLInput mlInput,
        Map<String, String> parameters,
        String payload,
        Map<Integer, ModelTensors> tensorOutputs,
        ExecutionContext countDownLatch,
        ActionListener<List<ModelTensors>> actionListener
    );
}
