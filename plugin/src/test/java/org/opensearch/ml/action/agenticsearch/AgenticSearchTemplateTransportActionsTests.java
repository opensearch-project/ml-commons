/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.agenticsearch.AgenticSearchTemplate;
import org.opensearch.ml.common.transport.agenticsearch.MLDeleteAgenticSearchTemplateRequest;
import org.opensearch.ml.common.transport.agenticsearch.MLDeleteAgenticSearchTemplateResponse;
import org.opensearch.ml.common.transport.agenticsearch.MLGetAgenticSearchTemplateRequest;
import org.opensearch.ml.common.transport.agenticsearch.MLGetAgenticSearchTemplateResponse;
import org.opensearch.ml.common.transport.agenticsearch.MLListAgenticSearchTemplatesRequest;
import org.opensearch.ml.common.transport.agenticsearch.MLListAgenticSearchTemplatesResponse;
import org.opensearch.ml.common.transport.agenticsearch.MLRegisterAgenticSearchTemplateRequest;
import org.opensearch.ml.common.transport.agenticsearch.MLRegisterAgenticSearchTemplateResponse;
import org.opensearch.ml.common.transport.agenticsearch.MLUpdateAgenticSearchTemplateRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

/**
 * Delegation tests for the agentic search template transport actions: each doExecute
 * forwards to {@link AgenticSearchTemplateService} and wraps the service result in the
 * response, or forwards a failure to the listener.
 */
public class AgenticSearchTemplateTransportActionsTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private Client client;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private AgenticSearchTemplateService service;

    private final RuntimeException failure = new RuntimeException("boom");

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        ThreadContext threadContext = new ThreadContext(Settings.builder().build());
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void register_success_wrapsCreatedResponse() {
        RegisterAgenticSearchTemplateTransportAction action = new RegisterAgenticSearchTemplateTransportAction(
            transportService,
            actionFilters,
            client,
            service
        );
        AgenticSearchTemplate template = AgenticSearchTemplate.builder().templateId("tmpl").build();
        doAnswer((Answer<Void>) inv -> {
            ActionListener<AgenticSearchTemplate> l = inv.getArgument(5);
            l.onResponse(template);
            return null;
        }).when(service).register(any(), any(), any(), any(), any(), any());

        @SuppressWarnings("unchecked")
        ActionListener<MLRegisterAgenticSearchTemplateResponse> listener = mock(ActionListener.class);
        action.doExecute(null, new MLRegisterAgenticSearchTemplateRequest("tmpl", "idx", "d", null), listener);

        ArgumentCaptor<MLRegisterAgenticSearchTemplateResponse> captor = ArgumentCaptor
            .forClass(MLRegisterAgenticSearchTemplateResponse.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("tmpl", captor.getValue().getTemplateId());
        assertEquals("created", captor.getValue().getStatus());
    }

    @Test
    public void register_failure_forwardsToListener() {
        RegisterAgenticSearchTemplateTransportAction action = new RegisterAgenticSearchTemplateTransportAction(
            transportService,
            actionFilters,
            client,
            service
        );
        doAnswer((Answer<Void>) inv -> {
            ActionListener<AgenticSearchTemplate> l = inv.getArgument(5);
            l.onFailure(failure);
            return null;
        }).when(service).register(any(), any(), any(), any(), any(), any());

        @SuppressWarnings("unchecked")
        ActionListener<MLRegisterAgenticSearchTemplateResponse> listener = mock(ActionListener.class);
        action.doExecute(null, new MLRegisterAgenticSearchTemplateRequest("tmpl", "idx", "d", null), listener);

        verify(listener, times(1)).onFailure(failure);
    }

    @Test
    public void get_success_wrapsTemplate() {
        GetAgenticSearchTemplateTransportAction action = new GetAgenticSearchTemplateTransportAction(
            transportService,
            actionFilters,
            client,
            service
        );
        AgenticSearchTemplate template = AgenticSearchTemplate.builder().templateId("tmpl").build();
        doAnswer((Answer<Void>) inv -> {
            ActionListener<AgenticSearchTemplate> l = inv.getArgument(1);
            l.onResponse(template);
            return null;
        }).when(service).getTemplate(eq("tmpl"), any());

        @SuppressWarnings("unchecked")
        ActionListener<MLGetAgenticSearchTemplateResponse> listener = mock(ActionListener.class);
        action.doExecute(null, new MLGetAgenticSearchTemplateRequest("tmpl"), listener);

        verify(listener, times(1)).onResponse(any(MLGetAgenticSearchTemplateResponse.class));
    }

    @Test
    public void get_failure_forwardsToListener() {
        GetAgenticSearchTemplateTransportAction action = new GetAgenticSearchTemplateTransportAction(
            transportService,
            actionFilters,
            client,
            service
        );
        doAnswer((Answer<Void>) inv -> {
            ActionListener<AgenticSearchTemplate> l = inv.getArgument(1);
            l.onFailure(failure);
            return null;
        }).when(service).getTemplate(eq("tmpl"), any());

        @SuppressWarnings("unchecked")
        ActionListener<MLGetAgenticSearchTemplateResponse> listener = mock(ActionListener.class);
        action.doExecute(null, new MLGetAgenticSearchTemplateRequest("tmpl"), listener);

        verify(listener, times(1)).onFailure(failure);
    }

    @Test
    public void delete_success_wrapsDeletedResponse() {
        DeleteAgenticSearchTemplateTransportAction action = new DeleteAgenticSearchTemplateTransportAction(
            transportService,
            actionFilters,
            client,
            service
        );
        doAnswer((Answer<Void>) inv -> {
            ActionListener<Boolean> l = inv.getArgument(1);
            l.onResponse(true);
            return null;
        }).when(service).deleteTemplate(eq("tmpl"), any());

        @SuppressWarnings("unchecked")
        ActionListener<MLDeleteAgenticSearchTemplateResponse> listener = mock(ActionListener.class);
        action.doExecute(null, new MLDeleteAgenticSearchTemplateRequest("tmpl"), listener);

        ArgumentCaptor<MLDeleteAgenticSearchTemplateResponse> captor = ArgumentCaptor.forClass(MLDeleteAgenticSearchTemplateResponse.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("tmpl", captor.getValue().getTemplateId());
        assertEquals("deleted", captor.getValue().getStatus());
    }

    @Test
    public void delete_failure_forwardsToListener() {
        DeleteAgenticSearchTemplateTransportAction action = new DeleteAgenticSearchTemplateTransportAction(
            transportService,
            actionFilters,
            client,
            service
        );
        doAnswer((Answer<Void>) inv -> {
            ActionListener<Boolean> l = inv.getArgument(1);
            l.onFailure(failure);
            return null;
        }).when(service).deleteTemplate(eq("tmpl"), any());

        @SuppressWarnings("unchecked")
        ActionListener<MLDeleteAgenticSearchTemplateResponse> listener = mock(ActionListener.class);
        action.doExecute(null, new MLDeleteAgenticSearchTemplateRequest("tmpl"), listener);

        verify(listener, times(1)).onFailure(failure);
    }

    @Test
    public void list_success_wrapsResult() {
        ListAgenticSearchTemplatesTransportAction action = new ListAgenticSearchTemplatesTransportAction(
            transportService,
            actionFilters,
            client,
            service
        );
        AgenticSearchTemplate template = AgenticSearchTemplate.builder().templateId("tmpl").build();
        doAnswer((Answer<Void>) inv -> {
            ActionListener<AgenticSearchTemplateService.MLListResult> l = inv.getArgument(2);
            l.onResponse(new AgenticSearchTemplateService.MLListResult(List.of(template), 1L));
            return null;
        }).when(service).listTemplates(eq(0), eq(10), any());

        @SuppressWarnings("unchecked")
        ActionListener<MLListAgenticSearchTemplatesResponse> listener = mock(ActionListener.class);
        action.doExecute(null, new MLListAgenticSearchTemplatesRequest(0, 10), listener);

        verify(listener, times(1)).onResponse(any(MLListAgenticSearchTemplatesResponse.class));
    }

    @Test
    public void list_failure_forwardsToListener() {
        ListAgenticSearchTemplatesTransportAction action = new ListAgenticSearchTemplatesTransportAction(
            transportService,
            actionFilters,
            client,
            service
        );
        doAnswer((Answer<Void>) inv -> {
            ActionListener<AgenticSearchTemplateService.MLListResult> l = inv.getArgument(2);
            l.onFailure(failure);
            return null;
        }).when(service).listTemplates(eq(0), eq(10), any());

        @SuppressWarnings("unchecked")
        ActionListener<MLListAgenticSearchTemplatesResponse> listener = mock(ActionListener.class);
        action.doExecute(null, new MLListAgenticSearchTemplatesRequest(0, 10), listener);

        verify(listener, times(1)).onFailure(failure);
    }

    @Test
    public void update_delegatesToServiceWithListener() {
        UpdateAgenticSearchTemplateTransportAction action = new UpdateAgenticSearchTemplateTransportAction(
            transportService,
            actionFilters,
            client,
            service
        );
        UpdateResponse updateResponse = mock(UpdateResponse.class);
        doAnswer((Answer<Void>) inv -> {
            ActionListener<UpdateResponse> l = inv.getArgument(2);
            l.onResponse(updateResponse);
            return null;
        }).when(service).updateTemplate(eq("tmpl"), any(), any());

        AgenticSearchTemplate patch = AgenticSearchTemplate.builder().templateId("tmpl").description("d").build();
        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        action.doExecute(null, new MLUpdateAgenticSearchTemplateRequest("tmpl", patch), listener);

        verify(listener, times(1)).onResponse(updateResponse);
    }

    @Test
    public void update_failure_forwardsToListener() {
        UpdateAgenticSearchTemplateTransportAction action = new UpdateAgenticSearchTemplateTransportAction(
            transportService,
            actionFilters,
            client,
            service
        );
        doAnswer((Answer<Void>) inv -> {
            ActionListener<UpdateResponse> l = inv.getArgument(2);
            l.onFailure(failure);
            return null;
        }).when(service).updateTemplate(eq("tmpl"), any(), any());

        AgenticSearchTemplate patch = AgenticSearchTemplate.builder().templateId("tmpl").description("d").build();
        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        action.doExecute(null, new MLUpdateAgenticSearchTemplateRequest("tmpl", patch), listener);

        verify(listener, times(1)).onFailure(failure);
    }
}
