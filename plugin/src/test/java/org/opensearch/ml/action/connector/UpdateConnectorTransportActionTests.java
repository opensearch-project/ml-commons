/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;
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
import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.DocWriteResponse.Result;
import org.opensearch.action.index.IndexRequest;
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
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.McpConnector;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
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

    /**
     * Regression test for https://github.com/opensearch-project/ml-commons/issues/5032: the "is this connector still
     * referenced?" guard used an analysed match query on the `connector_id` text field, so a connector whose custom id
     * merely shared a token with a referenced id became un-updatable, blocking credential rotation.
     */
    @Test
    public void testUpdateConnector_ReferenceGuardUsesExactTermQuery() {
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

        ArgumentCaptor<SearchRequest> searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(searchRequestCaptor.capture(), isA(ActionListener.class));
        QueryBuilder query = searchRequestCaptor.getValue().source().query();
        assertTrue("Expected BoolQueryBuilder but got: " + query.getClass().getSimpleName(), query instanceof BoolQueryBuilder);
        // Locate the clause by type rather than by position: the order of the must() calls is an implementation detail.
        TermQueryBuilder connectorIdClause = ((BoolQueryBuilder) query)
            .must()
            .stream()
            .filter(TermQueryBuilder.class::isInstance)
            .map(TermQueryBuilder.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No TermQueryBuilder among the must clauses: " + query));
        assertEquals(MLModel.CONNECTOR_ID_KEYWORD_FIELD, connectorIdClause.fieldName());
        assertEquals(TEST_CONNECTOR_ID, connectorIdClause.value());
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

    @Test
    public void testExecuteWithValidDynamicHeaders() {
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        MLCreateConnectorInput updateContent = MLCreateConnectorInput
            .builder()
            .updateConnector(true)
            .version("2")
            .actions(
                Arrays
                    .asList(
                        ConnectorAction
                            .builder()
                            .actionType(ConnectorAction.ActionType.PREDICT)
                            .method("POST")
                            .url("https://api.openai.com/v1/chat/completions")
                            .headers(Map.of("X-Custom-Header", "${parameters.custom_value}"))
                            .requestBody("{ \"model\": \"${parameters.model}\" }")
                            .build()
                    )
            )
            .build();
        when(updateRequest.getUpdateContent()).thenReturn(updateContent);

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
    public void testExecuteWithBlockedDynamicHeaderThrowsException() {
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        MLCreateConnectorInput updateContent = MLCreateConnectorInput
            .builder()
            .updateConnector(true)
            .version("2")
            .actions(
                Arrays
                    .asList(
                        ConnectorAction
                            .builder()
                            .actionType(ConnectorAction.ActionType.PREDICT)
                            .method("POST")
                            .url("https://api.openai.com/v1/chat/completions")
                            .headers(Map.of("Authorization", "${parameters.token}"))
                            .requestBody("{ \"model\": \"${parameters.model}\" }")
                            .build()
                    )
            )
            .build();
        when(updateRequest.getUpdateContent()).thenReturn(updateContent);

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue().getMessage().contains("cannot use ${parameters.*} placeholders for security reasons"));
    }

    /**
     * ConnectorAccessControlHelper strips credentials before handing the connector over, so a stored
     * mutual TLS connector arrives here with none. Validating certificate presence unconditionally
     * would reject every update to a working connector, including one that only changes a
     * description.
     */
    @Test
    public void testUpdate_mutualTlsConnector_withCredentialsStripped_isAccepted() {
        stubStoredConnector(ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build(), null);
        when(updateRequest.getUpdateContent())
            .thenReturn(MLCreateConnectorInput.builder().updateConnector(true).description("just a description change").build());
        stubUpdateSucceeds();

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        // Assert the update actually succeeded rather than merely that no certificate message
        // appeared: any unrelated failure carries none of those strings and would pass silently.
        verify(actionListener).onResponse(any(UpdateResponse.class));
        verify(actionListener, never()).onFailure(any());
    }

    /** Otherwise an update would be a way around the check performed at create time. */
    @Test
    public void testUpdate_addingSkipSslVerification_isRejected() {
        stubStoredConnector(ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build(), null);
        when(updateRequest.getUpdateContent())
            .thenReturn(
                MLCreateConnectorInput
                    .builder()
                    .updateConnector(true)
                    .connectorClientConfig(
                        ConnectorClientConfig.builder().mutualTlsEnabled(true).skipSslVerification(true).keystoreType("PEM").build()
                    )
                    .build()
            );

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        Exception failure = argumentCaptor.getValue();
        assertTrue("Expected IllegalArgumentException, got: " + failure.getClass(), failure instanceof IllegalArgumentException);
        assertTrue(failure.getMessage(), failure.getMessage().contains("skip_ssl_verification"));
    }

    /** When the update does supply credentials, their completeness can and should be judged. */
    @Test
    public void testUpdate_withIncompleteCredentials_isRejected() {
        stubStoredConnector(null, null);
        when(updateRequest.getUpdateContent())
            .thenReturn(
                MLCreateConnectorInput
                    .builder()
                    .updateConnector(true)
                    .credential(Map.of("client_cert_pem", "cert"))
                    .connectorClientConfig(ConnectorClientConfig.builder().mutualTlsEnabled(true).keystoreType("PEM").build())
                    .build()
            );

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        Exception failure = argumentCaptor.getValue();
        assertTrue("Expected IllegalArgumentException, got: " + failure.getClass(), failure instanceof IllegalArgumentException);
        assertTrue(failure.getMessage(), failure.getMessage().contains("client_key_pem"));
    }

    private void stubStoredConnector(ConnectorClientConfig clientConfig, Map<String, String> credential) {
        // Without this the mock denies access and doExecute never reaches the mutual TLS validation.
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));
        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(5);
            listener
                .onResponse(
                    HttpConnector
                        .builder()
                        .name("test")
                        .protocol("http")
                        .version("1")
                        .credential(credential)
                        .connectorClientConfig(clientConfig)
                        .parameters(Map.of("param1", "value1"))
                        .actions(
                            Arrays
                                .asList(
                                    ConnectorAction
                                        .builder()
                                        .actionType(ConnectorAction.ActionType.PREDICT)
                                        .method("POST")
                                        .url("https://api.openai.com/v1/chat/completions")
                                        .headers(Map.of("Content-Type", "application/json"))
                                        .requestBody("{\"model\": \"${parameters.model}\"}")
                                        .build()
                                )
                        )
                        .build()
                );
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(), any(), any(), any(), any(), any());
    }

    private void stubUpdateSucceeds() {
        // updateUndeployedConnector searches for models using the connector before updating it.
        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(1);
            searchListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());
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

    /**
     * A connector's protocol can be changed by an update, so a protocol whose opt-in feature flag is off must
     * be rejected there too. Gating only creation lets the flag be sidestepped by creating an allowed protocol
     * and switching it afterwards.
     */
    @Test
    public void testUpdateConnectorRejectsSwitchToDisabledProtocol() {
        when(mlFeatureEnabledSetting.isVertexAIConnectorEnabled()).thenReturn(false);
        when(updateRequest.getUpdateContent())
            .thenReturn(MLCreateConnectorInput.builder().updateConnector(true).protocol(ConnectorProtocols.GOOGLE_CLOUD).build());
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) captor.getValue()).status());
        verify(client, never()).update(any(UpdateRequest.class), isA(ActionListener.class));
        verify(client, never()).index(any(IndexRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testUpdateConnectorAllowsSwitchToEnabledProtocol() {
        when(mlFeatureEnabledSetting.isVertexAIConnectorEnabled()).thenReturn(true);
        when(updateRequest.getUpdateContent())
            .thenReturn(MLCreateConnectorInput.builder().updateConnector(true).protocol(ConnectorProtocols.GOOGLE_CLOUD).build());
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));
        stubSearchReturnsNoModels();
        stubUpdateSucceeds();

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        verify(actionListener).onResponse(any(UpdateResponse.class));
    }

    /**
     * Switching an existing connector across the MCP boundary rewrites which class its stored document parses
     * back into: an MCP document carries no actions, an inference one does. The document keeps the fields of the
     * old family, so the connector - and any model referencing it - is read back as a shape whose accessors are
     * missing or unimplemented. The "models are still using this connector" guard does not catch it, because it
     * only considers deployed models.
     */
    @Test
    public void testUpdateConnectorRejectsSwitchToMcpProtocol() {
        when(mlFeatureEnabledSetting.isMcpConnectorEnabled()).thenReturn(true);
        when(updateRequest.getUpdateContent())
            .thenReturn(MLCreateConnectorInput.builder().updateConnector(true).protocol(ConnectorProtocols.MCP_SSE).build());
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertEquals(
            "Cannot change connector protocol from [http] to [mcp_sse]: an MCP connector and an inference connector are not interchangeable.",
            captor.getValue().getMessage()
        );
        assertEquals(RestStatus.BAD_REQUEST, ExceptionsHelper.status(captor.getValue()));
        verify(client, never()).update(any(UpdateRequest.class), isA(ActionListener.class));
        verify(client, never()).index(any(IndexRequest.class), isA(ActionListener.class));
    }

    /**
     * Supplying actions for an MCP connector used to reach connector.getActions() on the MCP instance, which
     * throws, and the failure surfaced through the enclosing handler as a permission-denied style error.
     */
    @Test
    public void testUpdateConnectorRejectsActionsOnMcpConnector() {
        when(mlFeatureEnabledSetting.isMcpConnectorEnabled()).thenReturn(true);
        when(updateRequest.getUpdateContent())
            .thenReturn(
                MLCreateConnectorInput
                    .builder()
                    .updateConnector(true)
                    .actions(
                        List
                            .of(
                                ConnectorAction
                                    .builder()
                                    .actionType(ConnectorAction.ActionType.PREDICT)
                                    .method("POST")
                                    .url("https://api.openai.com/v1/chat/completions")
                                    .build()
                            )
                    )
                    .build()
            );
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));
        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(5);
            listener
                .onResponse(
                    McpConnector
                        .builder()
                        .name("mcp")
                        .protocol(ConnectorProtocols.MCP_SSE)
                        .url("https://api.openai.com/mcp")
                        .credential(Map.of("api_key", "credential_value"))
                        .build()
                );
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(), any(), any(), any(), any(), any());

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertEquals("Connector actions are not supported for protocol [mcp_sse].", captor.getValue().getMessage());
        assertEquals(RestStatus.BAD_REQUEST, ExceptionsHelper.status(captor.getValue()));
        verify(client, never()).update(any(UpdateRequest.class), isA(ActionListener.class));
    }

    /** Switching between the two MCP protocols keeps the same document shape, so it stays allowed. */
    @Test
    public void testUpdateConnectorAllowsSwitchBetweenMcpProtocols() {
        when(mlFeatureEnabledSetting.isMcpConnectorEnabled()).thenReturn(true);
        when(updateRequest.getUpdateContent())
            .thenReturn(MLCreateConnectorInput.builder().updateConnector(true).protocol(ConnectorProtocols.MCP_STREAMABLE_HTTP).build());
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));
        // The stored connector is already MCP, so this update stays on the same side of the boundary.
        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(5);
            listener
                .onResponse(
                    McpConnector
                        .builder()
                        .name("mcp")
                        .protocol(ConnectorProtocols.MCP_SSE)
                        .url("https://api.openai.com/mcp")
                        .credential(Map.of("api_key", "credential_value"))
                        .build()
                );
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(), any(), any(), any(), any(), any());
        stubSearchReturnsNoModels();
        stubUpdateSucceeds();

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        verify(actionListener).onResponse(any(UpdateResponse.class));
    }

    /**
     * The update parse path skips the create-time validation block, so without an explicit check an update can
     * store an unsupported protocol string. Every later read of that document then fails to resolve a connector
     * class, leaving the connector permanently unusable.
     */
    @Test
    public void testUpdateConnectorRejectsUnsupportedProtocol() {
        when(updateRequest.getUpdateContent())
            .thenReturn(MLCreateConnectorInput.builder().updateConnector(true).protocol("not_a_real_protocol").build());
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        verify(client, never()).update(any(UpdateRequest.class), isA(ActionListener.class));
        verify(client, never()).index(any(IndexRequest.class), isA(ActionListener.class));
    }

    /**
     * mutual_tls_enabled is accepted on every protocol but only honoured by the non-streaming http executor.
     * Accepting it elsewhere reports a transport protection back to the operator that is never applied, so the
     * request has to fail instead.
     */
    @Test
    public void testUpdateConnectorRejectsMutualTlsOnUnsupportedProtocol() {
        when(mlFeatureEnabledSetting.isVertexAIConnectorEnabled()).thenReturn(true);
        when(updateRequest.getUpdateContent())
            .thenReturn(
                MLCreateConnectorInput
                    .builder()
                    .updateConnector(true)
                    .protocol(ConnectorProtocols.GOOGLE_CLOUD)
                    .connectorClientConfig(ConnectorClientConfig.builder().mutualTlsEnabled(true).build())
                    .build()
            );
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertTrue(captor.getValue().getMessage().contains("Mutual TLS is not supported"));
    }

    /** The setting is there to stop a connector newly relying on mutual TLS while support is incomplete. */
    @Test
    public void testUpdateConnectorRejectsNewlyEnablingMutualTlsWhileSettingOff() {
        when(mlFeatureEnabledSetting.isMutualTlsEnabled()).thenReturn(false);
        when(updateRequest.getUpdateContent())
            .thenReturn(
                MLCreateConnectorInput
                    .builder()
                    .updateConnector(true)
                    .connectorClientConfig(ConnectorClientConfig.builder().mutualTlsEnabled(true).build())
                    .build()
            );
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) captor.getValue()).status());
        verify(client, never()).update(any(UpdateRequest.class), isA(ActionListener.class));
    }

    /**
     * An update replaces client_config wholesale, so editing an unrelated field means re-sending
     * mutual_tls_enabled to keep it. That must not be rejected, or turning the setting off would make an
     * existing mutual-TLS connector uneditable rather than just preventing new reliance on it.
     */
    @Test
    public void testUpdateConnectorAllowsCarryingForwardStoredMutualTlsWhileSettingOff() {
        when(mlFeatureEnabledSetting.isMutualTlsEnabled()).thenReturn(false);
        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(5);
            listener
                .onResponse(
                    HttpConnector
                        .builder()
                        .name("test")
                        .protocol("http")
                        .version("1")
                        .connectorClientConfig(ConnectorClientConfig.builder().mutualTlsEnabled(true).build())
                        .build()
                );
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(), any(), any(), any(), any(), any());
        when(updateRequest.getUpdateContent())
            .thenReturn(
                MLCreateConnectorInput
                    .builder()
                    .updateConnector(true)
                    .description("an unrelated edit")
                    .connectorClientConfig(ConnectorClientConfig.builder().mutualTlsEnabled(true).maxConnections(50).build())
                    .build()
            );
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));
        stubSearchReturnsNoModels();
        stubUpdateSucceeds();

        updateConnectorTransportAction.doExecute(task, updateRequest, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, atMost(1)).onFailure(captor.capture());
        for (Exception e : captor.getAllValues()) {
            assertFalse(
                "carrying the stored mutual_tls_enabled forward was rejected: " + e.getMessage(),
                e instanceof OpenSearchStatusException && ((OpenSearchStatusException) e).status() == RestStatus.FORBIDDEN
            );
        }
    }

    private void stubSearchReturnsNoModels() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));
    }

    private void stubUpdateSucceeds() {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));
    }

}
