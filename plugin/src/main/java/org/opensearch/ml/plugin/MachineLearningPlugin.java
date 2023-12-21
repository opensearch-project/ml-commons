/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin;

import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.opensearch.action.ActionRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.IndexScopedSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.SettingsFilter;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.NamedWriteableRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.env.Environment;
import org.opensearch.env.NodeEnvironment;
import org.opensearch.ml.action.agents.DeleteAgentTransportAction;
import org.opensearch.ml.action.agents.GetAgentTransportAction;
import org.opensearch.ml.action.agents.TransportRegisterAgentAction;
import org.opensearch.ml.action.connector.DeleteConnectorTransportAction;
import org.opensearch.ml.action.connector.GetConnectorTransportAction;
import org.opensearch.ml.action.connector.SearchConnectorTransportAction;
import org.opensearch.ml.action.connector.TransportCreateConnectorAction;
import org.opensearch.ml.action.connector.UpdateConnectorTransportAction;
import org.opensearch.ml.action.deploy.TransportDeployModelAction;
import org.opensearch.ml.action.deploy.TransportDeployModelOnNodeAction;
import org.opensearch.ml.action.execute.TransportExecuteTaskAction;
import org.opensearch.ml.action.forward.TransportForwardAction;
import org.opensearch.ml.action.handler.MLSearchHandler;
import org.opensearch.ml.action.model_group.DeleteModelGroupTransportAction;
import org.opensearch.ml.action.model_group.SearchModelGroupTransportAction;
import org.opensearch.ml.action.model_group.TransportRegisterModelGroupAction;
import org.opensearch.ml.action.model_group.TransportUpdateModelGroupAction;
import org.opensearch.ml.action.models.DeleteModelTransportAction;
import org.opensearch.ml.action.models.GetModelTransportAction;
import org.opensearch.ml.action.models.InPlaceUpdateModelTransportAction;
import org.opensearch.ml.action.models.SearchModelTransportAction;
import org.opensearch.ml.action.models.UpdateModelTransportAction;
import org.opensearch.ml.action.prediction.TransportPredictionTaskAction;
import org.opensearch.ml.action.profile.MLProfileAction;
import org.opensearch.ml.action.profile.MLProfileTransportAction;
import org.opensearch.ml.action.register.TransportRegisterModelAction;
import org.opensearch.ml.action.stats.MLStatsNodesAction;
import org.opensearch.ml.action.stats.MLStatsNodesTransportAction;
import org.opensearch.ml.action.syncup.TransportSyncUpOnNodeAction;
import org.opensearch.ml.action.tasks.DeleteTaskTransportAction;
import org.opensearch.ml.action.tasks.GetTaskTransportAction;
import org.opensearch.ml.action.tasks.SearchTaskTransportAction;
import org.opensearch.ml.action.tools.GetToolTransportAction;
import org.opensearch.ml.action.tools.ListToolsTransportAction;
import org.opensearch.ml.action.training.TransportTrainingTaskAction;
import org.opensearch.ml.action.trainpredict.TransportTrainAndPredictionTaskAction;
import org.opensearch.ml.action.undeploy.TransportUndeployModelAction;
import org.opensearch.ml.action.undeploy.TransportUndeployModelsAction;
import org.opensearch.ml.action.upload_chunk.MLModelChunkUploader;
import org.opensearch.ml.action.upload_chunk.TransportRegisterModelMetaAction;
import org.opensearch.ml.action.upload_chunk.TransportUploadModelChunkAction;
import org.opensearch.ml.autoredeploy.MLModelAutoReDeployer;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.cluster.MLCommonsClusterEventListener;
import org.opensearch.ml.cluster.MLCommonsClusterManagerEventListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.execute.anomalylocalization.AnomalyLocalizationInput;
import org.opensearch.ml.common.input.execute.metricscorrelation.MetricsCorrelationInput;
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
import org.opensearch.ml.common.spi.MLCommonsExtension;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteAction;
import org.opensearch.ml.common.transport.agent.MLAgentGetAction;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentAction;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteAction;
import org.opensearch.ml.common.transport.connector.MLConnectorGetAction;
import org.opensearch.ml.common.transport.connector.MLConnectorSearchAction;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelOnNodeAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.forward.MLForwardAction;
import org.opensearch.ml.common.transport.model.MLInPlaceUpdateModelAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.model.MLUpdateModelAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupDeleteAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupSearchAction;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.task.MLTaskDeleteAction;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskSearchAction;
import org.opensearch.ml.common.transport.tools.MLGetToolAction;
import org.opensearch.ml.common.transport.tools.MLListToolsAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsAction;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaAction;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkAction;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.algorithms.agent.MLAgentExecutor;
import org.opensearch.ml.engine.algorithms.anomalylocalization.AnomalyLocalizerImpl;
import org.opensearch.ml.engine.algorithms.metrics_correlation.MetricsCorrelation;
import org.opensearch.ml.engine.algorithms.sample.LocalSampleCalculator;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.engine.indices.MLInputDatasetHandler;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.MLMemoryManager;
import org.opensearch.ml.engine.tools.*;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.memory.ConversationalMemoryHandler;
import org.opensearch.ml.memory.action.conversation.CreateConversationAction;
import org.opensearch.ml.memory.action.conversation.CreateConversationTransportAction;
import org.opensearch.ml.memory.action.conversation.CreateInteractionAction;
import org.opensearch.ml.memory.action.conversation.CreateInteractionTransportAction;
import org.opensearch.ml.memory.action.conversation.DeleteConversationAction;
import org.opensearch.ml.memory.action.conversation.DeleteConversationTransportAction;
import org.opensearch.ml.memory.action.conversation.GetConversationAction;
import org.opensearch.ml.memory.action.conversation.GetConversationTransportAction;
import org.opensearch.ml.memory.action.conversation.GetConversationsAction;
import org.opensearch.ml.memory.action.conversation.GetConversationsTransportAction;
import org.opensearch.ml.memory.action.conversation.GetInteractionAction;
import org.opensearch.ml.memory.action.conversation.GetInteractionTransportAction;
import org.opensearch.ml.memory.action.conversation.GetInteractionsAction;
import org.opensearch.ml.memory.action.conversation.GetInteractionsTransportAction;
import org.opensearch.ml.memory.action.conversation.GetTracesAction;
import org.opensearch.ml.memory.action.conversation.GetTracesTransportAction;
import org.opensearch.ml.memory.action.conversation.SearchConversationsAction;
import org.opensearch.ml.memory.action.conversation.SearchConversationsTransportAction;
import org.opensearch.ml.memory.action.conversation.SearchInteractionsAction;
import org.opensearch.ml.memory.action.conversation.SearchInteractionsTransportAction;
import org.opensearch.ml.memory.action.conversation.UpdateConversationAction;
import org.opensearch.ml.memory.action.conversation.UpdateConversationTransportAction;
import org.opensearch.ml.memory.action.conversation.UpdateInteractionAction;
import org.opensearch.ml.memory.action.conversation.UpdateInteractionTransportAction;
import org.opensearch.ml.memory.index.ConversationMetaIndex;
import org.opensearch.ml.memory.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableList;
import org.opensearch.ml.rest.RestMLCreateConnectorAction;
import org.opensearch.ml.rest.RestMLDeleteAgentAction;
import org.opensearch.ml.rest.RestMLDeleteConnectorAction;
import org.opensearch.ml.rest.RestMLDeleteModelAction;
import org.opensearch.ml.rest.RestMLDeleteModelGroupAction;
import org.opensearch.ml.rest.RestMLDeleteTaskAction;
import org.opensearch.ml.rest.RestMLDeployModelAction;
import org.opensearch.ml.rest.RestMLExecuteAction;
import org.opensearch.ml.rest.RestMLGetAgentAction;
import org.opensearch.ml.rest.RestMLGetConnectorAction;
import org.opensearch.ml.rest.RestMLGetModelAction;
import org.opensearch.ml.rest.RestMLGetTaskAction;
import org.opensearch.ml.rest.RestMLGetToolAction;
import org.opensearch.ml.rest.RestMLListToolsAction;
import org.opensearch.ml.rest.RestMLPredictionAction;
import org.opensearch.ml.rest.RestMLProfileAction;
import org.opensearch.ml.rest.RestMLRegisterAgentAction;
import org.opensearch.ml.rest.RestMLRegisterModelAction;
import org.opensearch.ml.rest.RestMLRegisterModelGroupAction;
import org.opensearch.ml.rest.RestMLRegisterModelMetaAction;
import org.opensearch.ml.rest.RestMLSearchConnectorAction;
import org.opensearch.ml.rest.RestMLSearchModelAction;
import org.opensearch.ml.rest.RestMLSearchModelGroupAction;
import org.opensearch.ml.rest.RestMLSearchTaskAction;
import org.opensearch.ml.rest.RestMLStatsAction;
import org.opensearch.ml.rest.RestMLTrainAndPredictAction;
import org.opensearch.ml.rest.RestMLTrainingAction;
import org.opensearch.ml.rest.RestMLUndeployModelAction;
import org.opensearch.ml.rest.RestMLUpdateConnectorAction;
import org.opensearch.ml.rest.RestMLUpdateModelAction;
import org.opensearch.ml.rest.RestMLUpdateModelGroupAction;
import org.opensearch.ml.rest.RestMLUploadModelChunkAction;
import org.opensearch.ml.rest.RestMemoryCreateConversationAction;
import org.opensearch.ml.rest.RestMemoryCreateInteractionAction;
import org.opensearch.ml.rest.RestMemoryDeleteConversationAction;
import org.opensearch.ml.rest.RestMemoryGetConversationAction;
import org.opensearch.ml.rest.RestMemoryGetConversationsAction;
import org.opensearch.ml.rest.RestMemoryGetInteractionAction;
import org.opensearch.ml.rest.RestMemoryGetInteractionsAction;
import org.opensearch.ml.rest.RestMemoryGetTracesAction;
import org.opensearch.ml.rest.RestMemorySearchConversationsAction;
import org.opensearch.ml.rest.RestMemorySearchInteractionsAction;
import org.opensearch.ml.rest.RestMemoryUpdateConversationAction;
import org.opensearch.ml.rest.RestMemoryUpdateInteractionAction;
import org.opensearch.ml.settings.MLCommonsSettings;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
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
import org.opensearch.monitor.os.OsService;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.ExtensiblePlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.plugins.SearchPipelinePlugin;
import org.opensearch.plugins.SearchPlugin;
import org.opensearch.repositories.RepositoriesService;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestHandler;
import org.opensearch.script.ScriptService;
import org.opensearch.search.pipeline.Processor;
import org.opensearch.search.pipeline.SearchRequestProcessor;
import org.opensearch.search.pipeline.SearchResponseProcessor;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQAProcessorConstants;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQARequestProcessor;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQAResponseProcessor;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParamExtBuilder;
import org.opensearch.threadpool.ExecutorBuilder;
import org.opensearch.threadpool.FixedExecutorBuilder;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.watcher.ResourceWatcherService;

