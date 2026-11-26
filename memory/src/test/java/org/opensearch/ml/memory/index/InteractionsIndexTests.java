/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.memory.index;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_INDEX_NAME;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opensearch.OpenSearchWrapperException;
import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.admin.indices.refresh.RefreshResponse;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.SendRequestTransportException;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.IndicesAdminClient;

public class InteractionsIndexTests extends OpenSearchTestCase {
    @Mock
    Client client;

    @Mock
    ClusterService clusterService;

    @Mock
    ClusterState clusterState;

    @Mock
    Metadata metadata;

    @Mock
    AdminClient adminClient;

    @Mock
    IndicesAdminClient indicesAdminClient;

    @Mock
    ThreadPool threadPool;

    @Mock
    ConversationMetaIndex conversationMetaIndex;

    InteractionsIndex interactionsIndex;

    @Before
    public void setup() {
        this.client = mock(Client.class);
        this.clusterService = mock(ClusterService.class);
        this.clusterState = mock(ClusterState.class);
        this.metadata = mock(Metadata.class);
        this.adminClient = mock(AdminClient.class);
        this.indicesAdminClient = mock(IndicesAdminClient.class);
        this.threadPool = mock(ThreadPool.class);
        this.conversationMetaIndex = mock(ConversationMetaIndex.class);

        doReturn(clusterState).when(clusterService).state();
        doReturn(metadata).when(clusterState).metadata();
        doReturn(adminClient).when(client).admin();
        doReturn(indicesAdminClient).when(adminClient).indices();
        doReturn(threadPool).when(client).threadPool();
        doReturn(new ThreadContext(Settings.EMPTY)).when(threadPool).getThreadContext();
        this.interactionsIndex = spy(new InteractionsIndex(client, clusterService, conversationMetaIndex));
    }

