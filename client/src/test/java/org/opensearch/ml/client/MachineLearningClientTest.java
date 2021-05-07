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


import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.parameter.MLParameter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

public class MachineLearningClientTest {


    MachineLearningClient machineLearningClient;

    @Mock
    DataFrame input;

    @Mock
    DataFrame output;

    @Mock
    List<MLParameter> mlParameters;

    @Mock
    ActionListener<DataFrame> dataFrameActionListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        machineLearningClient = new MachineLearningClient() {
            @Override
            public void predict(String algorithm, List<MLParameter> parameters, MLInputDataset inputData, String modelId,
                                ActionListener<DataFrame> listener) {
                listener.onResponse(output);
            }

            @Override
            public void train(String algorithm, List<MLParameter> parameters, MLInputDataset inputData,
                              ActionListener<String> listener) {
                listener.onResponse("taskId");
            }
        };
    }

    @Test
    public void predict_WithAlgoAndInputData() {
        assertEquals(output, machineLearningClient.predict("algo", input).actionGet());
    }

    @Test
    public void predict_WithAlgoAndParametersAndInputData() {
        assertEquals(output, machineLearningClient.predict("algo", mlParameters, input).actionGet());
    }

    @Test
    public void predict_WithAlgoAndParametersAndInputDataAndModelId() {
        assertEquals(output, machineLearningClient.predict("algo", mlParameters, input, "modelId").actionGet());
    }

    @Test
    public void predict_WithAlgoAndInputDataAndListener() {
        ArgumentCaptor<DataFrame> dataFrameArgumentCaptor = ArgumentCaptor.forClass(DataFrame.class);
        machineLearningClient.predict("algo", input, dataFrameActionListener);
        verify(dataFrameActionListener).onResponse(dataFrameArgumentCaptor.capture());
        assertEquals(output, dataFrameArgumentCaptor.getValue());
    }

    @Test
    public void predict_WithAlgoAndInputDataAndParametersAndListener() {
        ArgumentCaptor<DataFrame> dataFrameArgumentCaptor = ArgumentCaptor.forClass(DataFrame.class);
        machineLearningClient.predict("algo", mlParameters, input, dataFrameActionListener);
        verify(dataFrameActionListener).onResponse(dataFrameArgumentCaptor.capture());
        assertEquals(output, dataFrameArgumentCaptor.getValue());
    }

    @Test
    public void train() {
        assertEquals("taskId", machineLearningClient.train("algo", mlParameters, input).actionGet());
    }
}