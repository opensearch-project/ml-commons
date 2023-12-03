/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.common.input.Constants.ACTION;
import static org.opensearch.ml.common.input.Constants.ALGORITHM;
import static org.opensearch.ml.common.input.Constants.KMEANS;
import static org.opensearch.ml.common.input.Constants.MODELID;
import static org.opensearch.ml.common.input.Constants.PREDICT;
import static org.opensearch.ml.common.input.Constants.RCF;
import static org.opensearch.ml.common.input.Constants.TRAIN;
import static org.opensearch.ml.common.input.Constants.TRAINANDPREDICT;

import java.io.IOException;
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
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.ClusterName;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.common.output.MLTrainingOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteAction;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteRequest;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentAction;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentRequest;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteAction;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteRequest;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorRequest;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
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
    ActionListener<MLRegisterAgentResponse> registerAgentResponseActionListener;

    @Mock
    ActionListener<DeleteResponse> deleteAgentActionListener;

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
        machineLearningNodeClient.deleteTask(taskId, deleteTaskActionListener);

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
        assertEquals(1, argumentCaptor.getValue().getHits().getTotalHits().value);
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
            .protocol("testProtocol")
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
        MLAgent mlAgent = MLAgent.builder().name("Agent name").build();

        machineLearningNodeClient.registerAgent(mlAgent, registerAgentResponseActionListener);

        verify(client).execute(eq(MLRegisterAgentAction.INSTANCE), isA(MLRegisterAgentRequest.class), any());
        verify(registerAgentResponseActionListener).onResponse(argumentCaptor.capture());
        assertEquals(agentId, (argumentCaptor.getValue()).getAgentId());
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

        machineLearningNodeClient.deleteAgent(agentId, deleteAgentActionListener);

        verify(client).execute(eq(MLAgentDeleteAction.INSTANCE), isA(MLAgentDeleteRequest.class), any());
        verify(deleteAgentActionListener).onResponse(argumentCaptor.capture());
        assertEquals(agentId, (argumentCaptor.getValue()).getId());
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
