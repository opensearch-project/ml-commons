/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.client;


import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.output.MLTrainingOutput;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.common.input.Constants.ACTION;
import static org.opensearch.ml.common.input.Constants.ALGORITHM;
import static org.opensearch.ml.common.input.Constants.KMEANS;
import static org.opensearch.ml.common.input.Constants.TRAIN;

public class MachineLearningClientTest {


    MachineLearningClient machineLearningClient;

    @Mock
    DataFrame input;

    @Mock
    MLOutput output;

    @Mock
    MLAlgoParams mlParameters;

    @Mock
    ActionListener<MLOutput> dataFrameActionListener;

    @Mock
    DeleteResponse deleteResponse;

    @Mock
    SearchResponse searchResponse;

    private String modekId = "test_model_id";
    private MLModel mlModel;
    private MLTask mlTask;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        String taskId = "taskId";
        String modelId = "modelId";
        mlTask = MLTask.builder()
                .taskId(taskId)
                .modelId(modelId)
                .functionName(FunctionName.KMEANS)
                .build();

        String modelContent = "test content";
        mlModel = MLModel.builder()
                .algorithm(FunctionName.KMEANS)
                .name("test")
                .content(modelContent)
                .build();

        machineLearningClient = new MachineLearningClient() {
            @Override
            public void predict(String modelId,
                                MLInput mlInput,
                                ActionListener<MLOutput> listener) {
                listener.onResponse(output);
            }

            @Override
            public void trainAndPredict(MLInput mlInput, ActionListener<MLOutput> listener) {
                listener.onResponse(output);
            }

            @Override
            public void train(MLInput mlInput, boolean asyncTask, ActionListener<MLOutput> listener) {
                listener.onResponse(MLTrainingOutput.builder().modelId(modekId).build());
            }

            @Override
            public void run(MLInput mlInput, Map<String, Object> args, ActionListener<MLOutput> listener) {
                listener.onResponse(output);
            }

            @Override
            public void getModel(String modelId, ActionListener<MLModel> listener) {
                listener.onResponse(mlModel);
            }

            @Override
            public void deleteModel(String modelId, ActionListener<DeleteResponse> listener) {
                listener.onResponse(deleteResponse);
            }

            @Override
            public void searchModel(SearchRequest searchRequest, ActionListener<SearchResponse> listener) {
                listener.onResponse(searchResponse);
            }

            @Override
            public void searchModelGroup(SearchRequest searchRequest, ActionListener<SearchResponse> listener) {
                listener.onResponse(searchResponse);
            }

            @Override
            public void getTask(String taskId, ActionListener<MLTask> listener) {
                listener.onResponse(mlTask);
            }

            @Override
            public void deleteTask(String taskId, ActionListener<DeleteResponse> listener) {
                listener.onResponse(deleteResponse);
            }

            @Override
            public void searchTask(SearchRequest searchRequest, ActionListener<SearchResponse> listener) {
                listener.onResponse(searchResponse);
            }
        };
    }

    @Test
    public void predict_WithAlgoAndInputData() {
        MLInput mlInput = MLInput.builder()
                .algorithm(FunctionName.KMEANS)
                .inputDataset(new DataFrameInputDataset(input))
                .build();
        assertEquals(output, machineLearningClient.predict(null, mlInput).actionGet());
    }

    @Test
    public void predict_WithAlgoAndParametersAndInputData() {
        MLInput mlInput = MLInput.builder()
                .algorithm(FunctionName.KMEANS)
                .parameters(mlParameters)
                .inputDataset(new DataFrameInputDataset(input))
                .build();
        assertEquals(output, machineLearningClient.predict(null, mlInput).actionGet());
    }

    @Test
    public void predict_WithAlgoAndParametersAndInputDataAndModelId() {
        MLInput mlInput = MLInput.builder()
                .algorithm(FunctionName.KMEANS)
                .parameters(mlParameters)
                .inputDataset(new DataFrameInputDataset(input))
                .build();
        assertEquals(output, machineLearningClient.predict("modelId", mlInput).actionGet());
    }

    @Test
    public void predict_WithAlgoAndInputDataAndListener() {
        MLInput mlInput = MLInput.builder()
                .algorithm(FunctionName.KMEANS)
                .inputDataset(new DataFrameInputDataset(input))
                .build();
        ArgumentCaptor<MLOutput> dataFrameArgumentCaptor = ArgumentCaptor.forClass(MLOutput.class);
        machineLearningClient.predict(null, mlInput, dataFrameActionListener);
        verify(dataFrameActionListener).onResponse(dataFrameArgumentCaptor.capture());
        assertEquals(output, dataFrameArgumentCaptor.getValue());
    }

    @Test
    public void predict_WithAlgoAndInputDataAndParametersAndListener() {
        MLInput mlInput = MLInput.builder()
                .algorithm(FunctionName.KMEANS)
                .parameters(mlParameters)
                .inputDataset(new DataFrameInputDataset(input))
                .build();
        ArgumentCaptor<MLOutput> dataFrameArgumentCaptor = ArgumentCaptor.forClass(MLOutput.class);
        machineLearningClient.predict(null, mlInput, dataFrameActionListener);
        verify(dataFrameActionListener).onResponse(dataFrameArgumentCaptor.capture());
        assertEquals(output, dataFrameArgumentCaptor.getValue());
    }

    @Test
    public void train() {
        MLInput mlInput = MLInput.builder()
                .algorithm(FunctionName.KMEANS)
                .parameters(mlParameters)
                .inputDataset(new DataFrameInputDataset(input))
                .build();
        assertEquals(modekId, ((MLTrainingOutput)machineLearningClient.train(mlInput, false).actionGet()).getModelId());
    }

    @Test
    public void trainAndPredict() {
        MLInput mlInput = MLInput.builder()
                .algorithm(FunctionName.KMEANS)
                .parameters(mlParameters)
                .inputDataset(new DataFrameInputDataset(input))
                .build();
        assertEquals(output, machineLearningClient.trainAndPredict(mlInput).actionGet());
    }

    @Test
    public void execute() {
        MLInput mlInput = MLInput.builder()
                .algorithm(FunctionName.SAMPLE_ALGO)
                .parameters(mlParameters)
                .inputDataset(new DataFrameInputDataset(input))
                .build();
        Map<String, Object> args = new HashMap<>();
        args.put(ACTION, TRAIN);
        args.put(ALGORITHM, KMEANS);
        assertEquals(output, machineLearningClient.run(mlInput, args).actionGet());
    }

    @Test
    public void getModel() {
        assertEquals(mlModel, machineLearningClient.getModel("modelId").actionGet());
    }

    @Test
    public void deleteModel() {
        assertEquals(deleteResponse, machineLearningClient.deleteModel("modelId").actionGet());
    }

    @Test
    public void searchModel() {
        assertEquals(searchResponse, machineLearningClient.searchModel(new SearchRequest()).actionGet());
    }

    @Test
    public void getTask() {
        assertEquals(mlTask, machineLearningClient.getTask("taskId").actionGet());
    }

    @Test
    public void deleteTask() {
        assertEquals(deleteResponse, machineLearningClient.deleteTask("taskId").actionGet());
    }

    @Test
    public void searchTask() {
        assertEquals(searchResponse, machineLearningClient.searchTask(new SearchRequest()).actionGet());
    }
}
