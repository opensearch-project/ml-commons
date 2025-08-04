/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;

import java.io.IOException;
import java.util.Collections;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.model_group.MLModelGroupDeleteRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class DeleteModelGroupTransportActionTests extends OpenSearchTestCase {

    private static final String MODEL_GROUP_ID = "model_group_id";
    DeleteResponse deleteResponse = new DeleteResponse(new ShardId(ML_MODEL_GROUP_INDEX, "_na_", 0), MODEL_GROUP_ID, 1, 0, 2, true);

    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    SdkClient sdkClient;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<DeleteResponse> actionListener;

    @Mock
    ClusterService clusterService;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    DeleteModelGroupTransportAction deleteModelGroupTransportAction;
    MLModelGroupDeleteRequest mlModelGroupDeleteRequest;
    ThreadContext threadContext;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        Settings settings = Settings.builder().build();
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        mlModelGroupDeleteRequest = MLModelGroupDeleteRequest.builder().modelGroupId("test_id").build();
        deleteModelGroupTransportAction = spy(
            new DeleteModelGroupTransportAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                xContentRegistry,
                clusterService,
                modelAccessControlHelper,
                mlFeatureEnabledSetting
            )
        );

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(6);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any());

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void testDeleteModelGroup_Success() throws InterruptedException {

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        SearchResponse searchResponse = getEmptySearchResponse();

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, actionListener);

        // Capture and verify the response
        ArgumentCaptor<DeleteResponse> captor = forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());

        // Assert the captured response matches the expected values
        DeleteResponse actualResponse = captor.getValue();
        assertEquals(deleteResponse.getId(), actualResponse.getId());
        assertEquals(deleteResponse.getIndex(), actualResponse.getIndex());
        assertEquals(deleteResponse.getVersion(), actualResponse.getVersion());
        assertEquals(deleteResponse.getResult(), actualResponse.getResult());
    }

    @Test
    public void test_AssociatedModelsExistException() throws IOException, InterruptedException {

        SearchResponse searchResponse = getNonEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Cannot delete the model group when it has associated model versions", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_IndexNotFoundExceptionDuringSearch() throws IOException, InterruptedException {

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("index_not_found"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, actionListener);

        // Capture and verify the response
        ArgumentCaptor<DeleteResponse> captor = forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());

        // Assert the captured response matches the expected values
        DeleteResponse actualResponse = captor.getValue();
        assertEquals(deleteResponse.getId(), actualResponse.getId());
        assertEquals(deleteResponse.getIndex(), actualResponse.getIndex());
        assertEquals(deleteResponse.getVersion(), actualResponse.getVersion());
        assertEquals(deleteResponse.getResult(), actualResponse.getResult());
    }

    @Test
    public void test_DeleteRequestInternalServerError() {
        SearchResponse searchResponse = getEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new OpenSearchException("Internal Server Error", RestStatus.INTERNAL_SERVER_ERROR));
            return null;
        }).when(client).delete(any(), any());

        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to delete data object from index .plugins-ml-model-group", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_UserHasNoAccessException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(6);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any());

        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to delete this model group", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_ValidationFailedException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(6);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any());

        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteModelGroup_IndexNotFoundException() {
        SearchResponse searchResponse = getEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("errorMessage"));
            return null;
        }).when(client).delete(any(), any());
        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model group", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteModelGroup_Failure() {
        SearchResponse searchResponse = getEmptySearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("errorMessage"));
            return null;
        }).when(client).delete(any(), any());

        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to delete data object from index .plugins-ml-model-group", argumentCaptor.getValue().getMessage());
    }

    private SearchResponse getEmptySearchResponse() {
        SearchHits hits = new SearchHits(new SearchHit[0], null, Float.NaN);
        SearchResponseSections searchSections = new SearchResponseSections(hits, InternalAggregations.EMPTY, null, true, false, null, 1);
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

    private SearchResponse getNonEmptySearchResponse() throws IOException {
        SearchHit[] hits = new SearchHit[1];
        String modelContent = "{\n"
            + "                    \"created_time\": 1684981986069,\n"
            + "                    \"last_updated_time\": 1684981986069,\n"
            + "                    \"_id\": \"model_ID\",\n"
            + "                    \"name\": \"test_model\",\n"
            + "                    \"description\": \"This is an example description\"\n"
            + "                }";
        SearchHit model = SearchHit.fromXContent(TestHelper.parser(modelContent));
        hits[0] = model;
        SearchHits searchHits = new SearchHits(hits, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponseSections searchSections = new SearchResponseSections(
            searchHits,
            InternalAggregations.EMPTY,
            null,
            true,
            false,
            null,
            1
        );
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
