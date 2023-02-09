/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.model;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_RELOAD_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.CommonValue.MODEL_LOAD_RETRY_TIMES_FIELD;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_LOAD_MODEL_TASKS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_MODELS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_MODEL_RELOAD_MAX_RETRY_TIMES;
import static org.opensearch.ml.utils.TestHelper.ML_ROLE;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.ActionListener;
import org.opensearch.action.StepListener;
import org.opensearch.action.admin.indices.create.CreateIndexAction;
import org.opensearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.index.IndexAction;
import org.opensearch.action.index.IndexRequestBuilder;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.collect.ImmutableOpenMap;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.transport.TransportAddress;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;

public class MLModelAutoReLoaderTests extends OpenSearchTestCase {
    private final Instant time = Instant.now();
    private static final AtomicInteger portGenerator = new AtomicInteger();
    private ClusterState testState;
    private DiscoveryNode node;
    @Mock
    private ClusterService clusterService;
    @Mock
    private Client client;
    @Mock
    private ThreadPool threadPool;
    private NamedXContentRegistry xContentRegistry;
    @Mock
    private ModelHelper modelHelper;
    private Settings settings;
    @Mock
    private MLCircuitBreakerService mlCircuitBreakerService;
    @Mock
    private MLIndicesHandler mlIndicesHandler;
    @Mock
    private MLTaskManager mlTaskManager;
    private MLModelManager modelManager;
    @Mock
    private ExecutorService taskExecutorService;
    private ThreadContext threadContext;
    private String taskId;
    private String modelId;
    private String localNodeId;
    private MLModel modelChunk0;
    private MLModel modelChunk1;
    @Mock
    private MLModelCacheHelper modelCacheHelper;
    @Mock
    private DiscoveryNodeHelper nodeHelper;
    private MLModelAutoReLoader mlModelAutoReLoader;

    @Before
    public void setup() throws Exception {
        super.setUp();

        MockitoAnnotations.openMocks(this);

        MLEngine mlEngine = new MLEngine(Path.of("/tmp/test" + randomAlphaOfLength(10)));
        settings = Settings
            .builder()
            .put(ML_COMMONS_MAX_MODELS_PER_NODE.getKey(), 10)
            .put(ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE.getKey(), 10)
            .put(ML_COMMONS_MONITORING_REQUEST_COUNT.getKey(), 10)
            .put(ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE.getKey(), true)
            .put(ML_MODEL_RELOAD_MAX_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .put(ML_COMMONS_MAX_LOAD_MODEL_TASKS_PER_NODE.getKey(), 1)
            .build();
        DiscoveryNode localNode = setupTestMLNode();
        when(clusterService.localNode()).thenReturn(localNode);
        testState = setupTestClusterState(localNode);

        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_MAX_MODELS_PER_NODE,
            ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE,
            ML_COMMONS_MONITORING_REQUEST_COUNT,
            ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE,
            ML_MODEL_RELOAD_MAX_RETRY_TIMES,
            ML_COMMONS_ONLY_RUN_ON_ML_NODE,
            ML_COMMONS_MAX_LOAD_MODEL_TASKS_PER_NODE
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(clusterService.state()).thenReturn(testState);

        xContentRegistry = NamedXContentRegistry.EMPTY;

        taskId = "taskId1";
        String modelName = "model_name1";
        modelId = randomAlphaOfLength(10);
        String modelContentHashValue = "c446f747520bcc6af053813cb1e8d34944a7c4686bbb405aeaa23883b5a806c8";
        String version = "1.0.0";
        MLModelConfig modelConfig = TextEmbeddingModelConfig
            .builder()
            .modelType("bert")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(384)
            .build();

        Map<Enum, MLStat<?>> stats = new ConcurrentHashMap<>();
        // node level stats
        stats.put(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_FAILURE_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_MODEL_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_CIRCUIT_BREAKER_TRIGGER_COUNT, new MLStat<>(false, new CounterSupplier()));
        MLStats mlStats = spy(new MLStats(stats));

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(taskExecutorService).execute(any());

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.generic()).thenReturn(taskExecutorService);

        modelManager = spy(
            new MLModelManager(
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
            )
        );
        nodeHelper = spy(new DiscoveryNodeHelper(clusterService, settings));

        mlModelAutoReLoader = spy(
            new MLModelAutoReLoader(clusterService, client.threadPool(), client, xContentRegistry, nodeHelper, settings)
        );

        Long modelContentSize = 1000L;
        MLModel model = MLModel
            .builder()
            .modelId(modelId)
            .modelState(MLModelState.UPLOADED)
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .name(modelName)
            .version(version)
            .totalChunks(2)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelConfig(modelConfig)
            .modelContentHash(modelContentHashValue)
            .modelContentSizeInBytes(modelContentSize)
            .build();
        modelChunk0 = model.toBuilder().content(Base64.getEncoder().encodeToString("test chunk1".getBytes(StandardCharsets.UTF_8))).build();
        modelChunk1 = model.toBuilder().content(Base64.getEncoder().encodeToString("test chunk2".getBytes(StandardCharsets.UTF_8))).build();

        localNodeId = clusterService.localNode().getId();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();

        settings = null;
        nodeHelper = null;
        mlModelAutoReLoader = null;
        modelId = null;
        localNodeId = null;
        modelManager = null;
    }

