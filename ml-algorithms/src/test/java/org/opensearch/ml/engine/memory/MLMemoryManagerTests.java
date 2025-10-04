/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.memory.action.conversation.CreateConversationAction;
import org.opensearch.ml.memory.action.conversation.CreateConversationRequest;
import org.opensearch.ml.memory.action.conversation.CreateConversationResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionAction;
import org.opensearch.ml.memory.action.conversation.CreateInteractionRequest;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.memory.action.conversation.GetTracesAction;
import org.opensearch.ml.memory.action.conversation.GetTracesRequest;
import org.opensearch.ml.memory.action.conversation.GetTracesResponse;
import org.opensearch.ml.memory.action.conversation.UpdateInteractionAction;
import org.opensearch.ml.memory.action.conversation.UpdateInteractionRequest;
import org.opensearch.ml.memory.index.ConversationMetaIndex;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.IndicesAdminClient;

public class MLMemoryManagerTests {

    @Mock
    Client client;

    @Mock
    ClusterService clusterService;

    @Mock
    ClusterState clusterState;

    @Mock
    MLMemoryManager mlMemoryManager;

    @Mock
    ConversationMetaIndex conversationMetaIndex;

    @Mock
    Metadata metadata;

    @Mock
    AdminClient adminClient;

    @Mock
    IndicesAdminClient indicesAdminClient;

    @Mock
    ThreadPool threadPool;

    @Mock
    ActionListener<CreateConversationResponse> createConversationResponseActionListener;

    @Mock
    ActionListener<CreateInteractionResponse> createInteractionResponseActionListener;

    @Mock
    ActionListener<List<Interaction>> interactionListActionListener;

    @Mock
    ActionListener<UpdateResponse> updateResponseActionListener;

    @Mock
    ActionListener<Boolean> deletionInteractionListener;

