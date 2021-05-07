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
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.training.MLTrainingTaskResponse;

import static org.junit.Assert.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MachineLearningNodeClientTest {

    @Mock(answer = RETURNS_DEEP_STUBS)
    NodeClient client;

    @Mock
    MLInputDataset input;

    @Mock
    DataFrame output;

    @Mock
    ActionListener<DataFrame> dataFrameActionListener;

    @Mock
    ActionListener<String> trainingActionListener;

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
            ActionListener<MLPredictionTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLPredictionTaskResponse.builder()
                    .status("Success")
                    .predictionResult(output)
                    .taskId("taskId")
                    .build());
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<DataFrame> dataFrameArgumentCaptor = ArgumentCaptor.forClass(DataFrame.class);
        machineLearningNodeClient.predict("algo", null, input, null, dataFrameActionListener);

        verify(client).execute(eq(MLPredictionTaskAction.INSTANCE), isA(MLPredictionTaskRequest.class),
                any(ActionListener.class));
        verify(dataFrameActionListener).onResponse(dataFrameArgumentCaptor.capture());
        assertEquals(output, dataFrameArgumentCaptor.getValue());
    }

    @Test
    public void predict_Exception_WithNullAlgorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("algorithm name can't be null or empty");
        machineLearningNodeClient.predict(null, null, input, null, dataFrameActionListener);
    }

    @Test
    public void predict_Exception_WithNullDataSet() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("input data set can't be null");
        machineLearningNodeClient.predict("algo", null, (MLInputDataset) null, null, dataFrameActionListener);
    }

    @Test
    public void train() {
        doAnswer(invocation -> {
            ActionListener<MLTrainingTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTrainingTaskResponse.builder()
                    .status("InProgress")
                    .taskId("taskId")
                    .build());
            return null;
        }).when(client).execute(eq(MLTrainingTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

        machineLearningNodeClient.train("algo", null, input, trainingActionListener);

        verify(client).execute(eq(MLTrainingTaskAction.INSTANCE), isA(MLTrainingTaskRequest.class),
                any(ActionListener.class));
        verify(trainingActionListener).onResponse(argumentCaptor.capture());
        assertEquals("taskId", argumentCaptor.getValue());
    }

    @Test
    public void train_Exception_WithNullAlgorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("algorithm name can't be null or empty");
        machineLearningNodeClient.train(null, null, input, trainingActionListener);
    }

    @Test
    public void train_Exception_WithNullDataSet() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("input data set can't be null");
        machineLearningNodeClient.train("algo", null, (MLInputDataset)null, trainingActionListener);
    }
}