    public void testAutoReLoadModel() {
        mlModelAutoReLoader.autoReLoadModel();
    }

    public void testAutoReLoadModel_setting_false() {
        settings = Settings
            .builder()
            .put(ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE.getKey(), false)
            .put(ML_MODEL_RELOAD_MAX_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .build();

        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE,
            ML_MODEL_RELOAD_MAX_RETRY_TIMES,
            ML_COMMONS_ONLY_RUN_ON_ML_NODE
        );
        clusterService = spy(new ClusterService(settings, clusterSettings, null));
        nodeHelper = spy(new DiscoveryNodeHelper(clusterService, settings));
        mlModelAutoReLoader = spy(new MLModelAutoReLoader(clusterService, threadPool, client, xContentRegistry, nodeHelper, settings));

        mlModelAutoReLoader.autoReLoadModel();
    }

    public void testAutoReLoadModel_Is_Not_ML_Node() {
        settings = Settings
            .builder()
            .put(ML_COMMONS_MAX_MODELS_PER_NODE.getKey(), 10)
            .put(ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE.getKey(), 10)
            .put(ML_COMMONS_MONITORING_REQUEST_COUNT.getKey(), 10)
            .put(ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE.getKey(), true)
            .put(ML_MODEL_RELOAD_MAX_RETRY_TIMES.getKey(), 3)
            .put(ML_COMMONS_ONLY_RUN_ON_ML_NODE.getKey(), true)
            .build();
        DiscoveryNode localNode = setupTestDataNode();
        when(clusterService.localNode()).thenReturn(localNode);
        testState = setupTestClusterState(localNode);
        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_MAX_MODELS_PER_NODE,
            ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE,
            ML_COMMONS_MONITORING_REQUEST_COUNT,
            ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE,
            ML_MODEL_RELOAD_MAX_RETRY_TIMES,
            ML_COMMONS_ONLY_RUN_ON_ML_NODE
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(clusterService.state()).thenReturn(testState);

        xContentRegistry = NamedXContentRegistry.EMPTY;

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(taskExecutorService).execute(any());

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.generic()).thenReturn(taskExecutorService);

        nodeHelper = spy(new DiscoveryNodeHelper(clusterService, settings));

        mlModelAutoReLoader = spy(
            new MLModelAutoReLoader(clusterService, client.threadPool(), client, xContentRegistry, nodeHelper, settings)
        );

