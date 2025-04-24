/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.batch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.MLTask.ERROR_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTaskState.COMPLETED;
import static org.opensearch.ml.common.MLTaskState.FAILED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_BATCH_INGESTION_BULK_SIZE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MAX_BATCH_INGESTION_TASKS;
import static org.opensearch.ml.engine.ingest.S3DataIngestion.SOURCE;
import static org.opensearch.ml.plugin.MachineLearningPlugin.INGEST_THREAD_POOL;
import static org.opensearch.ml.task.MLTaskManager.TASK_SEMAPHORE_TIMEOUT;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionInput;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionRequest;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionResponse;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.jayway.jsonpath.PathNotFoundException;

public class TransportBatchIngestionActionTests extends OpenSearchTestCase {
    @Mock
    private Client client;
    @Mock
    private TransportService transportService;
    @Mock
    private MLTaskManager mlTaskManager;
    @Mock
    MLModelManager mlModelManager;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private MLBatchIngestionRequest mlBatchIngestionRequest;
    @Mock
    private Task task;
    @Mock
    ActionListener<MLBatchIngestionResponse> actionListener;
    @Mock
    ThreadPool threadPool;
    @Mock
    ExecutorService executorService;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Mock
    private ClusterService clusterService;
    private Settings settings;

    private TransportBatchIngestionAction batchAction;
    private MLBatchIngestionInput batchInput;
    private MLBatchIngestionInput mlBatchIngestionInputWithConnector;
    private String[] ingestFields;
    private Map<String, String> credential;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().put(ML_COMMONS_BATCH_INGESTION_BULK_SIZE.getKey(), 100).build();
        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_BATCH_INGESTION_BULK_SIZE,
            ML_COMMONS_MAX_BATCH_INGESTION_TASKS
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        batchAction = new TransportBatchIngestionAction(
            clusterService,
            transportService,
            actionFilters,
            client,
            mlTaskManager,
            threadPool,
            mlModelManager,
            mlFeatureEnabledSetting,
            settings
        );

        Map<String, Object> fieldMap = new HashMap<>();
        fieldMap.put("chapter", "$.content[0]");
        fieldMap.put("title", "$.content[1]");
        fieldMap.put("chapter_embedding", "$.SageMakerOutput[0]");
        fieldMap.put("title_embedding", "$.SageMakerOutput[1]");

        ingestFields = new String[] { "$.id" };

        credential = Map
            .of("region", "us-east-1", "access_key", "some accesskey", "secret_key", "some secret", "session_token", "some token");
        Map<String, Object> dataSource = new HashMap<>();
        dataSource.put("type", "s3");
        dataSource.put(SOURCE, Arrays.asList("s3://offlinebatch/output/sagemaker_djl_batch_input.json.out"));

        batchInput = MLBatchIngestionInput
            .builder()
            .indexName("testIndex")
            .fieldMapping(fieldMap)
            .ingestFields(ingestFields)
            .credential(credential)
            .dataSources(dataSource)
            .build();
        when(mlBatchIngestionRequest.getMlBatchIngestionInput()).thenReturn(batchInput);

        mlBatchIngestionInputWithConnector = MLBatchIngestionInput
            .builder()
            .indexName("testIndex")
            .fieldMapping(fieldMap)
            .ingestFields(ingestFields)
            .connectorId("test_connector_id")
            .dataSources(dataSource)
            .build();

