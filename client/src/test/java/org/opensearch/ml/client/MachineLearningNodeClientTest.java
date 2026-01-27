/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.common.CommonValue.MASTER_KEY;
import static org.opensearch.ml.common.input.Constants.ACTION;
import static org.opensearch.ml.common.input.Constants.ALGORITHM;
import static org.opensearch.ml.common.input.Constants.ASYNC;
import static org.opensearch.ml.common.input.Constants.KMEANS;
import static org.opensearch.ml.common.input.Constants.MODELID;
import static org.opensearch.ml.common.input.Constants.PREDICT;
import static org.opensearch.ml.common.input.Constants.RCF;
import static org.opensearch.ml.common.input.Constants.TRAIN;
import static org.opensearch.ml.common.input.Constants.TRAINANDPREDICT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.cluster.ClusterName;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.Configuration;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.MLConfig;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.ToolMetadata;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.execute.metricscorrelation.MetricsCorrelationInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.common.output.MLTrainingOutput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensor;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensors;
import org.opensearch.ml.common.output.execute.metrics_correlation.MetricsCorrelationOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteAction;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteRequest;
import org.opensearch.ml.common.transport.agent.MLAgentGetAction;
import org.opensearch.ml.common.transport.agent.MLAgentGetRequest;
import org.opensearch.ml.common.transport.agent.MLAgentGetResponse;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentAction;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentRequest;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;
import org.opensearch.ml.common.transport.config.MLConfigGetAction;
import org.opensearch.ml.common.transport.config.MLConfigGetRequest;
import org.opensearch.ml.common.transport.config.MLConfigGetResponse;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteAction;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteRequest;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorRequest;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupRequest;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.register.MLRegisterModelAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelRequest;
import org.opensearch.ml.common.transport.register.MLRegisterModelResponse;
import org.opensearch.ml.common.transport.task.MLTaskDeleteAction;
import org.opensearch.ml.common.transport.task.MLTaskDeleteRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetResponse;
import org.opensearch.ml.common.transport.task.MLTaskSearchAction;
import org.opensearch.ml.common.transport.tools.MLGetToolAction;
import org.opensearch.ml.common.transport.tools.MLListToolsAction;
import org.opensearch.ml.common.transport.tools.MLToolGetRequest;
import org.opensearch.ml.common.transport.tools.MLToolGetResponse;
import org.opensearch.ml.common.transport.tools.MLToolsListRequest;
import org.opensearch.ml.common.transport.tools.MLToolsListResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;
import org.opensearch.transport.client.node.NodeClient;

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

    @Mock
    ActionListener<MLModel> getModelActionListener;

    @Mock
    ActionListener<DeleteResponse> deleteModelActionListener;

    @Mock
    ActionListener<SearchResponse> searchModelActionListener;

    @Mock
    ActionListener<MLTask> getTaskActionListener;

    @Mock
    ActionListener<DeleteResponse> deleteTaskActionListener;

    @Mock
    ActionListener<SearchResponse> searchTaskActionListener;

    @Mock
    ActionListener<MLRegisterModelResponse> registerModelActionListener;

    @Mock
    ActionListener<MLDeployModelResponse> deployModelActionListener;

    @Mock
    ActionListener<MLUndeployModelsResponse> undeployModelsActionListener;

    @Mock
    ActionListener<MLCreateConnectorResponse> createConnectorActionListener;

    @Mock
    ActionListener<DeleteResponse> deleteConnectorActionListener;

    @Mock
    ActionListener<MLRegisterModelGroupResponse> registerModelGroupResponseActionListener;

    @Mock
    ActionListener<MLExecuteTaskResponse> executeTaskResponseActionListener;

    @Mock
    ActionListener<MLRegisterAgentResponse> registerAgentResponseActionListener;

    @Mock
    ActionListener<MLAgentGetResponse> getAgentResponseActionListener;

    @Mock
    ActionListener<DeleteResponse> deleteAgentActionListener;

    @Mock
    ActionListener<List<ToolMetadata>> listToolsActionListener;

    @Mock
    ActionListener<ToolMetadata> getToolActionListener;

    @Mock
    ActionListener<MLConfig> getMlConfigListener;

    @InjectMocks
    MachineLearningNodeClient machineLearningNodeClient;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void predict() {
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            MLPredictionOutput predictionOutput = MLPredictionOutput
                .builder()
                .status("Success")
                .predictionResult(output)
                .taskId("taskId")
                .build();
            actionListener.onResponse(MLTaskResponse.builder().output(predictionOutput).build());
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<MLOutput> dataFrameArgumentCaptor = ArgumentCaptor.forClass(MLOutput.class);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(input).build();
        machineLearningNodeClient.predict(null, mlInput, dataFrameActionListener);

        verify(client).execute(eq(MLPredictionTaskAction.INSTANCE), isA(MLPredictionTaskRequest.class), any());
        verify(dataFrameActionListener).onResponse(dataFrameArgumentCaptor.capture());
        assertEquals(output, ((MLPredictionOutput) dataFrameArgumentCaptor.getValue()).getPredictionResult());
    }

    @Test
    public void execute_train_asyncTask() {
        String modelId = "test_model_id";
        String status = "InProgress";
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            MLTrainingOutput output = MLTrainingOutput.builder().status(status).modelId(modelId).build();
            actionListener.onResponse(MLTaskResponse.builder().output(output).build());
            return null;
        }).when(client).execute(eq(MLTrainingTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<MLOutput> argumentCaptor = ArgumentCaptor.forClass(MLOutput.class);
        Map<String, Object> args = new HashMap<>();
        args.put(ACTION, TRAIN);
        args.put(ALGORITHM, KMEANS);
        args.put(ASYNC, true);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.SAMPLE_ALGO).inputDataset(input).build();
        machineLearningNodeClient.run(mlInput, args, trainingActionListener);

        verify(client).execute(eq(MLTrainingTaskAction.INSTANCE), isA(MLTrainingTaskRequest.class), any());
        verify(trainingActionListener).onResponse(argumentCaptor.capture());
        assertEquals(modelId, ((MLTrainingOutput) argumentCaptor.getValue()).getModelId());
        assertEquals(status, ((MLTrainingOutput) argumentCaptor.getValue()).getStatus());
    }

    @Test
    public void execute_predict_missing_modelId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("The model ID is required for prediction.");
        Map<String, Object> args = new HashMap<>();
        args.put(ACTION, PREDICT);
        args.put(ALGORITHM, KMEANS);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.SAMPLE_ALGO).inputDataset(input).build();
        machineLearningNodeClient.run(mlInput, args, dataFrameActionListener);
    }

    @Test
    public void predict_Exception_WithNullAlgorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("algorithm can't be null");
        MLInput mlInput = MLInput.builder().inputDataset(input).build();
        machineLearningNodeClient.predict(null, mlInput, dataFrameActionListener);
    }

    @Test
    public void predict_Exception_WithNullDataSet() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("input data set can't be null");
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).build();
        machineLearningNodeClient.predict(null, mlInput, dataFrameActionListener);
    }

    @Test
    public void train() {
        String modelId = "test_model_id";
        String status = "InProgress";
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            MLTrainingOutput output = MLTrainingOutput.builder().status(status).modelId(modelId).build();
            actionListener.onResponse(MLTaskResponse.builder().output(output).build());
            return null;
        }).when(client).execute(eq(MLTrainingTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<MLOutput> argumentCaptor = ArgumentCaptor.forClass(MLOutput.class);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(input).build();
        machineLearningNodeClient.train(mlInput, false, trainingActionListener);

        verify(client).execute(eq(MLTrainingTaskAction.INSTANCE), isA(MLTrainingTaskRequest.class), any());
        verify(trainingActionListener).onResponse(argumentCaptor.capture());
        assertEquals(modelId, ((MLTrainingOutput) argumentCaptor.getValue()).getModelId());
        assertEquals(status, ((MLTrainingOutput) argumentCaptor.getValue()).getStatus());
    }

    @Test
    public void getModel_withTenantId() {
        String modelContent = "test content";
        String tenantId = "tenantId";
        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> actionListener = invocation.getArgument(2);
            MLModel mlModel = MLModel.builder().algorithm(FunctionName.KMEANS).name("test").content(modelContent).build();
            MLModelGetResponse output = MLModelGetResponse.builder().mlModel(mlModel).build();
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLModelGetAction.INSTANCE), any(), any());

        ArgumentCaptor<MLModel> argumentCaptor = ArgumentCaptor.forClass(MLModel.class);
        machineLearningNodeClient.getModel("modelId", tenantId, getModelActionListener);

        verify(client).execute(eq(MLModelGetAction.INSTANCE), isA(MLModelGetRequest.class), any());
        verify(getModelActionListener).onResponse(argumentCaptor.capture());
        assertEquals(FunctionName.KMEANS, argumentCaptor.getValue().getAlgorithm());
        assertEquals(modelContent, argumentCaptor.getValue().getContent());
    }

    @Test
    public void undeployModels_withNullNodeIds() {
        doAnswer(invocation -> {
            ActionListener<MLUndeployModelsResponse> actionListener = invocation.getArgument(2);
            MLUndeployModelsResponse output = new MLUndeployModelsResponse(
                new MLUndeployModelNodesResponse(ClusterName.DEFAULT, Collections.emptyList(), Collections.emptyList())
            );
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLUndeployModelsAction.INSTANCE), any(), any());

        machineLearningNodeClient.undeploy(new String[] { "model1" }, null, undeployModelsActionListener);
        verify(client).execute(eq(MLUndeployModelsAction.INSTANCE), isA(MLUndeployModelsRequest.class), any());
    }

    @Test
    public void createConnector_withValidInput() {
        doAnswer(invocation -> {
            ActionListener<MLCreateConnectorResponse> actionListener = invocation.getArgument(2);
            MLCreateConnectorResponse output = new MLCreateConnectorResponse("connectorId");
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLCreateConnectorAction.INSTANCE), any(), any());

        MLCreateConnectorInput input = MLCreateConnectorInput
            .builder()
            .name("testConnector")
            .protocol("http")
            .version("1")
            .credential(Map.of("TEST_CREDENTIAL_KEY", "TEST_CREDENTIAL_VALUE"))
            .parameters(Map.of("endpoint", "https://example.com"))
            .build();

        machineLearningNodeClient.createConnector(input, createConnectorActionListener);
        verify(client).execute(eq(MLCreateConnectorAction.INSTANCE), isA(MLCreateConnectorRequest.class), any());
    }

    @Test
    public void registerModelGroup_withValidInput() {
        doAnswer(invocation -> {
            ActionListener<MLRegisterModelGroupResponse> actionListener = invocation.getArgument(2);
            MLRegisterModelGroupResponse output = new MLRegisterModelGroupResponse("groupId", "created");
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLRegisterModelGroupAction.INSTANCE), any(), any());

        MLRegisterModelGroupInput input = MLRegisterModelGroupInput
            .builder()
            .name("test")
            .description("description")
            .backendRoles(Arrays.asList("role1", "role2"))
            .modelAccessMode(AccessMode.PUBLIC)
            .build();

        machineLearningNodeClient.registerModelGroup(input, registerModelGroupResponseActionListener);
        verify(client).execute(eq(MLRegisterModelGroupAction.INSTANCE), isA(MLRegisterModelGroupRequest.class), any());
    }

    @Test
    public void listTools_withValidRequest() {
        doAnswer(invocation -> {
            ActionListener<MLToolsListResponse> actionListener = invocation.getArgument(2);
            MLToolsListResponse output = MLToolsListResponse
                .builder()
                .toolMetadata(
                    Arrays
                        .asList(
                            ToolMetadata.builder().name("tool1").description("description1").build(),
                            ToolMetadata.builder().name("tool2").description("description2").build()
                        )
                )
                .build();
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLListToolsAction.INSTANCE), any(), any());

        machineLearningNodeClient.listTools(listToolsActionListener);
        verify(client).execute(eq(MLListToolsAction.INSTANCE), isA(MLToolsListRequest.class), any());
    }

    @Test
    public void listTools_withEmptyResponse() {
        doAnswer(invocation -> {
            ActionListener<MLToolsListResponse> actionListener = invocation.getArgument(2);
            MLToolsListResponse output = MLToolsListResponse.builder().toolMetadata(Collections.emptyList()).build();
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLListToolsAction.INSTANCE), any(), any());

        ArgumentCaptor<List<ToolMetadata>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        machineLearningNodeClient.listTools(listToolsActionListener);

        verify(client).execute(eq(MLListToolsAction.INSTANCE), isA(MLToolsListRequest.class), any());
        verify(listToolsActionListener).onResponse(argumentCaptor.capture());

        List<ToolMetadata> capturedTools = argumentCaptor.getValue();
        assertTrue(capturedTools.isEmpty());
    }

    @Test
    public void getTool_withValidToolName() {
        doAnswer(invocation -> {
            ActionListener<MLToolGetResponse> actionListener = invocation.getArgument(2);
            MLToolGetResponse output = MLToolGetResponse
                .builder()
                .toolMetadata(ToolMetadata.builder().name("tool1").description("description1").build())
                .build();
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLGetToolAction.INSTANCE), any(), any());

        machineLearningNodeClient.getTool("tool1", getToolActionListener);
        verify(client).execute(eq(MLGetToolAction.INSTANCE), isA(MLToolGetRequest.class), any());
    }

    @Test
    public void getTool_withValidRequest() {
        ToolMetadata toolMetadata = ToolMetadata
            .builder()
            .name("MathTool")
            .description("Use this tool to calculate any math problem.")
            .build();

        doAnswer(invocation -> {
            ActionListener<MLToolGetResponse> actionListener = invocation.getArgument(2);
            MLToolGetResponse output = MLToolGetResponse.builder().toolMetadata(toolMetadata).build();
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLGetToolAction.INSTANCE), any(), any());

        ArgumentCaptor<ToolMetadata> argumentCaptor = ArgumentCaptor.forClass(ToolMetadata.class);
        machineLearningNodeClient.getTool("MathTool", getToolActionListener);

        verify(client).execute(eq(MLGetToolAction.INSTANCE), isA(MLToolGetRequest.class), any());
        verify(getToolActionListener).onResponse(argumentCaptor.capture());

        ToolMetadata capturedTool = argumentCaptor.getValue();
        assertEquals("MathTool", capturedTool.getName());
        assertEquals("Use this tool to calculate any math problem.", capturedTool.getDescription());
    }

    @Test
    public void getTool_withFailureResponse() {
        doAnswer(invocation -> {
            ActionListener<MLToolGetResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("Test exception"));
            return null;
        }).when(client).execute(eq(MLGetToolAction.INSTANCE), any(), any());

        machineLearningNodeClient.getTool("MathTool", new ActionListener<>() {
            @Override
            public void onResponse(ToolMetadata toolMetadata) {
                fail("Expected failure but got response");
            }

            @Override
            public void onFailure(Exception e) {
                assertEquals("Test exception", e.getMessage());
            }
        });

        verify(client).execute(eq(MLGetToolAction.INSTANCE), isA(MLToolGetRequest.class), any());
    }

    @Test
    public void train_withAsync() {
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            MLTrainingOutput output = MLTrainingOutput.builder().status("InProgress").modelId("modelId").build();
            actionListener.onResponse(MLTaskResponse.builder().output(output).build());
            return null;
        }).when(client).execute(eq(MLTrainingTaskAction.INSTANCE), any(), any());

        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(input).build();
        machineLearningNodeClient.train(mlInput, true, trainingActionListener);
        verify(client).execute(eq(MLTrainingTaskAction.INSTANCE), isA(MLTrainingTaskRequest.class), any());
    }

    @Test
    public void deleteModel_withTenantId() {
        String modelId = "testModelId";
        String tenantId = "tenantId";
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            DeleteResponse output = new DeleteResponse(shardId, modelId, 1, 1, 1, true);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLModelDeleteAction.INSTANCE), any(), any());

        ArgumentCaptor<DeleteResponse> argumentCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        machineLearningNodeClient.deleteModel(modelId, tenantId, deleteModelActionListener);

        verify(client).execute(eq(MLModelDeleteAction.INSTANCE), isA(MLModelDeleteRequest.class), any());
        verify(deleteModelActionListener).onResponse(argumentCaptor.capture());
        assertEquals(modelId, argumentCaptor.getValue().getId());
    }

    @Test
    public void train_Exception_WithNullDataSet() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("input data set can't be null");
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).build();
        machineLearningNodeClient.train(mlInput, false, trainingActionListener);
    }

    @Test
    public void train_Exception_WithNullInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("ML Input can't be null");
        machineLearningNodeClient.train(null, false, trainingActionListener);
    }

    @Test
    public void trainAndPredict() {
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            MLPredictionOutput predictionOutput = MLPredictionOutput
                .builder()
                .status(MLTaskState.COMPLETED.name())
                .predictionResult(output)
                .taskId("taskId")
                .build();
            actionListener.onResponse(MLTaskResponse.builder().output(predictionOutput).build());
            return null;
        }).when(client).execute(eq(MLTrainAndPredictionTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<MLOutput> argumentCaptor = ArgumentCaptor.forClass(MLOutput.class);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(input).build();
        machineLearningNodeClient.trainAndPredict(mlInput, trainingActionListener);

        verify(client).execute(eq(MLTrainAndPredictionTaskAction.INSTANCE), isA(MLTrainingTaskRequest.class), any());
        verify(trainingActionListener).onResponse(argumentCaptor.capture());
        assertEquals(MLTaskState.COMPLETED.name(), ((MLPredictionOutput) argumentCaptor.getValue()).getStatus());
        assertEquals(output, ((MLPredictionOutput) argumentCaptor.getValue()).getPredictionResult());
    }

    @Test
    public void execute_predict() {
        Map<String, Object> args = new HashMap<>();
        args.put(ACTION, PREDICT);
        args.put(ALGORITHM, KMEANS);
        args.put(MODELID, "123");
        execute_predict(args);
    }

    @Test
    public void execute_predict_null_model_id() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("The model ID is required for prediction.");
        Map<String, Object> args = new HashMap<>();
        args.put(ACTION, PREDICT);
        args.put(ALGORITHM, KMEANS);
        execute_predict(args);
    }

    private void execute_predict(Map<String, Object> args) {
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            MLPredictionOutput predictionOutput = MLPredictionOutput
                .builder()
                .status("Success")
                .predictionResult(output)
                .taskId("taskId")
                .build();
            actionListener.onResponse(MLTaskResponse.builder().output(predictionOutput).build());
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<MLOutput> dataFrameArgumentCaptor = ArgumentCaptor.forClass(MLOutput.class);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.SAMPLE_ALGO).inputDataset(input).build();
        machineLearningNodeClient.run(mlInput, args, dataFrameActionListener);

        verify(client).execute(eq(MLPredictionTaskAction.INSTANCE), isA(MLPredictionTaskRequest.class), any());
        verify(dataFrameActionListener).onResponse(dataFrameArgumentCaptor.capture());
        assertEquals(output, ((MLPredictionOutput) dataFrameArgumentCaptor.getValue()).getPredictionResult());
    }

    @Test
    public void execute_train() {
        String modelId = "test_model_id";
        String status = "InProgress";
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            MLTrainingOutput output = MLTrainingOutput.builder().status(status).modelId(modelId).build();
            actionListener.onResponse(MLTaskResponse.builder().output(output).build());
            return null;
        }).when(client).execute(eq(MLTrainingTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<MLOutput> argumentCaptor = ArgumentCaptor.forClass(MLOutput.class);
        Map<String, Object> args = new HashMap<>();
        args.put(ACTION, TRAIN);
        args.put(ALGORITHM, KMEANS);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.SAMPLE_ALGO).inputDataset(input).build();
        machineLearningNodeClient.run(mlInput, args, trainingActionListener);

        verify(client).execute(eq(MLTrainingTaskAction.INSTANCE), isA(MLTrainingTaskRequest.class), any());
        verify(trainingActionListener).onResponse(argumentCaptor.capture());
        assertEquals(modelId, ((MLTrainingOutput) argumentCaptor.getValue()).getModelId());
        assertEquals(status, ((MLTrainingOutput) argumentCaptor.getValue()).getStatus());
    }

    @Test
    public void execute_null_action() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("The parameter action is required.");
        Map<String, Object> args = new HashMap<>();
        args.put(ALGORITHM, KMEANS);
        execute_trainandpredict(args);
    }

    @Test
    public void execute_unsupported_action() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unsupported action.");
        Map<String, Object> args = new HashMap<>();
        args.put(ACTION, "unsupported");
        args.put(ALGORITHM, KMEANS);
        execute_trainandpredict(args);
    }

    @Test
    public void execute_null_algorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("The parameter algorithm is required.");
        Map<String, Object> args = new HashMap<>();
        args.put(ACTION, TRAINANDPREDICT);
        execute_trainandpredict(args);
    }

    @Test
    public void execute_unsupported_algorithm() {
        String algo = "dummyAlgo";
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(String.format("unsupported algorithm: %s.", algo));
        Map<String, Object> args = new HashMap<>();
        args.put(ACTION, TRAINANDPREDICT);
        args.put(ALGORITHM, algo);
        execute_trainandpredict(args);
    }

    @Test
    public void execute_trainandpredict_kmeans() {
        Map<String, Object> args = new HashMap<>();
        args.put(ACTION, TRAINANDPREDICT);
        args.put(ALGORITHM, KMEANS);
        execute_trainandpredict(args);
    }

    @Test
    public void execute_trainandpredict_batch_rcf() {
        Map<String, Object> args = new HashMap<>();
        args.put(ACTION, TRAINANDPREDICT);
        args.put(ALGORITHM, RCF);
        execute_trainandpredict(args);
    }

    @Test
    public void execute_trainandpredict_fit_rcf() {
        Map<String, Object> args = new HashMap<>();
        args.put(ACTION, TRAINANDPREDICT);
        args.put(ALGORITHM, RCF);
        args.put("timeField", "ts");
        execute_trainandpredict(args);
    }

    private void execute_trainandpredict(Map<String, Object> args) {
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            MLPredictionOutput predictionOutput = MLPredictionOutput
                .builder()
                .status(MLTaskState.COMPLETED.name())
                .predictionResult(output)
                .taskId("taskId")
                .build();
            actionListener.onResponse(MLTaskResponse.builder().output(predictionOutput).build());
            return null;
        }).when(client).execute(eq(MLTrainAndPredictionTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<MLOutput> argumentCaptor = ArgumentCaptor.forClass(MLOutput.class);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.SAMPLE_ALGO).inputDataset(input).build();
        machineLearningNodeClient.run(mlInput, args, trainingActionListener);

        verify(client).execute(eq(MLTrainAndPredictionTaskAction.INSTANCE), isA(MLTrainingTaskRequest.class), any());
        verify(trainingActionListener).onResponse(argumentCaptor.capture());
        assertEquals(MLTaskState.COMPLETED.name(), ((MLPredictionOutput) argumentCaptor.getValue()).getStatus());
        assertEquals(output, ((MLPredictionOutput) argumentCaptor.getValue()).getPredictionResult());
    }

    @Test
    public void getModel() {
        String modelContent = "test content";
        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> actionListener = invocation.getArgument(2);
            MLModel mlModel = MLModel.builder().algorithm(FunctionName.KMEANS).name("test").content(modelContent).build();
            MLModelGetResponse output = MLModelGetResponse.builder().mlModel(mlModel).build();
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLModelGetAction.INSTANCE), any(), any());

        ArgumentCaptor<MLModel> argumentCaptor = ArgumentCaptor.forClass(MLModel.class);
        machineLearningNodeClient.getModel("modelId", getModelActionListener);

        verify(client).execute(eq(MLModelGetAction.INSTANCE), isA(MLModelGetRequest.class), any());
        verify(getModelActionListener).onResponse(argumentCaptor.capture());
        assertEquals(FunctionName.KMEANS, argumentCaptor.getValue().getAlgorithm());
        assertEquals(modelContent, argumentCaptor.getValue().getContent());
    }

    @Test
    public void deleteConnector_withTenantId() {
        String connectorId = "connectorId";
        String tenantId = "tenantId";
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            DeleteResponse output = new DeleteResponse(shardId, connectorId, 1, 1, 1, true);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLConnectorDeleteAction.INSTANCE), any(), any());

        ArgumentCaptor<DeleteResponse> argumentCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        machineLearningNodeClient.deleteConnector(connectorId, tenantId, deleteConnectorActionListener);

        verify(client).execute(eq(MLConnectorDeleteAction.INSTANCE), isA(MLConnectorDeleteRequest.class), any());
        verify(deleteConnectorActionListener).onResponse(argumentCaptor.capture());
        assertEquals(connectorId, (argumentCaptor.getValue()).getId());
    }

    @Test
    public void deleteModel() {
        String modelId = "testModelId";
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            DeleteResponse output = new DeleteResponse(shardId, modelId, 1, 1, 1, true);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLModelDeleteAction.INSTANCE), any(), any());

        ArgumentCaptor<DeleteResponse> argumentCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        machineLearningNodeClient.deleteModel(modelId, deleteModelActionListener);

        verify(client).execute(eq(MLModelDeleteAction.INSTANCE), isA(MLModelDeleteRequest.class), any());
        verify(deleteModelActionListener).onResponse(argumentCaptor.capture());
        assertEquals(modelId, argumentCaptor.getValue().getId());
    }

    @Test
    public void searchModel() {
        String modelContent = "test content";
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(2);
            MLModel mlModel = MLModel.builder().algorithm(FunctionName.KMEANS).name("test").content(modelContent).build();
            SearchResponse output = createSearchResponse(mlModel);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLModelSearchAction.INSTANCE), any(), any());

        ArgumentCaptor<SearchResponse> argumentCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        SearchRequest searchREquest = new SearchRequest();
        machineLearningNodeClient.searchModel(searchREquest, searchModelActionListener);

        verify(client).execute(eq(MLModelSearchAction.INSTANCE), isA(SearchRequest.class), any());
        verify(searchModelActionListener).onResponse(argumentCaptor.capture());
        Map<String, Object> source = argumentCaptor.getValue().getHits().getAt(0).getSourceAsMap();
        assertEquals(modelContent, source.get(MLModel.MODEL_CONTENT_FIELD));
    }

    @Test
    public void registerModelGroup() {

        String modelGroupId = "modeGroupId";
        String status = MLTaskState.CREATED.name();

        doAnswer(invocation -> {
            ActionListener<MLRegisterModelGroupResponse> actionListener = invocation.getArgument(2);
            MLRegisterModelGroupResponse output = new MLRegisterModelGroupResponse(modelGroupId, status);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLRegisterModelGroupAction.INSTANCE), any(), any());

        ArgumentCaptor<MLRegisterModelGroupResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelGroupResponse.class);

        List<String> backendRoles = Arrays.asList("IT", "HR");

        MLRegisterModelGroupInput mlRegisterModelGroupInput = MLRegisterModelGroupInput
            .builder()
            .name("test")
            .description("description")
            .backendRoles(backendRoles)
            .modelAccessMode(AccessMode.from("public"))
            .isAddAllBackendRoles(false)
            .build();

        machineLearningNodeClient.registerModelGroup(mlRegisterModelGroupInput, registerModelGroupResponseActionListener);

        verify(client).execute(eq(MLRegisterModelGroupAction.INSTANCE), isA(MLRegisterModelGroupRequest.class), any());
        verify(registerModelGroupResponseActionListener).onResponse(argumentCaptor.capture());
        assertEquals(modelGroupId, (argumentCaptor.getValue().getModelGroupId()));
        assertEquals(status, (argumentCaptor.getValue().getStatus()));
    }

    @Test
    public void getTask() {
        String taskId = "taskId";
        String modelId = "modelId";
        doAnswer(invocation -> {
            ActionListener<MLTaskGetResponse> actionListener = invocation.getArgument(2);
            MLTask mlTask = MLTask.builder().taskId(taskId).modelId(modelId).functionName(FunctionName.KMEANS).build();
            MLTaskGetResponse output = MLTaskGetResponse.builder().mlTask(mlTask).build();
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLTaskGetAction.INSTANCE), any(), any());

        ArgumentCaptor<MLTask> argumentCaptor = ArgumentCaptor.forClass(MLTask.class);
        machineLearningNodeClient.getTask(taskId, getTaskActionListener);

        verify(client).execute(eq(MLTaskGetAction.INSTANCE), isA(MLTaskGetRequest.class), any());
        verify(getTaskActionListener).onResponse(argumentCaptor.capture());
        assertEquals(FunctionName.KMEANS, argumentCaptor.getValue().getFunctionName());
        assertEquals(modelId, argumentCaptor.getValue().getModelId());
        assertEquals(taskId, argumentCaptor.getValue().getTaskId());
    }

    @Test
    public void deleteTask() {
        String taskId = "taskId";
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            DeleteResponse output = new DeleteResponse(shardId, taskId, 1, 1, 1, true);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLTaskDeleteAction.INSTANCE), any(), any());

        ArgumentCaptor<DeleteResponse> argumentCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        machineLearningNodeClient.deleteTask(taskId, null, deleteTaskActionListener);

        verify(client).execute(eq(MLTaskDeleteAction.INSTANCE), isA(MLTaskDeleteRequest.class), any());
        verify(deleteTaskActionListener).onResponse(argumentCaptor.capture());
        assertEquals(taskId, argumentCaptor.getValue().getId());
    }

    @Test
    public void searchTask() {
        String taskId = "taskId";
        String modelId = "modelId";
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(2);
            MLTask mlTask = MLTask.builder().taskId(taskId).modelId(modelId).functionName(FunctionName.KMEANS).build();
            SearchResponse output = createSearchResponse(mlTask);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLTaskSearchAction.INSTANCE), any(), any());

        ArgumentCaptor<SearchResponse> argumentCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        SearchRequest searchREquest = new SearchRequest();
        machineLearningNodeClient.searchTask(searchREquest, searchTaskActionListener);

        verify(client).execute(eq(MLTaskSearchAction.INSTANCE), isA(SearchRequest.class), any());
        verify(searchTaskActionListener).onResponse(argumentCaptor.capture());
        assertEquals(1, argumentCaptor.getValue().getHits().getTotalHits().value());
        Map<String, Object> source = argumentCaptor.getValue().getHits().getAt(0).getSourceAsMap();
        assertEquals(taskId, source.get(MLTask.TASK_ID_FIELD));
        assertEquals(modelId, source.get(MLTask.MODEL_ID_FIELD));
    }

    @Test
    public void register() {
        String taskId = "taskId";
        String status = MLTaskState.CREATED.name();
        FunctionName functionName = FunctionName.KMEANS;
        doAnswer(invocation -> {
            ActionListener<MLRegisterModelResponse> actionListener = invocation.getArgument(2);
            MLRegisterModelResponse output = new MLRegisterModelResponse(taskId, status);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLRegisterModelAction.INSTANCE), any(), any());

        ArgumentCaptor<MLRegisterModelResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelResponse.class);
        MLModelConfig config = TextEmbeddingModelConfig
            .builder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(100)
            .build();
        MLRegisterModelInput mlInput = MLRegisterModelInput
            .builder()
            .functionName(functionName)
            .modelName("testModelName")
            .version("testModelVersion")
            .modelGroupId("modelGroupId")
            .url("url")
            .modelFormat(MLModelFormat.ONNX)
            .modelConfig(config)
            .deployModel(true)
            .modelNodeIds(new String[] { "modelNodeIds" })
            .build();
        machineLearningNodeClient.register(mlInput, registerModelActionListener);

        verify(client).execute(eq(MLRegisterModelAction.INSTANCE), isA(MLRegisterModelRequest.class), any());
        verify(registerModelActionListener).onResponse(argumentCaptor.capture());
        assertEquals(taskId, (argumentCaptor.getValue()).getTaskId());
        assertEquals(status, (argumentCaptor.getValue()).getStatus());
    }

    @Test
    public void deploy() {
        String taskId = "taskId";
        String status = MLTaskState.CREATED.name();
        MLTaskType mlTaskType = MLTaskType.DEPLOY_MODEL;
        String modelId = "modelId";
        doAnswer(invocation -> {
            ActionListener<MLDeployModelResponse> actionListener = invocation.getArgument(2);
            MLDeployModelResponse output = new MLDeployModelResponse(taskId, mlTaskType, status);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLDeployModelAction.INSTANCE), any(), any());

        ArgumentCaptor<MLDeployModelResponse> argumentCaptor = ArgumentCaptor.forClass(MLDeployModelResponse.class);
        machineLearningNodeClient.deploy(modelId, deployModelActionListener);

        verify(client).execute(eq(MLDeployModelAction.INSTANCE), isA(MLDeployModelRequest.class), any());
        verify(deployModelActionListener).onResponse(argumentCaptor.capture());
        assertEquals(taskId, (argumentCaptor.getValue()).getTaskId());
        assertEquals(status, (argumentCaptor.getValue()).getStatus());
    }

    @Test
    public void undeploy() {
        ClusterName clusterName = new ClusterName("clusterName");
        String[] modelIds = new String[] { "modelId" };
        String[] nodeIds = new String[] { "nodeId" };
        doAnswer(invocation -> {
            ActionListener<MLUndeployModelsResponse> actionListener = invocation.getArgument(2);
            MLUndeployModelNodesResponse mlUndeployModelNodesResponse = new MLUndeployModelNodesResponse(
                clusterName,
                Collections.emptyList(),
                Collections.emptyList()
            );
            MLUndeployModelsResponse output = new MLUndeployModelsResponse(mlUndeployModelNodesResponse);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLUndeployModelsAction.INSTANCE), any(), any());

        ArgumentCaptor<MLUndeployModelsResponse> argumentCaptor = ArgumentCaptor.forClass(MLUndeployModelsResponse.class);
        machineLearningNodeClient.undeploy(modelIds, nodeIds, undeployModelsActionListener);

        verify(client).execute(eq(MLUndeployModelsAction.INSTANCE), isA(MLUndeployModelsRequest.class), any());
        verify(undeployModelsActionListener).onResponse(argumentCaptor.capture());
        assertEquals(clusterName, (argumentCaptor.getValue()).getResponse().getClusterName());
        assertTrue((argumentCaptor.getValue()).getResponse().getNodes().isEmpty());
        assertFalse((argumentCaptor.getValue()).getResponse().hasFailures());
    }

    @Test
    public void createConnector() {

        String connectorId = "connectorId";

        doAnswer(invocation -> {
            ActionListener<MLCreateConnectorResponse> actionListener = invocation.getArgument(2);
            MLCreateConnectorResponse output = new MLCreateConnectorResponse(connectorId);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLCreateConnectorAction.INSTANCE), any(), any());

        ArgumentCaptor<MLCreateConnectorResponse> argumentCaptor = ArgumentCaptor.forClass(MLCreateConnectorResponse.class);

        Map<String, String> params = Map.ofEntries(Map.entry("endpoint", "endpoint"), Map.entry("temp", "7"));
        Map<String, String> credentials = Map.ofEntries(Map.entry("key1", "value1"), Map.entry("key2", "value2"));
        List<String> backendRoles = Arrays.asList("IT", "HR");

        MLCreateConnectorInput mlCreateConnectorInput = MLCreateConnectorInput
            .builder()
            .name("test")
            .description("description")
            .version("testModelVersion")
            .protocol("http")
            .parameters(params)
            .credential(credentials)
            .actions(null)
            .backendRoles(backendRoles)
            .addAllBackendRoles(false)
            .access(AccessMode.from("private"))
            .dryRun(false)
            .build();

        machineLearningNodeClient.createConnector(mlCreateConnectorInput, createConnectorActionListener);

        verify(client).execute(eq(MLCreateConnectorAction.INSTANCE), isA(MLCreateConnectorRequest.class), any());
        verify(createConnectorActionListener).onResponse(argumentCaptor.capture());
        assertEquals(connectorId, (argumentCaptor.getValue()).getConnectorId());

    }

    @Test
    public void executeMetricsCorrelation() {
        Output metricsCorrelationOutput;
        List<MCorrModelTensors> outputs = new ArrayList<>();
        MCorrModelTensor mCorrModelTensor = MCorrModelTensor
            .builder()
            .event_pattern(new float[] { 1.0f, 2.0f, 3.0f })
            .event_window(new float[] { 4.0f, 5.0f, 6.0f })
            .suspected_metrics(new long[] { 1, 2 })
            .build();
        List<MCorrModelTensor> mlModelTensors = Arrays.asList(mCorrModelTensor);
        MCorrModelTensors modelTensors = MCorrModelTensors.builder().mCorrModelTensors(mlModelTensors).build();
        outputs.add(modelTensors);
        metricsCorrelationOutput = MetricsCorrelationOutput.builder().modelOutput(outputs).build();

        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> actionListener = invocation.getArgument(2);
            MLExecuteTaskResponse output = new MLExecuteTaskResponse(FunctionName.METRICS_CORRELATION, metricsCorrelationOutput);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<MLExecuteTaskResponse> argumentCaptor = ArgumentCaptor.forClass(MLExecuteTaskResponse.class);

        List<float[]> inputData = new ArrayList<>(
            Arrays
                .asList(
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

        machineLearningNodeClient.execute(FunctionName.METRICS_CORRELATION, metricsCorrelationInput, executeTaskResponseActionListener);

        verify(client).execute(eq(MLExecuteTaskAction.INSTANCE), isA(MLExecuteTaskRequest.class), any());
        verify(executeTaskResponseActionListener).onResponse(argumentCaptor.capture());
        assertEquals(FunctionName.METRICS_CORRELATION, argumentCaptor.getValue().getFunctionName());
        assertTrue(argumentCaptor.getValue().getOutput() instanceof MetricsCorrelationOutput);
    }

    @Test
    public void deleteConnector() {

        String connectorId = "connectorId";

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            DeleteResponse output = new DeleteResponse(shardId, connectorId, 1, 1, 1, true);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLConnectorDeleteAction.INSTANCE), any(), any());

        ArgumentCaptor<DeleteResponse> argumentCaptor = ArgumentCaptor.forClass(DeleteResponse.class);

        machineLearningNodeClient.deleteConnector(connectorId, deleteConnectorActionListener);

        verify(client).execute(eq(MLConnectorDeleteAction.INSTANCE), isA(MLConnectorDeleteRequest.class), any());
        verify(deleteConnectorActionListener).onResponse(argumentCaptor.capture());
        assertEquals(connectorId, (argumentCaptor.getValue()).getId());
    }

    @Test
    public void testRegisterAgent() {
        String agentId = "agentId";

        doAnswer(invocation -> {
            ActionListener<MLRegisterAgentResponse> actionListener = invocation.getArgument(2);
            MLRegisterAgentResponse output = new MLRegisterAgentResponse(agentId);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLRegisterAgentAction.INSTANCE), any(), any());

        ArgumentCaptor<MLRegisterAgentResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterAgentResponse.class);
        MLAgent mlAgent = MLAgent.builder().name("Agent name").type(MLAgentType.FLOW.name()).build();

        machineLearningNodeClient.registerAgent(mlAgent, registerAgentResponseActionListener);

        verify(client).execute(eq(MLRegisterAgentAction.INSTANCE), isA(MLRegisterAgentRequest.class), any());
        verify(registerAgentResponseActionListener).onResponse(argumentCaptor.capture());
        assertEquals(agentId, (argumentCaptor.getValue()).getAgentId());
    }

    @Test
    public void testGetAgent() {
        String agentId = "agentId";
        MLAgent mlAgent = MLAgent.builder().name("Agent name").type(MLAgentType.FLOW.name()).build();

        doAnswer(invocation -> {
            ActionListener<MLAgentGetResponse> actionListener = invocation.getArgument(2);
            MLAgentGetResponse output = new MLAgentGetResponse(mlAgent);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLAgentGetAction.INSTANCE), any(), any());

        ArgumentCaptor<MLAgentGetResponse> argumentCaptor = ArgumentCaptor.forClass(MLAgentGetResponse.class);

        machineLearningNodeClient.getAgent(agentId, getAgentResponseActionListener);

        verify(client).execute(eq(MLAgentGetAction.INSTANCE), isA(MLAgentGetRequest.class), any());
        verify(getAgentResponseActionListener).onResponse(argumentCaptor.capture());
        assertEquals(mlAgent, (argumentCaptor.getValue()).getMlAgent());
    }

    @Test
    public void deleteAgent() {

        String agentId = "agentId";

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            DeleteResponse output = new DeleteResponse(shardId, agentId, 1, 1, 1, true);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLAgentDeleteAction.INSTANCE), any(), any());

        ArgumentCaptor<DeleteResponse> argumentCaptor = ArgumentCaptor.forClass(DeleteResponse.class);

        machineLearningNodeClient.deleteAgent(agentId, null, deleteAgentActionListener);

        verify(client).execute(eq(MLAgentDeleteAction.INSTANCE), isA(MLAgentDeleteRequest.class), any());
        verify(deleteAgentActionListener).onResponse(argumentCaptor.capture());
        assertEquals(agentId, (argumentCaptor.getValue()).getId());
    }

    @Test
    public void getTool() {
        ToolMetadata toolMetadata = ToolMetadata
            .builder()
            .name("MathTool")
            .description("Use this tool to calculate any math problem.")
            .build();

        doAnswer(invocation -> {
            ActionListener<MLToolGetResponse> actionListener = invocation.getArgument(2);
            MLToolGetResponse output = MLToolGetResponse.builder().toolMetadata(toolMetadata).build();
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLGetToolAction.INSTANCE), any(), any());

        ArgumentCaptor<ToolMetadata> argumentCaptor = ArgumentCaptor.forClass(ToolMetadata.class);
        machineLearningNodeClient.getTool("MathTool", getToolActionListener);

        verify(client).execute(eq(MLGetToolAction.INSTANCE), isA(MLToolGetRequest.class), any());
        verify(getToolActionListener).onResponse(argumentCaptor.capture());
        assertEquals("MathTool", argumentCaptor.getValue().getName());
        assertEquals("Use this tool to calculate any math problem.", argumentCaptor.getValue().getDescription());
    }

    @Test
    public void listTools() {
        List<ToolMetadata> toolMetadataList = new ArrayList<>();
        ToolMetadata wikipediaTool = ToolMetadata
            .builder()
            .name("WikipediaTool")
            .description("Use this tool to search general knowledge on wikipedia.")
            .build();
        toolMetadataList.add(wikipediaTool);

        doAnswer(invocation -> {
            ActionListener<MLToolsListResponse> actionListener = invocation.getArgument(2);
            MLToolsListResponse output = MLToolsListResponse.builder().toolMetadata(toolMetadataList).build();
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLListToolsAction.INSTANCE), any(), any());

        ArgumentCaptor<List<ToolMetadata>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        machineLearningNodeClient.listTools(listToolsActionListener);

        verify(client).execute(eq(MLListToolsAction.INSTANCE), isA(MLToolsListRequest.class), any());
        verify(listToolsActionListener).onResponse(argumentCaptor.capture());
        assertEquals("WikipediaTool", argumentCaptor.getValue().get(0).getName());
        assertEquals("Use this tool to search general knowledge on wikipedia.", argumentCaptor.getValue().get(0).getDescription());
    }

    @Test
    public void getConfig() {
        MLConfig mlConfig = MLConfig.builder().type("type").configuration(Configuration.builder().agentId("agentId").build()).build();

        doAnswer(invocation -> {
            ActionListener<MLConfigGetResponse> actionListener = invocation.getArgument(2);
            MLConfigGetResponse output = MLConfigGetResponse.builder().mlConfig(mlConfig).build();
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLConfigGetAction.INSTANCE), any(), any());

        ArgumentCaptor<MLConfig> argumentCaptor = ArgumentCaptor.forClass(MLConfig.class);
        machineLearningNodeClient.getConfig("agentId", getMlConfigListener);

        verify(client).execute(eq(MLConfigGetAction.INSTANCE), isA(MLConfigGetRequest.class), any());
        verify(getMlConfigListener).onResponse(argumentCaptor.capture());
        assertEquals("agentId", argumentCaptor.getValue().getConfiguration().getAgentId());
        assertEquals("type", argumentCaptor.getValue().getType());
    }

    @Test
    public void getConfigRejectedMasterKey() {
        doAnswer(invocation -> {
            ActionListener<MLConfigGetResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new OpenSearchStatusException("You are not allowed to access this config doc", RestStatus.FORBIDDEN));
            return null;
        }).when(client).execute(eq(MLConfigGetAction.INSTANCE), any(), any());

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        machineLearningNodeClient.getConfig(MASTER_KEY, getMlConfigListener);

        verify(client).execute(eq(MLConfigGetAction.INSTANCE), isA(MLConfigGetRequest.class), any());
        verify(getMlConfigListener).onFailure(argumentCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, argumentCaptor.getValue().status());
        assertEquals("You are not allowed to access this config doc", argumentCaptor.getValue().getLocalizedMessage());
    }

    @Test
    public void predict_withTenantId() {
        String tenantId = "testTenant";
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            MLPredictionOutput predictionOutput = MLPredictionOutput
                .builder()
                .status("Success")
                .predictionResult(output)
                .taskId("taskId")
                .build();
            actionListener.onResponse(MLTaskResponse.builder().output(predictionOutput).build());
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<MLPredictionTaskRequest> requestCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(input).build();
        machineLearningNodeClient.predict("modelId", tenantId, mlInput, dataFrameActionListener);

        verify(client).execute(eq(MLPredictionTaskAction.INSTANCE), requestCaptor.capture(), any());
        assertEquals(tenantId, requestCaptor.getValue().getTenantId());
        assertEquals("modelId", requestCaptor.getValue().getModelId());
    }

    @Test
    public void getTask_withFailure() {
        String taskId = "taskId";
        String errorMessage = "Task not found";

        doAnswer(invocation -> {
            ActionListener<MLTaskGetResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new IllegalArgumentException(errorMessage));
            return null;
        }).when(client).execute(eq(MLTaskGetAction.INSTANCE), any(), any());

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);

        machineLearningNodeClient.getTask(taskId, new ActionListener<>() {
            @Override
            public void onResponse(MLTask mlTask) {
                fail("Expected failure but got success");
            }

            @Override
            public void onFailure(Exception e) {
                assertEquals(errorMessage, e.getMessage());
            }
        });

        verify(client).execute(eq(MLTaskGetAction.INSTANCE), isA(MLTaskGetRequest.class), any());
    }

    @Test
    public void deploy_withTenantId() {
        String modelId = "testModel";
        String tenantId = "testTenant";
        String taskId = "taskId";
        String status = MLTaskState.CREATED.name();

        doAnswer(invocation -> {
            ActionListener<MLDeployModelResponse> actionListener = invocation.getArgument(2);
            MLDeployModelResponse output = new MLDeployModelResponse(taskId, MLTaskType.DEPLOY_MODEL, status);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLDeployModelAction.INSTANCE), any(), any());

        ArgumentCaptor<MLDeployModelRequest> requestCaptor = ArgumentCaptor.forClass(MLDeployModelRequest.class);
        machineLearningNodeClient.deploy(modelId, tenantId, deployModelActionListener);

        verify(client).execute(eq(MLDeployModelAction.INSTANCE), requestCaptor.capture(), any());
        assertEquals(modelId, requestCaptor.getValue().getModelId());
        assertEquals(tenantId, requestCaptor.getValue().getTenantId());
    }

    @Test
    public void trainAndPredict_withNullInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("ML Input can't be null");

        machineLearningNodeClient.trainAndPredict(null, trainingActionListener);
    }

    @Test
    public void trainAndPredict_withNullDataSet() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("input data set can't be null");

        MLInput mlInput = MLInput.builder().algorithm(FunctionName.KMEANS).build();
        machineLearningNodeClient.trainAndPredict(mlInput, trainingActionListener);
    }

    @Test
    public void getTask_withTaskIdAndTenantId() {
        String taskId = "taskId";
        String tenantId = "testTenant";
        String modelId = "modelId";

        doAnswer(invocation -> {
            ActionListener<MLTaskGetResponse> actionListener = invocation.getArgument(2);
            MLTask mlTask = MLTask.builder().taskId(taskId).modelId(modelId).functionName(FunctionName.KMEANS).build();
            MLTaskGetResponse output = MLTaskGetResponse.builder().mlTask(mlTask).build();
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLTaskGetAction.INSTANCE), any(), any());

        ArgumentCaptor<MLTaskGetRequest> requestCaptor = ArgumentCaptor.forClass(MLTaskGetRequest.class);
        ArgumentCaptor<MLTask> taskCaptor = ArgumentCaptor.forClass(MLTask.class);

        machineLearningNodeClient.getTask(taskId, tenantId, getTaskActionListener);

        verify(client).execute(eq(MLTaskGetAction.INSTANCE), requestCaptor.capture(), any());
        verify(getTaskActionListener).onResponse(taskCaptor.capture());

        // Verify request parameters
        assertEquals(taskId, requestCaptor.getValue().getTaskId());
        assertEquals(tenantId, requestCaptor.getValue().getTenantId());

        // Verify response
        assertEquals(taskId, taskCaptor.getValue().getTaskId());
        assertEquals(modelId, taskCaptor.getValue().getModelId());
        assertEquals(FunctionName.KMEANS, taskCaptor.getValue().getFunctionName());
    }

    @Test
    public void deleteTask_withTaskId() {
        String taskId = "taskId";

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            DeleteResponse output = new DeleteResponse(shardId, taskId, 1, 1, 1, true);
            actionListener.onResponse(output);
            return null;
        }).when(client).execute(eq(MLTaskDeleteAction.INSTANCE), any(), any());

        ArgumentCaptor<MLTaskDeleteRequest> requestCaptor = ArgumentCaptor.forClass(MLTaskDeleteRequest.class);
        ArgumentCaptor<DeleteResponse> responseCaptor = ArgumentCaptor.forClass(DeleteResponse.class);

        machineLearningNodeClient.deleteTask(taskId, deleteTaskActionListener);

        verify(client).execute(eq(MLTaskDeleteAction.INSTANCE), requestCaptor.capture(), any());
        verify(deleteTaskActionListener).onResponse(responseCaptor.capture());

        // Verify request parameter
        assertEquals(taskId, requestCaptor.getValue().getTaskId());

        // Verify response
        assertEquals(taskId, responseCaptor.getValue().getId());
        assertEquals("DELETED", responseCaptor.getValue().getResult().toString());
    }

    @Test
    public void deleteTask_withFailure() {
        String taskId = "taskId";
        String errorMessage = "Task deletion failed";

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException(errorMessage));
            return null;
        }).when(client).execute(eq(MLTaskDeleteAction.INSTANCE), any(), any());

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);

        machineLearningNodeClient.deleteTask(taskId, new ActionListener<>() {
            @Override
            public void onResponse(DeleteResponse deleteResponse) {
                fail("Expected failure but got success");
            }

            @Override
            public void onFailure(Exception e) {
                assertEquals(errorMessage, e.getMessage());
            }
        });

        verify(client).execute(eq(MLTaskDeleteAction.INSTANCE), isA(MLTaskDeleteRequest.class), any());
    }

    private SearchResponse createSearchResponse(ToXContentObject o) throws IOException {
        XContentBuilder content = o.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);

        SearchHit[] hits = new SearchHit[1];
        hits[0] = new SearchHit(0).sourceRef(BytesReference.bytes(content));

        return new SearchResponse(
            new InternalSearchResponse(
                new SearchHits(hits, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f),
                InternalAggregations.EMPTY,
                new Suggest(Collections.emptyList()),
                new SearchProfileShardResults(Collections.emptyMap()),
                false,
                false,
                1
            ),
            "",
            5,
            5,
            0,
            100,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
    }
}