import lombok.SneakyThrows;

public class MachineLearningPlugin extends Plugin implements ActionPlugin, SearchPlugin, SearchPipelinePlugin, ExtensiblePlugin {
    public static final String ML_THREAD_POOL_PREFIX = "thread_pool.ml_commons.";
    public static final String GENERAL_THREAD_POOL = "opensearch_ml_general";
    public static final String EXECUTE_THREAD_POOL = "opensearch_ml_execute";
    public static final String TRAIN_THREAD_POOL = "opensearch_ml_train";
    public static final String PREDICT_THREAD_POOL = "opensearch_ml_predict";
    public static final String REGISTER_THREAD_POOL = "opensearch_ml_register";
    public static final String DEPLOY_THREAD_POOL = "opensearch_ml_deploy";
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

    private MLModelChunkUploader mlModelChunkUploader;
    private MLEngine mlEngine;

    private Client client;
    private ClusterService clusterService;
    private ThreadPool threadPool;
    private Set<String> indicesToListen;

    public static final String ML_ROLE_NAME = "ml";
    private NamedXContentRegistry xContentRegistry;

    private ModelAccessControlHelper modelAccessControlHelper;

    private ConnectorAccessControlHelper connectorAccessControlHelper;

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private ConversationalMemoryHandler cmHandler;

