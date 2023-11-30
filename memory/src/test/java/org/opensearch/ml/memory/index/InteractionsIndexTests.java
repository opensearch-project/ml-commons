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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opensearch.OpenSearchWrapperException;
import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.admin.indices.refresh.RefreshResponse;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.AdminClient;
import org.opensearch.client.Client;
import org.opensearch.client.IndicesAdminClient;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.SendRequestTransportException;

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
        String userstr = user == null ? "" : user + "||";
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(false);
            return null;
        }).when(conversationMetaIndex).checkAccess(anyString(), any());
        doAnswer(invocation -> {
            ThreadContext tc = new ThreadContext(Settings.EMPTY);
            tc.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, userstr);
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
        assert (argCaptor.getValue().getMessage().equals("no index to add conversation to"));
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
        assert (argCaptor.getValue().getMessage().equals("Failed to create interaction"));
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
            .equals("User [" + ActionConstants.DEFAULT_USERNAME_FOR_ERRORS + "] does not have access to conversation cid"));
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
        assert (argCaptor.getValue().getMessage().equals("User [user] does not have access to conversation cid"));
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
            .equals("User [" + ActionConstants.DEFAULT_USERNAME_FOR_ERRORS + "] does not have access to conversation cid"));
    }

    public void testGetAll_BadMaxResults_ThenFail() {
        @SuppressWarnings("unchecked")
        ActionListener<List<Interaction>> getInteractionsListener = mock(ActionListener.class);
        interactionsIndex.nextGetListener("cid", 0, 0, getInteractionsListener, List.of());
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getInteractionsListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("maxResults must be positive"));
    }

    public void testGetAll_Recursion() {
        List<Interaction> interactions = List
            .of(
                new Interaction("iid1", Instant.now(), "cid", "inp", "pt", "rsp", "ogn", Collections.singletonMap("meta", "some meta")),
                new Interaction("iid2", Instant.now(), "cid", "inp", "pt", "rsp", "ogn", Collections.singletonMap("meta", "some meta")),
                new Interaction("iid3", Instant.now(), "cid", "inp", "pt", "rsp", "ogn", Collections.singletonMap("meta", "some meta")),
                new Interaction("iid4", Instant.now(), "cid", "inp", "pt", "rsp", "ogn", Collections.singletonMap("meta", "some meta"))
            );
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> al = invocation.getArgument(3);
            al.onResponse(interactions.subList(0, 2));
            return null;
        }).when(interactionsIndex).innerGetInteractions(anyString(), eq(0), anyInt(), any());
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> al = invocation.getArgument(3);
            al.onResponse(interactions.subList(2, 4));
            return null;
        }).when(interactionsIndex).innerGetInteractions(anyString(), eq(2), anyInt(), any());
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> al = invocation.getArgument(3);
            al.onResponse(List.of());
            return null;
        }).when(interactionsIndex).innerGetInteractions(anyString(), eq(4), anyInt(), any());
        @SuppressWarnings("unchecked")
        ActionListener<List<Interaction>> getInteractionsListener = mock(ActionListener.class);
        interactionsIndex.getAllInteractions("cid", 2, getInteractionsListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Interaction>> argCaptor = ArgumentCaptor.forClass(List.class);
        verify(getInteractionsListener, times(1)).onResponse(argCaptor.capture());
        List<Interaction> result = argCaptor.getValue();
        assert (result.size() == 4);
        assert (result.get(0).getId().equals("iid1"));
        assert (result.get(1).getId().equals("iid2"));
        assert (result.get(2).getId().equals("iid3"));
        assert (result.get(3).getId().equals("iid4"));
    }

    public void testGetAll_GetFails_ThenFail() {
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> al = invocation.getArgument(3);
            al.onFailure(new Exception("Failure in Get"));
            return null;
        }).when(interactionsIndex).innerGetInteractions(anyString(), anyInt(), anyInt(), any());
        @SuppressWarnings("unchecked")
        ActionListener<List<Interaction>> getInteractionsListener = mock(ActionListener.class);
        interactionsIndex.getAllInteractions("cid", 2, getInteractionsListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getInteractionsListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failure in Get"));
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
        BulkResponse bulkResponse = mock(BulkResponse.class);
        doReturn(true).when(bulkResponse).hasFailures();
        doAnswer(invocation -> {
            ActionListener<BulkResponse> al = invocation.getArgument(1);
            al.onResponse(bulkResponse);
            return null;
        }).when(client).bulk(any(), any());
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> al = invocation.getArgument(2);
            al.onResponse(List.of());
            return null;
        }).when(interactionsIndex).getAllInteractions(anyString(), anyInt(), any());
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
        doAnswer(invocation -> {
            ActionListener<BulkResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Failure during Bulk"));
            return null;
        }).when(client).bulk(any(), any());
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> al = invocation.getArgument(2);
            al.onResponse(List.of());
            return null;
        }).when(interactionsIndex).getAllInteractions(anyString(), anyInt(), any());
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
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> al = invocation.getArgument(2);
            al.onFailure(new Exception("Failure during GetAllInteractions"));
            return null;
        }).when(interactionsIndex).getAllInteractions(anyString(), anyInt(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failure during GetAllInteractions"));
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
            .equals("User [" + ActionConstants.DEFAULT_USERNAME_FOR_ERRORS + "] does not have access to conversation cid"));
    }

    public void testDelete_NoAccessWithUser_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupDenyAccess("user");
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        interactionsIndex.deleteConversation("cid", deleteConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("User [user] does not have access to conversation cid"));
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
        assert (argCaptor.getValue().getMessage().equals("User [user] does not have access to conversation test_cid"));
    }

    public void testGetSg_NoIndex_ThenFail() {
        doReturn(false).when(metadata).hasIndex(anyString());
        @SuppressWarnings("unchecked")
        ActionListener<Interaction> getListener = mock(ActionListener.class);
        interactionsIndex.getInteraction("cid", "iid", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor
            .getValue()
            .getMessage()
            .equals("no such index [.plugins-ml-memory-message] and cannot get interaction since the interactions index does not exist"));
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
        interactionsIndex.getInteraction("cid", "iid", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Interaction [iid] not found"));
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
        interactionsIndex.getInteraction("cid", "iid", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Interaction [iid] not found"));
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
        interactionsIndex.getInteraction("cid", "iid", getListener);
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
        interactionsIndex.getInteraction("cid", "iid", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Client Failure in Sg Get"));
    }

    public void testGetSg_NoAccess_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupDenyAccess("Henry");
        @SuppressWarnings("unchecked")
        ActionListener<Interaction> getListener = mock(ActionListener.class);
        interactionsIndex.getInteraction("cid", "iid", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("User [Henry] does not have access to conversation cid"));
    }
}
