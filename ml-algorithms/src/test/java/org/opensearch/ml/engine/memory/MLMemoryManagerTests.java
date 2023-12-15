/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_ADDITIONAL_INFO_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.memory.action.conversation.CreateConversationAction;
import org.opensearch.ml.memory.action.conversation.CreateConversationRequest;
import org.opensearch.ml.memory.action.conversation.CreateConversationResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionAction;
import org.opensearch.ml.memory.action.conversation.CreateInteractionRequest;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.memory.action.conversation.GetInteractionsAction;
import org.opensearch.ml.memory.action.conversation.GetInteractionsRequest;
import org.opensearch.ml.memory.action.conversation.GetInteractionsResponse;
import org.opensearch.ml.memory.action.conversation.GetTracesAction;
import org.opensearch.ml.memory.action.conversation.GetTracesRequest;
import org.opensearch.ml.memory.action.conversation.GetTracesResponse;
import org.opensearch.ml.memory.action.conversation.UpdateInteractionAction;
import org.opensearch.ml.memory.action.conversation.UpdateInteractionRequest;

public class MLMemoryManagerTests {

    @Mock
    Client client;

    @Mock
    MLMemoryManager mlMemoryManager;

    @Mock
    ActionListener<CreateConversationResponse> createConversationResponseActionListener;

    @Mock
    ActionListener<CreateInteractionResponse> createInteractionResponseActionListener;

    @Mock
    ActionListener<List<Interaction>> interactionListActionListener;

    @Mock
    ActionListener<UpdateResponse> updateResponseActionListener;

    String conversationName;
    String applicationType;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mlMemoryManager = new MLMemoryManager(client);
        conversationName = "new conversation";
        applicationType = "ml application";
    }

    @Test
    public void testCreateConversation() {
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
    public void testGetInteractions() {
        List<Interaction> interactions = List
            .of(
                new Interaction(
                    "id0",
                    Instant.now(),
                    "cid",
                    "input",
                    "pt",
                    "response",
                    "origin",
                    Collections.singletonMap("metadata", "some meta")
                )
            );
        ArgumentCaptor<GetInteractionsRequest> captor = ArgumentCaptor.forClass(GetInteractionsRequest.class);
        doAnswer(invocation -> {
            ActionListener<GetInteractionsResponse> al = invocation.getArgument(2);
            GetInteractionsResponse getInteractionsResponse = new GetInteractionsResponse(interactions, 4, false);
            al.onResponse(getInteractionsResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        mlMemoryManager.getFinalInteractions("cid", 10, interactionListActionListener);

        verify(client, times(1)).execute(eq(GetInteractionsAction.INSTANCE), captor.capture(), any());
        assertEquals("cid", captor.getValue().getConversationId());
        assertEquals(0, captor.getValue().getFrom());
        assertEquals(10, captor.getValue().getMaxResults());
    }

    @Test
    public void testGetInteractionFails_thenFail() {
        doThrow(new RuntimeException("Failure in runtime")).when(client).execute(any(), any(), any());
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
            .of(INTERACTIONS_ADDITIONAL_INFO_FIELD, Map.of("feedback", "thumbs up!"), INTERACTIONS_RESPONSE_FIELD, "response");
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
        assertEquals(1, captor.getValue().getUpdateContent().keySet().size());
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
}