    private volatile boolean ragSearchPipelineEnabled;

    private Map<String, Tool.Factory> externalToolFactories;
    private Map<String, Tool.Factory> toolFactories;
    private ScriptService scriptService;
    private Encryptor encryptor;

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
                new ActionHandler<>(MLRegisterModelAction.INSTANCE, TransportRegisterModelAction.class),
                new ActionHandler<>(MLDeployModelAction.INSTANCE, TransportDeployModelAction.class),
                new ActionHandler<>(MLDeployModelOnNodeAction.INSTANCE, TransportDeployModelOnNodeAction.class),
                new ActionHandler<>(MLUndeployModelAction.INSTANCE, TransportUndeployModelAction.class),
                new ActionHandler<>(MLUndeployModelsAction.INSTANCE, TransportUndeployModelsAction.class),
                new ActionHandler<>(MLRegisterModelMetaAction.INSTANCE, TransportRegisterModelMetaAction.class),
                new ActionHandler<>(MLUploadModelChunkAction.INSTANCE, TransportUploadModelChunkAction.class),
                new ActionHandler<>(MLUpdateModelAction.INSTANCE, UpdateModelTransportAction.class),
                new ActionHandler<>(MLInPlaceUpdateModelAction.INSTANCE, InPlaceUpdateModelTransportAction.class),
                new ActionHandler<>(MLForwardAction.INSTANCE, TransportForwardAction.class),
                new ActionHandler<>(MLSyncUpAction.INSTANCE, TransportSyncUpOnNodeAction.class),
                new ActionHandler<>(MLRegisterModelGroupAction.INSTANCE, TransportRegisterModelGroupAction.class),
                new ActionHandler<>(MLUpdateModelGroupAction.INSTANCE, TransportUpdateModelGroupAction.class),
                new ActionHandler<>(MLModelGroupSearchAction.INSTANCE, SearchModelGroupTransportAction.class),
                new ActionHandler<>(MLModelGroupDeleteAction.INSTANCE, DeleteModelGroupTransportAction.class),
                new ActionHandler<>(MLCreateConnectorAction.INSTANCE, TransportCreateConnectorAction.class),
                new ActionHandler<>(MLConnectorGetAction.INSTANCE, GetConnectorTransportAction.class),
                new ActionHandler<>(MLConnectorDeleteAction.INSTANCE, DeleteConnectorTransportAction.class),
                new ActionHandler<>(MLConnectorSearchAction.INSTANCE, SearchConnectorTransportAction.class),
                new ActionHandler<>(CreateConversationAction.INSTANCE, CreateConversationTransportAction.class),
                new ActionHandler<>(GetConversationsAction.INSTANCE, GetConversationsTransportAction.class),
                new ActionHandler<>(CreateInteractionAction.INSTANCE, CreateInteractionTransportAction.class),
                new ActionHandler<>(GetInteractionsAction.INSTANCE, GetInteractionsTransportAction.class),
                new ActionHandler<>(DeleteConversationAction.INSTANCE, DeleteConversationTransportAction.class),
                new ActionHandler<>(MLUpdateConnectorAction.INSTANCE, UpdateConnectorTransportAction.class),
                new ActionHandler<>(MLRegisterAgentAction.INSTANCE, TransportRegisterAgentAction.class),
                new ActionHandler<>(MLListToolsAction.INSTANCE, ListToolsTransportAction.class),
                new ActionHandler<>(MLGetToolAction.INSTANCE, GetToolTransportAction.class),
                new ActionHandler<>(SearchInteractionsAction.INSTANCE, SearchInteractionsTransportAction.class),
                new ActionHandler<>(SearchConversationsAction.INSTANCE, SearchConversationsTransportAction.class),
                new ActionHandler<>(UpdateConversationAction.INSTANCE, UpdateConversationTransportAction.class),
                new ActionHandler<>(UpdateInteractionAction.INSTANCE, UpdateInteractionTransportAction.class),
                new ActionHandler<>(GetTracesAction.INSTANCE, GetTracesTransportAction.class),
                new ActionHandler<>(MLAgentGetAction.INSTANCE, GetAgentTransportAction.class),
                new ActionHandler<>(MLAgentDeleteAction.INSTANCE, DeleteAgentTransportAction.class),
                new ActionHandler<>(GetConversationAction.INSTANCE, GetConversationTransportAction.class),
                new ActionHandler<>(GetInteractionAction.INSTANCE, GetInteractionTransportAction.class)
            );
    }

    @SneakyThrows
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
        this.scriptService = scriptService;
        Settings settings = environment.settings();
        Path dataPath = environment.dataFiles()[0];
        Path configFile = environment.configFile();

        encryptor = new EncryptorImpl(clusterService, client);

        mlEngine = new MLEngine(dataPath, encryptor);
        nodeHelper = new DiscoveryNodeHelper(clusterService, settings);
        modelCacheHelper = new MLModelCacheHelper(clusterService, settings);
        cmHandler = new OpenSearchConversationalMemoryHandler(client, clusterService);

        JvmService jvmService = new JvmService(environment.settings());
        OsService osService = new OsService(environment.settings());
        MLCircuitBreakerService mlCircuitBreakerService = new MLCircuitBreakerService(jvmService, osService, settings, clusterService)
            .init(dataPath);

        Map<Enum, MLStat<?>> stats = new ConcurrentHashMap<>();
        // cluster level stats
        stats.put(MLClusterLevelStat.ML_MODEL_INDEX_STATUS, new MLStat<>(true, new IndexStatusSupplier(indexUtils, ML_MODEL_INDEX)));
        stats
            .put(MLClusterLevelStat.ML_CONNECTOR_INDEX_STATUS, new MLStat<>(true, new IndexStatusSupplier(indexUtils, ML_CONNECTOR_INDEX)));
        stats.put(MLClusterLevelStat.ML_CONFIG_INDEX_STATUS, new MLStat<>(true, new IndexStatusSupplier(indexUtils, ML_CONFIG_INDEX)));
        stats.put(MLClusterLevelStat.ML_TASK_INDEX_STATUS, new MLStat<>(true, new IndexStatusSupplier(indexUtils, ML_TASK_INDEX)));
        stats.put(MLClusterLevelStat.ML_MODEL_COUNT, new MLStat<>(true, new CounterSupplier()));
        stats.put(MLClusterLevelStat.ML_CONNECTOR_COUNT, new MLStat<>(true, new CounterSupplier()));
        // node level stats
        stats.put(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_REQUEST_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_FAILURE_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_CIRCUIT_BREAKER_TRIGGER_COUNT, new MLStat<>(false, new CounterSupplier()));
        this.mlStats = new MLStats(stats);

        mlIndicesHandler = new MLIndicesHandler(clusterService, client);
        mlTaskManager = new MLTaskManager(client, threadPool, mlIndicesHandler);
        modelHelper = new ModelHelper(mlEngine);
        mlModelManager = new MLModelManager(
            clusterService,
            scriptService,
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
            mlEngine,
            nodeHelper
        );
        mlInputDatasetHandler = new MLInputDatasetHandler(client);
        modelAccessControlHelper = new ModelAccessControlHelper(clusterService, settings);
        connectorAccessControlHelper = new ConnectorAccessControlHelper(clusterService, settings);
        mlFeatureEnabledSetting = new MLFeatureEnabledSetting(clusterService, settings);

        mlModelChunkUploader = new MLModelChunkUploader(mlIndicesHandler, client, xContentRegistry, modelAccessControlHelper);

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

        toolFactories = new HashMap<>();

        MLModelTool.Factory.getInstance().init(client);
        MathTool.Factory.getInstance().init(scriptService);
        VectorDBTool.Factory.getInstance().init(client, xContentRegistry);
        NeuralSparseTool.Factory.getInstance().init(client, xContentRegistry);
        AgentTool.Factory.getInstance().init(client);
        CatIndexTool.Factory.getInstance().init(client, clusterService);
        PainlessScriptTool.Factory.getInstance().init(client, scriptService);
        VisualizationsTool.Factory.getInstance().init(client);
        RAGTool.Factory.getInstance().init(client, xContentRegistry);
        SearchAlertsTool.Factory.getInstance().init(client);
        IndexMappingTool.Factory.getInstance().init(client);

        toolFactories.put(MLModelTool.TYPE, MLModelTool.Factory.getInstance());
        toolFactories.put(MathTool.TYPE, MathTool.Factory.getInstance());
        toolFactories.put(VectorDBTool.TYPE, VectorDBTool.Factory.getInstance());
        toolFactories.put(NeuralSparseTool.TYPE, NeuralSparseTool.Factory.getInstance());
        toolFactories.put(AgentTool.TYPE, AgentTool.Factory.getInstance());
        toolFactories.put(CatIndexTool.TYPE, CatIndexTool.Factory.getInstance());
        toolFactories.put(PainlessScriptTool.TYPE, PainlessScriptTool.Factory.getInstance());
        toolFactories.put(VisualizationsTool.TYPE, VisualizationsTool.Factory.getInstance());
        toolFactories.put(SearchAlertsTool.TYPE, SearchAlertsTool.Factory.getInstance());
        toolFactories.put(RAGTool.TYPE, RAGTool.Factory.getInstance());
        toolFactories.put(IndexMappingTool.TYPE, IndexMappingTool.Factory.getInstance());

        if (externalToolFactories != null) {
            toolFactories.putAll(externalToolFactories);
        }

        MLMemoryManager memoryManager = new MLMemoryManager(client, clusterService, new ConversationMetaIndex(client, clusterService));
        Map<String, Memory.Factory> memoryFactoryMap = new HashMap<>();
        ConversationIndexMemory.Factory conversationIndexMemoryFactory = new ConversationIndexMemory.Factory();
        conversationIndexMemoryFactory.init(client, mlIndicesHandler, memoryManager);
        memoryFactoryMap.put(ConversationIndexMemory.TYPE, conversationIndexMemoryFactory);

        MLAgentExecutor agentExecutor = new MLAgentExecutor(
            client,
            settings,
            clusterService,
            xContentRegistry,
            toolFactories,
            memoryFactoryMap
        );
        MLEngineClassLoader.register(FunctionName.LOCAL_SAMPLE_CALCULATOR, localSampleCalculator);
        MLEngineClassLoader.register(FunctionName.AGENT, agentExecutor);

        AnomalyLocalizerImpl anomalyLocalizer = new AnomalyLocalizerImpl(client, settings, clusterService, indexNameExpressionResolver);
        MLEngineClassLoader.register(FunctionName.ANOMALY_LOCALIZATION, anomalyLocalizer);

        MetricsCorrelation metricsCorrelation = new MetricsCorrelation(client, settings, clusterService);
        MLEngineClassLoader.register(FunctionName.METRICS_CORRELATION, metricsCorrelation);
        MLSearchHandler mlSearchHandler = new MLSearchHandler(client, xContentRegistry, modelAccessControlHelper, clusterService);
        MLModelAutoReDeployer mlModelAutoRedeployer = new MLModelAutoReDeployer(
            clusterService,
            client,
            settings,
            mlModelManager,
            new MLModelAutoReDeployer.SearchRequestBuilderFactory()
        );
        MLCommonsClusterEventListener mlCommonsClusterEventListener = new MLCommonsClusterEventListener(
            clusterService,
            mlModelManager,
            mlTaskManager,
            modelCacheHelper,
            mlModelAutoRedeployer
        );
        MLCommonsClusterManagerEventListener clusterManagerEventListener = new MLCommonsClusterManagerEventListener(
            clusterService,
            client,
            settings,
            threadPool,
            nodeHelper,
            mlIndicesHandler,
            encryptor
        );

        // TODO move this into MLFeatureEnabledSetting
        // search processor factories below will get BooleanSupplier that supplies the current value being updated through this.
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_RAG_PIPELINE_FEATURE_ENABLED, it -> ragSearchPipelineEnabled = it);

        return ImmutableList
            .of(
                encryptor,
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
                modelAccessControlHelper,
                connectorAccessControlHelper,
                mlFeatureEnabledSetting,
                mlSearchHandler,
                mlTaskDispatcher,
                mlModelChunkUploader,
                modelHelper,
                mlCommonsClusterEventListener,
                clusterManagerEventListener,
                mlCircuitBreakerService,
                mlModelAutoRedeployer,
                cmHandler
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
        RestMLStatsAction restMLStatsAction = new RestMLStatsAction(mlStats, clusterService, indexUtils, xContentRegistry);
        RestMLTrainingAction restMLTrainingAction = new RestMLTrainingAction();
        RestMLTrainAndPredictAction restMLTrainAndPredictAction = new RestMLTrainAndPredictAction();
        RestMLPredictionAction restMLPredictionAction = new RestMLPredictionAction(mlModelManager, mlFeatureEnabledSetting);
        RestMLExecuteAction restMLExecuteAction = new RestMLExecuteAction(client);
        RestMLGetModelAction restMLGetModelAction = new RestMLGetModelAction();
        RestMLDeleteModelAction restMLDeleteModelAction = new RestMLDeleteModelAction();
        RestMLSearchModelAction restMLSearchModelAction = new RestMLSearchModelAction();
        RestMLGetTaskAction restMLGetTaskAction = new RestMLGetTaskAction();
        RestMLDeleteTaskAction restMLDeleteTaskAction = new RestMLDeleteTaskAction();
        RestMLSearchTaskAction restMLSearchTaskAction = new RestMLSearchTaskAction();
        RestMLProfileAction restMLProfileAction = new RestMLProfileAction(clusterService);
        RestMLRegisterModelAction restMLRegisterModelAction = new RestMLRegisterModelAction(
            clusterService,
            settings,
            mlFeatureEnabledSetting
        );
        RestMLRegisterAgentAction restMLRegisterAgentAction = new RestMLRegisterAgentAction();
        RestMLDeployModelAction restMLDeployModelAction = new RestMLDeployModelAction();
        RestMLUndeployModelAction restMLUndeployModelAction = new RestMLUndeployModelAction(clusterService, settings);
        RestMLRegisterModelMetaAction restMLRegisterModelMetaAction = new RestMLRegisterModelMetaAction(clusterService, settings);
        RestMLUploadModelChunkAction restMLUploadModelChunkAction = new RestMLUploadModelChunkAction(clusterService, settings);
        RestMLRegisterModelGroupAction restMLCreateModelGroupAction = new RestMLRegisterModelGroupAction();
        RestMLUpdateModelGroupAction restMLUpdateModelGroupAction = new RestMLUpdateModelGroupAction();
        RestMLSearchModelGroupAction restMLSearchModelGroupAction = new RestMLSearchModelGroupAction();
        RestMLUpdateModelAction restMLUpdateModelAction = new RestMLUpdateModelAction();
        RestMLDeleteModelGroupAction restMLDeleteModelGroupAction = new RestMLDeleteModelGroupAction();
        RestMLCreateConnectorAction restMLCreateConnectorAction = new RestMLCreateConnectorAction(mlFeatureEnabledSetting);
        RestMLGetConnectorAction restMLGetConnectorAction = new RestMLGetConnectorAction();
        RestMLDeleteConnectorAction restMLDeleteConnectorAction = new RestMLDeleteConnectorAction();
        RestMLSearchConnectorAction restMLSearchConnectorAction = new RestMLSearchConnectorAction();
        RestMemoryCreateConversationAction restCreateConversationAction = new RestMemoryCreateConversationAction();
        RestMemoryGetConversationsAction restListConversationsAction = new RestMemoryGetConversationsAction();
        RestMemoryCreateInteractionAction restCreateInteractionAction = new RestMemoryCreateInteractionAction();
        RestMemoryGetInteractionsAction restListInteractionsAction = new RestMemoryGetInteractionsAction();
        RestMemoryDeleteConversationAction restDeleteConversationAction = new RestMemoryDeleteConversationAction();
        RestMLUpdateConnectorAction restMLUpdateConnectorAction = new RestMLUpdateConnectorAction(mlFeatureEnabledSetting);
        RestMLListToolsAction restMLListToolsAction = new RestMLListToolsAction(toolFactories);
        RestMLGetToolAction restMLGetToolAction = new RestMLGetToolAction(toolFactories);
        RestMemorySearchConversationsAction restSearchConversationsAction = new RestMemorySearchConversationsAction();
        RestMemorySearchInteractionsAction restSearchInteractionsAction = new RestMemorySearchInteractionsAction();
        RestMemoryUpdateConversationAction restMemoryUpdateConversationAction = new RestMemoryUpdateConversationAction();
        RestMemoryUpdateInteractionAction restMemoryUpdateInteractionAction = new RestMemoryUpdateInteractionAction();
        RestMemoryGetTracesAction restMemoryGetTracesAction = new RestMemoryGetTracesAction();
        RestMLGetAgentAction restMLGetAgentAction = new RestMLGetAgentAction();
        RestMLDeleteAgentAction restMLDeleteAgentAction = new RestMLDeleteAgentAction();
        RestMemoryGetConversationAction restGetConversationAction = new RestMemoryGetConversationAction();
        RestMemoryGetInteractionAction restGetInteractionAction = new RestMemoryGetInteractionAction();
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
                restMLUpdateModelAction,
                restMLGetTaskAction,
                restMLDeleteTaskAction,
                restMLSearchTaskAction,
                restMLProfileAction,
                restMLRegisterModelAction,
                restMLRegisterAgentAction,
                restMLDeployModelAction,
                restMLUndeployModelAction,
                restMLRegisterModelMetaAction,
                restMLUploadModelChunkAction,
                restMLCreateModelGroupAction,
                restMLUpdateModelGroupAction,
                restMLSearchModelGroupAction,
                restMLDeleteModelGroupAction,
                restMLCreateConnectorAction,
                restMLGetConnectorAction,
                restMLDeleteConnectorAction,
                restMLSearchConnectorAction,
                restCreateConversationAction,
                restListConversationsAction,
                restCreateInteractionAction,
                restListInteractionsAction,
                restDeleteConversationAction,
                restMLUpdateConnectorAction,
                restMLListToolsAction,
                restMLGetToolAction,
                restSearchConversationsAction,
                restSearchInteractionsAction,
                restMemoryUpdateConversationAction,
                restMemoryUpdateInteractionAction,
                restMemoryGetTracesAction,
                restMLGetAgentAction,
                restMLDeleteAgentAction,
                restGetConversationAction,
                restGetInteractionAction
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
        FixedExecutorBuilder registerModelThreadPool = new FixedExecutorBuilder(
            settings,
            REGISTER_THREAD_POOL,
            Math.max(4, OpenSearchExecutors.allocatedProcessors(settings) - 1),
            10,
            ML_THREAD_POOL_PREFIX + REGISTER_THREAD_POOL,
            false
        );
        FixedExecutorBuilder deployModelThreadPool = new FixedExecutorBuilder(
            settings,
            DEPLOY_THREAD_POOL,
            Math.max(4, OpenSearchExecutors.allocatedProcessors(settings) - 1),
            10,
            ML_THREAD_POOL_PREFIX + DEPLOY_THREAD_POOL,
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

        return ImmutableList
            .of(generalThreadPool, registerModelThreadPool, deployModelThreadPool, executeThreadPool, trainThreadPool, predictThreadPool);
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
                MetricsCorrelationInput.XCONTENT_REGISTRY,
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
                MLCommonsSettings.ML_COMMONS_ML_TASK_TIMEOUT_IN_SECONDS,
                MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT,
                MLCommonsSettings.ML_COMMONS_MAX_REGISTER_MODEL_TASKS_PER_NODE,
                MLCommonsSettings.ML_COMMONS_MAX_ML_TASK_PER_NODE,
                MLCommonsSettings.ML_COMMONS_MAX_DEPLOY_MODEL_TASKS_PER_NODE,
                MLCommonsSettings.ML_COMMONS_TRUSTED_URL_REGEX,
                MLCommonsSettings.ML_COMMONS_NATIVE_MEM_THRESHOLD,
                MLCommonsSettings.ML_COMMONS_JVM_HEAP_MEM_THRESHOLD,
                MLCommonsSettings.ML_COMMONS_EXCLUDE_NODE_NAMES,
                MLCommonsSettings.ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN,
                MLCommonsSettings.ML_COMMONS_ENABLE_INHOUSE_PYTHON_MODEL,
                MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE,
                MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES,
                MLCommonsSettings.ML_COMMONS_ALLOW_MODEL_URL,
                MLCommonsSettings.ML_COMMONS_ALLOW_LOCAL_FILE_UPLOAD,
                MLCommonsSettings.ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED,
                MLCommonsSettings.ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED,
                MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX,
                MLCommonsSettings.ML_COMMONS_REMOTE_MODEL_ELIGIBLE_NODE_ROLES,
                MLCommonsSettings.ML_COMMONS_LOCAL_MODEL_ELIGIBLE_NODE_ROLES,
                MLCommonsSettings.ML_COMMONS_REMOTE_INFERENCE_ENABLED,
                MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED,
                MLCommonsSettings.ML_COMMONS_RAG_PIPELINE_FEATURE_ENABLED
            );
        return settings;
    }

    /**
     *
     * Search processors for Retrieval Augmented Generation
     *
     */

    @Override
    public List<SearchPlugin.SearchExtSpec<?>> getSearchExts() {
        List<SearchPlugin.SearchExtSpec<?>> searchExts = new ArrayList<>();

        searchExts
            .add(
                new SearchPlugin.SearchExtSpec<>(
                    GenerativeQAParamExtBuilder.PARAMETER_NAME,
                    input -> new GenerativeQAParamExtBuilder(input),
                    parser -> GenerativeQAParamExtBuilder.parse(parser)
                )
            );

        return searchExts;
    }

    @Override
    public Map<String, Processor.Factory<SearchRequestProcessor>> getRequestProcessors(Parameters parameters) {
        Map<String, Processor.Factory<SearchRequestProcessor>> requestProcessors = new HashMap<>();

        requestProcessors
            .put(
                GenerativeQAProcessorConstants.REQUEST_PROCESSOR_TYPE,
                new GenerativeQARequestProcessor.Factory(() -> this.ragSearchPipelineEnabled)
            );

        return requestProcessors;
    }

    @Override
    public Map<String, Processor.Factory<SearchResponseProcessor>> getResponseProcessors(Parameters parameters) {
        Map<String, Processor.Factory<SearchResponseProcessor>> responseProcessors = new HashMap<>();

        responseProcessors
            .put(
                GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE,
                new GenerativeQAResponseProcessor.Factory(this.client, () -> this.ragSearchPipelineEnabled)
            );

        return responseProcessors;
    }

    @Override
    public void loadExtensions(ExtensionLoader loader) {
        externalToolFactories = new HashMap<>();
        for (MLCommonsExtension extension : loader.loadExtensions(MLCommonsExtension.class)) {
            List<Tool.Factory<? extends Tool>> toolFactories = extension.getToolFactories();
            for (Tool.Factory<? extends Tool> toolFactory : toolFactories) {
                ToolAnnotation toolAnnotation = toolFactory.getClass().getDeclaringClass().getAnnotation(ToolAnnotation.class);
                if (toolAnnotation == null) {
                    throw new IllegalArgumentException(
                        "Missing ToolAnnotation for Tool " + toolFactory.getClass().getDeclaringClass().getSimpleName()
                    );
                }
                String annotationValue = toolAnnotation.value();
                externalToolFactories.put(annotationValue, toolFactory);
            }
        }
    }
}
