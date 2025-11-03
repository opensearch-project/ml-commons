/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.common.contextmanager.ContextManagerConfig;
import org.opensearch.ml.common.transport.contextmanagement.MLGetContextManagementTemplateRequest;
import org.opensearch.ml.common.transport.contextmanagement.MLGetContextManagementTemplateResponse;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class GetContextManagementTemplateTransportActionTests extends OpenSearchTestCase {

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
    private GetContextManagementTemplateTransportAction transportAction;

    private ThreadContext threadContext;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        transportAction = new GetContextManagementTemplateTransportAction(
            transportService,
            actionFilters,
            client,
            contextManagementTemplateService
        );
    }

    @Test
    public void testDoExecute_Success() {
        String templateName = "test_template";
        ContextManagementTemplate template = createTestTemplate();
        MLGetContextManagementTemplateRequest request = new MLGetContextManagementTemplateRequest(templateName);
        Task task = mock(Task.class);
        ActionListener<MLGetContextManagementTemplateResponse> listener = mock(ActionListener.class);

        // Mock successful template retrieval
        doAnswer(invocation -> {
            ActionListener<ContextManagementTemplate> getListener = invocation.getArgument(1);
            getListener.onResponse(template);
            return null;
        }).when(contextManagementTemplateService).getTemplate(eq(templateName), any());

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<MLGetContextManagementTemplateResponse> responseCaptor = ArgumentCaptor
            .forClass(MLGetContextManagementTemplateResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        MLGetContextManagementTemplateResponse response = responseCaptor.getValue();
        assertEquals(template, response.getTemplate());
    }

    @Test
    public void testDoExecute_TemplateNotFound() {
        String templateName = "nonexistent_template";
        MLGetContextManagementTemplateRequest request = new MLGetContextManagementTemplateRequest(templateName);
        Task task = mock(Task.class);
        ActionListener<MLGetContextManagementTemplateResponse> listener = mock(ActionListener.class);

        // Mock template not found (null response)
        doAnswer(invocation -> {
            ActionListener<ContextManagementTemplate> getListener = invocation.getArgument(1);
            getListener.onResponse(null);
            return null;
        }).when(contextManagementTemplateService).getTemplate(eq(templateName), any());

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<RuntimeException> exceptionCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(listener).onFailure(exceptionCaptor.capture());

        RuntimeException exception = exceptionCaptor.getValue();
        assertEquals("Context management template not found: " + templateName, exception.getMessage());
    }

    @Test
    public void testDoExecute_ServiceException() {
        String templateName = "test_template";
        MLGetContextManagementTemplateRequest request = new MLGetContextManagementTemplateRequest(templateName);
        Task task = mock(Task.class);
        ActionListener<MLGetContextManagementTemplateResponse> listener = mock(ActionListener.class);

        RuntimeException serviceException = new RuntimeException("Database error");

        // Mock exception during template retrieval
        doAnswer(invocation -> {
            ActionListener<ContextManagementTemplate> getListener = invocation.getArgument(1);
            getListener.onFailure(serviceException);
            return null;
        }).when(contextManagementTemplateService).getTemplate(eq(templateName), any());

        transportAction.doExecute(task, request, listener);

        verify(listener).onFailure(serviceException);
    }

    @Test
    public void testDoExecute_UnexpectedException() {
        String templateName = "test_template";
        MLGetContextManagementTemplateRequest request = new MLGetContextManagementTemplateRequest(templateName);
        Task task = mock(Task.class);
        ActionListener<MLGetContextManagementTemplateResponse> listener = mock(ActionListener.class);

        RuntimeException unexpectedException = new RuntimeException("Unexpected error");

        // Mock unexpected exception
        doThrow(unexpectedException).when(contextManagementTemplateService).getTemplate(any(), any());

        transportAction.doExecute(task, request, listener);

        verify(listener).onFailure(unexpectedException);
    }

    @Test
    public void testDoExecute_VerifyServiceCall() {
        String templateName = "test_template";
        ContextManagementTemplate template = createTestTemplate();
        MLGetContextManagementTemplateRequest request = new MLGetContextManagementTemplateRequest(templateName);
        Task task = mock(Task.class);
        ActionListener<MLGetContextManagementTemplateResponse> listener = mock(ActionListener.class);

        // Mock successful template retrieval
        doAnswer(invocation -> {
            ActionListener<ContextManagementTemplate> getListener = invocation.getArgument(1);
            getListener.onResponse(template);
            return null;
        }).when(contextManagementTemplateService).getTemplate(eq(templateName), any());

        transportAction.doExecute(task, request, listener);

        // Verify the service was called with correct parameters
        verify(contextManagementTemplateService).getTemplate(eq(templateName), any());
    }

    private ContextManagementTemplate createTestTemplate() {
        Map<String, Object> config = Collections.singletonMap("summary_ratio", 0.3);
        ContextManagerConfig contextManagerConfig = new ContextManagerConfig("SummarizationManager", null, config);

        return ContextManagementTemplate
            .builder()
            .name("test_template")
            .description("Test template")
            .hooks(Collections.singletonMap("PreLLMEvent", Collections.singletonList(contextManagerConfig)))
            .build();
    }
}
