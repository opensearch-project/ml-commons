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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opensearch.OpenSearchWrapperException;
import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.admin.indices.refresh.RefreshResponse;
import org.opensearch.action.delete.DeleteResponse;
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
import org.opensearch.ml.common.conversation.ConversationMeta;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.SendRequestTransportException;

public class ConversationMetaIndexTests extends OpenSearchTestCase {
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

    ConversationMetaIndex conversationMetaIndex;

    @Before
    public void setup() {
        this.client = mock(Client.class);
        this.clusterService = mock(ClusterService.class);
        this.clusterState = mock(ClusterState.class);
        this.metadata = mock(Metadata.class);
        this.adminClient = mock(AdminClient.class);
        this.indicesAdminClient = mock(IndicesAdminClient.class);
        this.threadPool = mock(ThreadPool.class);

        doReturn(clusterState).when(clusterService).state();
        doReturn(metadata).when(clusterState).metadata();
        doReturn(adminClient).when(client).admin();
        doReturn(indicesAdminClient).when(adminClient).indices();
        doReturn(threadPool).when(client).threadPool();
        doReturn(new ThreadContext(Settings.EMPTY)).when(threadPool).getThreadContext();
        conversationMetaIndex = spy(new ConversationMetaIndex(client, clusterService));
    }

