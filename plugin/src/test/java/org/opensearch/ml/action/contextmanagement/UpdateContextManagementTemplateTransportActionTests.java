/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.common.transport.contextmanagement.MLUpdateContextManagementTemplateRequest;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class UpdateContextManagementTemplateTransportActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private Client client;

    @Mock
    private ContextManagementTemplateService contextManagementTemplateService;

    @Mock
    private Task task;

    @Mock
    private ActionListener<UpdateResponse> actionListener;

    @Mock
    private ThreadPool threadPool;

    private ThreadContext threadContext;

    private UpdateContextManagementTemplateTransportAction action;
    private MLUpdateContextManagementTemplateRequest request;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        // Create real ThreadContext instance with settings
        Settings settings = Settings.builder().build();
        this.threadContext = new ThreadContext(settings);

        // Mock client threadPool and threadContext
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        action = new UpdateContextManagementTemplateTransportAction(
            transportService,
            actionFilters,
            client,
            contextManagementTemplateService
        );

        ContextManagementTemplate template = ContextManagementTemplate.builder().name("test_template").description("Test template").build();

        request = new MLUpdateContextManagementTemplateRequest("test_template", template);
    }

    @Test
    public void testDoExecute() {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            listener.onResponse(null);
            return null;
        }).when(contextManagementTemplateService).updateTemplate(any(), any(), any());

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<String> templateNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ContextManagementTemplate> templateCaptor = ArgumentCaptor.forClass(ContextManagementTemplate.class);
        verify(contextManagementTemplateService).updateTemplate(templateNameCaptor.capture(), templateCaptor.capture(), any());

        assertEquals("test_template", templateNameCaptor.getValue());
        assertEquals("test_template", templateCaptor.getValue().getName());
    }

    @Test
    public void testDoExecute_ServiceFailure() {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Service error"));
            return null;
        }).when(contextManagementTemplateService).updateTemplate(any(), any(), any());

        action.doExecute(task, request, actionListener);

        verify(actionListener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testDoExecute_ExceptionInTryBlock() {
        // Test exception handling in the try-catch block by making threadPool throw
        when(client.threadPool()).thenThrow(new RuntimeException("ThreadPool error"));

        action.doExecute(task, request, actionListener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof RuntimeException);
        assertEquals("ThreadPool error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_SuccessfulUpdate() {
        UpdateResponse mockResponse = mock(UpdateResponse.class);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            listener.onResponse(mockResponse);
            return null;
        }).when(contextManagementTemplateService).updateTemplate(any(), any(), any());

        action.doExecute(task, request, actionListener);

        verify(actionListener).onResponse(mockResponse);
        verify(contextManagementTemplateService).updateTemplate(eq("test_template"), any(ContextManagementTemplate.class), any());
    }
}
