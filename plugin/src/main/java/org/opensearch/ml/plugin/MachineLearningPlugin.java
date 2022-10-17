/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.stream.NamedWriteableRegistry;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.IndexScopedSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.SettingsFilter;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.env.Environment;
import org.opensearch.env.NodeEnvironment;
import org.opensearch.ml.action.execute.TransportExecuteTaskAction;
import org.opensearch.ml.action.handler.MLSearchHandler;
import org.opensearch.ml.action.models.DeleteModelTransportAction;
import org.opensearch.ml.action.models.GetModelTransportAction;
import org.opensearch.ml.action.models.SearchModelTransportAction;
import org.opensearch.ml.action.prediction.TransportPredictionTaskAction;
import org.opensearch.ml.action.profile.MLProfileAction;
import org.opensearch.ml.action.profile.MLProfileTransportAction;
import org.opensearch.ml.action.stats.MLStatsNodesAction;
import org.opensearch.ml.action.stats.MLStatsNodesTransportAction;
import org.opensearch.ml.action.tasks.DeleteTaskTransportAction;
import org.opensearch.ml.action.tasks.GetTaskTransportAction;
import org.opensearch.ml.action.tasks.SearchTaskTransportAction;
import org.opensearch.ml.action.training.TransportTrainingTaskAction;
import org.opensearch.ml.action.trainpredict.TransportTrainAndPredictionTaskAction;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.input.execute.anomalylocalization.AnomalyLocalizationInput;
import org.opensearch.ml.common.input.execute.samplecalculator.LocalSampleCalculatorInput;
import org.opensearch.ml.common.input.parameter.ad.AnomalyDetectionLibSVMParams;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.input.parameter.clustering.RCFSummarizeParams;
import org.opensearch.ml.common.input.parameter.rcf.BatchRCFParams;
import org.opensearch.ml.common.input.parameter.rcf.FitRCFParams;
import org.opensearch.ml.common.input.parameter.regression.LinearRegressionParams;
import org.opensearch.ml.common.input.parameter.regression.LogisticRegressionParams;
import org.opensearch.ml.common.input.parameter.sample.SampleAlgoParams;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.task.MLTaskDeleteAction;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskSearchAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.algorithms.anomalylocalization.AnomalyLocalizerImpl;
import org.opensearch.ml.engine.algorithms.sample.LocalSampleCalculator;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.indices.MLInputDatasetHandler;
import org.opensearch.ml.rest.*;
import org.opensearch.ml.stats.MLClusterLevelStat;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.stats.suppliers.IndexStatusSupplier;
import org.opensearch.ml.task.MLExecuteTaskRunner;
import org.opensearch.ml.task.MLPredictTaskRunner;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.task.MLTrainAndPredictTaskRunner;
import org.opensearch.ml.task.MLTrainingTaskRunner;
import org.opensearch.ml.utils.IndexUtils;
import org.opensearch.monitor.jvm.JvmService;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.repositories.RepositoriesService;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestHandler;
import org.opensearch.script.ScriptService;
import org.opensearch.threadpool.ExecutorBuilder;
import org.opensearch.threadpool.FixedExecutorBuilder;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.watcher.ResourceWatcherService;

import com.google.common.collect.ImmutableList;

public class MachineLearningPlugin extends Plugin implements ActionPlugin {
    public static final String TASK_THREAD_POOL = "OPENSEARCH_ML_TASK_THREAD_POOL";
    public static final String ML_BASE_URI = "/_plugins/_ml";

    private MLStats mlStats;
    private MLTaskManager mlTaskManager;
    private MLIndicesHandler mlIndicesHandler;
    private MLInputDatasetHandler mlInputDatasetHandler;
    private MLTrainingTaskRunner mlTrainingTaskRunner;
    private MLPredictTaskRunner mlPredictTaskRunner;
    private MLTrainAndPredictTaskRunner mlTrainAndPredictTaskRunner;
    private MLExecuteTaskRunner mlExecuteTaskRunner;
    private IndexUtils indexUtils;

    private Client client;
    private ClusterService clusterService;
    private ThreadPool threadPool;

