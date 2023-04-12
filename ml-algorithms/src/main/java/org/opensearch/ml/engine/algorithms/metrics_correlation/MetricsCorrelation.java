/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.metrics_correlation;

import ai.djl.modality.Output;
import ai.djl.translate.TranslateException;
import com.google.common.annotations.VisibleForTesting;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.ActionFuture;
import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.metricscorrelation.MetricsCorrelationInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.MetricsCorrelationModelConfig;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensors;
import org.opensearch.ml.common.output.execute.metrics_correlation.MetricsCorrelationOutput;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelRequest;
import org.opensearch.ml.common.transport.register.MLRegisterModelResponse;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetResponse;
import org.opensearch.ml.common.exception.ExecuteException;
import org.opensearch.ml.engine.algorithms.DLModelExecute;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.opensearch.index.query.QueryBuilders.termQuery;

@Log4j2
@Function(FunctionName.METRICS_CORRELATION)
public class MetricsCorrelation extends DLModelExecute {

    private static final int AWAIT_BUSY_THRESHOLD = 1000;
    private Client client;
    private final Settings settings;
    public static final String MCORR_ML_VERSION = "1.0.0b1";

    //TODO: Model didn't publish yet to the release repo, so using this for the development.
    // But before merging this code, we will have the release artifact url here for
    public static final String MCORR_MODEL_URL =
            "https://artifacts.opensearch.org/models/ml-models/amazon/metrics_correlation/1.0.0b1/torch_script/metrics_correlation-1.0.0b1-torch_script.zip";

    public MetricsCorrelation(Client client, Settings settings) {
        this.client = client;
        this.settings = settings;
    }

    @Override
    public MetricsCorrelationOutput execute(Input input) throws ExecuteException {
        if (!(input instanceof MetricsCorrelationInput)) {
            throw new ExecuteException("wrong input");
        }
        List<MCorrModelTensors> tensorOutputs = new ArrayList<>();
        MetricsCorrelationInput metricsCorrelation = (MetricsCorrelationInput) input;
        List<float[]> inputData = metricsCorrelation.getInputData();

        // converting List of float array to 2 dimensional float array for DJL input
        float[][] processedInputData = processedInput(inputData);

        SearchRequest modelSearchRequest = getSearchRequest();
        // Searching in the model index to see if there's any model in the index already or not.
        if (modelId == null) {
            searchModel(modelSearchRequest, ActionListener.wrap(modelInfo -> {
                if (modelInfo.isEmpty()) {
                    // if we don't find any model in the index then we will register a model in the index
                    registerModel(ActionListener.wrap(registerModelResponse ->
                                    modelId = getTask(registerModelResponse.getTaskId()).getModelId(),
                            e -> log.error("Metrics correlation model didn't get registered to the index successfully", e)));
                } else {
                    MLModel model = getModel(modelInfo.get(MLModel.MODEL_ID_FIELD).toString());
                    if (model.getModelState() != MLModelState.DEPLOYED &&
                            model.getModelState() != MLModelState.PARTIALLY_DEPLOYED) {
                        // if we find a model in the index but the model is not loaded into memory then we will
                        // load the model in memory
                        deployModel(ActionListener.wrap(deployModelResponse -> modelId = getTask(deployModelResponse.getTaskId()).getModelId(), e -> log.error("Metrics correlation model didn't get deployed to the index successfully", e)));
                    }
                }
            }, e -> {
                //If the model index didn't get created before this request then we can face model index not found exception
                log.error("Model Index Not found", e);
                try {
                    registerModel(ActionListener.wrap(registerModelResponse ->
                                    modelId = getTask(registerModelResponse.getTaskId()).getModelId(),
                            ex -> log.error("Exception during registering the Metrics correlation model", ex)));
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }));
        }

        //We will be waiting here until actionListeners set the model id to the loadedModelId.
        waitUntil(() -> {
            if (modelId != null) {
                MLModelState modelState = getModel(modelId).getModelState();
                return modelState == MLModelState.DEPLOYED || modelState == MLModelState.PARTIALLY_DEPLOYED;
            }
            return false;
        }, 120, TimeUnit.SECONDS);

        Output djlOutput;
        try {
            if (modelId == null) {
                throw new ExecuteException("Model is not loaded yet. Please try again.");
            }
            djlOutput = getPredictor().predict(processedInputData);
        } catch (TranslateException translateException) {
            throw new ExecuteException(translateException);
        }

        tensorOutputs.add(parseModelTensorOutput(djlOutput, null));
        return new MetricsCorrelationOutput(tensorOutputs);
    }

    @VisibleForTesting
    void searchModel(SearchRequest modelSearchRequest, ActionListener<Map<String, Object>> listener) {
        client.execute(MLModelSearchAction.INSTANCE, modelSearchRequest, ActionListener.wrap(searchModelResponse -> {
            if (searchModelResponse != null) {
                SearchHit[] searchHits = searchModelResponse.getHits().getHits();
                Map<String, Object> modelInfo = searchHits[0].getSourceAsMap();
                listener.onResponse(modelInfo);
            }
        }, e -> {
            log.error("Failed to update model state", e);
            listener.onFailure(e);
        }));
    }

    @VisibleForTesting
    void registerModel(ActionListener<MLRegisterModelResponse> listener) throws InterruptedException {

        FunctionName functionName = FunctionName.METRICS_CORRELATION;
        MLModelFormat modelFormat = MLModelFormat.TORCH_SCRIPT;
        String modelType = "custom";
        MLModelConfig modelConfig = MetricsCorrelationModelConfig.builder()
                .modelType(modelType)
                .allConfig(null).build();
        MLRegisterModelInput input = MLRegisterModelInput
                .builder()
                .functionName(functionName)
                .modelName(FunctionName.METRICS_CORRELATION.name())
                .version(MCORR_ML_VERSION)
                .modelFormat(modelFormat)
                .modelConfig(modelConfig)
                .url(MCORR_MODEL_URL)
                .deployModel(true)
                .build();
        MLRegisterModelRequest registerRequest = MLRegisterModelRequest.builder().registerModelInput(input).build();

        client.execute(MLRegisterModelAction.INSTANCE, registerRequest, ActionListener.wrap(listener::onResponse, e -> {
            log.error("Failed to Register Model", e);
            listener.onFailure(e);
        }));
    }

    @VisibleForTesting
    void deployModel(ActionListener<MLDeployModelResponse> listener) {
        MLDeployModelRequest loadRequest = MLDeployModelRequest
                .builder()
                .modelId(modelId)
                .async(false)
                .dispatchTask(false)
                .build();
        client.execute(MLDeployModelAction.INSTANCE, loadRequest, ActionListener.wrap(listener::onResponse, e -> {
            log.error("Failed to deploy Model", e);
            listener.onFailure(e);
        }));
    }

    @VisibleForTesting
    float[][] processedInput(List<float[]> input) {
        float[][] processInput = new float[input.size()][];
        for (int i = 0; i < input.size(); i++) {
            float[] innerList = input.get(i);
            float[] temp = processInput[i] = new float[innerList.length];
            System.arraycopy(innerList, 0, temp, 0, temp.length);
        }
        return processInput;
    }

    @Override
    public MetricsCorrelationTranslator getTranslator() {
        return new MetricsCorrelationTranslator();
    }

    @VisibleForTesting
    SearchRequest getSearchRequest() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.fetchSource(new String[] { MLModel.MODEL_ID_FIELD,
                        MLModel.MODEL_NAME_FIELD, MLModel.MODEL_STATE_FIELD, MLModel.MODEL_VERSION_FIELD, MLModel.MODEL_CONTENT_FIELD },
                new String[] { MLModel.MODEL_CONTENT_FIELD });

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
                .should(termQuery(MLModel.MODEL_NAME_FIELD, FunctionName.METRICS_CORRELATION.name()))
                .should(termQuery(MLModel.MODEL_VERSION_FIELD, MCORR_ML_VERSION));
        searchSourceBuilder.query(boolQueryBuilder);
        return new SearchRequest().source(searchSourceBuilder)
                .indices(CommonValue.ML_MODEL_INDEX);
    }

