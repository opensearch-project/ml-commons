package org.opensearch.ml.model;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.plugin.MachineLearningPlugin.UPLOAD_THREAD_POOL;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_MODELS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_RELOAD_ENABLE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT;
import static org.opensearch.ml.utils.MockHelper.mock_MLIndicesHandler_initModelIndex;
import static org.opensearch.ml.utils.MockHelper.mock_client_index;
import static org.opensearch.ml.utils.MockHelper.mock_client_update;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.load.MLLoadModelAction;
import org.opensearch.ml.common.transport.upload.MLUploadInput;
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

/**
 * @author frank woo(吴峻申) <br> email:<a
 * href="mailto:frank_wjs@hotmail.com">frank_wjs@hotmail.com</a> <br>
 * @date 2022/12/21 14:07<br>
 */
public class MLModelAndNodeManagerTests extends OpenSearchTestCase {
	@Rule
	public ExpectedException expectedEx = ExpectedException.none();

	private ClusterService clusterService;
	@Mock
	private Client client;
	@Mock
	private ThreadPool threadPool;
	private NamedXContentRegistry xContentRegistry;
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

	private MLModelManager modelManager;

	private MLModelAndNodeManager modelAndNodeManager;

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
	private String chunk0;
	private String chunk1;
	private MLModel model;
	private MLModel modelChunk0;
	private MLModel modelChunk1;
	private Long modelContentSize;
	@Mock
	private MLModelCacheHelper modelCacheHelper;
	private MLEngine mlEngine;

	@Before
	public void setup() throws URISyntaxException {
		client = mock(Client.class);
		threadPool = mock(ThreadPool.class);

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
		threadContext = new ThreadContext(settings);
		clusterService = spy(new ClusterService(settings, clusterSettings, null));
		xContentRegistry = NamedXContentRegistry.EMPTY;

		modelName = "model_name1";
		modelId = randomAlphaOfLength(10);
		modelContentHashValue = "c446f747520bcc6af053813cb1e8d34944a7c4686bbb405aeaa23883b5a806c8";
		version = "1.0.0";
		url = "http://testurl";
		MLModelConfig modelConfig = TextEmbeddingModelConfig
				.builder()
				.modelType("bert")
				.frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
				.embeddingDimension(384)
				.build();
		uploadInput = MLUploadInput
				.builder()
				.modelName(modelName)
				.version(version)
				.functionName(FunctionName.TEXT_EMBEDDING)
				.modelFormat(MLModelFormat.TORCH_SCRIPT)
				.modelConfig(modelConfig)
				.url(url)
				.build();

		Map<Enum, MLStat<?>> stats = new ConcurrentHashMap<>();
		// node level stats
		stats.put(
				MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT, new MLStat<>(false, new CounterSupplier()));
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

		doAnswer(invocation -> {
			Runnable runnable = invocation.getArgument(0);
			runnable.run();
			return null;
		}).when(taskExecutorService).execute(any());

		threadContext = new ThreadContext(settings);
		when(client.threadPool()).thenReturn(threadPool);
		when(threadPool.getThreadContext()).thenReturn(threadContext);

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

		modelAndNodeManager = spy(
				new MLModelAndNodeManager(
						clusterService,
						client,
						threadPool,
						xContentRegistry,
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

		chunk0 = getClass().getResource("chunk/0").toURI().getPath();
		chunk1 = getClass().getResource("chunk/1").toURI().getPath();

		modelContentSize = 1000L;
		model = MLModel
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
		modelChunk0 = model.toBuilder().content(
				Base64.getEncoder().encodeToString("test chunk1".getBytes(StandardCharsets.UTF_8))).build();
		modelChunk1 = model.toBuilder().content(Base64.getEncoder().encodeToString("test chunk2".getBytes(StandardCharsets.UTF_8))).build();
	}

	public void testAutoLoadModel() throws IOException {
		when(mlTaskManager.checkLimitAndAddRunningTask(any(), any())).thenReturn(null);
		when(mlCircuitBreakerService.checkOpenCB()).thenReturn(null);
		when(threadPool.executor(UPLOAD_THREAD_POOL)).thenReturn(taskExecutorService);
		mock_MLIndicesHandler_initModelIndex(mlIndicesHandler, true);
		mock_client_index(client, modelId);
		String[] newChunks = createTempChunkFiles();
		setUpMock_DownloadModelFile(newChunks, 1000L);
		mock_client_update(client);

		MLUploadInput mlUploadInput = uploadInput.toBuilder().loadModel(true).build();
		modelAndNodeManager.autoReLoadModel();
		verify(mlIndicesHandler).initModelIndexIfAbsent(any());
		verify(client, times(3)).index(any(), any());
		verify(modelHelper).downloadAndSplit(eq(modelId), eq(modelName), eq(version), eq(url), any());
		verify(client).execute(eq(MLLoadModelAction.INSTANCE), any(), any());
	}
}
