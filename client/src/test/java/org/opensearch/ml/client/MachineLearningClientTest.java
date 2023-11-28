/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.client;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.common.input.Constants.ACTION;
import static org.opensearch.ml.common.input.Constants.ALGORITHM;
import static org.opensearch.ml.common.input.Constants.KMEANS;
import static org.opensearch.ml.common.input.Constants.TRAIN;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.ToolMetadata;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLTrainingOutput;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupResponse;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelResponse;

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

    @Mock
    MLRegisterModelResponse registerModelResponse;

    @Mock
    MLDeployModelResponse deployModelResponse;

    @Mock
    MLCreateConnectorResponse createConnectorResponse;

    @Mock
    MLRegisterModelGroupResponse registerModelGroupResponse;

    @Mock
    MLRegisterAgentResponse registerAgentResponse;

    private String modekId = "test_model_id";
    private MLModel mlModel;
    private MLTask mlTask;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        String taskId = "taskId";
        String modelId = "modelId";
        mlTask = MLTask.builder().taskId(taskId).modelId(modelId).functionName(FunctionName.KMEANS).build();

        String modelContent = "test content";
        mlModel = MLModel.builder().algorithm(FunctionName.KMEANS).name("test").content(modelContent).build();

        machineLearningClient = new MachineLearningClient() {
            @Override
            public void predict(String modelId, MLInput mlInput, ActionListener<MLOutput> listener) {
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

            @Override
            public void register(MLRegisterModelInput mlInput, ActionListener<MLRegisterModelResponse> listener) {
                listener.onResponse(registerModelResponse);
            }

            @Override
            public void deploy(String modelId, ActionListener<MLDeployModelResponse> listener) {
                listener.onResponse(deployModelResponse);
            }

            @Override
            public void createConnector(MLCreateConnectorInput mlCreateConnectorInput, ActionListener<MLCreateConnectorResponse> listener) {
                listener.onResponse(createConnectorResponse);
            }

            @Override
            public void deleteConnector(String connectorId, ActionListener<DeleteResponse> listener) {
                listener.onResponse(deleteResponse);
            }

            public void registerModelGroup(
                MLRegisterModelGroupInput mlRegisterModelGroupInput,
                ActionListener<MLRegisterModelGroupResponse> listener
            ) {
                listener.onResponse(registerModelGroupResponse);
            }

            @Override
            public void listTools(ActionListener<List<ToolMetadata>> listener) {
                listener.onResponse(null);
            }

            @Override
            public void getTool(String toolName, ActionListener<ToolMetadata> listener) {
                listener.onResponse(null);
            }

            @Override
            public void registerAgent(MLAgent mlAgent, ActionListener<MLRegisterAgentResponse> listener) {
                listener.onResponse(registerAgentResponse);
            }
        };
    }

    @Test
    public void predict_WithAlgoAndInputData() {
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(new DataFrameInputDataset(input)).build();
        assertEquals(output, machineLearningClient.predict(null, mlInput).actionGet());
    }

    @Test
    public void predict_WithAlgoAndParametersAndInputData() {
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.KMEANS)
            .parameters(mlParameters)
            .inputDataset(new DataFrameInputDataset(input))
            .build();
        assertEquals(output, machineLearningClient.predict(null, mlInput).actionGet());
    }

    @Test
    public void predict_WithAlgoAndParametersAndInputDataAndModelId() {
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.KMEANS)
            .parameters(mlParameters)
            .inputDataset(new DataFrameInputDataset(input))
            .build();
        assertEquals(output, machineLearningClient.predict("modelId", mlInput).actionGet());
    }

    @Test
    public void predict_WithAlgoAndInputDataAndListener() {
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(new DataFrameInputDataset(input)).build();
        ArgumentCaptor<MLOutput> dataFrameArgumentCaptor = ArgumentCaptor.forClass(MLOutput.class);
        machineLearningClient.predict(null, mlInput, dataFrameActionListener);
        verify(dataFrameActionListener).onResponse(dataFrameArgumentCaptor.capture());
        assertEquals(output, dataFrameArgumentCaptor.getValue());
    }

    @Test
    public void predict_WithAlgoAndInputDataAndParametersAndListener() {
        MLInput mlInput = MLInput
            .builder()
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
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.KMEANS)
            .parameters(mlParameters)
            .inputDataset(new DataFrameInputDataset(input))
            .build();
        assertEquals(modekId, ((MLTrainingOutput) machineLearningClient.train(mlInput, false).actionGet()).getModelId());
    }

    @Test
    public void trainAndPredict() {
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.KMEANS)
            .parameters(mlParameters)
            .inputDataset(new DataFrameInputDataset(input))
            .build();
        assertEquals(output, machineLearningClient.trainAndPredict(mlInput).actionGet());
    }

    @Test
    public void execute() {
        MLInput mlInput = MLInput
            .builder()
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
    public void registerModelGroup() {
        List<String> backendRoles = Arrays.asList("IT", "HR");

        MLRegisterModelGroupInput mlRegisterModelGroupInput = MLRegisterModelGroupInput
            .builder()
            .name("test")
            .description("description")
            .backendRoles(backendRoles)
            .modelAccessMode(AccessMode.from("public"))
            .isAddAllBackendRoles(false)
            .build();

        assertEquals(registerModelGroupResponse, machineLearningClient.registerModelGroup(mlRegisterModelGroupInput).actionGet());
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

    @Test
    public void register() {
        MLModelConfig config = TextEmbeddingModelConfig
            .builder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(100)
            .build();
        MLRegisterModelInput mlInput = MLRegisterModelInput
            .builder()
            .functionName(FunctionName.KMEANS)
            .modelName("testModelName")
            .version("testModelVersion")
            .modelGroupId("modelGroupId")
            .url("url")
            .modelFormat(MLModelFormat.ONNX)
            .modelConfig(config)
            .deployModel(true)
            .modelNodeIds(new String[] { "modelNodeIds" })
            .build();
        assertEquals(registerModelResponse, machineLearningClient.register(mlInput).actionGet());
    }

    @Test
    public void deploy() {
        assertEquals(deployModelResponse, machineLearningClient.deploy("modelId").actionGet());
    }

    @Test
    public void createConnector() {
        Map<String, String> params = Map.ofEntries(Map.entry("endpoint", "endpoint"), Map.entry("temp", "7"));
        Map<String, String> credentials = Map.ofEntries(Map.entry("key1", "key1"), Map.entry("key2", "key2"));

        MLCreateConnectorInput mlCreateConnectorInput = MLCreateConnectorInput
            .builder()
            .name("test")
            .description("description")
            .version("testModelVersion")
            .protocol("testProtocol")
            .parameters(params)
            .credential(credentials)
            .actions(null)
            .backendRoles(null)
            .addAllBackendRoles(false)
            .access(AccessMode.from("private"))
            .dryRun(false)
            .build();

        assertEquals(createConnectorResponse, machineLearningClient.createConnector(mlCreateConnectorInput).actionGet());
    }

    @Test
    public void deleteConnector() {
        assertEquals(deleteResponse, machineLearningClient.deleteConnector("connectorId").actionGet());
    }

    @Test
    public void testRegisterAgent() {
        MLAgent mlAgent = MLAgent.builder().name("Agent name").build();
        assertEquals(registerAgentResponse, machineLearningClient.registerAgent(mlAgent).actionGet());
    }
}