        mlModelAutoReLoader.autoReLoadModel();
    }

    public void testAutoReLoadModelByNodeId() throws IOException, ExecutionException, InterruptedException {
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        initDataOfMlModel(modelId);

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);

        StepListener<SearchResponse> getReTryTimesStep = new StepListener<>();
        mlModelAutoReLoader.getReTryTimes(localNodeId, ActionListener.wrap(getReTryTimesStep::onResponse, getReTryTimesStep::onFailure));
    }

    public void testAutoReLoadModelByNodeId_ReTry() throws IOException, ExecutionException, InterruptedException {
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);

        StepListener<SearchResponse> getReTryTimesStep = new StepListener<>();
        mlModelAutoReLoader.getReTryTimes(localNodeId, ActionListener.wrap(getReTryTimesStep::onResponse, getReTryTimesStep::onFailure));
    }

    public void testAutoReLoadModelByNodeId_Max_ReTryTimes() throws IOException, ExecutionException, InterruptedException {
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);

        StepListener<IndexResponse> saveLatestReTryTimesStep = new StepListener<>();
        mlModelAutoReLoader
            .saveLatestReTryTimes(
                localNodeId,
                3,
                ActionListener.wrap(saveLatestReTryTimesStep::onResponse, saveLatestReTryTimesStep::onFailure)
            );

        saveLatestReTryTimesStep.whenComplete(response -> {
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.status(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.status().getStatus(), anyOf(is(201), is(200)));
        }, exception -> fail(exception.getMessage()));

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);
    }

    public void testAutoReLoadModelByNodeId_IndexNotFound() throws ExecutionException, InterruptedException {
        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);
    }

    public void testAutoReLoadModelByNodeId_EmptyHits() throws ExecutionException, InterruptedException {
        createIndex(ML_TASK_INDEX);

        mlModelAutoReLoader.autoReLoadModelByNodeId(localNodeId);
    }

    public void testAutoReLoadModelByNodeAndModelId() throws IOException {
        initDataOfMlTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        initDataOfMlModel(modelId);
        mlModelAutoReLoader.autoReLoadModelByNodeAndModelId(localNodeId, modelId);
    }

    public void testAutoReLoadModelByNodeAndModelId_Exception() {
        mlModelAutoReLoader.autoReLoadModelByNodeAndModelId(localNodeId, modelId);
    }

    public void testQueryTask() throws IOException {
        StepListener<SearchResponse> queryTaskStep = queryTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);

        queryTaskStep.whenComplete(response -> {
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits().length, is(1));
        }, exception -> fail(exception.getMessage()));

        queryTaskStep = queryTask(localNodeId, modelId, MLTaskType.UPLOAD_MODEL, MLTaskState.COMPLETED);

        queryTaskStep.whenComplete(response -> {
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits().length, is(0));
        }, exception -> fail(exception.getMessage()));

        queryTaskStep = queryTask(localNodeId, modelId, MLTaskType.LOAD_MODEL, MLTaskState.RUNNING);

        queryTaskStep.whenComplete(response -> {
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits().length, is(0));
        }, exception -> fail(exception.getMessage()));
    }

    public void testQueryTask_MultiDataInTaskIndex() throws IOException {
        StepListener<SearchResponse> queryTaskStep = queryTask(localNodeId, "modelId1", MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        queryTaskStep.whenComplete(response -> {}, exception -> fail(exception.getMessage()));

        queryTaskStep = queryTask(localNodeId, "modelId2", MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);
        queryTaskStep.whenComplete(response -> {}, exception -> fail(exception.getMessage()));

        queryTaskStep = queryTask(localNodeId, "modelId3", MLTaskType.LOAD_MODEL, MLTaskState.COMPLETED);

        queryTaskStep.whenComplete(response -> {
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits().length, is(1));

            Map<String, Object> source = response.getHits().getHits()[0].getSourceAsMap();
            org.hamcrest.MatcherAssert.assertThat(source.get("model_id"), is("modelId3"));
        }, exception -> fail(exception.getMessage()));
    }

    public void testQueryTask_MultiDataInTaskIndex_TaskState_COMPLETED_WITH_ERROR() throws IOException {
        StepListener<SearchResponse> queryTaskStep = queryTask(
            localNodeId,
            "modelId3",
            MLTaskType.LOAD_MODEL,
            MLTaskState.COMPLETED_WITH_ERROR
        );

        queryTaskStep.whenComplete(response -> {
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits().length, is(1));

            Map<String, Object> source = response.getHits().getHits()[0].getSourceAsMap();
            org.hamcrest.MatcherAssert.assertThat(source.get("model_id"), is("modelId3"));
        }, exception -> fail(exception.getMessage()));
    }

    public void testQueryTask_IndexNotExisted() {
        StepListener<SearchResponse> queryTaskStep = new StepListener<>();

        mlModelAutoReLoader.queryTask(localNodeId, ActionListener.wrap(queryTaskStep::onResponse, queryTaskStep::onFailure));

        queryTaskStep.whenComplete(response -> {}, exception -> {
            org.hamcrest.MatcherAssert.assertThat(exception.getClass(), is(IndexNotFoundException.class));
            org.hamcrest.MatcherAssert
                .assertThat(exception.getMessage(), containsString("no such index [index " + ML_TASK_INDEX + " not found]"));
        });
    }

    public void testGetReTryTimes() {
        assertLatestReTryTimes(1);
        assertLatestReTryTimes(3);
    }

    public void testGetReTryTimes_IndexNotExisted() {
        StepListener<SearchResponse> getReTryTimesStep = new StepListener<>();

        mlModelAutoReLoader.getReTryTimes(localNodeId, ActionListener.wrap(getReTryTimesStep::onResponse, getReTryTimesStep::onFailure));
    }

    public void testGetReTryTimes_EmptyHits() {
        createIndex(ML_MODEL_RELOAD_INDEX);

        StepListener<SearchResponse> getReTryTimesStep = new StepListener<>();
        mlModelAutoReLoader.getReTryTimes(localNodeId, ActionListener.wrap(getReTryTimesStep::onResponse, getReTryTimesStep::onFailure));

        getReTryTimesStep.whenComplete(response -> {}, exception -> {
            org.hamcrest.MatcherAssert.assertThat(exception.getClass(), is(RuntimeException.class));
            org.hamcrest.MatcherAssert.assertThat(exception.getMessage(), containsString("can't get reTryTimes from node"));
        });
    }

    public void testSaveLatestReTryTimes() {
        assertLatestReTryTimes(0);
        assertLatestReTryTimes(1);
        assertLatestReTryTimes(3);
    }

    private void createIndex(String indexName) {
        StepListener<CreateIndexResponse> actionListener = new StepListener<>();
        CreateIndexRequestBuilder requestBuilder = new CreateIndexRequestBuilder(client, CreateIndexAction.INSTANCE, indexName);

        requestBuilder.execute(ActionListener.wrap(actionListener::onResponse, actionListener::onFailure));
    }

    private void setUpMock_GetModelChunks(MLModel model) {
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(model);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(modelChunk0);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(1);
            listener.onResponse(modelChunk1);
            return null;
        }).when(modelManager).getModel(any(), any());
    }

    private DiscoveryNode setupTestMLNode() {
        Set<DiscoveryNodeRole> roleSet = new HashSet<>();
        roleSet.add(ML_ROLE);
        node = new DiscoveryNode(
            "node",
            new TransportAddress(TransportAddress.META_ADDRESS, portGenerator.incrementAndGet()),
            new HashMap<>(),
            roleSet,
            Version.CURRENT
        );

        return node;
    }

    private DiscoveryNode setupTestDataNode() {
        Set<DiscoveryNodeRole> roleSet = new HashSet<>();
        roleSet.add(DiscoveryNodeRole.DATA_ROLE);
        node = new DiscoveryNode(
            "node",
            new TransportAddress(TransportAddress.META_ADDRESS, portGenerator.incrementAndGet()),
            new HashMap<>(),
            roleSet,
            Version.CURRENT
        );

        return node;
    }

    private ClusterState setupTestClusterState(DiscoveryNode discoveryNode) {
        Metadata metadata = new Metadata.Builder()
            .indices(
                ImmutableOpenMap
                    .<String, IndexMetadata>builder()
                    .fPut(
                        ML_MODEL_RELOAD_INDEX,
                        IndexMetadata
                            .builder("test")
                            .settings(
                                Settings
                                    .builder()
                                    .put("index.number_of_shards", 1)
                                    .put("index.number_of_replicas", 1)
                                    .put("index.version.created", Version.CURRENT.id)
                            )
                            .build()
                    )
                    .build()
            )
            .build();
        return new ClusterState(
            new ClusterName("clusterName"),
            123L,
            "111111",
            metadata,
            null,
            DiscoveryNodes.builder().add(discoveryNode).localNodeId(discoveryNode.getId()).build(),
            null,
            null,
            0,
            false
        );
    }

    private void assertLatestReTryTimes(int reTryTimes) {
        StepListener<IndexResponse> saveLatestReTryTimesStep = new StepListener<>();
        mlModelAutoReLoader
            .saveLatestReTryTimes(
                localNodeId,
                reTryTimes,
                ActionListener.wrap(saveLatestReTryTimesStep::onResponse, saveLatestReTryTimesStep::onFailure)
            );

        saveLatestReTryTimesStep.whenComplete(response -> {
            // inProgressLatch.countDown();
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.status(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.status().getStatus(), anyOf(is(201), is(200)));
        }, exception -> fail(exception.getMessage()));

        StepListener<SearchResponse> getReTryTimesStep = new StepListener<>();
        mlModelAutoReLoader.getReTryTimes(localNodeId, ActionListener.wrap(getReTryTimesStep::onResponse, getReTryTimesStep::onFailure));

        getReTryTimesStep.whenComplete(response -> {
            org.hamcrest.MatcherAssert.assertThat(response, notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits(), notNullValue());
            org.hamcrest.MatcherAssert.assertThat(response.getHits().getHits().length, is(1));

            Map<String, Object> sourceAsMap = response.getHits().getHits()[0].getSourceAsMap();
            int result = (Integer) sourceAsMap.get(MODEL_LOAD_RETRY_TIMES_FIELD);
            org.hamcrest.MatcherAssert.assertThat(result, is(reTryTimes));
        }, exception -> fail(exception.getMessage()));
    }

    private StepListener<SearchResponse> queryTask(String localNodeId, String modelId, MLTaskType mlTaskType, MLTaskState mlTaskState)
        throws IOException {
        initDataOfMlTask(localNodeId, modelId, mlTaskType, mlTaskState);

        StepListener<SearchResponse> queryTaskStep = new StepListener<>();
        mlModelAutoReLoader.queryTask(localNodeId, ActionListener.wrap(queryTaskStep::onResponse, queryTaskStep::onFailure));

        return queryTaskStep;
    }

    private void initDataOfMlTask(String nodeId, String modelId, MLTaskType mlTaskType, MLTaskState mlTaskState) throws IOException {
        MLTask mlTask = MLTask
            .builder()
            .taskId(taskId)
            .modelId(modelId)
            .taskType(mlTaskType)
            .state(mlTaskState)
            .workerNodes(List.of(nodeId))
            .progress(0.0f)
            .outputIndex("test_index")
            .createTime(time.minus(1, ChronoUnit.MINUTES))
            .async(true)
            .lastUpdateTime(time)
            .build();

        StepListener<IndexResponse> actionListener = new StepListener<>();
        IndexRequestBuilder requestBuilder = new IndexRequestBuilder(client, IndexAction.INSTANCE, ML_TASK_INDEX);
        requestBuilder.setId(taskId);
        requestBuilder.setSource(mlTask.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
        requestBuilder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        requestBuilder.execute(ActionListener.wrap(actionListener::onResponse, actionListener::onFailure));
    }

    private void initDataOfMlModel(String modelId) throws IOException {
        MLModelConfig modelConfig = TextEmbeddingModelConfig
            .builder()
            .modelType("bert")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(384)
            .build();

        MLModel mlModel = MLModel
            .builder()
            .modelId(modelId)
            .name("model_name")
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .version("1.0.0")
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelState(MLModelState.LOADED)
            .modelConfig(modelConfig)
            .totalChunks(2)
            .modelContentHash("c446f747520bcc6af053813cb1e8d34944a7c4686bbb405aeaa23883b5a806c8")
            .modelContentSizeInBytes(1000L)
            .createdTime(time.minus(1, ChronoUnit.MINUTES))
            .build();
        modelChunk0 = mlModel
            .toBuilder()
            .content(Base64.getEncoder().encodeToString("test chunk1".getBytes(StandardCharsets.UTF_8)))
            .build();
        modelChunk1 = mlModel
            .toBuilder()
            .content(Base64.getEncoder().encodeToString("test chunk2".getBytes(StandardCharsets.UTF_8)))
            .build();

        setUpMock_GetModelChunks(mlModel);

        StepListener<IndexResponse> actionListener = new StepListener<>();
        IndexRequestBuilder requestBuilder = new IndexRequestBuilder(client, IndexAction.INSTANCE, ML_MODEL_INDEX);
        requestBuilder.setId(modelId);
        requestBuilder.setSource(mlModel.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
        requestBuilder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        requestBuilder.execute(ActionListener.wrap(actionListener::onResponse, actionListener::onFailure));
    }

}
