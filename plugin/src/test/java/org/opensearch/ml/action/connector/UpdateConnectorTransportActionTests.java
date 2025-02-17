/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.DocWriteResponse.Result;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableList;

public class UpdateConnectorTransportActionTests extends OpenSearchTestCase {

    private UpdateConnectorTransportAction updateConnectorTransportAction;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Mock
    private Task task;

    @Mock
    private Client client;
    private SdkClient sdkClient;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private ClusterService clusterService;

    @Mock
    private TransportService transportService;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private MLUpdateConnectorRequest updateRequest;

    private UpdateResponse updateResponse;

    @Mock
    ActionListener<UpdateResponse> actionListener;

    @Mock
    MLModelManager mlModelManager;

    ThreadContext threadContext;

    private Settings settings;

    private ShardId shardId;

    private SearchResponse searchResponse;

    private MLEngine mlEngine;

    private static final String TEST_CONNECTOR_ID = "test_connector_id";
    private static final List<String> TRUSTED_CONNECTOR_ENDPOINTS_REGEXES = ImmutableList
        .of("^https://runtime\\.sagemaker\\..*\\.amazonaws\\.com/.*$", "^https://api\\.openai\\.com/.*$", "^https://api\\.cohere\\.ai/.*$");

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        settings = Settings
            .builder()
            .putList(ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), TRUSTED_CONNECTOR_ENDPOINTS_REGEXES)
            .build();
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());

        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX,
            ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED
        );

        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), true).build();
        threadContext = new ThreadContext(settings);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        MLCreateConnectorInput updateContent = MLCreateConnectorInput
            .builder()
            .updateConnector(true)
            .version("2")
            .description("updated description")
            .build();
        when(updateRequest.getConnectorId()).thenReturn(TEST_CONNECTOR_ID);
        when(updateRequest.getUpdateContent()).thenReturn(updateContent);

        SearchHits hits = new SearchHits(new SearchHit[] {}, new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
        SearchResponseSections searchSections = new SearchResponseSections(hits, InternalAggregations.EMPTY, null, false, false, null, 1);
        searchResponse = new SearchResponse(
            searchSections,
            null,
            1,
            1,
            0,
            11,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );

        Encryptor encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(Path.of("/tmp/test" + UUID.randomUUID()), encryptor);

        updateConnectorTransportAction = new UpdateConnectorTransportAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            connectorAccessControlHelper,
            mlModelManager,
            settings,
            clusterService,
            mlEngine,
            mlFeatureEnabledSetting
        );

        when(mlModelManager.getAllModelIds()).thenReturn(new String[] {});
        shardId = new ShardId(new Index("indexName", "uuid"), 1);
        updateResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED);

        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(5);
            Connector connector = HttpConnector
                .builder()
                .name("test")
                .protocol("http")
                .version("1")
                .credential(Map.of("api_key", "credential_value"))
                .parameters(Map.of("param1", "value1"))
                .actions(
                    Arrays
                        .asList(
                            ConnectorAction
                                .builder()
                                .actionType(ConnectorAction.ActionType.PREDICT)
                                .method("POST")
                                .url("https://api.openai.com/v1/chat/completions")
                                .headers(Map.of("Authorization", "Bearer ${credential.api_key}"))
                                .requestBody("{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }")
                                .build()
                        )
                )
                .build();
            // Connector connector = mock(HttpConnector.class);
            // doNothing().when(connector).update(any(), any());
            listener.onResponse(connector);
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testUpdateConnectorUpdatesHttpConnectorTimeFields() {
        HttpConnector connector = HttpConnector
            .builder()
            .name("test")
            .protocol("http")
            .version("1")
            .credential(Map.of("api_key", "credential_value"))
            .parameters(Map.of("param1", "value1"))
            .actions(
                Arrays
                    .asList(
                        ConnectorAction
                            .builder()
                            .actionType(ConnectorAction.ActionType.PREDICT)
                            .method("POST")
                            .url("https://api.openai.com/v1/chat/completions")
                            .headers(Map.of("Authorization", "Bearer ${credential.api_key}"))
                            .requestBody("{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }")
                            .build()
                    )
            )
            .build();

        Instant testInitialTime = Instant.now();
        connector.setCreatedTime(testInitialTime);
        connector.setLastUpdateTime(testInitialTime);

        assert (connector.getCreatedTime().toEpochMilli() == connector.getLastUpdateTime().toEpochMilli());

        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(2);
            listener.onResponse(connector);
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(Client.class), any(String.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        assertTrue(
            "Last update time must be bigger than the creation time",
            connector.getLastUpdateTime().toEpochMilli() >= connector.getCreatedTime().toEpochMilli()
        );
    }

    @Test
    public void testExecuteConnectorAccessControlSuccess() throws InterruptedException {
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);
        verify(actionListener).onResponse(any(UpdateResponse.class));
    }

    @Test
    public void testExecuteConnectorAccessControlNoPermission() {
        doReturn(false).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You don't have permission to update the connector, connector id: test_connector_id",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void testExecuteConnectorAccessControlAccessError() {
        doThrow(new RuntimeException("Connector Access Control Error"))
            .when(connectorAccessControlHelper)
            .validateConnectorAccess(any(Client.class), any(Connector.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Connector Access Control Error", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testExecuteConnectorAccessControlException() {
        doThrow(new RuntimeException("exception in access control"))
            .when(connectorAccessControlHelper)
            .validateConnectorAccess(any(Client.class), any(Connector.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("exception in access control", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testExecuteUpdateWrongStatus() throws InterruptedException {
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        UpdateResponse updateResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.CREATED);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        ArgumentCaptor<UpdateResponse> argumentCaptor = ArgumentCaptor.forClass(UpdateResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(updateResponse.getId(), argumentCaptor.getValue().getId());
        assertEquals(updateResponse.getResult(), argumentCaptor.getValue().getResult());
    }

    @Test
    public void testExecuteUpdateException() throws InterruptedException {
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("update document failure"));
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to update data object in index .plugins-ml-connector", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testExecuteSearchResponseNotEmpty() throws IOException, InterruptedException {
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(noneEmptySearchResponse());
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(
            argumentCaptor.getValue().getMessage().contains("1 models are still using this connector, please undeploy the models first")
        );
    }

    @Test
    public void testExecuteSearchResponseError() throws InterruptedException {
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException("Error in Search Request"));
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to search indices [.plugins-ml-model]", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testExecuteSearchIndexNotFoundError() throws InterruptedException {
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(2);
            Connector connector = HttpConnector
                .builder()
                .name("test")
                .protocol("http")
                .version("1")
                .credential(Map.of("api_key", "credential_value"))
                .parameters(Map.of("param1", "value1"))
                .actions(
                    Arrays
                        .asList(
                            ConnectorAction
                                .builder()
                                .actionType(ConnectorAction.ActionType.PREDICT)
                                .method("POST")
                                .url("https://api.openai.com/v1/chat/completions")
                                .headers(Map.of("Authorization", "Bearer ${credential.api_key}"))
                                .requestBody("{ \"model\": \"${parameters.model}\", \"messages\": ${parameters.messages} }")
                                .build()
                        )
                )
                .build();
            // Connector connector = mock(HttpConnector.class);
            // doNothing().when(connector).update(any(), any());
            listener.onResponse(connector);
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(Client.class), any(String.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new IndexNotFoundException("Index not found!"));
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        ArgumentCaptor<UpdateResponse> argumentCaptor = ArgumentCaptor.forClass(UpdateResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(Result.UPDATED, argumentCaptor.getValue().getResult());
    }

    private SearchResponse noneEmptySearchResponse() throws IOException {
        String modelContent = "{\"name\":\"Remote_Model\",\"algorithm\":\"Remote\",\"version\":1,\"connector_id\":\"test_id\"}";
        SearchHit model = SearchHit.fromXContent(TestHelper.parser(modelContent));
        SearchHits hits = new SearchHits(new SearchHit[] { model }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
        SearchResponseSections searchSections = new SearchResponseSections(hits, InternalAggregations.EMPTY, null, false, false, null, 1);
        SearchResponse searchResponse = new SearchResponse(
            searchSections,
            null,
            1,
            1,
            0,
            11,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );

        return searchResponse;
    }
}
