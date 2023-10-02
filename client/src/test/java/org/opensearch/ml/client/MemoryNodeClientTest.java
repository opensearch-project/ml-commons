package org.opensearch.ml.client;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.memory.action.conversation.CreateConversationAction;
import org.opensearch.ml.memory.action.conversation.CreateConversationRequest;
import org.opensearch.ml.memory.action.conversation.CreateConversationResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionAction;
import org.opensearch.ml.memory.action.conversation.CreateInteractionRequest;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.memory.action.conversation.DeleteConversationAction;
import org.opensearch.ml.memory.action.conversation.DeleteConversationRequest;
import org.opensearch.ml.memory.action.conversation.DeleteConversationResponse;
import org.opensearch.ml.memory.action.conversation.GetConversationsAction;
import org.opensearch.ml.memory.action.conversation.GetConversationsRequest;
import org.opensearch.ml.memory.action.conversation.GetConversationsResponse;
import org.opensearch.ml.memory.action.conversation.GetInteractionsAction;
import org.opensearch.ml.memory.action.conversation.GetInteractionsRequest;
import org.opensearch.ml.memory.action.conversation.GetInteractionsResponse;

public class MemoryNodeClientTest {
    
    @Mock
    Client client;

    @Mock
    ActionListener<CreateConversationResponse> createConversationListener;

    @Mock
    ActionListener<CreateInteractionResponse> createInteractionListener;

    @Mock
    ActionListener<GetConversationsResponse> getConversationsListener;

    @Mock
    ActionListener<GetInteractionsResponse> getInteractionsListener;

    @Mock
    ActionListener<DeleteConversationResponse> deleteConversationListener;

    @InjectMocks
    MemoryNodeClient memoryClient;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void createConversation_Success() {
        CreateConversationResponse response = new CreateConversationResponse("Test id");
        doAnswer(invocation -> {
            ActionListener<CreateConversationResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(CreateConversationAction.INSTANCE), any(), any());

        ArgumentCaptor<CreateConversationResponse> argCaptor = ArgumentCaptor.forClass(CreateConversationResponse.class);
        CreateConversationRequest request = new CreateConversationRequest();
        memoryClient.createConversation(request, createConversationListener);

        verify(createConversationListener, times(1)).onResponse(argCaptor.capture());
        assertEquals(response, argCaptor.getValue());
    }

    @Test
    public void createConversation_Fails() {
        doAnswer(invocation -> {
            ActionListener<CreateConversationResponse> listener = invocation.getArgument(2);
            listener.onFailure(new Exception("CC Fail"));
            return null;
        }).when(client).execute(eq(CreateConversationAction.INSTANCE), any(), any());

        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        CreateConversationRequest request = new CreateConversationRequest();
        memoryClient.createConversation(request, createConversationListener);

        verify(createConversationListener, times(1)).onFailure(argCaptor.capture());
        assertEquals("CC Fail", argCaptor.getValue().getMessage());
    }

    @Test
    public void createConversation_Future() {
        CreateConversationResponse response = new CreateConversationResponse("Test id");
        doAnswer(invocation -> {
            ActionListener<CreateConversationResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(CreateConversationAction.INSTANCE), any(), any());

        CreateConversationRequest request = new CreateConversationRequest();
        assertEquals(memoryClient.createConversation(request).actionGet(), response);
    }

    @Test
    public void createInteraction_Success() {
        CreateInteractionResponse response = new CreateInteractionResponse("Test IID");
        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(CreateInteractionAction.INSTANCE), any(), any());

        ArgumentCaptor<CreateInteractionResponse> argCaptor = ArgumentCaptor.forClass(CreateInteractionResponse.class);
        CreateInteractionRequest request = new CreateInteractionRequest("cid", "inp", "pt", "rsp", "ogn", "add");
        memoryClient.createInteraction(request, createInteractionListener);

        verify(createInteractionListener, times(1)).onResponse(argCaptor.capture());
        assertEquals(response, argCaptor.getValue());
    }

    @Test
    public void createInteraction_Fails() {
        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> listener = invocation.getArgument(2);
            listener.onFailure(new Exception("CI Fail"));
            return null;
        }).when(client).execute(eq(CreateInteractionAction.INSTANCE), any(), any());

        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        CreateInteractionRequest request = new CreateInteractionRequest("cid", "inp", "pt", "rsp", "ogn", "add");
        memoryClient.createInteraction(request, createInteractionListener);

