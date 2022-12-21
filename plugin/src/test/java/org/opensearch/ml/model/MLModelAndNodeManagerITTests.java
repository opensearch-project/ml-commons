package org.opensearch.ml.model;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_RELOAD_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_RELOAD_INDEX_MAPPING;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_MODELS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.admin.indices.create.CreateIndexAction;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.upload.MLUploadInput;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.test.OpenSearchIntegTestCase;
import org.opensearch.test.OpenSearchIntegTestCase.Scope;
import org.opensearch.threadpool.ThreadPool;

/**
 * @author frank woo(吴峻申) <br> email:<a
 * href="mailto:frank_wjs@hotmail.com">frank_wjs@hotmail.com</a> <br>
 * @date 2022/12/17 20:17<br>
 */
@OpenSearchIntegTestCase.ClusterScope(scope = Scope.TEST, numDataNodes = 3)
public class MLModelAndNodeManagerITTests extends MLCommonsIntegTestCase {
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private ClusterService clusterService;
    @Mock
    private Client client;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private ModelHelper modelHelper;
    @Mock
    private DiscoveryNodeHelper nodeHelper;
    private Settings settings;
    private MLStats mlStats;
    @Mock
    private MLCircuitBreakerService mlCircuitBreakerService;
    @Mock
    private MLIndicesHandler mlIndicesHandler;
    @Mock
    private MLTaskManager mlTaskManager;

    private MLModelAndNodeManager mlModelAndNodeManager;

    private String modelName;
    private String version;
    private MLUploadInput uploadInput;
    private MLTask mlTask;
    @Mock
    private ExecutorService taskExecutorService;
    private ThreadContext threadContext;
    private String modelId;
    private String modelContentHashValue;
    private String url;
    @Mock
    private MLModelCacheHelper modelCacheHelper;
    private MLEngine mlEngine;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        mlEngine = new MLEngine(Path.of("/tmp/test" + randomAlphaOfLength(10)));
        settings = Settings.builder().put(ML_COMMONS_MAX_MODELS_PER_NODE.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MONITORING_REQUEST_COUNT.getKey(), 10).build();
        settings = Settings.builder().put(ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE.getKey(), true).build();

        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_MAX_MODELS_PER_NODE,
            ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE,
            ML_COMMONS_MONITORING_REQUEST_COUNT,
            ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE
        );
        clusterService = spy(new ClusterService(settings, clusterSettings, null));
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);

        modelName = "model_name1";
        modelId = randomAlphaOfLength(10);
        modelContentHashValue = "c446f747520bcc6af053813cb1e8d34944a7c4686bbb405aeaa23883b5a806c8";
        version = "1.0.0";
        url = "http://testurl";
        MLModelConfig modelConfig = TextEmbeddingModelConfig
            .builder()
            .modelType("bert")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(768)
            .build();

        Map<Enum, MLStat<?>> stats = new ConcurrentHashMap<>();
        // node level stats
        stats.put(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_FAILURE_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_MODEL_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_NODE_TOTAL_CIRCUIT_BREAKER_TRIGGER_COUNT, new MLStat<>(false, new CounterSupplier()));
        this.mlStats = spy(new MLStats(stats));

        mlTask = MLTask
            .builder()
            .taskId("taskId1")
            .modelId("modelId1")
            .taskType(MLTaskType.UPLOAD_MODEL)
            .functionName(FunctionName.TEXT_EMBEDDING)
            .state(MLTaskState.CREATED)
            .inputType(MLInputDataType.TEXT_DOCS)
            .build();

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        mlModelAndNodeManager = spy(
            new MLModelAndNodeManager(
                clusterService(),
                client(),
                threadPool,
                xContentRegistry(),
                modelHelper,
                nodeHelper,
                settings,
                mlStats,
                mlCircuitBreakerService,
                mlIndicesHandler,
                mlTaskManager,
                modelCacheHelper,
                mlEngine
            )
        );
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testIsExistedIndex() {
        assertFalse(mlModelAndNodeManager.isExistedIndex(ML_MODEL_RELOAD_INDEX));

        createIndex(ML_MODEL_RELOAD_INDEX);

        assertTrue(mlModelAndNodeManager.isExistedIndex(ML_MODEL_RELOAD_INDEX));
    }

    private void createIndex(String indexName) {
        CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName);
        createIndexRequest.mapping(ML_MODEL_RELOAD_INDEX_MAPPING);
        CreateIndexResponse createIndexResponse = client().execute(CreateIndexAction.INSTANCE, createIndexRequest).actionGet(5000);

        if (createIndexResponse.isAcknowledged()) {
            System.out.println("create index:" + indexName + " with its mapping successfully");
        } else {
            throw new RuntimeException("can't create index:" + indexName + " with its mapping");
        }
    }

    public void testSaveLatestReTryTimes() {
        String localNodeId = clusterService().localNode().getId();

        mlModelAndNodeManager.saveLatestReTryTimes(localNodeId, 0);
        Integer retryTimes = mlModelAndNodeManager.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(0));

        mlModelAndNodeManager.saveLatestReTryTimes(localNodeId, 1);
        retryTimes = mlModelAndNodeManager.getReTryTimes(localNodeId);
        assertThat(retryTimes, is(1));
    }

    public void testAutoReLoadModelByNodeAndModelId() {
        String localNodeId = clusterService().localNode().getId();

        mlModelAndNodeManager.autoReLoadModelByNodeAndModelId(localNodeId, modelId);
    }

    public void testAutoReLoadModelByNodeId() {
        String localNodeId = clusterService().localNode().getId();

        mlModelAndNodeManager.autoReLoadModelByNodeId(localNodeId);
    }

    public void testAutoReLoadModel() {
        mlModelAndNodeManager.autoReLoadModel();
    }
}
