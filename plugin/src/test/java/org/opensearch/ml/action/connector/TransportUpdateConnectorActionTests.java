/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableList;

public class TransportUpdateConnectorActionTests extends OpenSearchTestCase {

    private UpdateConnectorTransportAction transportUpdateConnectorAction;

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Mock
    private Task task;

    @Mock
    private Client client;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private ClusterService clusterService;

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private MLUpdateConnectorRequest updateRequest;

    @Mock
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

    private static final List<String> TRUSTED_CONNECTOR_ENDPOINTS_REGEXES = ImmutableList
        .of("^https://runtime\\.sagemaker\\..*\\.amazonaws\\.com/.*$", "^https://api\\.openai\\.com/.*$", "^https://api\\.cohere\\.ai/.*$");

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        settings = Settings
            .builder()
            .putList(ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), TRUSTED_CONNECTOR_ENDPOINTS_REGEXES)
            .build();
        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX,
            ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED
        );

        Settings settings = Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), true).build();
        threadContext = new ThreadContext(settings);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        String connector_id = "test_connector_id";
        MLCreateConnectorInput updateContent = MLCreateConnectorInput
            .builder()
            .updateConnector(true)
            .version("2")
            .description("updated description")
            .build();
        when(updateRequest.getConnectorId()).thenReturn(connector_id);
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

        transportUpdateConnectorAction = new UpdateConnectorTransportAction(
            transportService,
            actionFilters,
            client,
            connectorAccessControlHelper,
            mlModelManager,
            settings,
            clusterService,
            mlEngine
        );

        when(mlModelManager.getAllModelIds()).thenReturn(new String[] {});
        shardId = new ShardId(new Index("indexName", "uuid"), 1);
        updateResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED);
    }

    @Test
    public void test_execute_connectorAccessControl_success() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(String.class), isA(ActionListener.class));

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

        transportUpdateConnectorAction.doExecute(task, updateRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void test_execute_connectorAccessControl_NoPermission() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(false);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(String.class), isA(ActionListener.class));

        transportUpdateConnectorAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(
            "You don't have permission to update the connector, connector id: test_connector_id",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void test_execute_connectorAccessControl_AccessError() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Connector Access Control Error"));
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(String.class), isA(ActionListener.class));

        transportUpdateConnectorAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Connector Access Control Error", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_execute_connectorAccessControl_Exception() {
        doThrow(new RuntimeException("exception in access control"))
            .when(connectorAccessControlHelper)
            .validateConnectorAccess(any(Client.class), any(String.class), isA(ActionListener.class));

        transportUpdateConnectorAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("exception in access control", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_execute_UpdateWrongStatus() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(String.class), isA(ActionListener.class));

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

        transportUpdateConnectorAction.doExecute(task, updateRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void test_execute_UpdateException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(String.class), isA(ActionListener.class));

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

        transportUpdateConnectorAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("update document failure", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_execute_SearchResponseNotEmpty() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(String.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(noneEmptySearchResponse());
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        transportUpdateConnectorAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("1 models are still using this connector, please undeploy the models first!", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_execute_SearchResponseError() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(String.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException("Error in Search Request"));
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));

        transportUpdateConnectorAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Error in Search Request", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_execute_SearchIndexNotFoundError() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(2);
            listener.onResponse(true);
            return null;
        }).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(String.class), isA(ActionListener.class));

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

        transportUpdateConnectorAction.doExecute(task, updateRequest, actionListener);
        verify(actionListener).onResponse(updateResponse);
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