    private void setupDoesNotMakeIndex() {
        doReturn(false).when(metadata).hasIndex(anyString());
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> al = invocation.getArgument(1);
            al.onResponse(new CreateIndexResponse(false, false, "some-other-index-entirely"));
            return null;
        }).when(indicesAdminClient).create(any(), any());
    }

    private void setupRefreshSuccess() {
        doAnswer(invocation -> {
            ActionListener<RefreshResponse> al = invocation.getArgument(1);
            al.onResponse(mock(RefreshResponse.class));
            return null;
        }).when(indicesAdminClient).refresh(any(), any());
    }

    private void blanketGrantAccess() {
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(true);
            return null;
        }).when(conversationMetaIndex).checkAccess(any(), any());
    }

    private void setupUser(String user) {
        String userstr = user == null ? "" : user + "||";
        doAnswer(invocation -> {
            ThreadContext tc = new ThreadContext(Settings.EMPTY);
            tc.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, userstr);
            return tc;
        }).when(threadPool).getThreadContext();
    }

    private SearchRequest dummyRequest() {
        SearchRequest request = new SearchRequest();
        request.source(new SearchSourceBuilder());
        request.source().query(new MatchAllQueryBuilder());
        return request;
    }

    public void testInit_DoesNotCreateIndex() {
        setupDoesNotMakeIndex();
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> createIndexListener = mock(ActionListener.class);
        conversationMetaIndex.initConversationMetaIndexIfAbsent(createIndexListener);
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
        conversationMetaIndex.initConversationMetaIndexIfAbsent(createIndexListener);
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
        conversationMetaIndex.initConversationMetaIndexIfAbsent(createIndexListener);
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
        conversationMetaIndex.initConversationMetaIndexIfAbsent(createIndexListener);
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
        conversationMetaIndex.initConversationMetaIndexIfAbsent(createIndexListener);
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
        conversationMetaIndex.initConversationMetaIndexIfAbsent(createIndexListener);
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
        conversationMetaIndex.initConversationMetaIndexIfAbsent(createIndexListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createIndexListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test Client Failure"));
    }

    public void testCreate_DoesntMakeIndex_ThenFail() {
        setupDoesNotMakeIndex();
        @SuppressWarnings("unchecked")
        ActionListener<String> createConversationListener = mock(ActionListener.class);
        conversationMetaIndex.createConversation(createConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failed to add conversation due to missing index"));
    }

    public void testCreate_BadRestStatus_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        IndexResponse response = mock(IndexResponse.class);
        doReturn(RestStatus.GONE).when(response).status();
        doAnswer(invocation -> {
            ActionListener<IndexResponse> al = invocation.getArgument(1);
            al.onResponse(response);
            return null;
        }).when(client).index(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<String> createConversationListener = mock(ActionListener.class);
        conversationMetaIndex.createConversation(createConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("failed to create conversation"));
    }

    public void testCreate_InternalFailure_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        doAnswer(invocation -> {
            ActionListener<IndexResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Test Failure"));
            return null;
        }).when(client).index(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<String> createConversationListener = mock(ActionListener.class);
        conversationMetaIndex.createConversation(createConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test Failure"));
    }

    public void testCreate_ClientFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        doThrow(new RuntimeException("Test Failure")).when(client).index(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<String> createConversationListener = mock(ActionListener.class);
        conversationMetaIndex.createConversation(createConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test Failure"));
    }

    public void testCreate_InitFails_ThenFail() {
        doReturn(false).when(metadata).hasIndex(anyString());
        doThrow(new RuntimeException("Test Init Client Failure")).when(indicesAdminClient).create(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<String> createConversationListener = mock(ActionListener.class);
        conversationMetaIndex.createConversation(createConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test Init Client Failure"));
    }

    public void testGet_NoIndex_ThenEmpty() {
        doReturn(false).when(metadata).hasIndex(anyString());
        @SuppressWarnings("unchecked")
        ActionListener<List<ConversationMeta>> getConversationsListener = mock(ActionListener.class);
        conversationMetaIndex.getConversations(10, getConversationsListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ConversationMeta>> argCaptor = ArgumentCaptor.forClass(List.class);
        verify(getConversationsListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue().size() == 0);
    }

    public void testGet_SearchFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        setupRefreshSuccess();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Test Exception"));
            return null;
        }).when(client).search(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<List<ConversationMeta>> getConversationsListener = mock(ActionListener.class);
        conversationMetaIndex.getConversations(10, getConversationsListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getConversationsListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test Exception"));
    }

    public void testGet_RefreshFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        doAnswer(invocation -> {
            ActionListener<RefreshResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Refresh Exception"));
            return null;
        }).when(indicesAdminClient).refresh(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<List<ConversationMeta>> getConversationsListener = mock(ActionListener.class);
        conversationMetaIndex.getConversations(10, getConversationsListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getConversationsListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Refresh Exception"));
    }

    public void testGet_ClientFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        doThrow(new RuntimeException("Refresh Client Failure")).when(indicesAdminClient).refresh(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<List<ConversationMeta>> getConversationsListener = mock(ActionListener.class);
        conversationMetaIndex.getConversations(10, getConversationsListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getConversationsListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Refresh Client Failure"));
    }

    public void testDelete_NoIndex_ThenReturnTrue() {
        doReturn(false).when(metadata).hasIndex(anyString());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        conversationMetaIndex.deleteConversation("test-id", deleteConversationListener);
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(deleteConversationListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue());
    }

    public void testDelete_RestNotFoundStatus_ThenReturnTrue() {
        doReturn(true).when(metadata).hasIndex(anyString());
        blanketGrantAccess();
        DeleteResponse response = mock(DeleteResponse.class);
        doReturn(RestStatus.NOT_FOUND).when(response).status();
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> al = invocation.getArgument(1);
            al.onResponse(response);
            return null;
        }).when(client).delete(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        conversationMetaIndex.deleteConversation("test-id", deleteConversationListener);
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(deleteConversationListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue());
    }

    public void testDelete_BadResponse_ThenReturnFalse() {
        doReturn(true).when(metadata).hasIndex(anyString());
        blanketGrantAccess();
        DeleteResponse response = mock(DeleteResponse.class);
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> al = invocation.getArgument(1);
            al.onResponse(response);
            return null;
        }).when(client).delete(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        conversationMetaIndex.deleteConversation("test-id", deleteConversationListener);
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(deleteConversationListener, times(1)).onResponse(argCaptor.capture());
        assert (!argCaptor.getValue());
    }

    public void testDelete_DeleteFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        blanketGrantAccess();
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Test Fail in Delete"));
            return null;
        }).when(client).delete(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        conversationMetaIndex.deleteConversation("test-id", deleteConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test Fail in Delete"));
    }

    public void testDelete_ClientFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        blanketGrantAccess();
        doThrow(new RuntimeException("Client Fail in Delete")).when(client).delete(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteConversationListener = mock(ActionListener.class);
        conversationMetaIndex.deleteConversation("test-id", deleteConversationListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deleteConversationListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Client Fail in Delete"));
    }

    public void testCheckAccess_DoesNotExist_ThenFail() {
        setupUser("user");
        setupRefreshSuccess();
        doReturn(true).when(metadata).hasIndex(anyString());
        GetResponse response = mock(GetResponse.class);
        doReturn(false).when(response).isExists();
        doAnswer(invocation -> {
            ActionListener<GetResponse> al = invocation.getArgument(1);
            al.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> accessListener = mock(ActionListener.class);
        conversationMetaIndex.checkAccess("test id", accessListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(accessListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue() instanceof ResourceNotFoundException);
        assert (argCaptor.getValue().getMessage().equals("Conversation [test id] not found"));
    }

    public void testCheckAccess_WrongId_ThenFail() {
        setupUser("user");
        setupRefreshSuccess();
        doReturn(true).when(metadata).hasIndex(anyString());
        GetResponse response = mock(GetResponse.class);
        doReturn(true).when(response).isExists();
        doReturn("wrong id").when(response).getId();
        doAnswer(invocation -> {
            ActionListener<GetResponse> al = invocation.getArgument(1);
            al.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> accessListener = mock(ActionListener.class);
        conversationMetaIndex.checkAccess("test id", accessListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(accessListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue() instanceof ResourceNotFoundException);
        assert (argCaptor.getValue().getMessage().equals("Conversation [test id] not found"));
    }

    public void testCheckAccess_GetFails_ThenFail() {
        setupUser("user");
        setupRefreshSuccess();
        doReturn(true).when(metadata).hasIndex(anyString());
        doAnswer(invocation -> {
            ActionListener<GetResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Test Fail"));
            return null;
        }).when(client).get(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> accessListener = mock(ActionListener.class);
        conversationMetaIndex.checkAccess("test id", accessListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(accessListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test Fail"));
    }

    public void testCheckAccess_ClientFails_ThenFail() {
        setupUser("user");
        setupRefreshSuccess();
        doReturn(true).when(metadata).hasIndex(anyString());
        doThrow(new RuntimeException("Client Test Fail")).when(client).admin();
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> accessListener = mock(ActionListener.class);
        conversationMetaIndex.checkAccess("test id", accessListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(accessListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Client Test Fail"));
    }

    public void testCheckAccess_EmptyStringUser_ThenReturnTrue() {
        setupUser(null);
        setupRefreshSuccess();
        doReturn(true).when(metadata).hasIndex(anyString());
        final String id = "test_id";
        GetResponse dummyGetResponse = mock(GetResponse.class);
        doReturn(true).when(dummyGetResponse).isExists();
        doReturn(id).when(dummyGetResponse).getId();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(dummyGetResponse);
            return null;
        }).when(client).get(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> accessListener = mock(ActionListener.class);
        conversationMetaIndex.checkAccess(id, accessListener);
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(accessListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue());
    }

    public void testCheckAccess_RefreshFails_ThenFail() {
        setupUser("user");
        doReturn(true).when(metadata).hasIndex(anyString());
        doAnswer(invocation -> {
            ActionListener<RefreshResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Refresh Exception"));
            return null;
        }).when(indicesAdminClient).refresh(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> accessListener = mock(ActionListener.class);
        conversationMetaIndex.checkAccess("test id", accessListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(accessListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Refresh Exception"));
    }

    public void testSearchConversations_RefreshFails_ThenFail() {
        SearchRequest request = dummyRequest();
        doAnswer(invocation -> {
            ActionListener<RefreshResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Refresh Exception"));
            return null;
        }).when(indicesAdminClient).refresh(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<SearchResponse> searchConversationsListener = mock(ActionListener.class);
        conversationMetaIndex.searchConversations(request, searchConversationsListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(searchConversationsListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Refresh Exception"));
    }

    public void testSearchConversations_ClientFails_ThenFail() {
        SearchRequest request = dummyRequest();
        doReturn(true).when(metadata).hasIndex(anyString());
        doThrow(new RuntimeException("Client Test Fail")).when(client).admin();
        @SuppressWarnings("unchecked")
        ActionListener<SearchResponse> accessListener = mock(ActionListener.class);
        conversationMetaIndex.searchConversations(request, accessListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(accessListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Client Test Fail"));
    }

    public void testGetConversation_NoIndex_ThenFail() {
        doReturn(false).when(metadata).hasIndex(anyString());
        @SuppressWarnings("unchecked")
        ActionListener<ConversationMeta> getListener = mock(ActionListener.class);
        conversationMetaIndex.getConversation("tester_id", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor
            .getValue()
            .getMessage()
            .equals("no such index [.plugins-ml-memory-meta] and cannot get conversation since the conversation index does not exist"));
    }

    public void testGetConversation_ResponseNotExist_ThenFail() {
        setupRefreshSuccess();
        doReturn(true).when(metadata).hasIndex(anyString());
        GetResponse response = mock(GetResponse.class);
        doReturn(false).when(response).isExists();
        doAnswer(invocation -> {
            ActionListener<GetResponse> al = invocation.getArgument(1);
            al.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<ConversationMeta> getListener = mock(ActionListener.class);
        conversationMetaIndex.getConversation("tester_id", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Conversation [tester_id] not found"));
    }

    public void testGetConversation_WrongId_ThenFail() {
        setupRefreshSuccess();
        doReturn(true).when(metadata).hasIndex(anyString());
        GetResponse response = mock(GetResponse.class);
        doReturn(true).when(response).isExists();
        doReturn("wrong id").when(response).getId();
        doAnswer(invocation -> {
            ActionListener<GetResponse> al = invocation.getArgument(1);
            al.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<ConversationMeta> getListener = mock(ActionListener.class);
        conversationMetaIndex.getConversation("tester_id", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Conversation [tester_id] not found"));
    }

    public void testGetConversation_RefreshFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        doAnswer(invocation -> {
            ActionListener<RefreshResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Refresh Exception"));
            return null;
        }).when(indicesAdminClient).refresh(any(), any());
        @SuppressWarnings("unchecked")
        ActionListener<ConversationMeta> getListener = mock(ActionListener.class);
        conversationMetaIndex.getConversation("tester_id", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Refresh Exception"));
    }

    public void testGetConversation_ClientFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        doThrow(new RuntimeException("Clietn Failure")).when(client).admin();
        @SuppressWarnings("unchecked")
        ActionListener<ConversationMeta> getListener = mock(ActionListener.class);
        conversationMetaIndex.getConversation("tester_id", getListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(getListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Clietn Failure"));
    }
}
