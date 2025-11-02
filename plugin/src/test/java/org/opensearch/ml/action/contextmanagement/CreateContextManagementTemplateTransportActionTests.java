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
import org.opensearch.ml.common.transport.contextmanagement.MLCreateContextManagementTemplateRequest;
import org.opensearch.ml.common.transport.contextmanagement.MLCreateContextManagementTemplateResponse;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class CreateContextManagementTemplateTransportActionTests extends OpenSearchTestCase {

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
    private CreateContextManagementTemplateTransportAction transportAction;

    private ThreadContext threadContext;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        transportAction = new CreateContextManagementTemplateTransportAction(
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
        MLCreateContextManagementTemplateRequest request = new MLCreateContextManagementTemplateRequest(templateName, template);
        Task task = mock(Task.class);
        ActionListener<MLCreateContextManagementTemplateResponse> listener = mock(ActionListener.class);

        // Mock successful template save
        doAnswer(invocation -> {
            ActionListener<Boolean> saveListener = invocation.getArgument(2);
            saveListener.onResponse(true);
            return null;
        }).when(contextManagementTemplateService).saveTemplate(eq(templateName), eq(template), any());

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<MLCreateContextManagementTemplateResponse> responseCaptor = ArgumentCaptor
            .forClass(MLCreateContextManagementTemplateResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        MLCreateContextManagementTemplateResponse response = responseCaptor.getValue();
        assertEquals(templateName, response.getTemplateName());
        assertEquals("created", response.getStatus());
    }

    @Test
    public void testDoExecute_SaveFailure() {
        String templateName = "test_template";
        ContextManagementTemplate template = createTestTemplate();
        MLCreateContextManagementTemplateRequest request = new MLCreateContextManagementTemplateRequest(templateName, template);
        Task task = mock(Task.class);
        ActionListener<MLCreateContextManagementTemplateResponse> listener = mock(ActionListener.class);

        // Mock failed template save
        doAnswer(invocation -> {
            ActionListener<Boolean> saveListener = invocation.getArgument(2);
            saveListener.onResponse(false);
            return null;
        }).when(contextManagementTemplateService).saveTemplate(eq(templateName), eq(template), any());

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<RuntimeException> exceptionCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(listener).onFailure(exceptionCaptor.capture());

        RuntimeException exception = exceptionCaptor.getValue();
        assertEquals("Failed to create context management template", exception.getMessage());
    }

    @Test
    public void testDoExecute_SaveException() {
        String templateName = "test_template";
        ContextManagementTemplate template = createTestTemplate();
        MLCreateContextManagementTemplateRequest request = new MLCreateContextManagementTemplateRequest(templateName, template);
        Task task = mock(Task.class);
        ActionListener<MLCreateContextManagementTemplateResponse> listener = mock(ActionListener.class);

        RuntimeException saveException = new RuntimeException("Database error");

        // Mock exception during template save
        doAnswer(invocation -> {
            ActionListener<Boolean> saveListener = invocation.getArgument(2);
            saveListener.onFailure(saveException);
            return null;
        }).when(contextManagementTemplateService).saveTemplate(eq(templateName), eq(template), any());

        transportAction.doExecute(task, request, listener);

        verify(listener).onFailure(saveException);
    }

    @Test
    public void testDoExecute_UnexpectedException() {
        String templateName = "test_template";
        ContextManagementTemplate template = createTestTemplate();
        MLCreateContextManagementTemplateRequest request = new MLCreateContextManagementTemplateRequest(templateName, template);
        Task task = mock(Task.class);
        ActionListener<MLCreateContextManagementTemplateResponse> listener = mock(ActionListener.class);

        RuntimeException unexpectedException = new RuntimeException("Unexpected error");

        // Mock unexpected exception
        doThrow(unexpectedException).when(contextManagementTemplateService).saveTemplate(any(), any(), any());

        transportAction.doExecute(task, request, listener);

        verify(listener).onFailure(unexpectedException);
    }

    @Test
    public void testDoExecute_VerifyServiceCall() {
        String templateName = "test_template";
        ContextManagementTemplate template = createTestTemplate();
        MLCreateContextManagementTemplateRequest request = new MLCreateContextManagementTemplateRequest(templateName, template);
        Task task = mock(Task.class);
        ActionListener<MLCreateContextManagementTemplateResponse> listener = mock(ActionListener.class);

        // Mock successful template save
        doAnswer(invocation -> {
            ActionListener<Boolean> saveListener = invocation.getArgument(2);
            saveListener.onResponse(true);
            return null;
        }).when(contextManagementTemplateService).saveTemplate(eq(templateName), eq(template), any());

        transportAction.doExecute(task, request, listener);

        // Verify the service was called with correct parameters
        verify(contextManagementTemplateService).saveTemplate(eq(templateName), eq(template), any());
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
