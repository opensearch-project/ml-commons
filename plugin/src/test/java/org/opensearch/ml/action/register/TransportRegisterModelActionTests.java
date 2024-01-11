/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.register;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_URL_REGEX;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;
import org.opensearch.ml.common.transport.forward.MLForwardResponse;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelRequest;
import org.opensearch.ml.common.transport.register.MLRegisterModelResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelGroupManager;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class TransportRegisterModelActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    private TransportService transportService;

    @Mock
    private ModelHelper modelHelper;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private MLModelGroupManager mlModelGroupManager;

    @Mock
    private MLTaskManager mlTaskManager;

    @Mock
    private ClusterService clusterService;

    private Settings settings;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    @Mock
    private DiscoveryNodeHelper nodeFilter;

    @Mock
    private MLTaskDispatcher mlTaskDispatcher;

    @Mock
    private MLStats mlStats;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private Task task;

    @Mock
    private ActionListener<MLRegisterModelResponse> actionListener;

    @Mock
    private DiscoveryNode node1;

    @Mock
    private DiscoveryNode node2;

    @Mock
    private IndexResponse indexResponse;

    ThreadContext threadContext;

    private TransportRegisterModelAction transportRegisterModelAction;

    private String trustedUrlRegex = "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";

    private static final List<String> TRUSTED_CONNECTOR_ENDPOINTS_REGEXES = List
        .of("^https://runtime\\.sagemaker\\..*\\.amazonaws\\.com/.*$", "^https://api\\.openai\\.com/.*$", "^https://api\\.cohere\\.ai/.*$");

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        settings = Settings
            .builder()
            .put(ML_COMMONS_TRUSTED_URL_REGEX.getKey(), trustedUrlRegex)
            .putList(ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), TRUSTED_CONNECTOR_ENDPOINTS_REGEXES)
            .build();
        threadContext = new ThreadContext(settings);
        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_TRUSTED_URL_REGEX,
            ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(clusterService.getSettings()).thenReturn(settings);
        transportRegisterModelAction = new TransportRegisterModelAction(
            transportService,
            actionFilters,
            modelHelper,
            mlIndicesHandler,
            mlModelManager,
            mlTaskManager,
            clusterService,
            settings,
            threadPool,
            client,
            nodeFilter,
            mlTaskDispatcher,
            mlStats,
            modelAccessControlHelper,
            connectorAccessControlHelper,
            mlModelGroupManager
        );
        assertNotNull(transportRegisterModelAction);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        MLStat mlStat = mock(MLStat.class);
        when(mlStats.getStat(eq(MLNodeLevelStat.ML_REQUEST_COUNT))).thenReturn(mlStat);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(any(), any());

        doAnswer(invocation -> {
            ActionListener<DiscoveryNode> listener = invocation.getArgument(1);
            listener.onResponse(node1);
            return null;
        }).when(mlTaskDispatcher).dispatch(any(), any());

        SearchResponse searchResponse = createModelGroupSearchResponse(0);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(mlModelGroupManager).validateUniqueModelGroupName(any(), any());

        when(clusterService.localNode()).thenReturn(node2);
        when(node2.getId()).thenReturn("node2Id");

        doAnswer(invocation -> { return null; }).when(mlModelManager).registerMLModel(any(), any());
        doAnswer(invocation -> { return null; }).when(mlModelManager).registerMLRemoteModel(any(), any(), any());

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void testDoExecute_userHasNoAccessException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        transportRegisterModelAction.doExecute(task, prepareRequest("test url", "testModelGroupsID"), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("You don't have permissions to perform this operation on this model.", argumentCaptor.getValue().getMessage());
    }

    public void testDoExecute_successWithLocalNodeEqualToClusterNode() {
        when(node1.getId()).thenReturn("NodeId1");
        when(node2.getId()).thenReturn("NodeId1");

        MLForwardResponse forwardResponse = Mockito.mock(MLForwardResponse.class);
        doAnswer(invocation -> {
            ActionListenerResponseHandler<MLForwardResponse> handler = invocation.getArgument(3);
            handler.handleResponse(forwardResponse);
            return null;
        }).when(transportService).sendRequest(any(), any(), any(), any());
        transportRegisterModelAction.doExecute(task, prepareRequest("http://test_url", "modelGroupID"), actionListener);
        ArgumentCaptor<MLRegisterModelResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void testDoExecute_successWithCreateModelGroup() {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("modelGroupID");
            return null;
        }).when(mlModelGroupManager).createModelGroup(any(), any());

        when(node1.getId()).thenReturn("NodeId1");
        when(node2.getId()).thenReturn("NodeId1");

        MLForwardResponse forwardResponse = Mockito.mock(MLForwardResponse.class);
        doAnswer(invocation -> {
            ActionListenerResponseHandler<MLForwardResponse> handler = invocation.getArgument(3);
            handler.handleResponse(forwardResponse);
            return null;
        }).when(transportService).sendRequest(any(), any(), any(), any());

        transportRegisterModelAction.doExecute(task, prepareRequest("http://test_url", null), actionListener);
        ArgumentCaptor<MLRegisterModelResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void testDoExecute_failureWithCreateModelGroup() {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("Failed to create Model Group"));
            return null;
        }).when(mlModelGroupManager).createModelGroup(any(), any());

        transportRegisterModelAction.doExecute(task, prepareRequest("http://test_url", null), actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to create Model Group", argumentCaptor.getValue().getMessage());
    }

    public void testDoExecute_invalidURL() {
        transportRegisterModelAction.doExecute(task, prepareRequest("test url", "testModelGroupsID"), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("URL can't match trusted url regex", argumentCaptor.getValue().getMessage());
    }

    public void testDoExecute_successWithLocalNodeNotEqualToClusterNode() {
        when(node1.getId()).thenReturn("NodeId1");
        when(node2.getId()).thenReturn("NodeId2");
        MLForwardResponse forwardResponse = Mockito.mock(MLForwardResponse.class);
        doAnswer(invocation -> {
            ActionListenerResponseHandler<MLForwardResponse> handler = invocation.getArgument(3);
            handler.handleResponse(forwardResponse);
            return null;
        }).when(transportService).sendRequest(any(), any(), any(), any());

        transportRegisterModelAction.doExecute(task, prepareRequest("http://test_url", "modelGroupID"), actionListener);
        ArgumentCaptor<MLRegisterModelResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void testDoExecute_FailToSendForwardRequest() {
        when(node1.getId()).thenReturn("NodeId1");
        when(node2.getId()).thenReturn("NodeId2");
        doThrow(new RuntimeException("error")).when(transportService).sendRequest(any(), any(), any(), any());

        transportRegisterModelAction.doExecute(task, prepareRequest("http://test_url", "modelGroupID"), actionListener);
        ArgumentCaptor<MLRegisterModelResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void testTransportRegisterModelActionDoExecuteWithDispatchException() {
        doAnswer(invocation -> {
            ActionListener<Exception> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("Failed to dispatch register model task "));
            return null;
        }).when(mlTaskDispatcher).dispatch(any(), any());
        when(node1.getId()).thenReturn("NodeId1");
        when(clusterService.localNode()).thenReturn(node1);
        transportRegisterModelAction.doExecute(task, prepareRequest("http://test_url", "modelGroupID"), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
    }

    public void test_ValidationFailedException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        transportRegisterModelAction.doExecute(task, prepareRequest("http://test_url", "modelGroupID"), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
    }

    public void testTransportRegisterModelActionDoExecuteWithCreateTaskException() {
        doAnswer(invocation -> {
            ActionListener<Exception> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("Failed to create register model task"));
            return null;
        }).when(mlTaskManager).createMLTask(any(), any());
        when(node1.getId()).thenReturn("NodeId1");
        when(clusterService.localNode()).thenReturn(node1);
        transportRegisterModelAction.doExecute(task, prepareRequest("http://test_url", "modelGroupID"), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
    }

    public void test_execute_registerRemoteModel_withConnectorId_success() {
        MLRegisterModelRequest request = mock(MLRegisterModelRequest.class);
        MLRegisterModelInput input = mock(MLRegisterModelInput.class);
        when(request.getRegisterModelInput()).thenReturn(input);
        when(input.getModelName()).thenReturn("Test Model");
        when(input.getModelGroupId()).thenReturn("modelGroupID");
        when(input.getConnectorId()).thenReturn("mockConnectorId");
        when(input.getFunctionName()).thenReturn(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), anyString(), isA(ActionListener.class));
        MLRegisterModelResponse response = mock(MLRegisterModelResponse.class);
        transportRegisterModelAction.doExecute(task, request, actionListener);
        ArgumentCaptor<MLRegisterModelResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelResponse.class);
        verify(mlModelManager).registerMLRemoteModel(eq(input), isA(MLTask.class), eq(actionListener));
    }

    public void test_execute_registerRemoteModel_withConnectorId_noPermissionToConnectorId() {
        MLRegisterModelRequest request = mock(MLRegisterModelRequest.class);
        MLRegisterModelInput input = mock(MLRegisterModelInput.class);
        when(request.getRegisterModelInput()).thenReturn(input);
        when(input.getConnectorId()).thenReturn("mockConnectorId");
        when(input.getFunctionName()).thenReturn(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(false);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), anyString(), isA(ActionListener.class));
        MLRegisterModelResponse response = mock(MLRegisterModelResponse.class);
        doAnswer(invocation -> {
            ActionListener<MLRegisterModelResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(mlModelManager).registerMLModel(any(), any());
        transportRegisterModelAction.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You don't have permission to use the connector provided, connector id: mockConnectorId",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_execute_registerRemoteModel_withConnectorId_connectorValidationException() {
        MLRegisterModelRequest request = mock(MLRegisterModelRequest.class);
        MLRegisterModelInput input = mock(MLRegisterModelInput.class);
        when(request.getRegisterModelInput()).thenReturn(input);
        when(input.getConnectorId()).thenReturn("mockConnectorId");
        when(input.getFunctionName()).thenReturn(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(), anyString(), isA(ActionListener.class));
        transportRegisterModelAction.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
    }

    public void test_execute_registerRemoteModel_withInternalConnector_success() {
        MLRegisterModelRequest request = mock(MLRegisterModelRequest.class);
        MLRegisterModelInput input = mock(MLRegisterModelInput.class);
        when(request.getRegisterModelInput()).thenReturn(input);
        when(input.getModelName()).thenReturn("Test Model");
        when(input.getVersion()).thenReturn("1");
        when(input.getModelGroupId()).thenReturn("modelGroupID");
        when(input.getFunctionName()).thenReturn(FunctionName.REMOTE);
        Connector connector = mock(Connector.class);
        when(input.getConnector()).thenReturn(connector);
        when(connector.getPredictEndpoint(any(Map.class))).thenReturn("https://api.openai.com");
        MLCreateConnectorResponse mlCreateConnectorResponse = mock(MLCreateConnectorResponse.class);
        doAnswer(invocation -> {
            ActionListener<MLCreateConnectorResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlCreateConnectorResponse);
            return null;
        }).when(client).execute(eq(MLCreateConnectorAction.INSTANCE), any(), isA(ActionListener.class));
        MLRegisterModelResponse response = mock(MLRegisterModelResponse.class);
        transportRegisterModelAction.doExecute(task, request, actionListener);
        ArgumentCaptor<MLRegisterModelResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelResponse.class);
        verify(mlModelManager).registerMLRemoteModel(eq(input), isA(MLTask.class), eq(actionListener));
    }

    public void test_execute_registerRemoteModel_withInternalConnector_connectorIsNull() {
        MLRegisterModelRequest request = mock(MLRegisterModelRequest.class);
        MLRegisterModelInput input = mock(MLRegisterModelInput.class);
        when(request.getRegisterModelInput()).thenReturn(input);
        when(input.getFunctionName()).thenReturn(FunctionName.REMOTE);
        when(input.getConnector()).thenReturn(null);
        transportRegisterModelAction.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You must provide connector content when creating a remote model without connector id!",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_execute_registerRemoteModel_withInternalConnector_predictEndpointIsNull() {
        MLRegisterModelRequest request = mock(MLRegisterModelRequest.class);
        MLRegisterModelInput input = mock(MLRegisterModelInput.class);
        when(request.getRegisterModelInput()).thenReturn(input);
        when(input.getFunctionName()).thenReturn(FunctionName.REMOTE);
        Connector connector = mock(Connector.class);
        when(connector.getPredictEndpoint(any(Map.class))).thenReturn(null);
        when(input.getConnector()).thenReturn(connector);
        transportRegisterModelAction.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Connector endpoint is required when creating a remote model without connector id!",
            argumentCaptor.getValue().getMessage()
        );
    }

    public void test_ModelNameAlreadyExists() throws IOException {
        when(node1.getId()).thenReturn("NodeId1");
        when(node2.getId()).thenReturn("NodeId2");
        MLForwardResponse forwardResponse = Mockito.mock(MLForwardResponse.class);
        doAnswer(invocation -> {
            ActionListenerResponseHandler<MLForwardResponse> handler = invocation.getArgument(3);
            handler.handleResponse(forwardResponse);
            return null;
        }).when(transportService).sendRequest(any(), any(), any(), any());
        SearchResponse searchResponse = createModelGroupSearchResponse(1);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(mlModelGroupManager).validateUniqueModelGroupName(any(), any());

        transportRegisterModelAction.doExecute(task, prepareRequest("http://test_url", null), actionListener);
        ArgumentCaptor<MLRegisterModelResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_FailureWhenPreBuildModelNameAlreadyExists() throws IOException {
        SearchResponse searchResponse = createModelGroupSearchResponse(1);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(mlModelGroupManager).validateUniqueModelGroupName(any(), any());

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .modelName("huggingface/sentence-transformers/all-MiniLM-L12-v2")
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .version("1")
            .build();

        transportRegisterModelAction.doExecute(task, new MLRegisterModelRequest(registerModelInput), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "Without a model group ID, the system will use the model name {huggingface/sentence-transformers/all-MiniLM-L12-v2} to create a new model group. However, this name is taken by another group with id {model_group_ID} you can't access. To register this pre-trained model, create a new model group and use its ID in your request.",
            argumentCaptor.getValue().getMessage()

        );
    }

    public void test_FailureWhenSearchingModelGroupName() throws IOException {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Runtime exception"));
            return null;
        }).when(mlModelGroupManager).validateUniqueModelGroupName(any(), any());

        transportRegisterModelAction.doExecute(task, prepareRequest("Test URL", null), actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Runtime exception", argumentCaptor.getValue().getMessage());
    }

    public void test_NoAccessWhenModelNameAlreadyExists() throws IOException {

        SearchResponse searchResponse = createModelGroupSearchResponse(1);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(mlModelGroupManager).validateUniqueModelGroupName(any(), any());

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        transportRegisterModelAction.doExecute(task, prepareRequest("Test URL", null), actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "The name {Test Model} you provided is unavailable because it is used by another model group with id {model_group_ID} to which you do not have access. Please provide a different name.",
            argumentCaptor.getValue().getMessage()
        );
    }

    private MLRegisterModelRequest prepareRequest(String url, String modelGroupID) {
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .functionName(FunctionName.BATCH_RCF)
            .deployModel(true)
            .modelGroupId(modelGroupID)
            .modelName("Test Model")
            .modelConfig(
                new TextEmbeddingModelConfig(
                    "CUSTOM",
                    123,
                    TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS,
                    "all config",
                    TextEmbeddingModelConfig.PoolingMode.MEAN,
                    true,
                    512
                )
            )
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .url(url)
            .build();
        return new MLRegisterModelRequest(registerModelInput);
    }

    private SearchResponse createModelGroupSearchResponse(long totalHits) throws IOException {

        SearchResponse searchResponse = mock(SearchResponse.class);
        String modelContent = "{\n"
            + "                    \"created_time\": 1684981986069,\n"
            + "                    \"access\": \"public\",\n"
            + "                    \"latest_version\": 0,\n"
            + "                    \"last_updated_time\": 1684981986069,\n"
            + "                    \"_id\": \"model_group_ID\",\n"
            + "                    \"name\": \"Test Model\",\n"
            + "                    \"description\": \"This is an example description\"\n"
            + "                }";
        SearchHit modelGroup = SearchHit.fromXContent(TestHelper.parser(modelContent));
        SearchHits hits = new SearchHits(new SearchHit[] { modelGroup }, new TotalHits(totalHits, TotalHits.Relation.EQUAL_TO), Float.NaN);
        when(searchResponse.getHits()).thenReturn(hits);
        return searchResponse;
    }

}