    String conversationName;
    String applicationType;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mlMemoryManager = new MLMemoryManager(client, clusterService, conversationMetaIndex);
        conversationName = "new conversation";
        applicationType = "ml application";
        doReturn(clusterState).when(clusterService).state();
        doReturn(metadata).when(clusterState).metadata();
        doReturn(adminClient).when(client).admin();
        doReturn(indicesAdminClient).when(adminClient).indices();
        doReturn(threadPool).when(client).threadPool();
        doReturn(new ThreadContext(Settings.EMPTY)).when(threadPool).getThreadContext();
    }

    @Test
    public void testCreateConversation() {
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "user:admin");
        doReturn(threadContext).when(threadPool).getThreadContext();

        ArgumentCaptor<CreateConversationRequest> captor = ArgumentCaptor.forClass(CreateConversationRequest.class);
        doAnswer(invocation -> {
            ActionListener<CreateConversationResponse> al = invocation.getArgument(2);
            al.onResponse(new CreateConversationResponse("conversation-id"));
            return null;
        }).when(client).execute(any(), any(), any());

        mlMemoryManager.createConversation(conversationName, applicationType, createConversationResponseActionListener);

        verify(client, times(1))
            .execute(eq(CreateConversationAction.INSTANCE), captor.capture(), eq(createConversationResponseActionListener));
        assertEquals(conversationName, captor.getValue().getName());
        assertEquals(applicationType, captor.getValue().getApplicationType());
    }

    @Test
    public void testCreateConversationFails_thenFail() {
        doThrow(new RuntimeException("Failure in runtime")).when(client).execute(any(), any(), any());
        mlMemoryManager.createConversation(conversationName, applicationType, createConversationResponseActionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createConversationResponseActionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failure in runtime"));
    }

    @Test
    public void testCreateInteraction() {
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "user:admin");
        doReturn(threadContext).when(threadPool).getThreadContext();

        ArgumentCaptor<CreateInteractionRequest> captor = ArgumentCaptor.forClass(CreateInteractionRequest.class);
        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> al = invocation.getArgument(2);
            al.onResponse(new CreateInteractionResponse("interaction-id"));
            return null;
        }).when(client).execute(any(), any(), any());

        mlMemoryManager
            .createInteraction(
                "conversationId",
                "input",
                "prompt",
                "response",
                "origin",
                Collections.singletonMap("feedback", "thumbsup"),
                "parent-id",
                1,
                createInteractionResponseActionListener
            );
        verify(client, times(1))
            .execute(eq(CreateInteractionAction.INSTANCE), captor.capture(), eq(createInteractionResponseActionListener));
        assertEquals("conversationId", captor.getValue().getConversationId());
        assertEquals("input", captor.getValue().getInput());
        assertEquals("prompt", captor.getValue().getPromptTemplate());
        assertEquals("response", captor.getValue().getResponse());
        assertEquals("origin", captor.getValue().getOrigin());
        assertEquals(Collections.singletonMap("feedback", "thumbsup"), captor.getValue().getAdditionalInfo());
        assertEquals("parent-id", captor.getValue().getParentIid());
        assertEquals("1", captor.getValue().getTraceNumber().toString());
    }

    @Test
    public void testCreateInteractionFails_thenFail() {
        doThrow(new RuntimeException("Failure in runtime")).when(client).execute(any(), any(), any());
        mlMemoryManager
            .createInteraction(
                "conversationId",
                "input",
                "prompt",
                "response",
                "origin",
                Collections.singletonMap("feedback", "thumbsup"),
                "parent-id",
                1,
                createInteractionResponseActionListener
            );
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(createInteractionResponseActionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failure in runtime"));
    }

    @Test
    public void testGetInteractions_NoIndex_ThenEmpty() {
        doReturn(false).when(metadata).hasIndex(anyString());

        mlMemoryManager.getFinalInteractions("cid", 10, interactionListActionListener);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Interaction>> argCaptor = ArgumentCaptor.forClass(List.class);
        verify(interactionListActionListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue().size() == 0);
    }

    @Test
    public void testGetInteractions_SearchFails_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(true);
            return null;
        }).when(conversationMetaIndex).checkAccess(anyString(), any());

        doAnswer(invocation -> {
            ActionListener<SearchResponse> al = invocation.getArgument(1);
            al.onFailure(new Exception("Failure in Search"));
            return null;
        }).when(client).search(any(), any());
        mlMemoryManager.getFinalInteractions("cid", 10, interactionListActionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(interactionListActionListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failure in Search"));
    }

    @Test
    public void testGetInteractions_NoAccessNoUser_ThenFail() {
        doReturn(true).when(metadata).hasIndex(anyString());
        String userStr = "";
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
        mlMemoryManager.getFinalInteractions("cid", 10, interactionListActionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(interactionListActionListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("User [] does not have access to conversation cid"));
    }

    @Test
    public void testGetInteractions_Success() {
        doReturn(true).when(metadata).hasIndex(anyString());
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(true);
            return null;
        }).when(conversationMetaIndex).checkAccess(anyString(), any());

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

        mlMemoryManager.getFinalInteractions("cid", 10, interactionListActionListener);
        ArgumentCaptor<List<Interaction>> argCaptor = ArgumentCaptor.forClass(List.class);
        verify(interactionListActionListener, times(1)).onResponse(argCaptor.capture());
        assertEquals(1, argCaptor.getValue().size());
    }

    @Test
    public void testGetInteractionFails_thenFail() {
        doThrow(new RuntimeException("Failure in runtime")).when(threadPool).getThreadContext();
        mlMemoryManager.getFinalInteractions("cid", 10, interactionListActionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(interactionListActionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failure in runtime"));
    }

    @Test
    public void testGetTraces() {
        List<Interaction> traces = List
            .of(
                new Interaction(
                    "id0",
                    Instant.now(),
                    Instant.now(),
                    "cid",
                    "input",
                    "pt",
                    "response",
                    "origin",
                    Collections.singletonMap("metadata", "some meta"),
                    "parent_id",
                    1
                )
            );
        ArgumentCaptor<GetTracesRequest> captor = ArgumentCaptor.forClass(GetTracesRequest.class);
        doAnswer(invocation -> {
            ActionListener<GetTracesResponse> al = invocation.getArgument(2);
            GetTracesResponse getTracesResponse = new GetTracesResponse(traces, 4, false);
            al.onResponse(getTracesResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        mlMemoryManager.getTraces("iid", interactionListActionListener);

        verify(client, times(1)).execute(eq(GetTracesAction.INSTANCE), captor.capture(), any());
        assertEquals("iid", captor.getValue().getInteractionId());
        assertEquals(0, captor.getValue().getFrom());
        assertEquals(10, captor.getValue().getMaxResults());
    }

    @Test
    public void testGetTracesFails_thenFail() {
        doThrow(new RuntimeException("Failure in runtime")).when(client).execute(any(), any(), any());
        mlMemoryManager.getTraces("cid", interactionListActionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(interactionListActionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failure in runtime"));
    }

    @Test
    public void testUpdateInteraction() {
        Map<String, Object> updateContent = Map
            .of(
                INTERACTIONS_ADDITIONAL_INFO_FIELD,
                Map.of("feedback", "thumbs up!"),
                INTERACTIONS_RESPONSE_FIELD,
                "response",
                INTERACTIONS_INPUT_FIELD,
                "input"
            );
        ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
        UpdateResponse updateResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED);

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> al = invocation.getArgument(2);
            al.onResponse(updateResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        ArgumentCaptor<UpdateInteractionRequest> captor = ArgumentCaptor.forClass(UpdateInteractionRequest.class);
        mlMemoryManager.updateInteraction("iid", updateContent, updateResponseActionListener);
        verify(client, times(1)).execute(eq(UpdateInteractionAction.INSTANCE), captor.capture(), any());
        assertEquals("iid", captor.getValue().getInteractionId());
        assertEquals(2, captor.getValue().getUpdateContent().keySet().size());
        assertNotEquals(updateContent, captor.getValue().getUpdateContent());
    }

    @Test
    public void testUpdateInteraction_thenFail() {
        doThrow(new RuntimeException("Failure in runtime")).when(client).execute(any(), any(), any());
        mlMemoryManager
            .updateInteraction(
                "iid",
                Map.of(INTERACTIONS_ADDITIONAL_INFO_FIELD, Map.of("feedback", "thumbs up!")),
                updateResponseActionListener
            );
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(updateResponseActionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failure in runtime"));
    }

    @Test
    public void testBuildTraceQuery() {
        QueryBuilder queryBuilder = mlMemoryManager.buildDeleteInteractionQuery("interaction-id-1");
        String query = Strings.toString(XContentType.JSON, queryBuilder);
        Assert
            .assertEquals(
                "{\"bool\":{\"should\":[{\"ids\":{\"values\":[\"interaction-id-1\"],\"boost\":1.0}},{\"bool\":{\"must\":[{\"exists\":{\"field\":\"trace_number\",\"boost\":1.0}},{\"term\":{\"parent_message_id\":{\"value\":\"interaction-id-1\",\"boost\":1.0}}}],\"adjust_pure_negative\":true,\"boost\":1.0}}],\"adjust_pure_negative\":true,\"boost\":1.0}}",
                query
            );
    }

    @Test
    public void testDeleteInteraction() {
        Mockito.doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            BulkByScrollResponse bulkByScrollResponse = Mockito.mock(BulkByScrollResponse.class);
            Mockito.when(bulkByScrollResponse.getBulkFailures()).thenReturn(List.of());
            Mockito.when(bulkByScrollResponse.getSearchFailures()).thenReturn(List.of());
            listener.onResponse(bulkByScrollResponse);
            return null;
        }).when(client).execute(Mockito.eq(DeleteByQueryAction.INSTANCE), Mockito.any(DeleteByQueryRequest.class), Mockito.any());

        mlMemoryManager.deleteInteractionAndTrace("test-interaction", deletionInteractionListener);
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        Mockito.verify(deletionInteractionListener, times(1)).onResponse(argumentCaptor.capture());
        Assert.assertTrue(argumentCaptor.getValue());
    }

    @Test
    public void testDeleteInteractionFailed() {
        Mockito.doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            BulkByScrollResponse bulkByScrollResponse = Mockito.mock(BulkByScrollResponse.class);
            Mockito.when(bulkByScrollResponse.getBulkFailures()).thenReturn(List.of(Mockito.mock(BulkItemResponse.Failure.class)));
            Mockito.when(bulkByScrollResponse.getSearchFailures()).thenReturn(List.of());
            listener.onResponse(bulkByScrollResponse);
            return null;
        }).when(client).execute(Mockito.eq(DeleteByQueryAction.INSTANCE), Mockito.any(DeleteByQueryRequest.class), Mockito.any());

        mlMemoryManager.deleteInteractionAndTrace("test-interaction", deletionInteractionListener);
        ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        Mockito.verify(deletionInteractionListener, times(1)).onResponse(argumentCaptor.capture());
        Assert.assertFalse(argumentCaptor.getValue());
    }

    @Test
    public void testDeleteInteractionException() {
        Mockito.doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IndexNotFoundException("test-index"));
            return null;
        }).when(client).execute(Mockito.eq(DeleteByQueryAction.INSTANCE), Mockito.any(DeleteByQueryRequest.class), Mockito.any());

        mlMemoryManager.deleteInteractionAndTrace("test-interaction", deletionInteractionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        Mockito.verify(deletionInteractionListener, times(1)).onFailure(argumentCaptor.capture());
        Assert.assertTrue(argumentCaptor.getValue() instanceof IndexNotFoundException);
    }
}
