/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.SKIP_VALIDATE_MISSING_PARAMETERS;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.escapeRemoteInferenceInputData;
import static org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.processInput;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.Logger;
import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.bulk.BackoffPolicy;
import org.opensearch.action.support.GroupedActionListener;
import org.opensearch.action.support.RetryableAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
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
    public String RETRY_EXECUTOR = "opensearch_ml_predict_remote";

    default void executeAction(String action, MLInput mlInput, ActionListener<MLTaskResponse> actionListener) {
        ActionListener<Collection<Tuple<Integer, ModelTensors>>> tensorActionListener = ActionListener.wrap(r -> {
            // Only all sub-requests success will call logics here
            ModelTensors[] modelTensors = new ModelTensors[r.size()];
            r.forEach(sequenceNoAndModelTensor -> modelTensors[sequenceNoAndModelTensor.v1()] = sequenceNoAndModelTensor.v2());
            actionListener.onResponse(new MLTaskResponse(new ModelTensorOutput(Arrays.asList(modelTensors))));
        }, actionListener::onFailure);

        try {
            if (mlInput.getInputDataset() instanceof TextDocsInputDataSet) {
                TextDocsInputDataSet textDocsInputDataSet = (TextDocsInputDataSet) mlInput.getInputDataset();
                Tuple<Integer, Integer> calculatedChunkSize = calculateChunkSize(action, textDocsInputDataSet);
                GroupedActionListener<Tuple<Integer, ModelTensors>> groupedActionListener = new GroupedActionListener<>(
                    tensorActionListener,
                    calculatedChunkSize.v1()
                );
                int sequence = 0;
                for (int processedDocs = 0; processedDocs < textDocsInputDataSet.getDocs().size(); processedDocs += calculatedChunkSize
                    .v2()) {
                    List<String> textDocs = textDocsInputDataSet
                        .getDocs()
                        .subList(processedDocs, Math.min(processedDocs + calculatedChunkSize.v2(), textDocsInputDataSet.getDocs().size()));
                    preparePayloadAndInvoke(
                        action,
                        MLInput
                            .builder()
                            .algorithm(FunctionName.TEXT_EMBEDDING)
                            .inputDataset(TextDocsInputDataSet.builder().docs(textDocs).build())
                            .build(),
                        new ExecutionContext(sequence++),
                        groupedActionListener
                    );
                }
            } else {
                preparePayloadAndInvoke(action, mlInput, new ExecutionContext(0), new GroupedActionListener<>(tensorActionListener, 1));
            }
        } catch (Exception e) {
            actionListener.onFailure(e);
        }
    }

    /**
     * Calculate the chunk size.
     * @param textDocsInputDataSet Input dataset in textDocsInputDataSet format.
     * @return Tuple of chunk size and step size.
     */
    private Tuple<Integer, Integer> calculateChunkSize(String action, TextDocsInputDataSet textDocsInputDataSet) {
        int textDocsLength = textDocsInputDataSet.getDocs().size();
        Map<String, String> parameters = getConnector().getParameters();
        if (parameters != null && parameters.containsKey("input_docs_processed_step_size")) {
            int stepSize = Integer.parseInt(parameters.get("input_docs_processed_step_size"));
            // We need to check the parameter on runtime as parameter can be passed into action request
            if (stepSize <= 0) {
                throw new IllegalArgumentException("Invalid parameter: input_docs_processed_step_size. It must be positive integer.");
            } else {
                boolean isDivisible = textDocsLength % stepSize == 0;
                if (isDivisible) {
                    return Tuple.tuple(textDocsLength / stepSize, stepSize);
                }
                return Tuple.tuple(textDocsLength / stepSize + 1, stepSize);
            }
        } else {
            Optional<ConnectorAction> connectorAction = getConnector().findAction(action);
            if (connectorAction.isEmpty()) {
                throw new IllegalArgumentException("no " + action + " action found");
            }
            String preProcessFunction = connectorAction.get().getPreProcessFunction();
            if (preProcessFunction == null) {
                // default preprocess case, consider this a batch.
                return Tuple.tuple(1, textDocsLength);
            } else if (MLPreProcessFunction.TEXT_DOCS_TO_BEDROCK_EMBEDDING_INPUT.equals(preProcessFunction)
                || !MLPreProcessFunction.contains(preProcessFunction)) {
                // bedrock and user defined preprocess script, the chunk size is always equals to text docs length.
                return Tuple.tuple(textDocsLength, 1);
            }
            // Other cases: non-bedrock and user defined preprocess script, consider as batch.
            return Tuple.tuple(1, textDocsLength);
        }
    }

    default void setScriptService(ScriptService scriptService) {}

    ScriptService getScriptService();

    Connector getConnector();

    TokenBucket getRateLimiter();

    Map<String, TokenBucket> getUserRateLimiterMap();

    MLGuard getMlGuard();

    Client getClient();

    Logger getLogger();

    ConnectorClientConfig getConnectorClientConfig();

    default void setClient(Client client) {}

    default void setConnectorPrivateIpEnabled(AtomicBoolean connectorPrivateIpEnabled) {}

    default void setXContentRegistry(NamedXContentRegistry xContentRegistry) {}

    default void setClusterService(ClusterService clusterService) {}

    default void setRateLimiter(TokenBucket rateLimiter) {}

    default void setUserRateLimiterMap(Map<String, TokenBucket> userRateLimiterMap) {}

    default void setMlGuard(MLGuard mlGuard) {}

    default void preparePayloadAndInvoke(
        String action,
        MLInput mlInput,
        ExecutionContext executionContext,
        ActionListener<Tuple<Integer, ModelTensors>> actionListener
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
        RemoteInferenceInputDataSet inputData = processInput(action, mlInput, connector, parameters, getScriptService());
        if (inputData.getParameters() != null) {
            parameters.putAll(inputData.getParameters());
        }
        // override again to always prioritize the input parameter
        parameters.putAll(inputParameters);
        String payload = connector.createPayload(action, parameters);
        if (!Boolean.parseBoolean(parameters.getOrDefault(SKIP_VALIDATE_MISSING_PARAMETERS, "false"))) {
            connector.validatePayload(payload);
        }
        String userStr = getClient()
            .threadPool()
            .getThreadContext()
            .getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
        User user = User.parse(userStr);
        if (getRateLimiter() != null && !getRateLimiter().request()) {
            getLogger().error("Request is throttled at model level.");
            throw new OpenSearchStatusException("Request is throttled at model level.", RestStatus.TOO_MANY_REQUESTS);
        } else if (user != null
            && getUserRateLimiterMap() != null
            && getUserRateLimiterMap().get(user.getName()) != null
            && !getUserRateLimiterMap().get(user.getName()).request()) {
            getLogger().error("Request is throttled at user level.");
            throw new OpenSearchStatusException(
                "Request is throttled at user level. If you think there's an issue, please contact your cluster admin.",
                RestStatus.TOO_MANY_REQUESTS
            );
        } else {
            if (getMlGuard() != null && !getMlGuard().validate(payload, MLGuard.Type.INPUT, parameters)) {
                getLogger().error("guardrails triggered for user input");
                throw new IllegalArgumentException("guardrails triggered for user input");
            }
            if (getConnectorClientConfig().getMaxRetryTimes() != 0) {
                invokeRemoteServiceWithRetry(action, mlInput, parameters, payload, executionContext, actionListener);
            } else {
                invokeRemoteService(action, mlInput, parameters, payload, executionContext, actionListener);
            }
        }
    }

    default BackoffPolicy getRetryBackoffPolicy(ConnectorClientConfig connectorClientConfig) {
        switch (connectorClientConfig.getRetryBackoffPolicy()) {
            case EXPONENTIAL_EQUAL_JITTER:
                return BackoffPolicy
                    .exponentialEqualJitterBackoff(
                        connectorClientConfig.getRetryBackoffMillis(),
                        connectorClientConfig.getRetryTimeoutSeconds() * 1000
                    );
            case EXPONENTIAL_FULL_JITTER:
                return BackoffPolicy.exponentialFullJitterBackoff(connectorClientConfig.getRetryBackoffMillis());
            default:
                // The second parameter is the maxNumberOfRetries for ConstantBackoff.
                // However, we can't reuse it, because the ConstantBackoffIterator.next() throws exception when it reaches the limit,
                // but the RetryableAction doesn't handle the exception from iterator, and will make the request hanging.
                //
                // Setting it to Integer.MAX_VALUE to avoid throwing this exception. Instead, we handle the max retry numbers at
                // shouldRetry.
                return BackoffPolicy
                    .constantBackoff(TimeValue.timeValueMillis(connectorClientConfig.getRetryBackoffMillis()), Integer.MAX_VALUE);
        }
    }

    default void invokeRemoteServiceWithRetry(
        String action,
        MLInput mlInput,
        Map<String, String> parameters,
        String payload,
        ExecutionContext executionContext,
        ActionListener<Tuple<Integer, ModelTensors>> actionListener
    ) {
        final RetryableAction<Tuple<Integer, ModelTensors>> invokeRemoteModelAction = new RetryableAction<>(
            getLogger(),
            getClient().threadPool(),
            TimeValue.timeValueMillis(getConnectorClientConfig().getRetryBackoffMillis()),
            TimeValue.timeValueSeconds(getConnectorClientConfig().getRetryTimeoutSeconds()),
            actionListener,
            getRetryBackoffPolicy(getConnectorClientConfig()),
            RETRY_EXECUTOR
        ) {
            int retryTimes = 0;

            @Override
            public void tryAction(ActionListener<Tuple<Integer, ModelTensors>> listener) {
                // the listener here is RetryingListener
                // If the request success, or can not retry, will call delegate listener
                invokeRemoteService(action, mlInput, parameters, payload, executionContext, listener);
            }

            @Override
            public boolean shouldRetry(Exception e) {
                Throwable cause = ExceptionsHelper.unwrapCause(e);
                Integer maxRetryTimes = getConnectorClientConfig().getMaxRetryTimes();
                boolean shouldRetry = cause instanceof RemoteConnectorThrottlingException;
                if (++retryTimes > maxRetryTimes && maxRetryTimes != -1) {
                    shouldRetry = false;
                }
                if (shouldRetry) {
                    getLogger().debug(String.format(Locale.ROOT, "The %d-th retry for invoke remote model", retryTimes), e);
                }
                return shouldRetry;
            }
        };
        invokeRemoteModelAction.run();
    };

    void invokeRemoteService(
        String action,
        MLInput mlInput,
        Map<String, String> parameters,
        String payload,
        ExecutionContext executionContext,
        ActionListener<Tuple<Integer, ModelTensors>> actionListener
    );
}
