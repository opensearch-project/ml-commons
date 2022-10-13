/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.transport.MLTaskRequest;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportResponseHandler;
import org.opensearch.transport.TransportService;

public class TaskRunnerTests extends OpenSearchTestCase {

    @Mock
    MLTaskManager mlTaskManager;
    MLStats mlStats;
    @Mock
    DiscoveryNodeHelper nodeHelper;
    @Mock
    MLTaskDispatcher mlTaskDispatcher;
    @Mock
    MLCircuitBreakerService mlCircuitBreakerService;
    @Mock
    ClusterService clusterService;

    MLTaskRunner mlTaskRunner;
    MLTask mlTask;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setup() {
        Map<Enum, MLStat<?>> stats = new ConcurrentHashMap<>();
        stats.put(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_FAILURE_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_MODEL_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_CIRCUIT_BREAKER_TRIGGER_COUNT, new MLStat<>(false, new CounterSupplier()));
        mlStats = new MLStats(stats);

        MockitoAnnotations.openMocks(this);
        mlTaskRunner = new MLTaskRunner(mlTaskManager, mlStats, nodeHelper, mlTaskDispatcher, mlCircuitBreakerService, clusterService) {
            @Override
            public String getTransportActionName() {
                return null;
            }

            @Override
            public TransportResponseHandler getResponseHandler(ActionListener listener) {
                return null;
            }

            @Override
            public void executeTask(MLTaskRequest request, ActionListener listener) {}
        };
        mlTask = MLTask
            .builder()
            .taskId("task id")
            .taskType(MLTaskType.PREDICTION)
            .createTime(Instant.now())
            .state(MLTaskState.CREATED)
            .async(true)
            .build();
    }

    public void testHandleAsyncMLTaskFailure_AsyncTask() {
        String errorMessage = "test error";
        mlTaskRunner.handleAsyncMLTaskFailure(mlTask, new RuntimeException(errorMessage));
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mlTaskManager, times(1)).updateMLTask(eq(mlTask.getTaskId()), argumentCaptor.capture(), anyLong());
        assertEquals(errorMessage, argumentCaptor.getValue().get(MLTask.ERROR_FIELD));
    }

    public void testHandleAsyncMLTaskFailure_SyncTask() {
        MLTask syncMlTask = mlTask.toBuilder().async(false).build();
        mlTaskRunner.handleAsyncMLTaskFailure(syncMlTask, new RuntimeException("error"));
        verify(mlTaskManager, never()).updateMLTask(eq(syncMlTask.getTaskId()), any(), anyLong());
    }

    public void testHandleAsyncMLTaskComplete_AsyncTask() {
        String modelId = "testModelId";
        MLTask task = mlTask.toBuilder().modelId(modelId).build();
        mlTaskRunner.handleAsyncMLTaskComplete(task);
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mlTaskManager, times(1)).updateMLTask(eq(mlTask.getTaskId()), argumentCaptor.capture(), anyLong());
        assertEquals(modelId, argumentCaptor.getValue().get(MLTask.MODEL_ID_FIELD));
        assertEquals(MLTaskState.COMPLETED, argumentCaptor.getValue().get(MLTask.STATE_FIELD));
    }

    public void testHandleAsyncMLTaskComplete_SyncTask() {
        MLTask syncMlTask = mlTask.toBuilder().async(false).build();
        mlTaskRunner.handleAsyncMLTaskComplete(syncMlTask);
        verify(mlTaskManager, never()).updateMLTask(eq(syncMlTask.getTaskId()), any(), anyLong());
    }

    public void testRun_CircuitBreakerOpen() {
        when(mlCircuitBreakerService.isOpen()).thenReturn(true);
        TransportService transportService = mock(TransportService.class);
        ActionListener listener = mock(ActionListener.class);
        expectThrows(MLLimitExceededException.class, () -> mlTaskRunner.run(null, transportService, listener));
        Long value = (Long) mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_CIRCUIT_BREAKER_TRIGGER_COUNT).getValue();
        assertEquals(1L, value.longValue());
    }
}
