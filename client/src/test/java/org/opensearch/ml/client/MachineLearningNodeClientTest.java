/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.client;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.index.Index;
import org.opensearch.index.shard.ShardId;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.output.MLTrainingOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.task.MLTaskDeleteAction;
import org.opensearch.ml.common.transport.task.MLTaskDeleteRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetResponse;
import org.opensearch.ml.common.transport.task.MLTaskSearchAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

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

        verify(client).execute(eq(MLPredictionTaskAction.INSTANCE), isA(MLPredictionTaskRequest.class), any());
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

        verify(client).execute(eq(MLTrainingTaskAction.INSTANCE), isA(MLTrainingTaskRequest.class), any());
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

    @Test
    public void trainAndPredict() {
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            MLPredictionOutput predictionOutput = MLPredictionOutput.builder()
                    .status(MLTaskState.COMPLETED.name())
                    .predictionResult(output)
                    .taskId("taskId")
                    .build();
            actionListener.onResponse(MLTaskResponse.builder()
                    .output(predictionOutput)
                    .build());
            return null;
        }).when(client).execute(eq(MLTrainAndPredictionTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<MLOutput> argumentCaptor = ArgumentCaptor.forClass(MLOutput.class);
        MLInput mlInput = MLInput.builder()
                .algorithm(FunctionName.KMEANS)
                .inputDataset(input)
                .build();
        machineLearningNodeClient.trainAndPredict(mlInput, trainingActionListener);

        verify(client).execute(eq(MLTrainAndPredictionTaskAction.INSTANCE), isA(MLTrainingTaskRequest.class), any());
        verify(trainingActionListener).onResponse(argumentCaptor.capture());
        assertEquals(MLTaskState.COMPLETED.name(), ((MLPredictionOutput)argumentCaptor.getValue()).getStatus());
        assertEquals(output, ((MLPredictionOutput)argumentCaptor.getValue()).getPredictionResult());
    }

    @Test
    public void getModel() {
        String modelContent = "test content";
        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> actionListener = invocation.getArgument(2);
            MLModel mlModel = MLModel.builder()
                    .algorithm(FunctionName.KMEANS)
                    .name("test")
                    .content(modelContent)
                    .build();
            MLModelGetResponse output = MLModelGetResponse.builder()
                    .mlModel(mlModel)
                    .build();
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
            MLModel mlModel = MLModel.builder()
                    .algorithm(FunctionName.KMEANS)
                    .name("test")
                    .content(modelContent)
                    .build();
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
    public void getTask() {
        String taskId = "taskId";
        String modelId = "modelId";
        doAnswer(invocation -> {
            ActionListener<MLTaskGetResponse> actionListener = invocation.getArgument(2);
            MLTask mlTask = MLTask.builder()
                    .taskId(taskId)
                    .modelId(modelId)
                    .functionName(FunctionName.KMEANS)
                    .build();
            MLTaskGetResponse output = MLTaskGetResponse.builder()
                    .mlTask(mlTask)
                    .build();
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
            MLTask mlTask = MLTask.builder()
                    .taskId(taskId)
                    .modelId(modelId)
                    .functionName(FunctionName.KMEANS)
                    .build();
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