    private void setupDoesNotMakeIndex() {
        doReturn(false).when(metadata).hasIndex(anyString());
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> al = invocation.getArgument(1);
            al.onResponse(new CreateIndexResponse(false, false, "some-other-index-entirely"));
            return null;
        }).when(indicesAdminClient).create(any(), any());
    }

    private void setupGrantAccess() {
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(true);
            return null;
        }).when(conversationMetaIndex).checkAccess(anyString(), any());
    }

    private void setupDenyAccess(String user) {
        String userStr = user == null ? "" : user + "||";
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(false);
            return null;
        }).when(conversationMetaIndex).checkAccess(anyString(), any());
        doAnswer(invocation -> {
            ThreadContext tc = new ThreadContext(Settings.EMPTY);
            tc.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, userStr);
            return tc;
        }).when(threadPool).getThreadContext();
    }

    private void setupRefreshSuccess() {
        doAnswer(invocation -> {
            ActionListener<RefreshResponse> al = invocation.getArgument(1);
            al.onResponse(mock(RefreshResponse.class));
            return null;
        }).when(indicesAdminClient).refresh(any(), any());
    }

    private SearchRequest dummyRequest() {
        SearchRequest request = new SearchRequest();
        request.source(new SearchSourceBuilder());
        request.source().query(new MatchAllQueryBuilder());
        return request;
    }

    public void testInit_DoesNotCreateIndex_ThenReturnFalse() {
        setupDoesNotMakeIndex();
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> createIndexListener = mock(ActionListener.class);
        interactionsIndex.initInteractionsIndexIfAbsent(createIndexListener);
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(createIndexListener, times(1)).onResponse(argCaptor.capture());
        assert (!argCaptor.getValue());
    }

    public void testInit_CreateIndexFails_ThenFail() {
        doReturn(false).when(metadata).hasIndex(anyString());
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Test Error"));
            return null;
        }).when(indicesAdminClient).create(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> createIndexListener = mock(ActionListener.class);
        interactionsIndex.initInteractionsIndexIfAbsent(createIndexListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createIndexListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test Error"));
    }

    public void testInit_CreateIndexFails_WithWrapped_OtherException_ThenFail() {
        doReturn(false).when(metadata).hasIndex(anyString());
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> al = invocation.getArgument(1);
            al.onFailure(new SendRequestTransportException(null, "action", new Exception("some other exception")));
            return null;
        }).when(indicesAdminClient).create(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> createIndexListener = mock(ActionListener.class);
        interactionsIndex.initInteractionsIndexIfAbsent(createIndexListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createIndexListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue() instanceof OpenSearchWrapperException);
        assert (argCaptor.getValue().getCause().getMessage().equals("some other exception"));
    }

    public void testInit_ClientFails_WithResourceExists_ThenOK() {
        doReturn(false).when(metadata).hasIndex(anyString());
        doThrow(new ResourceAlreadyExistsException("Test index exists")).when(indicesAdminClient).create(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> createIndexListener = mock(ActionListener.class);
        interactionsIndex.initInteractionsIndexIfAbsent(createIndexListener);
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(createIndexListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue());
    }

    public void testInit_ClientFails_WithWrappedResourceExists_ThenOK() {
        doReturn(false).when(metadata).hasIndex(anyString());
        doThrow(new SendRequestTransportException(null, "action", new ResourceAlreadyExistsException("Test index exists")))
            .when(indicesAdminClient)
            .create(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> createIndexListener = mock(ActionListener.class);
        interactionsIndex.initInteractionsIndexIfAbsent(createIndexListener);
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(createIndexListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue());
    }

    public void testInit_ClientFails_WithWrappedOtherException_ThenFail() {
        doReturn(false).when(metadata).hasIndex(anyString());
        doThrow(new SendRequestTransportException(null, "action", new Exception("Some other exception")))
            .when(indicesAdminClient)
            .create(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> createIndexListener = mock(ActionListener.class);
        interactionsIndex.initInteractionsIndexIfAbsent(createIndexListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createIndexListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue() instanceof OpenSearchWrapperException);
        assert (argCaptor.getValue().getCause().getMessage().equals("Some other exception"));
    }

    public void testInit_ClientFails_ThenFail() {
        doReturn(false).when(metadata).hasIndex(anyString());
        doThrow(new RuntimeException("Test Client Failure")).when(indicesAdminClient).create(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> createIndexListener = mock(ActionListener.class);
        interactionsIndex.initInteractionsIndexIfAbsent(createIndexListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createIndexListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test Client Failure"));
    }

    public void testCreate_NoIndex_ThenFail() {
        setupDoesNotMakeIndex();
        @SuppressWarnings("unchecked")
        ActionListener<String> createInteractionListener = mock(ActionListener.class);
        interactionsIndex
            .createInteraction("cid", "inp", "pt", "rsp", "ogn", Collections.singletonMap("meta", "some meta"), createInteractionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createInteractionListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("no index to add memory to"));
    }

    public void testCreate_BadRestStatus_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        IndexResponse response = mock(IndexResponse.class);
        doReturn(RestStatus.GONE).when(response).status();
        doAnswer(invocation -> {
            ActionListener<IndexResponse> al = invocation.getArgument(1);
            al.onResponse(response);
            return null;
        }).when(client).index(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<String> createInteractionListener = mock(ActionListener.class);
        interactionsIndex
            .createInteraction("cid", "inp", "pt", "rsp", "ogn", Collections.singletonMap("meta", "some meta"), createInteractionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createInteractionListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failed to create message"));
    }

    public void testCreate_InternalFailure_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        doAnswer(invocation -> {
            ActionListener<IndexResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Test Failure"));
            return null;
        }).when(client).index(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<String> createInteractionListener = mock(ActionListener.class);
        interactionsIndex
            .createInteraction("cid", "inp", "pt", "rsp", "ogn", Collections.singletonMap("meta", "some meta"), createInteractionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createInteractionListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test Failure"));
    }

    public void testCreate_ClientFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        doThrow(new RuntimeException("Test Client Failure")).when(client).index(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<String> createInteractionListener = mock(ActionListener.class);
        interactionsIndex
            .createInteraction("cid", "inp", "pt", "rsp", "ogn", Collections.singletonMap("meta", "some meta"), createInteractionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createInteractionListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test Client Failure"));
    }

    public void testCreate_NoAccessNoUser_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupDenyAccess(null);
        doThrow(new RuntimeException("Test Client Failure")).when(client).index(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<String> createInteractionListener = mock(ActionListener.class);
        interactionsIndex
            .createInteraction("cid", "inp", "pt", "rsp", "ogn", Collections.singletonMap("meta", "some meta"), createInteractionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createInteractionListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor
            .getValue()
            .getMessage()
            .equals("User [" + ActionConstants.DEFAULT_USERNAME_FOR_ERRORS + "] does not have access to memory cid"));
    }

    public void testCreate_NoAccessWithUser_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupDenyAccess("user");
        doThrow(new RuntimeException("Test Client Failure")).when(client).index(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<String> createInteractionListener = mock(ActionListener.class);
        interactionsIndex
            .createInteraction("cid", "inp", "pt", "rsp", "ogn", Collections.singletonMap("meta", "some meta"), createInteractionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createInteractionListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("User [user] does not have access to memory cid"));
    }

    public void testCreate_CreateIndexFails_ThenFail() {
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(0);
            al.onFailure(new Exception("Fail in Index Creation"));
            return null;
        }).when(interactionsIndex).initInteractionsIndexIfAbsent(any());
        @SuppressWarnings("unchecked")
        ActionListener<String> createInteractionListener = mock(ActionListener.class);
        interactionsIndex
            .createInteraction("cid", "inp", "pt", "rsp", "ogn", Collections.singletonMap("meta", "some meta"), createInteractionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createInteractionListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Fail in Index Creation"));
    }

    public void testGet_NoIndex_ThenEmpty() {
        doReturn(false).when(metadata).hasIndex(anyString());
        @SuppressWarnings("unchecked")
        ActionListener<List<Interaction>> getInteractionsListener = mock(ActionListener.class);
        interactionsIndex.getInteractions("cid", 0, 10, getInteractionsListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Interaction>> argCaptor = ArgumentCaptor.forClass(List.class);
        verify(getInteractionsListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue().size() == 0);
    }

    public void testGet_SearchFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        setupRefreshSuccess();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Failure in Search"));
            return null;
        }).when(client).search(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<List<Interaction>> getInteractionsListener = mock(ActionListener.class);
        interactionsIndex.getInteractions("cid", 0, 10, getInteractionsListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getInteractionsListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failure in Search"));
    }

    public void testGet_RefreshFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        doAnswer(invocation -> {
            ActionListener<RefreshResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Failed to Refresh"));
            return null;
        }).when(indicesAdminClient).refresh(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<List<Interaction>> getInteractionsListener = mock(ActionListener.class);
        interactionsIndex.getInteractions("cid", 0, 10, getInteractionsListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getInteractionsListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failed to Refresh"));
    }

    public void testGet_ClientFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        doThrow(new RuntimeException("Client Failure")).when(indicesAdminClient).refresh(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<List<Interaction>> getInteractionsListener = mock(ActionListener.class);
        interactionsIndex.getInteractions("cid", 0, 10, getInteractionsListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getInteractionsListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Client Failure"));
    }

    public void testGet_NoAccessNoUser_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupDenyAccess(null);
        @SuppressWarnings("unchecked")
        ActionListener<List<Interaction>> getInteractionsListener = mock(ActionListener.class);
        interactionsIndex.getInteractions("cid", 0, 10, getInteractionsListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getInteractionsListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor
            .getValue()
            .getMessage()
            .equals("User [" + ActionConstants.DEFAULT_USERNAME_FOR_ERRORS + "] does not have access to memory cid"));
    }

    public void testGetTraces_NoIndex_ThenEmpty() {
        doReturn(false).when(metadata).hasIndex(anyString());
        @SuppressWarnings("unchecked")
        ActionListener<List<Interaction>> getTracesListener = mock(ActionListener.class);
        interactionsIndex.getTraces("cid", 0, 10, getTracesListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Interaction>> argCaptor = ArgumentCaptor.forClass(List.class);
        verify(getTracesListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue().size() == 0);
    }

    public void testInnerGetTraces_success() {
        setUpSearchTraceResponse();
        doReturn(true).when(metadata).hasIndex(anyString());
        @SuppressWarnings("unchecked")
        ActionListener<List<Interaction>> getTracesListener = mock(ActionListener.class);
        interactionsIndex.innerGetTraces("cid", 0, 10, getTracesListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Interaction>> argCaptor = ArgumentCaptor.forClass(List.class);
        verify(getTracesListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue().size() == 1);
    }

    public void testGetTraces_success() {
        setupGrantAccess();
        doReturn(true).when(metadata).hasIndex(anyString());
        setupRefreshSuccess();

        GetResponse response = setUpInteractionResponse("iid");
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        setUpSearchTraceResponse();
        doReturn(true).when(metadata).hasIndex(anyString());
        @SuppressWarnings("unchecked")
        ActionListener<List<Interaction>> getTracesListener = mock(ActionListener.class);
        interactionsIndex.getTraces("iid", 0, 10, getTracesListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Interaction>> argCaptor = ArgumentCaptor.forClass(List.class);
        verify(getTracesListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue().size() == 1);
    }

    public void testGetTraces_clientFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        doThrow(new RuntimeException("Client Failure")).when(client).search(any(), any());
        ActionListener<List<Interaction>> getTracesListener = mock(ActionListener.class);
        interactionsIndex.innerGetTraces("cid", 0, 10, getTracesListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getTracesListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Client Failure"));
    }

    public void testDelete_NoIndex_ThenReturnTrue() {
        doReturn(false).when(metadata).hasIndex(anyString());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(deleteConversationListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue());
    }

    public void testDelete_BulkHasFailures_ReturnFalse() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        setupRefreshSuccess();
        setupDeleteSearchResponses(List.of(List.of("iid")));
        setupBulkResponses(Set.of("iid"));
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(deleteConversationListener, times(1)).onResponse(argCaptor.capture());
        assert (!argCaptor.getValue());
    }

    public void testDelete_BulkFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        setupRefreshSuccess();
        setupDeleteSearchResponses(List.of(List.of("iid")));
        doAnswer(invocation -> {
            ActionListener<BulkResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Failure during Bulk"));
            return null;
        }).when(client).bulk(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failure during Bulk"));
    }

    public void testDelete_SearchFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        setupRefreshSuccess();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Failure during search"));
            return null;
        }).when(client).search(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failure during search"));
    }

    public void testDelete_RefreshFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        doAnswer(invocation -> {
            ActionListener<RefreshResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Failure during refresh"));
            return null;
        }).when(indicesAdminClient).refresh(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failure during refresh"));
        verify(client, never()).search(any(), any());
    }

    public void testDelete_SearchReturnEmpty_ThenPass() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        setupRefreshSuccess();
        setupDeleteSearchResponses(List.of(List.of()));
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(deleteConversationListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue());
        verify(client, never()).bulk(any(), any());
    }

    public void testDelete_QueryIncludesTraces() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        setupRefreshSuccess();
        setupDeleteSearchResponses(List.of(List.of("iid", "trace-1", "trace-2")));
        setupBulkSuccess();
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);

        // The delete query matches every document of the memory, with no filter that hides trace messages
        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(1)).search(searchCaptor.capture(), any());
        SearchRequest searchRequest = searchCaptor.getValue();
        assertEquals(INTERACTIONS_INDEX_NAME, searchRequest.indices()[0]);
        assertEquals(
            new TermQueryBuilder(ConversationalIndexConstants.INTERACTIONS_CONVERSATION_ID_FIELD, "cid"),
            searchRequest.source().query()
        );

        ArgumentCaptor<BulkRequest> bulkCaptor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(1)).bulk(bulkCaptor.capture(), any());
        List<String> deletedIds = bulkCaptor.getValue().requests().stream().map(DocWriteRequest::id).collect(Collectors.toList());
        assertEquals(List.of("iid", "trace-1", "trace-2"), deletedIds);

        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(deleteConversationListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue());
    }

    public void testDelete_DeletesInBatchesUntilNoneLeft() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        setupRefreshSuccess();
        // A full batch is followed by another search; a partial batch ends the delete
        List<String> fullBatch = IntStream.range(0, 300).mapToObj(i -> "id-" + i).collect(Collectors.toList());
        setupDeleteSearchResponses(List.of(fullBatch, List.of("id-300", "id-301")));
        setupBulkSuccess();
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);

        // Every round asks for the next batch from the start, with no paging offset
        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client, times(2)).search(searchCaptor.capture(), any());
        for (SearchRequest searchRequest : searchCaptor.getAllValues()) {
            assertEquals(-1, searchRequest.source().from());
            assertEquals(300, searchRequest.source().size());
            assertFalse(searchRequest.source().fetchSource().fetchSource());
        }
        ArgumentCaptor<BulkRequest> bulkCaptor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(2)).bulk(bulkCaptor.capture(), any());
        assertEquals(300, bulkCaptor.getAllValues().get(0).numberOfActions());
        assertEquals(2, bulkCaptor.getAllValues().get(1).numberOfActions());
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(deleteConversationListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue());
    }

    public void testDelete_PartialBulkFailure_KeepsDeletingThenReturnsFalse() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        setupRefreshSuccess();
        // One item of the first full batch fails; the rest of the conversation is still deleted
        List<String> fullBatch = IntStream.range(0, 300).mapToObj(i -> "id-" + i).collect(Collectors.toList());
        setupDeleteSearchResponses(List.of(fullBatch, List.of("id-0", "id-300")));
        setupBulkResponses(Set.of("id-0"));
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);

        verify(client, times(2)).search(any(), any());
        verify(client, times(2)).bulk(any(), any());
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(deleteConversationListener, times(1)).onResponse(argCaptor.capture());
        assert (!argCaptor.getValue());
    }

    public void testDelete_NoProgress_StopsAndReturnsFalse() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        setupRefreshSuccess();
        // A full batch where every delete fails must not loop forever
        List<String> fullBatch = IntStream.range(0, 300).mapToObj(i -> "id-" + i).collect(Collectors.toList());
        setupDeleteSearchResponses(List.of(fullBatch, fullBatch));
        setupBulkResponses(Set.copyOf(fullBatch));
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);

        verify(client, times(1)).search(any(), any());
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(deleteConversationListener, times(1)).onResponse(argCaptor.capture());
        assert (!argCaptor.getValue());
    }

    public void testDelete_NoAccessNoUser_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupDenyAccess(null);
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor
            .getValue()
            .getMessage()
            .equals("User [" + ActionConstants.DEFAULT_USERNAME_FOR_ERRORS + "] does not have access to memory cid"));
    }

    public void testDelete_NoAccessWithUser_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupDenyAccess("user");
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("User [user] does not have access to memory cid"));
    }

    public void testDelete_AccessFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onFailure(new Exception("Access Failure"));
            return null;
        }).when(conversationMetaIndex).checkAccess(anyString(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Access Failure"));
    }

    public void testDelete_MainFailure_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        doThrow(new RuntimeException("Test Failure")).when(conversationMetaIndex).checkAccess(anyString(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test Failure"));
    }

    public void testSearch_AggregationWithoutQuery_KeepsConversationFilter() {
        setupGrantAccess();
        setupRefreshSuccess();
        SearchRequest request = new SearchRequest()
            .source(new SearchSourceBuilder().size(0).aggregation(AggregationBuilders.terms("by_origin").field("origin")));
        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> al = invocation.getArgument(1);
            al.onResponse(mock(SearchResponse.class));
            return null;
        }).when(client).search(requestCaptor.capture(), any());
        @SuppressWarnings("unchecked")
        ActionListener<SearchResponse> searchListener = mock(ActionListener.class);

        interactionsIndex.searchInteractions("test_cid", request, searchListener);

        verify(searchListener, times(1)).onResponse(any());
        verify(searchListener, never()).onFailure(any());
        SearchSourceBuilder sent = requestCaptor.getValue().source();
        BoolQueryBuilder query = (BoolQueryBuilder) sent.query();
        assertEquals(
            List.of(new TermQueryBuilder(ConversationalIndexConstants.INTERACTIONS_CONVERSATION_ID_FIELD, "test_cid")),
            query.must()
        );
        assertEquals(1, sent.aggregations().count());
    }

    public void testSearch_RefreshFails_ThenFail() {
        setupGrantAccess();
        SearchRequest request = dummyRequest();
        final String cid = "test_id";
        doAnswer(invocation -> {
            ActionListener<RefreshResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Failed during Search Refresh"));
            return null;
        }).when(indicesAdminClient).refresh(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<SearchResponse> searchInteractionsListener = mock(ActionListener.class);
        interactionsIndex.searchInteractions(cid, request, searchInteractionsListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(searchInteractionsListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failed during Search Refresh"));
    }

    public void testSearch_ClientFails_ThenFail() {
        setupGrantAccess();
        SearchRequest request = dummyRequest();
        final String cid = "test_cid";
        doThrow(new RuntimeException("Client Failure in Search Interactions")).when(client).admin();
        @SuppressWarnings("unchecked")
        ActionListener<SearchResponse> searchInteractionsListener = mock(ActionListener.class);
        interactionsIndex.searchInteractions(cid, request, searchInteractionsListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(searchInteractionsListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Client Failure in Search Interactions"));
    }

    public void testSearch_NoAccess_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupDenyAccess("user");
        SearchRequest request = dummyRequest();
        final String cid = "test_cid";
        @SuppressWarnings("unchecked")
        ActionListener<SearchResponse> searchInteractionsListener = mock(ActionListener.class);
        interactionsIndex.searchInteractions(cid, request, searchInteractionsListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(searchInteractionsListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("User [user] does not have access to memory test_cid"));
    }

    public void testGetSg_NoIndex_ThenFail() {
        doReturn(false).when(metadata).hasIndex(anyString());
        @SuppressWarnings("unchecked")
        ActionListener<Interaction> getListener = mock(ActionListener.class);
        interactionsIndex.getInteraction("iid", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor
            .getValue()
            .getMessage()
            .equals("no such index [.plugins-ml-memory-message] and cannot get message since the messages index does not exist"));
    }

    public void testGetSg_InteractionNotExist_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        setupRefreshSuccess();
        GetResponse response = mock(GetResponse.class);
        doReturn(false).when(response).isExists();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Interaction> getListener = mock(ActionListener.class);
        interactionsIndex.getInteraction("iid", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Message [iid] not found"));
    }

    public void testGetSg_WrongId_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        setupRefreshSuccess();
        GetResponse response = mock(GetResponse.class);
        doReturn(true).when(response).isExists();
        doReturn("wrong id").when(response).getId();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Interaction> getListener = mock(ActionListener.class);
        interactionsIndex.getInteraction("iid", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Message [iid] not found"));
    }

    public void testGetSg_RefreshFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        doAnswer(invocation -> {
            ActionListener<RefreshResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Failed during Sg Get Refresh"));
            return null;
        }).when(indicesAdminClient).refresh(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Interaction> getListener = mock(ActionListener.class);
        interactionsIndex.getInteraction("iid", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failed during Sg Get Refresh"));
    }

    public void testGetSg_ClientFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupGrantAccess();
        doThrow(new RuntimeException("Client Failure in Sg Get")).when(client).admin();
        @SuppressWarnings("unchecked")
        ActionListener<Interaction> getListener = mock(ActionListener.class);
        interactionsIndex.getInteraction("iid", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Client Failure in Sg Get"));
    }

    public void testGetSg_NoAccess_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupDenyAccess("Henry");
        setupRefreshSuccess();
        GetResponse response = setUpInteractionResponse("iid");
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        ActionListener<Interaction> getListener = mock(ActionListener.class);
        interactionsIndex.getInteraction("iid", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("User [Henry] does not have access to message iid"));
    }

    public void testGetSg_GrantAccess_Success() {
        setupGrantAccess();
        doReturn(true).when(metadata).hasIndex(anyString());
        setupRefreshSuccess();
        GetResponse response = setUpInteractionResponse("iid");
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        ActionListener<Interaction> getListener = mock(ActionListener.class);
        interactionsIndex.getInteraction("iid", getListener);
        ArgumentCaptor<Interaction> argCaptor = ArgumentCaptor.forClass(Interaction.class);
        verify(getListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue().getId().equals("iid"));
        assert (argCaptor.getValue().getConversationId().equals("conversation test 1"));
    }

    public void testGetTraces_NoAccess_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupRefreshSuccess();
        setupDenyAccess("Xun");
        GetResponse response = setUpInteractionResponse("iid");
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        ActionListener<List<Interaction>> getListener = mock(ActionListener.class);
        interactionsIndex.getTraces("iid", 0, 10, getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("User [Xun] does not have access to message iid"));
    }

    public void testUpdateInteraction_NoAccess_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupRefreshSuccess();
        setupDenyAccess("Xun");
        GetResponse response = setUpInteractionResponse("iid");
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        ActionListener<UpdateResponse> updateListener = mock(ActionListener.class);
        interactionsIndex.updateInteraction("iid", new UpdateRequest(), updateListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(updateListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("User [Xun] does not have access to message iid"));
    }

    public void testUpdateInteraction_Success() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupRefreshSuccess();
        setupGrantAccess();
        GetResponse response = setUpInteractionResponse("iid");
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            UpdateResponse updateResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED);
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        ActionListener<UpdateResponse> updateListener = mock(ActionListener.class);
        interactionsIndex.updateInteraction("iid", new UpdateRequest(), updateListener);
        ArgumentCaptor<UpdateResponse> argCaptor = ArgumentCaptor.forClass(UpdateResponse.class);
        verify(updateListener, times(1)).onResponse(argCaptor.capture());
    }

    private GetResponse setUpInteractionResponse(String interactionId) {
        @SuppressWarnings("unchecked")
        GetResponse response = mock(GetResponse.class);
        doReturn(true).when(response).isExists();
        doReturn(interactionId).when(response).getId();
        doReturn(
            Map
                .of(
                    ConversationalIndexConstants.INTERACTIONS_CREATE_TIME_FIELD,
                    Instant.now().toString(),
                    ConversationalIndexConstants.INTERACTIONS_CONVERSATION_ID_FIELD,
                    "conversation test 1",
                    ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD,
                    "answer1"
                )
        ).when(response).getSourceAsMap();
        return response;
    }

    private void setUpSearchTraceResponse() {
        doAnswer(invocation -> {
            XContentBuilder content = XContentBuilder.builder(XContentType.JSON.xContent());
            content.startObject();
            content.field(ConversationalIndexConstants.INTERACTIONS_CREATE_TIME_FIELD, Instant.now());
            content.field(ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD, "sample inputs");
            content.field(ConversationalIndexConstants.INTERACTIONS_CONVERSATION_ID_FIELD, "conversation-id");
            content.endObject();

            SearchHit[] hits = new SearchHit[1];
            hits[0] = new SearchHit(0, "iId", null, null).sourceRef(BytesReference.bytes(content));
            SearchHits searchHits = new SearchHits(hits, null, Float.NaN);
            SearchResponseSections searchSections = new SearchResponseSections(
                searchHits,
                InternalAggregations.EMPTY,
                null,
                false,
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
            ActionListener<SearchResponse> al = invocation.getArgument(1);
            al.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());
    }

    private void setupBulkSuccess() {
        setupBulkResponses(Set.of());
    }

    /**
     * Each bulk deletes every requested id, except the ids in failedIds, which come back as failed items
     */
    private void setupBulkResponses(Set<String> failedIds) {
        doAnswer(invocation -> {
            BulkRequest request = invocation.getArgument(0);
            BulkItemResponse[] items = new BulkItemResponse[request.numberOfActions()];
            for (int i = 0; i < items.length; i++) {
                String id = request.requests().get(i).id();
                items[i] = failedIds.contains(id)
                    ? new BulkItemResponse(
                        i,
                        DocWriteRequest.OpType.DELETE,
                        new BulkItemResponse.Failure(INTERACTIONS_INDEX_NAME, id, new Exception("delete failed"))
                    )
                    : new BulkItemResponse(
                        i,
                        DocWriteRequest.OpType.DELETE,
                        new DeleteResponse(new ShardId(INTERACTIONS_INDEX_NAME, "uuid", 0), id, 1, 1, 1, true)
                    );
            }
            ActionListener<BulkResponse> al = invocation.getArgument(1);
            al.onResponse(new BulkResponse(items, 1));
            return null;
        }).when(client).bulk(any(), any());
    }

    /**
     * Each call to search returns the next list of document ids as hits
     */
    private void setupDeleteSearchResponses(List<List<String>> batches) {
        AtomicInteger call = new AtomicInteger();
        doAnswer(invocation -> {
            List<String> ids = batches.get(call.getAndIncrement());
            SearchHit[] hits = ids.stream().map(id -> new SearchHit(0, id, null, null)).toArray(SearchHit[]::new);
            SearchHits searchHits = new SearchHits(hits, null, Float.NaN);
            SearchResponseSections sections = new SearchResponseSections(
                searchHits,
                InternalAggregations.EMPTY,
                null,
                false,
                false,
                null,
                1
            );
            SearchResponse searchResponse = new SearchResponse(
                sections,
                null,
                1,
                1,
                0,
                11,
                ShardSearchFailure.EMPTY_ARRAY,
                SearchResponse.Clusters.EMPTY
            );
            ActionListener<SearchResponse> al = invocation.getArgument(1);
            al.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());
    }
}
