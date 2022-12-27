/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.SettingsFilter;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.env.Environment;
import org.opensearch.env.NodeEnvironment;
import org.opensearch.ml.action.execute.TransportExecuteTaskAction;
import org.opensearch.ml.action.forward.TransportForwardAction;
import org.opensearch.ml.action.handler.MLSearchHandler;
import org.opensearch.ml.action.load.TransportLoadModelAction;
import org.opensearch.ml.action.load.TransportLoadModelOnNodeAction;
import org.opensearch.ml.action.models.DeleteModelTransportAction;
import org.opensearch.ml.action.models.GetModelTransportAction;
import org.opensearch.ml.action.models.SearchModelTransportAction;
import org.opensearch.ml.action.prediction.TransportPredictionTaskAction;
import org.opensearch.ml.action.profile.MLProfileAction;
import org.opensearch.ml.action.profile.MLProfileTransportAction;
import org.opensearch.ml.action.stats.MLStatsNodesAction;
import org.opensearch.ml.action.stats.MLStatsNodesTransportAction;
import org.opensearch.ml.action.syncup.TransportSyncUpOnNodeAction;
import org.opensearch.ml.action.tasks.DeleteTaskTransportAction;
import org.opensearch.ml.action.tasks.GetTaskTransportAction;
import org.opensearch.ml.action.tasks.SearchTaskTransportAction;
import org.opensearch.ml.action.training.TransportTrainingTaskAction;
import org.opensearch.ml.action.trainpredict.TransportTrainAndPredictionTaskAction;
import org.opensearch.ml.action.unload.TransportUnloadModelAction;
import org.opensearch.ml.action.upload.TransportUploadModelAction;
import org.opensearch.ml.action.upload_chunk.MLModelChunkUploader;
import org.opensearch.ml.action.upload_chunk.MLModelMetaCreate;
import org.opensearch.ml.action.upload_chunk.TransportCreateModelMetaAction;
import org.opensearch.ml.action.upload_chunk.TransportUploadModelChunkAction;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.cluster.MLCommonsClusterEventListener;
import org.opensearch.ml.cluster.MLCommonsClusterManagerEventListener;
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
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.forward.MLForwardAction;
import org.opensearch.ml.common.transport.load.MLLoadModelAction;
import org.opensearch.ml.common.transport.load.MLLoadModelOnNodeAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.task.MLTaskDeleteAction;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskSearchAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;
import org.opensearch.ml.common.transport.unload.MLUnloadModelAction;
import org.opensearch.ml.common.transport.upload.MLUploadModelAction;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaAction;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkAction;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.algorithms.anomalylocalization.AnomalyLocalizerImpl;
import org.opensearch.ml.engine.algorithms.sample.LocalSampleCalculator;
import org.opensearch.ml.factory.MultiGetRequestBuilderFactory;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.indices.MLInputDatasetHandler;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.rest.RestMLCreateModelMetaAction;
import org.opensearch.ml.rest.RestMLDeleteModelAction;
import org.opensearch.ml.rest.RestMLDeleteTaskAction;
import org.opensearch.ml.rest.RestMLExecuteAction;
import org.opensearch.ml.rest.RestMLGetModelAction;
import org.opensearch.ml.rest.RestMLGetTaskAction;
import org.opensearch.ml.rest.RestMLLoadModelAction;
import org.opensearch.ml.rest.RestMLPredictionAction;
import org.opensearch.ml.rest.RestMLProfileAction;
import org.opensearch.ml.rest.RestMLSearchModelAction;
import org.opensearch.ml.rest.RestMLSearchTaskAction;
import org.opensearch.ml.rest.RestMLStatsAction;
import org.opensearch.ml.rest.RestMLTrainAndPredictAction;
import org.opensearch.ml.rest.RestMLTrainingAction;
import org.opensearch.ml.rest.RestMLUnloadModelAction;
import org.opensearch.ml.rest.RestMLUploadModelAction;
import org.opensearch.ml.rest.RestMLUploadModelChunkAction;
import org.opensearch.ml.settings.MLCommonsSettings;
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
    public static final String ML_THREAD_POOL_PREFIX = "thread_pool.ml_commons.";
    public static final String GENERAL_THREAD_POOL = "opensearch_ml_general";
    public static final String EXECUTE_THREAD_POOL = "opensearch_ml_execute";
    public static final String TRAIN_THREAD_POOL = "opensearch_ml_train";
    public static final String PREDICT_THREAD_POOL = "opensearch_ml_predict";
    public static final String UPLOAD_THREAD_POOL = "opensearch_ml_upload";
    public static final String LOAD_THREAD_POOL = "opensearch_ml_load";
    public static final String ML_BASE_URI = "/_plugins/_ml";

    private MLStats mlStats;
    private MLModelCacheHelper modelCacheHelper;
    private MLTaskManager mlTaskManager;
    private MLModelManager mlModelManager;
    private MLIndicesHandler mlIndicesHandler;
    private MLInputDatasetHandler mlInputDatasetHandler;
    private MLTrainingTaskRunner mlTrainingTaskRunner;
    private MLPredictTaskRunner mlPredictTaskRunner;
    private MLTrainAndPredictTaskRunner mlTrainAndPredictTaskRunner;
    private MLExecuteTaskRunner mlExecuteTaskRunner;
    private IndexUtils indexUtils;
    private ModelHelper modelHelper;
    private DiscoveryNodeHelper nodeHelper;

    private MLModelMetaCreate mlModelMetaCreate;
    private MLModelChunkUploader mlModelChunkUploader;
    private MLEngine mlEngine;

    private Client client;
    private ClusterService clusterService;
    private ThreadPool threadPool;
    private Set<String> indicesToListen;

    public static final String ML_ROLE_NAME = "ml";
    private NamedXContentRegistry xContentRegistry;

    private MultiGetRequestBuilderFactory multiGetRequestBuilderFactory;

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
                new ActionHandler<>(MLProfileAction.INSTANCE, MLProfileTransportAction.class),
                new ActionHandler<>(MLUploadModelAction.INSTANCE, TransportUploadModelAction.class),
                new ActionHandler<>(MLLoadModelAction.INSTANCE, TransportLoadModelAction.class),
                new ActionHandler<>(MLLoadModelOnNodeAction.INSTANCE, TransportLoadModelOnNodeAction.class),
                new ActionHandler<>(MLUnloadModelAction.INSTANCE, TransportUnloadModelAction.class),
                new ActionHandler<>(MLCreateModelMetaAction.INSTANCE, TransportCreateModelMetaAction.class),
                new ActionHandler<>(MLUploadModelChunkAction.INSTANCE, TransportUploadModelChunkAction.class),
                new ActionHandler<>(MLForwardAction.INSTANCE, TransportForwardAction.class),
                new ActionHandler<>(MLSyncUpAction.INSTANCE, TransportSyncUpOnNodeAction.class)
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
        this.xContentRegistry = xContentRegistry;
        Settings settings = environment.settings();
        mlEngine = new MLEngine(environment.dataFiles()[0]);
        nodeHelper = new DiscoveryNodeHelper(clusterService, settings);
        modelCacheHelper = new MLModelCacheHelper(clusterService, settings);

        JvmService jvmService = new JvmService(environment.settings());
        MLCircuitBreakerService mlCircuitBreakerService = new MLCircuitBreakerService(jvmService).init(environment.dataFiles()[0]);

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
        mlTaskManager = new MLTaskManager(client, threadPool, mlIndicesHandler);
        modelHelper = new ModelHelper(mlEngine);
        mlModelManager = new MLModelManager(
            clusterService,
            client,
            threadPool,
            xContentRegistry,
            modelHelper,
            settings,
            mlStats,
            mlCircuitBreakerService,
            mlIndicesHandler,
            mlTaskManager,
            modelCacheHelper,
            mlEngine
        );
        mlInputDatasetHandler = new MLInputDatasetHandler(client);

        mlModelMetaCreate = new MLModelMetaCreate(mlIndicesHandler, threadPool, client);
        mlModelChunkUploader = new MLModelChunkUploader(mlIndicesHandler, client, xContentRegistry);
        multiGetRequestBuilderFactory = new MultiGetRequestBuilderFactory();
        MLTaskDispatcher mlTaskDispatcher = new MLTaskDispatcher(clusterService, client, settings, nodeHelper);
        mlTrainingTaskRunner = new MLTrainingTaskRunner(
            threadPool,
            clusterService,
            client,
            mlTaskManager,
            mlStats,
            mlIndicesHandler,
            mlInputDatasetHandler,
            mlTaskDispatcher,
            mlCircuitBreakerService,
            nodeHelper,
            mlEngine
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
            xContentRegistry,
            mlModelManager,
            nodeHelper,
            mlEngine
        );
        mlTrainAndPredictTaskRunner = new MLTrainAndPredictTaskRunner(
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
        );
        mlExecuteTaskRunner = new MLExecuteTaskRunner(
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
        );

        // Register thread-safe ML objects here.
        LocalSampleCalculator localSampleCalculator = new LocalSampleCalculator(client, settings);
        MLEngineClassLoader.register(FunctionName.LOCAL_SAMPLE_CALCULATOR, localSampleCalculator);

        AnomalyLocalizerImpl anomalyLocalizer = new AnomalyLocalizerImpl(client, settings, clusterService, indexNameExpressionResolver);
        MLEngineClassLoader.register(FunctionName.ANOMALY_LOCALIZATION, anomalyLocalizer);

        MLSearchHandler mlSearchHandler = new MLSearchHandler(client, xContentRegistry);

        MLCommonsClusterEventListener mlCommonsClusterEventListener = new MLCommonsClusterEventListener(
            clusterService,
            mlModelManager,
            mlTaskManager,
            modelCacheHelper
        );
        MLCommonsClusterManagerEventListener clusterManagerEventListener = new MLCommonsClusterManagerEventListener(
            clusterService,
            client,
            settings,
            threadPool,
            nodeHelper
        );

        return ImmutableList
            .of(
                mlEngine,
                nodeHelper,
                modelCacheHelper,
                mlStats,
                mlTaskManager,
                mlModelManager,
                mlIndicesHandler,
                mlInputDatasetHandler,
                mlTrainingTaskRunner,
                mlPredictTaskRunner,
                mlTrainAndPredictTaskRunner,
                mlExecuteTaskRunner,
                mlSearchHandler,
                mlTaskDispatcher,
                mlModelMetaCreate,
                mlModelChunkUploader,
                modelHelper,
                mlCommonsClusterEventListener,
                clusterManagerEventListener,
                mlCircuitBreakerService
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
        RestMLProfileAction restMLProfileAction = new RestMLProfileAction(clusterService, multiGetRequestBuilderFactory);
        RestMLUploadModelAction restMLUploadModelAction = new RestMLUploadModelAction();
        RestMLLoadModelAction restMLLoadModelAction = new RestMLLoadModelAction();
        RestMLUnloadModelAction restMLUnloadModelAction = new RestMLUnloadModelAction(clusterService);
        RestMLCreateModelMetaAction restMLCreateModelMetaAction = new RestMLCreateModelMetaAction();
        RestMLUploadModelChunkAction restMLUploadModelChunkAction = new RestMLUploadModelChunkAction();

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
                restMLProfileAction,
                restMLUploadModelAction,
                restMLLoadModelAction,
                restMLUnloadModelAction,
                restMLCreateModelMetaAction,
                restMLUploadModelChunkAction
            );
    }

    @Override
    public List<ExecutorBuilder<?>> getExecutorBuilders(Settings settings) {
        FixedExecutorBuilder generalThreadPool = new FixedExecutorBuilder(
            settings,
            GENERAL_THREAD_POOL,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(settings) - 1),
            100,
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL,
            false
        );
        FixedExecutorBuilder uploadThreadPool = new FixedExecutorBuilder(
            settings,
            UPLOAD_THREAD_POOL,
            Math.max(4, OpenSearchExecutors.allocatedProcessors(settings) - 1),
            10,
            ML_THREAD_POOL_PREFIX + UPLOAD_THREAD_POOL,
            false
        );
        FixedExecutorBuilder loadThreadPool = new FixedExecutorBuilder(
            settings,
            LOAD_THREAD_POOL,
            Math.max(4, OpenSearchExecutors.allocatedProcessors(settings) - 1),
            10,
            ML_THREAD_POOL_PREFIX + LOAD_THREAD_POOL,
            false
        );
        FixedExecutorBuilder executeThreadPool = new FixedExecutorBuilder(
            settings,
            EXECUTE_THREAD_POOL,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(settings) - 1),
            10,
            ML_THREAD_POOL_PREFIX + EXECUTE_THREAD_POOL,
            false
        );
        FixedExecutorBuilder trainThreadPool = new FixedExecutorBuilder(
            settings,
            TRAIN_THREAD_POOL,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(settings) - 1),
            10,
            ML_THREAD_POOL_PREFIX + TRAIN_THREAD_POOL,
            false
        );
        FixedExecutorBuilder predictThreadPool = new FixedExecutorBuilder(
            settings,
            PREDICT_THREAD_POOL,
            OpenSearchExecutors.allocatedProcessors(settings) * 2,
            10000,
            ML_THREAD_POOL_PREFIX + PREDICT_THREAD_POOL,
            false
        );

        return ImmutableList.of(generalThreadPool, uploadThreadPool, loadThreadPool, executeThreadPool, trainThreadPool, predictThreadPool);
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
                LogisticRegressionParams.XCONTENT_REGISTRY,
                TextEmbeddingModelConfig.XCONTENT_REGISTRY
            );
    }

    @Override
    public List<Setting<?>> getSettings() {
        List<Setting<?>> settings = ImmutableList
            .of(
                MLCommonsSettings.ML_COMMONS_TASK_DISPATCH_POLICY,
                MLCommonsSettings.ML_COMMONS_MAX_MODELS_PER_NODE,
                MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE,
                MLCommonsSettings.ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS,
                MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT,
                MLCommonsSettings.ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE,
                MLCommonsSettings.ML_COMMONS_MAX_ML_TASK_PER_NODE,
                MLCommonsSettings.ML_COMMONS_MAX_LOAD_MODEL_TASKS_PER_NODE,
                MLCommonsSettings.ML_COMMONS_TRUSTED_URL_REGEX
            );
        return settings;
    }
}
