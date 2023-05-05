/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.metrics_correlation;

import ai.djl.modality.Output;
import ai.djl.translate.TranslateException;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.log4j.Log4j2;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.core.action.ActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.exception.ExecuteException;
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
    public static final String MODEL_CONTENT_HASH = "4d7e4ede2293d3611def0f9fc4065852cb7f6841bc7df7d6bfc16562ae4f6743";
    private Client client;
    private final Settings settings;
    //As metrics correlation is an experimental feature we are marking the version as 1.0.0b1
    public static final String MCORR_ML_VERSION = "1.0.0b1";
    //This is python based model which is developed in house.
    public static final String MODEL_TYPE = "in-house";
    //This is the opensearch release artifact url for the model
    // TODO: we need to make this URL more dynamic so that user can define the version from the settings to pull
    //  up the most updated model version.
    public static final String MCORR_MODEL_URL =
            "https://artifacts.opensearch.org/models/ml-models/amazon/metrics_correlation/1.0.0b1/torch_script/metrics_correlation-1.0.0b1-torch_script.zip";

    public MetricsCorrelation(Client client, Settings settings) {
        this.client = client;
        this.settings = settings;
    }

    /**
     * @param input input data for metrics correlation. This input expects a list of float array (List<float[]>)
     * @return MetricsCorrelationOutput output of the metrics correlation algorithm is a list of objects. Each object
     *  contains 3 properties  event_window, event_pattern and suspected_metrics
     * @throws ExecuteException
     */
    @Override
    public MetricsCorrelationOutput execute(Input input) throws ExecuteException {
        if (!(input instanceof MetricsCorrelationInput)) {
            throw new ExecuteException("wrong input");
        }
        List<MCorrModelTensors> tensorOutputs = new ArrayList<>();
        MetricsCorrelationInput metricsCorrelation = (MetricsCorrelationInput) input;
        List<float[]> inputData = metricsCorrelation.getInputData();

        // converting List of float array to 2 dimension float array for DJL input
        float[][] processedInputData = processedInput(inputData);

        // Searching in the model index to see if there's any model in the index already or not.
        if (modelId == null) {
            SearchRequest modelSearchRequest = getSearchRequest();
            searchModel(modelSearchRequest, ActionListener.wrap(modelInfo -> {
                if (modelInfo == null || modelInfo.isEmpty()) {
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
                        deployModel(modelInfo.get(MLModel.MODEL_ID_FIELD).toString(), ActionListener.wrap(deployModelResponse -> modelId = getTask(deployModelResponse.getTaskId()).getModelId(), e -> log.error("Metrics correlation model didn't get deployed to the index successfully", e)));
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
        } else {
            MLModel model = getModel(modelId);
            if (model.getModelState() != MLModelState.DEPLOYED &&
                    model.getModelState() != MLModelState.PARTIALLY_DEPLOYED) {
                deployModel(modelId, ActionListener.wrap(deployModelResponse -> modelId = getTask(deployModelResponse.getTaskId()).getModelId(), e -> log.error("Metrics correlation model didn't get deployed to the index successfully", e)));
            }
        }

        //We will be waiting here until actionListeners set the model id to the modelId.
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
            Map<String, Object> modelInfo = null;
            if (searchModelResponse != null) {
                SearchHit[] searchHits = searchModelResponse.getHits().getHits();
                for (SearchHit currentHit : searchHits) {
                    // access the current element
                    if (currentHit.getSourceAsMap().get(MLModel.MODEL_ID_FIELD) != null) {
                        modelInfo = currentHit.getSourceAsMap();
                        break;
                    }
                }
                listener.onResponse(modelInfo);
            } else {
                listener.onResponse(null);
            }
        }, e -> {
            log.error("Failed to find model", e);
            listener.onFailure(e);
        }));
    }

    @VisibleForTesting
    void registerModel(ActionListener<MLRegisterModelResponse> listener) throws InterruptedException {

        FunctionName functionName = FunctionName.METRICS_CORRELATION;
        MLModelFormat modelFormat = MLModelFormat.TORCH_SCRIPT;

        MLModelConfig modelConfig = MetricsCorrelationModelConfig.builder()
                .modelType(MODEL_TYPE)
                .allConfig(null).build();
        MLRegisterModelInput input = MLRegisterModelInput
                .builder()
                .functionName(functionName)
                .modelName(FunctionName.METRICS_CORRELATION.name())
                .version(MCORR_ML_VERSION)
                .modelFormat(modelFormat)
                .hashValue(MODEL_CONTENT_HASH)
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
    void deployModel(final String modelId, ActionListener<MLDeployModelResponse> listener) {
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
