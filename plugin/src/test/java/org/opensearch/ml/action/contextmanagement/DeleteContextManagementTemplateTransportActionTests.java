/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.transport.contextmanagement.MLDeleteContextManagementTemplateRequest;
import org.opensearch.ml.common.transport.contextmanagement.MLDeleteContextManagementTemplateResponse;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class DeleteContextManagementTemplateTransportActionTests extends OpenSearchTestCase {

    @Mock
    private Client client;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private ContextManagementTemplateService contextManagementTemplateService;

    @InjectMocks
    private DeleteContextManagementTemplateTransportAction transportAction;

    private ThreadContext threadContext;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        transportAction = new DeleteContextManagementTemplateTransportAction(
            transportService,
            actionFilters,
            client,
            contextManagementTemplateService
        );
    }

    @Test
    public void testDoExecute_Success() {
        String templateName = "test_template";
        MLDeleteContextManagementTemplateRequest request = new MLDeleteContextManagementTemplateRequest(templateName);
        Task task = mock(Task.class);
        ActionListener<MLDeleteContextManagementTemplateResponse> listener = mock(ActionListener.class);

        // Mock successful template deletion
        doAnswer(invocation -> {
            ActionListener<Boolean> deleteListener = invocation.getArgument(1);
            deleteListener.onResponse(true);
            return null;
        }).when(contextManagementTemplateService).deleteTemplate(eq(templateName), any());

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<MLDeleteContextManagementTemplateResponse> responseCaptor = ArgumentCaptor
            .forClass(MLDeleteContextManagementTemplateResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        MLDeleteContextManagementTemplateResponse response = responseCaptor.getValue();
        assertEquals(templateName, response.getTemplateName());
        assertEquals("deleted", response.getStatus());
    }

    @Test
    public void testDoExecute_DeleteFailure() {
        String templateName = "test_template";
        MLDeleteContextManagementTemplateRequest request = new MLDeleteContextManagementTemplateRequest(templateName);
        Task task = mock(Task.class);
        ActionListener<MLDeleteContextManagementTemplateResponse> listener = mock(ActionListener.class);

        // Mock failed template deletion
        doAnswer(invocation -> {
            ActionListener<Boolean> deleteListener = invocation.getArgument(1);
            deleteListener.onResponse(false);
            return null;
        }).when(contextManagementTemplateService).deleteTemplate(eq(templateName), any());

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<OpenSearchStatusException> exceptionCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(listener).onFailure(exceptionCaptor.capture());

        OpenSearchStatusException exception = exceptionCaptor.getValue();
        assertEquals("Context management template not found: test_template", exception.getMessage());
        assertEquals(RestStatus.NOT_FOUND, exception.status());
    }

    @Test
    public void testDoExecute_TemplateNotFound_Returns404() {
        String templateName = "sliding_window_max_40000_tokens_managers123";
        MLDeleteContextManagementTemplateRequest request = new MLDeleteContextManagementTemplateRequest(templateName);
        Task task = mock(Task.class);
        ActionListener<MLDeleteContextManagementTemplateResponse> listener = mock(ActionListener.class);

        // Mock template not found (service returns false)
        doAnswer(invocation -> {
            ActionListener<Boolean> deleteListener = invocation.getArgument(1);
            deleteListener.onResponse(false);
            return null;
        }).when(contextManagementTemplateService).deleteTemplate(eq(templateName), any());

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertTrue(
            "Expected OpenSearchStatusException but got " + exception.getClass().getName(),
            exception instanceof OpenSearchStatusException
        );
        OpenSearchStatusException statusException = (OpenSearchStatusException) exception;
        assertEquals(RestStatus.NOT_FOUND, statusException.status());
        assertTrue(statusException.getMessage().contains(templateName));
    }

    @Test
    public void testDoExecute_TemplateNotFound_ExceptionIsNotRuntimeException() {
        String templateName = "nonexistent_template";
        MLDeleteContextManagementTemplateRequest request = new MLDeleteContextManagementTemplateRequest(templateName);
        Task task = mock(Task.class);
        ActionListener<MLDeleteContextManagementTemplateResponse> listener = mock(ActionListener.class);

        // Mock template not found
        doAnswer(invocation -> {
            ActionListener<Boolean> deleteListener = invocation.getArgument(1);
            deleteListener.onResponse(false);
            return null;
        }).when(contextManagementTemplateService).deleteTemplate(eq(templateName), any());

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());

        // Verify it's NOT a plain RuntimeException (which would cause a 500)
        assertFalse("Should not be a plain RuntimeException", exceptionCaptor.getValue().getClass().equals(RuntimeException.class));
    }

    @Test
    public void testDoExecute_ServiceException() {
        String templateName = "test_template";
        MLDeleteContextManagementTemplateRequest request = new MLDeleteContextManagementTemplateRequest(templateName);
        Task task = mock(Task.class);
        ActionListener<MLDeleteContextManagementTemplateResponse> listener = mock(ActionListener.class);

        RuntimeException serviceException = new RuntimeException("Database error");

        // Mock exception during template deletion
        doAnswer(invocation -> {
            ActionListener<Boolean> deleteListener = invocation.getArgument(1);
            deleteListener.onFailure(serviceException);
            return null;
        }).when(contextManagementTemplateService).deleteTemplate(eq(templateName), any());

        transportAction.doExecute(task, request, listener);

        verify(listener).onFailure(serviceException);
    }

    @Test
    public void testDoExecute_UnexpectedException() {
        String templateName = "test_template";
        MLDeleteContextManagementTemplateRequest request = new MLDeleteContextManagementTemplateRequest(templateName);
        Task task = mock(Task.class);
        ActionListener<MLDeleteContextManagementTemplateResponse> listener = mock(ActionListener.class);

        RuntimeException unexpectedException = new RuntimeException("Unexpected error");

        // Mock unexpected exception
        doThrow(unexpectedException).when(contextManagementTemplateService).deleteTemplate(any(), any());

        transportAction.doExecute(task, request, listener);

        verify(listener).onFailure(unexpectedException);
    }

    @Test
    public void testDoExecute_VerifyServiceCall() {
        String templateName = "test_template";
        MLDeleteContextManagementTemplateRequest request = new MLDeleteContextManagementTemplateRequest(templateName);
        Task task = mock(Task.class);
        ActionListener<MLDeleteContextManagementTemplateResponse> listener = mock(ActionListener.class);

        // Mock successful template deletion
        doAnswer(invocation -> {
            ActionListener<Boolean> deleteListener = invocation.getArgument(1);
            deleteListener.onResponse(true);
            return null;
        }).when(contextManagementTemplateService).deleteTemplate(eq(templateName), any());

        transportAction.doExecute(task, request, listener);

        // Verify the service was called with correct parameters
        verify(contextManagementTemplateService).deleteTemplate(eq(templateName), any());
    }
}