    public static final String ML_ROLE_NAME = "ml";

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return ImmutableList
            .of(
                new ActionHandler<>(MLStatsNodesAction.INSTANCE, MLStatsNodesTransportAction.class),
                new ActionHandler<>(MLExecuteTaskAction.INSTANCE, TransportExecuteTaskAction.class),
                new ActionHandler<>(MLPredictionTaskAction.INSTANCE, TransportPredictionTaskAction.class),
                new ActionHandler<>(MLTrainingTaskAction.INSTANCE, TransportTrainingTaskAction.class),
                new ActionHandler<>(MLTrainAndPredictionTaskAction.INSTANCE, TransportTrainAndPredictionTaskAction.class),
                new ActionHandler<>(MLModelGetAction.INSTANCE, GetModelTransportAction.class),
                new ActionHandler<>(MLModelDeleteAction.INSTANCE, DeleteModelTransportAction.class),
                new ActionHandler<>(MLModelSearchAction.INSTANCE, SearchModelTransportAction.class),
                new ActionHandler<>(MLTaskGetAction.INSTANCE, GetTaskTransportAction.class),
                new ActionHandler<>(MLTaskDeleteAction.INSTANCE, DeleteTaskTransportAction.class),
                new ActionHandler<>(MLTaskSearchAction.INSTANCE, SearchTaskTransportAction.class),
                new ActionHandler<>(MLProfileAction.INSTANCE, MLProfileTransportAction.class)
            );
    }

    @Override
    public Collection<Object> createComponents(
        Client client,
        ClusterService clusterService,
        ThreadPool threadPool,
        ResourceWatcherService resourceWatcherService,
        ScriptService scriptService,
        NamedXContentRegistry xContentRegistry,
        Environment environment,
        NodeEnvironment nodeEnvironment,
        NamedWriteableRegistry namedWriteableRegistry,
        IndexNameExpressionResolver indexNameExpressionResolver,
        Supplier<RepositoriesService> repositoriesServiceSupplier
    ) {
        this.indexUtils = new IndexUtils(client, clusterService);
        this.client = client;
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        Settings settings = environment.settings();
        MLEngine.setDjlCachePath(environment.dataFiles()[0]);

        JvmService jvmService = new JvmService(environment.settings());
        MLCircuitBreakerService mlCircuitBreakerService = new MLCircuitBreakerService(jvmService).init();

        Map<Enum, MLStat<?>> stats = new ConcurrentHashMap<>();
        // cluster level stats
        stats.put(MLClusterLevelStat.ML_MODEL_INDEX_STATUS, new MLStat<>(true, new IndexStatusSupplier(indexUtils, ML_MODEL_INDEX)));
        stats.put(MLClusterLevelStat.ML_TASK_INDEX_STATUS, new MLStat<>(true, new IndexStatusSupplier(indexUtils, ML_TASK_INDEX)));
        stats.put(MLClusterLevelStat.ML_MODEL_COUNT, new MLStat<>(true, new CounterSupplier()));
        // node level stats
        stats.put(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_FAILURE_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_MODEL_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_CIRCUIT_BREAKER_TRIGGER_COUNT, new MLStat<>(false, new CounterSupplier()));
        this.mlStats = new MLStats(stats);

        mlIndicesHandler = new MLIndicesHandler(clusterService, client);
        mlTaskManager = new MLTaskManager(client, mlIndicesHandler);
        mlInputDatasetHandler = new MLInputDatasetHandler(client);

        MLTaskDispatcher mlTaskDispatcher = new MLTaskDispatcher(clusterService, client);
        mlTrainingTaskRunner = new MLTrainingTaskRunner(
            threadPool,
            clusterService,
            client,
            mlTaskManager,
            mlStats,
            mlIndicesHandler,
            mlInputDatasetHandler,
            mlTaskDispatcher,
            mlCircuitBreakerService
        );
        mlPredictTaskRunner = new MLPredictTaskRunner(
            threadPool,
            clusterService,
            client,
            mlTaskManager,
            mlStats,
            mlInputDatasetHandler,
            mlTaskDispatcher,
            mlCircuitBreakerService,
            xContentRegistry
        );
        mlTrainAndPredictTaskRunner = new MLTrainAndPredictTaskRunner(
            threadPool,
            clusterService,
            client,
            mlTaskManager,
            mlStats,
            mlInputDatasetHandler,
            mlTaskDispatcher,
            mlCircuitBreakerService
        );
        mlExecuteTaskRunner = new MLExecuteTaskRunner(
            threadPool,
            clusterService,
            client,
            mlTaskManager,
            mlStats,
            mlInputDatasetHandler,
            mlTaskDispatcher,
            mlCircuitBreakerService
        );

        // Register thread-safe ML objects here.
        LocalSampleCalculator localSampleCalculator = new LocalSampleCalculator(client, settings);
        MLEngineClassLoader.register(FunctionName.LOCAL_SAMPLE_CALCULATOR, localSampleCalculator);

        AnomalyLocalizerImpl anomalyLocalizer = new AnomalyLocalizerImpl(client, settings, clusterService, indexNameExpressionResolver);
        MLEngineClassLoader.register(FunctionName.ANOMALY_LOCALIZATION, anomalyLocalizer);

        MLSearchHandler mlSearchHandler = new MLSearchHandler(client, xContentRegistry);

        return ImmutableList
            .of(
                mlStats,
                mlTaskManager,
                mlIndicesHandler,
                mlInputDatasetHandler,
                mlTrainingTaskRunner,
                mlPredictTaskRunner,
                mlTrainAndPredictTaskRunner,
                mlExecuteTaskRunner,
                mlSearchHandler
            );
    }

    @Override
    public List<RestHandler> getRestHandlers(
        Settings settings,
        RestController restController,
        ClusterSettings clusterSettings,
        IndexScopedSettings indexScopedSettings,
        SettingsFilter settingsFilter,
        IndexNameExpressionResolver indexNameExpressionResolver,
        Supplier<DiscoveryNodes> nodesInCluster
    ) {
        RestMLStatsAction restMLStatsAction = new RestMLStatsAction(mlStats, clusterService, indexUtils);
        RestMLTrainingAction restMLTrainingAction = new RestMLTrainingAction();
        RestMLTrainAndPredictAction restMLTrainAndPredictAction = new RestMLTrainAndPredictAction();
        RestMLPredictionAction restMLPredictionAction = new RestMLPredictionAction();
        RestMLExecuteAction restMLExecuteAction = new RestMLExecuteAction();
        RestMLGetModelAction restMLGetModelAction = new RestMLGetModelAction();
        RestMLDeleteModelAction restMLDeleteModelAction = new RestMLDeleteModelAction();
        RestMLSearchModelAction restMLSearchModelAction = new RestMLSearchModelAction();
        RestMLGetTaskAction restMLGetTaskAction = new RestMLGetTaskAction();
        RestMLDeleteTaskAction restMLDeleteTaskAction = new RestMLDeleteTaskAction();
        RestMLSearchTaskAction restMLSearchTaskAction = new RestMLSearchTaskAction();
        RestMLProfileAction restMLProfileAction = new RestMLProfileAction(clusterService);

        return ImmutableList
            .of(
                restMLStatsAction,
                restMLTrainingAction,
                restMLPredictionAction,
                restMLExecuteAction,
                restMLTrainAndPredictAction,
                restMLGetModelAction,
                restMLDeleteModelAction,
                restMLSearchModelAction,
                restMLGetTaskAction,
                restMLDeleteTaskAction,
                restMLSearchTaskAction,
                restMLProfileAction
            );
    }

    @Override
    public List<ExecutorBuilder<?>> getExecutorBuilders(Settings settings) {
        FixedExecutorBuilder ml = new FixedExecutorBuilder(settings, TASK_THREAD_POOL, 4, 4, "ml.task_thread_pool", false);

        return Collections.singletonList(ml);
    }

    @Override
    public List<NamedXContentRegistry.Entry> getNamedXContent() {
        return ImmutableList
            .of(
                KMeansParams.XCONTENT_REGISTRY,
                LinearRegressionParams.XCONTENT_REGISTRY,
                AnomalyDetectionLibSVMParams.XCONTENT_REGISTRY,
                SampleAlgoParams.XCONTENT_REGISTRY,
                FitRCFParams.XCONTENT_REGISTRY,
                BatchRCFParams.XCONTENT_REGISTRY,
                LocalSampleCalculatorInput.XCONTENT_REGISTRY,
                AnomalyLocalizationInput.XCONTENT_REGISTRY_ENTRY,
                RCFSummarizeParams.XCONTENT_REGISTRY,
                LogisticRegressionParams.XCONTENT_REGISTRY
            );
    }
}
