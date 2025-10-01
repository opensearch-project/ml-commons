/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.*;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MAX_DEPLOY_MODEL_TASKS_PER_NODE;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterApplierService;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.execute.samplecalculator.LocalSampleCalculatorInput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.indices.MLInputDatasetHandler;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class MLExecuteTaskRunnerTests extends OpenSearchTestCase {

    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    MLTaskManager mlTaskManager;

    @Mock
    ExecutorService executorService;

    @Mock
    MLTaskDispatcher mlTaskDispatcher;

    @Mock
    MLCircuitBreakerService mlCircuitBreakerService;

    @Mock
    ActionListener<MLExecuteTaskResponse> listener;
    @Mock
    DiscoveryNodeHelper nodeHelper;
    @Mock
    ClusterApplierService clusterApplierService;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    MLInputDatasetHandler mlInputDatasetHandler;
    MLExecuteTaskRunner taskRunner;
    MLStats mlStats;
    MLExecuteTaskRequest mlExecuteTaskRequest;
    MLEngine mlEngine;
    private Encryptor encryptor;
    Settings settings;
    ClusterService clusterService;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = spy(new MLEngine(Path.of("/tmp/djl-cache/" + randomAlphaOfLength(10)), encryptor));
        when(threadPool.executor(anyString())).thenReturn(executorService);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));

        settings = Settings.builder().put(ML_COMMONS_MAX_MODELS_PER_NODE.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MAX_REGISTER_MODEL_TASKS_PER_NODE.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MONITORING_REQUEST_COUNT.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MAX_DEPLOY_MODEL_TASKS_PER_NODE.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_ENABLE_INHOUSE_PYTHON_MODEL.getKey(), false).build();
        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_MAX_MODELS_PER_NODE,
            ML_COMMONS_MAX_REGISTER_MODEL_TASKS_PER_NODE,
            ML_COMMONS_MONITORING_REQUEST_COUNT,
            ML_COMMONS_MAX_DEPLOY_MODEL_TASKS_PER_NODE,
            ML_COMMONS_ENABLE_INHOUSE_PYTHON_MODEL
        );
        clusterService = spy(new ClusterService(settings, clusterSettings, null, clusterApplierService));
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);

        Map<Enum, MLStat<?>> stats = new ConcurrentHashMap<>();
        stats.put(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_REQUEST_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_FAILURE_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT, new MLStat<>(false, new CounterSupplier()));
        this.mlStats = new MLStats(stats);

        mlInputDatasetHandler = spy(new MLInputDatasetHandler(client));
        taskRunner = spy(
            new MLExecuteTaskRunner(
                threadPool,
                clusterService,
                client,
                mlTaskManager,
                mlStats,
                mlInputDatasetHandler,
                mlTaskDispatcher,
                mlCircuitBreakerService,
                nodeHelper,
                mlEngine
            )
        );

        mlExecuteTaskRequest = new MLExecuteTaskRequest(
            FunctionName.LOCAL_SAMPLE_CALCULATOR,
            new LocalSampleCalculatorInput("sum", Arrays.asList(1.0, 2.0))
        );
    }

    public void testExecuteTask_Success() throws Exception {
        Output mockOutput = mock(Output.class);
        doAnswer(invocation -> {
            ActionListener<Output> actionListener = invocation.getArgument(1);
            actionListener.onResponse(mockOutput);
            return null;
        }).when(mlEngine).execute(any(), any(), any());

        taskRunner.executeTask(mlExecuteTaskRequest, listener);
        verify(listener).onResponse(any(MLExecuteTaskResponse.class));
    }

    public void testExecuteTask_NoExecutorService() {
        exceptionRule.expect(IllegalArgumentException.class);
        when(threadPool.executor(anyString())).thenThrow(new IllegalArgumentException());
        taskRunner.executeTask(mlExecuteTaskRequest, listener);
        verify(listener, never()).onResponse(any(MLExecuteTaskResponse.class));
    }
}