    public static boolean waitUntil(BooleanSupplier breakSupplier, long maxWaitTime, TimeUnit unit) throws ExecuteException {
        long maxTimeInMillis = TimeUnit.MILLISECONDS.convert(maxWaitTime, unit);
        long timeInMillis = 1;
        long sum = 0;
        while (sum + timeInMillis < maxTimeInMillis) {
            if (breakSupplier.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(timeInMillis);
            } catch (InterruptedException interruptedException) {
                throw new ExecuteException(interruptedException);
            }
            sum += timeInMillis;
            timeInMillis = Math.min(AWAIT_BUSY_THRESHOLD, timeInMillis * 2);
        }
        timeInMillis = maxTimeInMillis - sum;
        try {
            Thread.sleep(Math.max(timeInMillis, 0));
        } catch (InterruptedException interruptedException) {
            throw new ExecuteException(interruptedException);
        }
        return breakSupplier.getAsBoolean();
    }

    public MLTask getTask(String taskId) {
        MLTaskGetRequest getRequest = new MLTaskGetRequest(taskId);
        MLTaskGetResponse response = client.execute(MLTaskGetAction.INSTANCE, getRequest).actionGet(10000);
        return response.getMlTask();
    }

    public MLModel getModel(String modelId) {
        MLModelGetRequest getRequest = new MLModelGetRequest(modelId, false);
        ActionFuture<MLModelGetResponse> future = client.execute(MLModelGetAction.INSTANCE, getRequest);
        MLModelGetResponse response = future.actionGet(10000);
        return response.getMlModel();
    }

    /**
     * Parse model output to model tensor output and apply result filter.
     * @param output model output
     * @param resultFilter result filter
     * @return model tensor output
     */
    public MCorrModelTensors parseModelTensorOutput(ai.djl.modality.Output output, ModelResultFilter resultFilter) {

        //This is where we are making the pause. We need find out what will be the best way
        // to represent the model output.
        if (output == null) {
            throw new MLException("No output generated");
        }
        byte[] bytes = output.getData().getAsBytes();
        MCorrModelTensors tensorOutput = MCorrModelTensors.fromBytes(bytes);
        if (resultFilter != null) {
            tensorOutput.filter(resultFilter);
        }
        return tensorOutput;
    }
}
