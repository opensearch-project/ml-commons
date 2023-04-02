/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.metrics_correlation;

import ai.djl.modality.Output;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.ActionFuture;
import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.metricscorrelation.MetricsCorrelationInput;
import org.opensearch.ml.common.model.*;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensors;
import org.opensearch.ml.common.output.execute.metrics_correlation.MetricsCorrelationOutput;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetResponse;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelRequest;
import org.opensearch.ml.common.transport.register.MLRegisterModelResponse;
import org.opensearch.ml.engine.algorithms.DLModelExecute;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.opensearch.index.query.QueryBuilders.termQuery;

@Log4j2
@NoArgsConstructor
@Function(FunctionName.METRICS_CORRELATION)
public class MetricsCorrelation extends DLModelExecute {

    private Client client;
    private Settings settings;

    private static final String MODEL_NAME = "METRICS_CORRELATION";
    public static final String MCORR_ML_VERSION = "1.0.0";

    public static final String MCORR_MODEL_URL =
            "https://github.com/dhrubo-os/semantic-os/raw/main/nmf_mcorr.zip";

    public MetricsCorrelation(Client client, Settings settings) {
        this.client = client;
        this.settings = settings;
    }

    private void searchModel(SearchRequest modelSearchRequest, ActionListener<Map<String, Object>> listener) {
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

    private void deployModel(MLDeployModelRequest mlDeployModelRequest, ActionListener<MLDeployModelResponse> listener) {
        client.execute(MLDeployModelAction.INSTANCE, mlDeployModelRequest, ActionListener.wrap(mlDeployModelResponse -> {
            listener.onResponse(mlDeployModelResponse);
        }, e -> {
            log.error("Failed to Load Model", e);
            listener.onFailure(e);
        }));
    }

    public void registerModel(ActionListener<MLRegisterModelResponse> listener) throws InterruptedException {

        FunctionName functionName = FunctionName.METRICS_CORRELATION;
        MLModelFormat modelFormat = MLModelFormat.TORCH_SCRIPT;
        String modelType = "custom";
        MLModelConfig modelConfig = MetricsCorrelationModelConfig.builder()
                .modelType(modelType)
                .allConfig(null).build();
        MLRegisterModelInput input = MLRegisterModelInput
                .builder()
                .functionName(functionName)
                .modelName(MODEL_NAME)
                .version(MCORR_ML_VERSION)
                .modelFormat(modelFormat)
                .modelConfig(modelConfig)
                .url(MCORR_MODEL_URL)
                .deployModel(true)
                .build();
        MLRegisterModelRequest registerRequest = MLRegisterModelRequest.builder().registerModelInput(input).build();

        client.execute(MLRegisterModelAction.INSTANCE, registerRequest, ActionListener.wrap(mlDeployModelResponse -> {
            listener.onResponse(mlDeployModelResponse);
        }, e -> {
            log.error("Failed to Register Model", e);
            listener.onFailure(e);
        }));
    }

    @Override
    public MetricsCorrelationOutput execute(Input input) throws Exception {
        if (!(input instanceof MetricsCorrelationInput)) {
            throw new IllegalArgumentException("wrong input");
        }
        List<MCorrModelTensors> tensorOutputs = new ArrayList<>();
        MetricsCorrelationInput metricsCorrelation = (MetricsCorrelationInput) input;
        List<float[]> inputData = metricsCorrelation.getInputData();

        float[][] processedInputData = processedInput(inputData);


        SearchRequest modelSearchRequest = getSearchRequest();
        searchModel(modelSearchRequest, ActionListener.wrap(modelInfo -> {
            if (modelInfo.isEmpty()) {
                registerModel(ActionListener.wrap(r -> {
                }, e -> log.error("Final problem")));
            } else {
                //TODO deploy model done with state: DEPLOY_FAILED all the time but I'm getting the output
                if (modelInfo.get(MLModel.MODEL_STATE_FIELD) != MLModelState.DEPLOYED ||
                        modelInfo.get(MLModel.MODEL_STATE_FIELD) != MLModelState.PARTIALLY_DEPLOYED) {
                    MLDeployModelRequest loadRequest = MLDeployModelRequest
                            .builder()
                            .modelId(modelId)
                            .async(false)
                            .dispatchTask(false)
                            .build();
                    deployModel(loadRequest, ActionListener.wrap(r -> {
                    }, e -> log.error("Another problem")));

                }
            }
        }, e -> {
            log.error("Model Index Not found", e);
            try {
                registerModel(ActionListener.wrap(r -> {
                }, ex -> log.error("Exception during registering the Metrics correlation model", ex)));
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }));

        //TODO Need to find out the best way to catch the event or notification
        // that all the tasks (upload/load) are done
        Thread.sleep(50000);

        for(float[] x: inputData)
            System.out.println(Arrays.toString(x));
        Output djlOutput = getPredictor().predict(processedInputData);
        tensorOutputs.add(parseModelTensorOutput(djlOutput, null));
        return new MetricsCorrelationOutput(tensorOutputs);
    }


    public float[][] processedInput(List<float[]> input) {
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

    public SearchRequest getSearchRequest() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.fetchSource(new String[] { MLModel.MODEL_ID_FIELD,
                        MLModel.MODEL_NAME_FIELD, MLModel.MODEL_STATE_FIELD, MLModel.MODEL_VERSION_FIELD },
                new String[] { MLModel.OLD_MODEL_CONTENT_FIELD, MLModel.MODEL_CONTENT_FIELD });

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
                .should(termQuery(MLModel.MODEL_NAME_FIELD, MODEL_NAME))
                .should(termQuery(MLModel.MODEL_VERSION_FIELD, MCORR_ML_VERSION));
        searchSourceBuilder.query(boolQueryBuilder);
        SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder)
                .indices(CommonValue.ML_MODEL_INDEX);
        return searchRequest;
    }
}
