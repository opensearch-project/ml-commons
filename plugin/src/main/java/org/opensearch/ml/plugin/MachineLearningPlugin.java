/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.stream.NamedWriteableRegistry;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.IndexScopedSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.SettingsFilter;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.env.Environment;
import org.opensearch.env.NodeEnvironment;
import org.opensearch.ml.action.prediction.MLPredictionTaskExecutionAction;
import org.opensearch.ml.action.prediction.MLPredictionTaskExecutionTransportAction;
import org.opensearch.ml.action.prediction.TransportPredictionTaskAction;
import org.opensearch.ml.action.search.SearchTaskExecutionAction;
import org.opensearch.ml.action.search.SearchTaskExecutionTransportAction;
import org.opensearch.ml.action.search.TransportSearchTaskAction;
import org.opensearch.ml.action.stats.MLStatsNodesAction;
import org.opensearch.ml.action.stats.MLStatsNodesTransportAction;
import org.opensearch.ml.action.training.MLTrainingTaskExecutionAction;
import org.opensearch.ml.action.training.MLTrainingTaskExecutionTransportAction;
import org.opensearch.ml.action.training.TransportTrainingTaskAction;
import org.opensearch.ml.action.upload.TransportUploadTaskAction;
import org.opensearch.ml.action.upload.UploadTaskExecutionAction;
import org.opensearch.ml.action.upload.UploadTaskExecutionTransportAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.search.SearchTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.upload.UploadTaskAction;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.indices.MLInputDatasetHandler;
import org.opensearch.ml.rest.*;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.StatNames;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.task.MLPredictTaskRunner;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.task.MLTrainingTaskRunner;
import org.opensearch.ml.task.SearchTaskRunner;
import org.opensearch.ml.task.UploadTaskRunner;
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
import com.google.common.collect.ImmutableMap;

public class MachineLearningPlugin extends Plugin implements ActionPlugin {
    public static final String TASK_THREAD_POOL = "OPENSEARCH_ML_TASK_THREAD_POOL";
    public static final String ML_BASE_URI = "/_plugins/_ml";

    private MLStats mlStats;
    private MLTaskManager mlTaskManager;
    private MLIndicesHandler mlIndicesHandler;
    private MLInputDatasetHandler mlInputDatasetHandler;
    private MLTrainingTaskRunner mlTrainingTaskRunner;
    private MLPredictTaskRunner mlPredictTaskRunner;
    private UploadTaskRunner uploadTaskRunner;
    private SearchTaskRunner searchTaskRunner;

    private Client client;
    private ClusterService clusterService;
    private ThreadPool threadPool;

    public static final Setting<Boolean> IS_ML_NODE_SETTING = Setting.boolSetting("node.ml", false, Setting.Property.NodeScope);

    public static final DiscoveryNodeRole ML_ROLE = new DiscoveryNodeRole("ml", "l") {
        @Override
        public Setting<Boolean> legacySetting() {
            return IS_ML_NODE_SETTING;
        }
    };

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return ImmutableList
            .of(
                new ActionHandler<>(MLStatsNodesAction.INSTANCE, MLStatsNodesTransportAction.class),

                new ActionHandler<>(MLPredictionTaskAction.INSTANCE, TransportPredictionTaskAction.class),
                new ActionHandler<>(MLTrainingTaskAction.INSTANCE, TransportTrainingTaskAction.class),
                new ActionHandler<>(UploadTaskAction.INSTANCE, TransportUploadTaskAction.class),
                new ActionHandler<>(SearchTaskAction.INSTANCE, TransportSearchTaskAction.class),

                new ActionHandler<>(MLPredictionTaskExecutionAction.INSTANCE, MLPredictionTaskExecutionTransportAction.class),
                new ActionHandler<>(MLTrainingTaskExecutionAction.INSTANCE, MLTrainingTaskExecutionTransportAction.class),
                new ActionHandler<>(UploadTaskExecutionAction.INSTANCE, UploadTaskExecutionTransportAction.class),
                new ActionHandler<>(SearchTaskExecutionAction.INSTANCE, SearchTaskExecutionTransportAction.class)
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
        this.client = client;
        this.threadPool = threadPool;
        this.clusterService = clusterService;

        JvmService jvmService = new JvmService(environment.settings());

        Map<String, MLStat<?>> stats = ImmutableMap
            .<String, MLStat<?>>builder()
            .put(StatNames.ML_EXECUTING_TASK_COUNT.getName(), new MLStat<>(false, new CounterSupplier()))
            .build();
        this.mlStats = new MLStats(stats);

        mlTaskManager = new MLTaskManager();
        mlIndicesHandler = new MLIndicesHandler(clusterService, client);
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
            mlTaskDispatcher
        );
        mlPredictTaskRunner = new MLPredictTaskRunner(
            threadPool,
            clusterService,
            client,
            mlTaskManager,
            mlStats,
            mlInputDatasetHandler,
            mlTaskDispatcher
        );
        uploadTaskRunner = new UploadTaskRunner(
            threadPool,
            clusterService,
            client,
            mlTaskManager,
            mlStats,
            mlIndicesHandler,
            mlTaskDispatcher
        );
        searchTaskRunner = new SearchTaskRunner(threadPool, clusterService, client, mlTaskManager, mlStats, mlTaskDispatcher);

        return ImmutableList
            .of(
                jvmService,
                mlStats,
                mlTaskManager,
                mlIndicesHandler,
                mlInputDatasetHandler,
                mlTrainingTaskRunner,
                mlPredictTaskRunner,
                uploadTaskRunner,
                searchTaskRunner
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
        RestStatsMLAction restStatsMLAction = new RestStatsMLAction(mlStats);
        RestMLTrainingAction restMLTrainingAction = new RestMLTrainingAction();
        RestMLPredictionAction restMLPredictionAction = new RestMLPredictionAction();
        RestMLUploadAction restMLUploadAction = new RestMLUploadAction();
        RestMLSearchAction restMLSearchAction = new RestMLSearchAction();
        return ImmutableList.of(restStatsMLAction, restMLTrainingAction, restMLPredictionAction, restMLUploadAction, restMLSearchAction);
    }

    @Override
    public List<ExecutorBuilder<?>> getExecutorBuilders(Settings settings) {
        FixedExecutorBuilder ml = new FixedExecutorBuilder(settings, TASK_THREAD_POOL, 4, 4, "ml.task_thread_pool", false);

        return Collections.singletonList(ml);
    }
}