        verify(createInteractionListener, times(1)).onFailure(argCaptor.capture());
        assertEquals("CI Fail", argCaptor.getValue().getMessage());
    }

    @Test
    public void createInteraction_Future() {
        CreateInteractionResponse response = new CreateInteractionResponse("Test IID");
        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(CreateInteractionAction.INSTANCE), any(), any());

        CreateInteractionRequest request = new CreateInteractionRequest("cid", "inp", "pt", "rsp", "ogn", "add");
        assertEquals(memoryClient.createInteraction(request).actionGet(), response);
    }

    @Test
    public void getConversations_Success() {
        GetConversationsResponse response = new GetConversationsResponse(List.of(), 4, false);
        doAnswer(invocation -> {
            ActionListener<GetConversationsResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(GetConversationsAction.INSTANCE), any(), any());

        ArgumentCaptor<GetConversationsResponse> argCaptor = ArgumentCaptor.forClass(GetConversationsResponse.class);
        GetConversationsRequest request = new GetConversationsRequest();
        memoryClient.getConversations(request, getConversationsListener);

        verify(getConversationsListener, times(1)).onResponse(argCaptor.capture());
        assertEquals(response, argCaptor.getValue());
    }

    @Test
    public void getConversations_Fails() {
        doAnswer(invocation -> {
            ActionListener<GetConversationsResponse> listener = invocation.getArgument(2);
            listener.onFailure(new Exception("GC Fail"));
            return null;
        }).when(client).execute(eq(GetConversationsAction.INSTANCE), any(), any());

        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        GetConversationsRequest request = new GetConversationsRequest();
        memoryClient.getConversations(request, getConversationsListener);

        verify(getConversationsListener, times(1)).onFailure(argCaptor.capture());
        assertEquals("GC Fail", argCaptor.getValue().getMessage());
    }

    @Test
    public void getConversations_Future() {
        GetConversationsResponse response = new GetConversationsResponse(List.of(), 4, false);
        doAnswer(invocation -> {
            ActionListener<GetConversationsResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(GetConversationsAction.INSTANCE), any(), any());

        GetConversationsRequest request = new GetConversationsRequest();
        assertEquals(memoryClient.getConversations(request).actionGet(), response);
    }

    @Test
    public void getInteractions_Success() {
        GetInteractionsResponse response = new GetInteractionsResponse(List.of(), 4, false);
        doAnswer(invocation -> {
            ActionListener<GetInteractionsResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(GetInteractionsAction.INSTANCE), any(), any());

        ArgumentCaptor<GetInteractionsResponse> argCaptor = ArgumentCaptor.forClass(GetInteractionsResponse.class);
        GetInteractionsRequest request = new GetInteractionsRequest("Test CID");
        memoryClient.getInteractions(request, getInteractionsListener);

        verify(getInteractionsListener, times(1)).onResponse(argCaptor.capture());
        assertEquals(response, argCaptor.getValue());
    }

    @Test
    public void getInteractions_Fails() {
        doAnswer(invocation -> {
            ActionListener<GetInteractionsResponse> listener = invocation.getArgument(2);
            listener.onFailure(new Exception("GI Fail"));
            return null;
        }).when(client).execute(eq(GetInteractionsAction.INSTANCE), any(), any());

        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        GetInteractionsRequest request = new GetInteractionsRequest("Test CID");
        memoryClient.getInteractions(request, getInteractionsListener);

        verify(getInteractionsListener, times(1)).onFailure(argCaptor.capture());
        assertEquals("GI Fail", argCaptor.getValue().getMessage());
    }

    @Test
    public void getInteractions_Future() {
        GetInteractionsResponse response = new GetInteractionsResponse(List.of(), 4, false);
        doAnswer(invocation -> {
            ActionListener<GetInteractionsResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(GetInteractionsAction.INSTANCE), any(), any());

        GetInteractionsRequest request = new GetInteractionsRequest("Test CID");
        assertEquals(memoryClient.getInteractions(request).actionGet(), response);
    }

    @Test
    public void deleteConversation_Success() {
        DeleteConversationResponse response = new DeleteConversationResponse(true);
        doAnswer(invocation -> {
            ActionListener<DeleteConversationResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(DeleteConversationAction.INSTANCE), any(), any());

        ArgumentCaptor<DeleteConversationResponse> argCaptor = ArgumentCaptor.forClass(DeleteConversationResponse.class);
        DeleteConversationRequest request = new DeleteConversationRequest("Test CID");
        memoryClient.deleteConversation(request, deleteConversationListener);

        verify(deleteConversationListener, times(1)).onResponse(argCaptor.capture());
        assertEquals(response, argCaptor.getValue());
    }

    @Test
    public void deleteConversation_Fails() {
        doAnswer(invocation -> {
            ActionListener<DeleteConversationResponse> listener = invocation.getArgument(2);
            listener.onFailure(new Exception("DC Fail"));
            return null;
        }).when(client).execute(eq(DeleteConversationAction.INSTANCE), any(), any());

        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        DeleteConversationRequest request = new DeleteConversationRequest("Test CID");
        memoryClient.deleteConversation(request, deleteConversationListener);

        verify(deleteConversationListener, times(1)).onFailure(argCaptor.capture());
        assertEquals("DC Fail", argCaptor.getValue().getMessage());
    }

    @Test
    public void deleteConversation_Future() {
        DeleteConversationResponse response = new DeleteConversationResponse(true);
        doAnswer(invocation -> {
            ActionListener<DeleteConversationResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(DeleteConversationAction.INSTANCE), any(), any());

        DeleteConversationRequest request = new DeleteConversationRequest("Test CID");
        assertEquals(memoryClient.deleteConversation(request).actionGet(), response);
    }

}
