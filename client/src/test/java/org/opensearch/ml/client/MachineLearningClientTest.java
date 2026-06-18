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

import java.time.Instant;
import java.util.ArrayList;
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
import org.opensearch.ml.common.Configuration;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.MLConfig;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.ToolMetadata;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.execute.metricscorrelation.MetricsCorrelationInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLTrainingOutput;
import org.opensearch.ml.common.transport.agent.MLAgentGetResponse;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;
import org.opensearch.ml.common.transport.config.MLConfigGetResponse;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupResponse;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsResponse;

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
    ActionListener<MLModel> mlModelActionListener;

    @Mock
    DeleteResponse deleteResponse;

    @Mock
    SearchResponse searchResponse;

    @Mock
    MLRegisterModelResponse registerModelResponse;

    @Mock
    MLDeployModelResponse deployModelResponse;

    @Mock
    MLUndeployModelsResponse undeployModelsResponse;

    @Mock
    MLCreateConnectorResponse createConnectorResponse;

    @Mock
    MLRegisterModelGroupResponse registerModelGroupResponse;

    @Mock
    MLExecuteTaskResponse mlExecuteTaskResponse;

    @Mock
    MLRegisterAgentResponse registerAgentResponse;

    @Mock
    MLAgentGetResponse getAgentResponse;

    @Mock
    MLConfigGetResponse configGetResponse;

    private final String modekId = "test_model_id";
    private MLModel mlModel;
    private MLTask mlTask;
    private MLConfig mlConfig;
    private ToolMetadata toolMetadata;
    private final List<ToolMetadata> toolsList = new ArrayList<>();

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        String taskId = "taskId";
        String modelId = "modelId";
        mlTask = MLTask.builder().taskId(taskId).modelId(modelId).functionName(FunctionName.KMEANS).build();

        String modelContent = "test content";
        mlModel = MLModel.builder().algorithm(FunctionName.KMEANS).name("test").content(modelContent).build();

        toolMetadata = ToolMetadata
            .builder()
            .name("MathTool")
            .description("Use this tool to calculate any math problem.")
            .type("MathTool")
            .version(null)
            .build();
        toolsList.add(toolMetadata);

        mlConfig = MLConfig
            .builder()
            .type("dummyType")
            .configuration(Configuration.builder().agentId("agentId").build())
            .createTime(Instant.now())
            .lastUpdateTime(Instant.now())
            .build();

        machineLearningClient = new MachineLearningClient() {

            @Override
            public void predict(String modelId, String tenantId, MLInput mlInput, ActionListener<MLOutput> listener) {
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
            public void getModel(String modelId, String tenantId, ActionListener<MLModel> listener) {
                listener.onResponse(mlModel);
            }

            @Override
            public void deleteModel(String modelId, String tenantId, ActionListener<DeleteResponse> listener) {
                listener.onResponse(deleteResponse);
            }

            @Override
            public void searchModel(SearchRequest searchRequest, ActionListener<SearchResponse> listener) {
                listener.onResponse(searchResponse);
            }

            @Override
            public void getTask(String taskId, String tenantId, ActionListener<MLTask> listener) {
                listener.onResponse(mlTask);
            }

            @Override
            public void deleteTask(String taskId, String tenantId, ActionListener<DeleteResponse> listener) {
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
            public void deploy(String modelId, String tenantId, ActionListener<MLDeployModelResponse> listener) {
                listener.onResponse(deployModelResponse);
            }

            @Override
            public void undeploy(String[] modelIds, String[] nodeIds, String tenantId, ActionListener<MLUndeployModelsResponse> listener) {
                listener.onResponse(undeployModelsResponse);
            }

            @Override
            public void createConnector(MLCreateConnectorInput mlCreateConnectorInput, ActionListener<MLCreateConnectorResponse> listener) {
                listener.onResponse(createConnectorResponse);
            }

            @Override
            public void execute(FunctionName name, Input input, ActionListener<MLExecuteTaskResponse> listener) {
                listener.onResponse(mlExecuteTaskResponse);
            }

            @Override
            public void deleteConnector(String connectorId, String tenantId, ActionListener<DeleteResponse> listener) {
                listener.onResponse(deleteResponse);
            }

            @Override
            public void listTools(ActionListener<List<ToolMetadata>> listener) {
                listener.onResponse(toolsList);
            }

            @Override
            public void getTool(String toolName, ActionListener<ToolMetadata> listener) {
                listener.onResponse(toolMetadata);
            }

            public void registerModelGroup(
                MLRegisterModelGroupInput mlRegisterModelGroupInput,
                ActionListener<MLRegisterModelGroupResponse> listener
            ) {
                listener.onResponse(registerModelGroupResponse);
            }

            @Override
            public void registerAgent(MLAgent mlAgent, ActionListener<MLRegisterAgentResponse> listener) {
                listener.onResponse(registerAgentResponse);
            }

            @Override
            public void getAgent(String agentId, ActionListener<MLAgentGetResponse> listener) {
                listener.onResponse(getAgentResponse);
            }

            @Override
            public void deleteAgent(String agentId, String tenantId, ActionListener<DeleteResponse> listener) {
                listener.onResponse(deleteResponse);
            }

            @Override
            public void getConfig(String configId, String tenantId, ActionListener<MLConfig> listener) {
                listener.onResponse(mlConfig);
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
        machineLearningClient.predict(null, null, mlInput, dataFrameActionListener);
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
    public void getModelActionListener() {
        ArgumentCaptor<MLModel> dataFrameArgumentCaptor = ArgumentCaptor.forClass(MLModel.class);
        machineLearningClient.getModel("modelId", mlModelActionListener);
        verify(mlModelActionListener).onResponse(dataFrameArgumentCaptor.capture());
        assertEquals(mlModel, dataFrameArgumentCaptor.getValue());
        assertEquals(mlModel.getTenantId(), dataFrameArgumentCaptor.getValue().getTenantId());
    }

    @Test
    public void undeploy_WithSpecificNodes() {
        String[] modelIds = new String[] { "model1", "model2" };
        String[] nodeIds = new String[] { "node1", "node2" };
        assertEquals(undeployModelsResponse, machineLearningClient.undeploy(modelIds, nodeIds).actionGet());
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
    public void deleteConnector_WithTenantId() {
        assertEquals(deleteResponse, machineLearningClient.deleteConnector("connectorId").actionGet());
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
    public void undeploy() {
        assertEquals(undeployModelsResponse, machineLearningClient.undeploy(new String[] { "modelId" }, null).actionGet());
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
            .protocol("http")
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
    public void executeMetricsCorrelation() {
        List<float[]> inputData = new ArrayList<>(
            List
                .of(
                    new float[] {
                        0.89451003f,
                        4.2006273f,
                        0.3697659f,
                        2.2458954f,
                        -4.671612f,
                        -1.5076426f,
                        1.635445f,
                        -1.1394824f,
                        -0.7503817f,
                        0.98424894f,
                        -0.38896716f,
                        1.0328646f,
                        1.9543738f,
                        -0.5236269f,
                        0.14298044f,
                        3.2963762f,
                        8.1641035f,
                        5.717064f,
                        7.4869685f,
                        2.5987444f,
                        11.018798f,
                        9.151356f,
                        5.7354255f,
                        6.862203f,
                        3.0524514f,
                        4.431755f,
                        5.1481285f,
                        7.9548607f,
                        7.4519925f,
                        6.09533f,
                        7.634116f,
                        8.898271f,
                        3.898491f,
                        9.447067f,
                        8.197385f,
                        5.8284273f,
                        5.804283f,
                        7.089733f,
                        9.140584f }
                )
        );
        Input metricsCorrelationInput = MetricsCorrelationInput.builder().inputData(inputData).build();
        assertEquals(
            mlExecuteTaskResponse,
            machineLearningClient.execute(FunctionName.METRICS_CORRELATION, metricsCorrelationInput).actionGet()
        );
    }

    @Test
    public void deleteConnector() {
        assertEquals(deleteResponse, machineLearningClient.deleteConnector("connectorId").actionGet());
    }

    @Test
    public void testRegisterAgent() {
        MLAgent mlAgent = MLAgent.builder().name("Agent name").type(MLAgentType.FLOW.name()).build();
        assertEquals(registerAgentResponse, machineLearningClient.registerAgent(mlAgent).actionGet());
    }

    @Test
    public void testGetAgent() {
        assertEquals(getAgentResponse, machineLearningClient.getAgent("agentId").actionGet());
    }

    @Test
    public void deleteAgent() {
        assertEquals(deleteResponse, machineLearningClient.deleteAgent("agentId").actionGet());
    }

    @Test
    public void getTool() {
        assertEquals(toolMetadata, machineLearningClient.getTool("MathTool").actionGet());
    }

    @Test
    public void listTools() {
        assertEquals(toolMetadata, machineLearningClient.listTools().actionGet().get(0));
    }

    @Test
    public void getConfig() {
        assertEquals(mlConfig, machineLearningClient.getConfig("configId").actionGet());
    }
}
