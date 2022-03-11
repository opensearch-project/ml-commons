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

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.parameter.MLTask;
import org.opensearch.ml.common.parameter.MLTaskState;
import org.opensearch.ml.common.parameter.MLTaskType;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class TaskRunnerTests extends OpenSearchTestCase {

    @Mock
    MLTaskManager mlTaskManager;
    @Mock
    MLStats mlStats;
    @Mock
    MLTaskDispatcher mlTaskDispatcher;
    @Mock
    MLCircuitBreakerService mlCircuitBreakerService;

    MLTaskRunner mlTaskRunner;
    MLTask mlTask;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mlTaskRunner = new MLTaskRunner(mlTaskManager, mlStats, mlTaskDispatcher, mlCircuitBreakerService) {
            @Override
            public void executeTask(Object o, TransportService transportService, ActionListener listener) {}
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
        exceptionRule.expect(MLLimitExceededException.class);
        exceptionRule.expectMessage("Circuit breaker is open");
        when(mlCircuitBreakerService.isOpen()).thenReturn(true);
        TransportService transportService = mock(TransportService.class);
        ActionListener listener = mock(ActionListener.class);
        mlTaskRunner.run(null, transportService, listener);
    }
}
