/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package org.opensearch.ml.client;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.common.parameter.MLOutput;
import org.opensearch.ml.common.parameter.MLPredictionOutput;
import org.opensearch.ml.common.parameter.MLTrainingOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;

import static org.junit.Assert.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

public class MachineLearningNodeClientTest {

    @Mock(answer = RETURNS_DEEP_STUBS)
    NodeClient client;

    @Mock
    MLInputDataset input;

    @Mock
    DataFrame output;

    @Mock
    ActionListener<MLOutput> dataFrameActionListener;

    @Mock
    ActionListener<MLOutput> trainingActionListener;

    @InjectMocks
    MachineLearningNodeClient machineLearningNodeClient;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void predict() {
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            MLPredictionOutput predictionOutput = MLPredictionOutput.builder()
                    .status("Success")
                    .predictionResult(output)
                    .taskId("taskId")
                    .build();
            actionListener.onResponse(MLTaskResponse.builder()
                    .output(predictionOutput)
                    .build());
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<MLOutput> dataFrameArgumentCaptor = ArgumentCaptor.forClass(MLOutput.class);
        MLInput mlInput = MLInput.builder()
                .algorithm(FunctionName.KMEANS)
                .inputDataset(input)
                .build();
        machineLearningNodeClient.predict(null, mlInput, dataFrameActionListener);

        verify(client).execute(eq(MLPredictionTaskAction.INSTANCE), isA(MLPredictionTaskRequest.class),
                any(ActionListener.class));
        verify(dataFrameActionListener).onResponse(dataFrameArgumentCaptor.capture());
        assertEquals(output, ((MLPredictionOutput)dataFrameArgumentCaptor.getValue()).getPredictionResult());
    }

    @Test
    public void predict_Exception_WithNullAlgorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("algorithm can't be null");
        MLInput mlInput = MLInput.builder()
                .inputDataset(input)
                .build();
        machineLearningNodeClient.predict(null, mlInput, dataFrameActionListener);
    }

    @Test
    public void predict_Exception_WithNullDataSet() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("input data set can't be null");
        MLInput mlInput = MLInput.builder()
                .algorithm(FunctionName.KMEANS)
                .build();
        machineLearningNodeClient.predict(null, mlInput, dataFrameActionListener);
    }

    @Test
    public void train() {
        String modelId = "test_model_id";
        String status = "InProgress";
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            MLTrainingOutput output = MLTrainingOutput.builder()
                    .status(status)
                    .modelId(modelId)
                    .build();
            actionListener.onResponse(MLTaskResponse.builder()
                    .output(output)
                    .build());
            return null;
        }).when(client).execute(eq(MLTrainingTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<MLOutput> argumentCaptor = ArgumentCaptor.forClass(MLOutput.class);
        MLInput mlInput = MLInput.builder()
                .algorithm(FunctionName.KMEANS)
                .inputDataset(input)
                .build();
        machineLearningNodeClient.train(mlInput, false, trainingActionListener);

        verify(client).execute(eq(MLTrainingTaskAction.INSTANCE), isA(MLTrainingTaskRequest.class),
                any(ActionListener.class));
        verify(trainingActionListener).onResponse(argumentCaptor.capture());
        assertEquals(modelId, ((MLTrainingOutput)argumentCaptor.getValue()).getModelId());
        assertEquals(status, ((MLTrainingOutput)argumentCaptor.getValue()).getStatus());
    }

    @Test
    public void train_Exception_WithNullDataSet() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("input data set can't be null");
        MLInput mlInput = MLInput.builder()
                .algorithm(FunctionName.KMEANS)
                .build();
        machineLearningNodeClient.train(mlInput, false, trainingActionListener);
    }

    @Test
    public void train_Exception_WithNullInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("ML Input can't be null");
        machineLearningNodeClient.train(null, false, trainingActionListener);
    }
}