        when(mlFeatureEnabledSetting.isOfflineBatchIngestionEnabled()).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(false);
            return null;
        }).when(mlModelManager).checkMaxBatchJobTask(any(MLTask.class), isA(ActionListener.class));
    }

    public void test_doExecute_success() {
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            IndexResponse indexResponse = new IndexResponse(shardId, "taskId", 1, 1, 1, true);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(isA(MLTask.class), isA(ActionListener.class));
        doReturn(executorService).when(threadPool).executor(INGEST_THREAD_POOL);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
        batchAction.doExecute(task, mlBatchIngestionRequest, actionListener);

        verify(actionListener).onResponse(any(MLBatchIngestionResponse.class));
        verify(threadPool).executor(INGEST_THREAD_POOL);
    }

    public void test_doExecute_ExecuteWithNoErrorHandling() {
        batchAction.executeWithErrorHandling(() -> {}, "taskId");

        verify(mlTaskManager, never()).updateMLTask(anyString(), anyString(), isA(Map.class), anyLong(), anyBoolean());
    }

    public void test_doExecute_ExecuteWithPathNotFoundException() {
        batchAction.executeWithErrorHandling(() -> { throw new PathNotFoundException("jsonPath not found!"); }, "taskId");

        verify(mlTaskManager)
            .updateMLTask("taskId", null, Map.of(STATE_FIELD, FAILED, ERROR_FIELD, "jsonPath not found!"), TASK_SEMAPHORE_TIMEOUT, true);
    }

    public void test_doExecute_RuntimeException() {
        batchAction.executeWithErrorHandling(() -> { throw new RuntimeException("runtime exception in the ingestion!"); }, "taskId");

        verify(mlTaskManager)
            .updateMLTask(
                "taskId",
                null,
                Map.of(STATE_FIELD, FAILED, ERROR_FIELD, "runtime exception in the ingestion!"),
                TASK_SEMAPHORE_TIMEOUT,
                true
            );
    }

    public void test_doExecute_handleSuccessRate100() {
        batchAction.handleSuccessRate(100, "taskid");
        verify(mlTaskManager).updateMLTask("taskid", null, Map.of(STATE_FIELD, COMPLETED), 5000, true);
    }

    public void test_doExecute_handleSuccessRate50() {
        batchAction.handleSuccessRate(50, "taskid");
        verify(mlTaskManager)
            .updateMLTask(
                "taskid",
                null,
                Map.of(STATE_FIELD, FAILED, ERROR_FIELD, "batch ingestion successful rate is 50.0"),
                TASK_SEMAPHORE_TIMEOUT,
                true
            );
    }

    public void test_doExecute_handleSuccessRate0() {
        batchAction.handleSuccessRate(0, "taskid");
        verify(mlTaskManager)
            .updateMLTask(
                "taskid",
                null,
                Map.of(STATE_FIELD, FAILED, ERROR_FIELD, "batch ingestion successful rate is 0"),
                TASK_SEMAPHORE_TIMEOUT,
                true
            );
    }

    public void test_doExecute_batchIngestionDisabled() {
        when(mlFeatureEnabledSetting.isOfflineBatchIngestionEnabled()).thenReturn(false);
        batchAction.doExecute(task, mlBatchIngestionRequest, actionListener);

        ArgumentCaptor<IllegalStateException> argumentCaptor = ArgumentCaptor.forClass(IllegalStateException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
                "Offline batch ingestion is currently disabled. To enable it, update the setting \"plugins.ml_commons.offline_batch_ingestion_enabled\" to true.",
                argumentCaptor.getValue().getMessage()
        );
    }

    public void test_doExecute_noDataSource() {
        MLBatchIngestionInput batchInput = MLBatchIngestionInput
            .builder()
            .indexName("testIndex")
            .fieldMapping(new HashMap<>())
            .credential(new HashMap<>())
            .dataSources(new HashMap<>())
            .build();
        when(mlBatchIngestionRequest.getMlBatchIngestionInput()).thenReturn(batchInput);
        batchAction.doExecute(task, mlBatchIngestionRequest, actionListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "IllegalArgumentException in the batch ingestion input: The batch ingest input data source cannot be null",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_doExecute_noTypeInDataSource() {
        MLBatchIngestionInput batchInput = MLBatchIngestionInput
            .builder()
            .indexName("testIndex")
            .fieldMapping(new HashMap<>())
            .credential(new HashMap<>())
            .dataSources(Map.of("source", "some url"))
            .build();
        when(mlBatchIngestionRequest.getMlBatchIngestionInput()).thenReturn(batchInput);
        batchAction.doExecute(task, mlBatchIngestionRequest, actionListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "IllegalArgumentException in the batch ingestion input: The batch ingest input data source is missing data type or source",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_doExecute_invalidS3DataSource() {
        Map<String, Object> dataSource = new HashMap<>();
        dataSource.put("type", "s3");
        dataSource.put(SOURCE, Arrays.asList("s3://offlinebatch/output/sagemaker_djl_batch_input.json.out", "invalid s3"));

        MLBatchIngestionInput batchInput = MLBatchIngestionInput
            .builder()
            .indexName("testIndex")
            .fieldMapping(new HashMap<>())
            .credential(new HashMap<>())
            .dataSources(dataSource)
            .build();
        when(mlBatchIngestionRequest.getMlBatchIngestionInput()).thenReturn(batchInput);
        batchAction.doExecute(task, mlBatchIngestionRequest, actionListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "IllegalArgumentException in the batch ingestion input: The following batch ingest input S3 URIs are invalid: [invalid s3]",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_doExecute_emptyS3DataSource() {
        Map<String, Object> dataSource = new HashMap<>();
        dataSource.put("type", "s3");
        dataSource.put(SOURCE, new ArrayList<>());

        MLBatchIngestionInput batchInput = MLBatchIngestionInput
            .builder()
            .indexName("testIndex")
            .fieldMapping(new HashMap<>())
            .credential(new HashMap<>())
            .dataSources(dataSource)
            .build();
        when(mlBatchIngestionRequest.getMlBatchIngestionInput()).thenReturn(batchInput);
        batchAction.doExecute(task, mlBatchIngestionRequest, actionListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "IllegalArgumentException in the batch ingestion input: The batch ingest input s3Uris is empty",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_doExecute_mlTaskCreateException() {
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Failed to create ML Task"));
            return null;
        }).when(mlTaskManager).createMLTask(isA(MLTask.class), isA(ActionListener.class));
        batchAction.doExecute(task, mlBatchIngestionRequest, actionListener);

        ArgumentCaptor<RuntimeException> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to create ML Task", argumentCaptor.getValue().getMessage());
    }

    public void test_doExecute_batchIngestionFailed() {
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            IndexResponse indexResponse = new IndexResponse(shardId, "taskId", 1, 1, 1, true);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(isA(MLTask.class), isA(ActionListener.class));

        doThrow(new OpenSearchStatusException("some error", RestStatus.INTERNAL_SERVER_ERROR)).when(mlTaskManager).add(isA(MLTask.class));
        batchAction.doExecute(task, mlBatchIngestionRequest, actionListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("some error", argumentCaptor.getValue().getMessage());
        verify(mlTaskManager)
            .updateMLTask("taskId", null, Map.of(STATE_FIELD, FAILED, ERROR_FIELD, "some error"), TASK_SEMAPHORE_TIMEOUT, true);
    }

    public void test_doExecute_withConnector_success() {
        when(mlBatchIngestionRequest.getMlBatchIngestionInput()).thenReturn(mlBatchIngestionInputWithConnector);

        doAnswer(invocation -> {
            ActionListener<Map<String, String>> listener = invocation.getArgument(1);
            listener.onResponse(credential);
            return null;
        }).when(mlModelManager).getConnectorCredential(anyString(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            IndexResponse indexResponse = new IndexResponse(shardId, "taskId", 1, 1, 1, true);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(isA(MLTask.class), isA(ActionListener.class));
        doReturn(executorService).when(threadPool).executor(INGEST_THREAD_POOL);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));

        batchAction.doExecute(task, mlBatchIngestionRequest, actionListener);

        verify(actionListener).onResponse(any(MLBatchIngestionResponse.class));
        verify(threadPool).executor(INGEST_THREAD_POOL);
    }
}
