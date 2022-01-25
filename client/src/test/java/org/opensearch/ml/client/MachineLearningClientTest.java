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
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLAlgoParams;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.common.parameter.MLOutput;
import org.opensearch.ml.common.parameter.MLTrainingOutput;
import org.opensearch.ml.common.parameter.Output;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

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

    private String modekId = "test_model_id";
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

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
            public void train(MLInput mlInput, ActionListener<MLOutput> listener) {
                listener.onResponse(MLTrainingOutput.builder().modelId(modekId).build());
            }

            @Override
            public void execute(Input input, ActionListener<Output> listener) {
                listener.onResponse(new Output() {
                    @Override
                    public void writeTo(StreamOutput out) {

                    }

                    @Override
                    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
                        builder.startObject();
                        builder.field("test", "test_value");
                        builder.endObject();
                        return builder;
                    }
                });
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
        assertEquals(modekId, ((MLTrainingOutput)machineLearningClient.train(mlInput).actionGet()).getModelId());
    }
}