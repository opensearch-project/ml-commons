/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
import org.opensearch.ml.common.transport.contextmanagement.MLListContextManagementTemplatesRequest;
import org.opensearch.ml.common.transport.contextmanagement.MLListContextManagementTemplatesResponse;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class ListContextManagementTemplatesTransportActionTests extends OpenSearchTestCase {

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
    private ListContextManagementTemplatesTransportAction transportAction;

    private ThreadContext threadContext;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        transportAction = new ListContextManagementTemplatesTransportAction(
            transportService,
            actionFilters,
            client,
            contextManagementTemplateService
        );
    }

    @Test
    public void testDoExecute_Success() {
        int from = 0;
        int size = 10;
        MLListContextManagementTemplatesRequest request = new MLListContextManagementTemplatesRequest(from, size);
        Task task = mock(Task.class);
        ActionListener<MLListContextManagementTemplatesResponse> listener = mock(ActionListener.class);

        List<ContextManagementTemplate> templates = Arrays.asList(createTestTemplate("template1"), createTestTemplate("template2"));

        // Mock successful template listing
        doAnswer(invocation -> {
            ActionListener<List<ContextManagementTemplate>> listListener = invocation.getArgument(2);
            listListener.onResponse(templates);
            return null;
        }).when(contextManagementTemplateService).listTemplates(eq(from), eq(size), any());

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<MLListContextManagementTemplatesResponse> responseCaptor = ArgumentCaptor
            .forClass(MLListContextManagementTemplatesResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        MLListContextManagementTemplatesResponse response = responseCaptor.getValue();
        assertEquals(templates, response.getTemplates());
    }

    @Test
    public void testDoExecute_EmptyList() {
        int from = 0;
        int size = 10;
        MLListContextManagementTemplatesRequest request = new MLListContextManagementTemplatesRequest(from, size);
        Task task = mock(Task.class);
        ActionListener<MLListContextManagementTemplatesResponse> listener = mock(ActionListener.class);

        List<ContextManagementTemplate> emptyTemplates = Collections.emptyList();

        // Mock empty template list
        doAnswer(invocation -> {
            ActionListener<List<ContextManagementTemplate>> listListener = invocation.getArgument(2);
            listListener.onResponse(emptyTemplates);
            return null;
        }).when(contextManagementTemplateService).listTemplates(eq(from), eq(size), any());

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<MLListContextManagementTemplatesResponse> responseCaptor = ArgumentCaptor
            .forClass(MLListContextManagementTemplatesResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        MLListContextManagementTemplatesResponse response = responseCaptor.getValue();
        assertEquals(emptyTemplates, response.getTemplates());
        assertTrue(response.getTemplates().isEmpty());
    }

    @Test
    public void testDoExecute_ServiceException() {
        int from = 0;
        int size = 10;
        MLListContextManagementTemplatesRequest request = new MLListContextManagementTemplatesRequest(from, size);
        Task task = mock(Task.class);
        ActionListener<MLListContextManagementTemplatesResponse> listener = mock(ActionListener.class);

        RuntimeException serviceException = new RuntimeException("Database error");

        // Mock exception during template listing
        doAnswer(invocation -> {
            ActionListener<List<ContextManagementTemplate>> listListener = invocation.getArgument(2);
            listListener.onFailure(serviceException);
            return null;
        }).when(contextManagementTemplateService).listTemplates(eq(from), eq(size), any());

        transportAction.doExecute(task, request, listener);

        verify(listener).onFailure(serviceException);
    }

    @Test
    public void testDoExecute_UnexpectedException() {
        int from = 0;
        int size = 10;
        MLListContextManagementTemplatesRequest request = new MLListContextManagementTemplatesRequest(from, size);
        Task task = mock(Task.class);
        ActionListener<MLListContextManagementTemplatesResponse> listener = mock(ActionListener.class);

        RuntimeException unexpectedException = new RuntimeException("Unexpected error");

        // Mock unexpected exception
        doThrow(unexpectedException).when(contextManagementTemplateService).listTemplates(anyInt(), anyInt(), any());

        transportAction.doExecute(task, request, listener);

        verify(listener).onFailure(unexpectedException);
    }

    @Test
    public void testDoExecute_VerifyServiceCall() {
        int from = 5;
        int size = 20;
        MLListContextManagementTemplatesRequest request = new MLListContextManagementTemplatesRequest(from, size);
        Task task = mock(Task.class);
        ActionListener<MLListContextManagementTemplatesResponse> listener = mock(ActionListener.class);

        List<ContextManagementTemplate> templates = Arrays.asList(createTestTemplate("template1"));

        // Mock successful template listing
        doAnswer(invocation -> {
            ActionListener<List<ContextManagementTemplate>> listListener = invocation.getArgument(2);
            listListener.onResponse(templates);
            return null;
        }).when(contextManagementTemplateService).listTemplates(eq(from), eq(size), any());

        transportAction.doExecute(task, request, listener);

        // Verify the service was called with correct parameters
        verify(contextManagementTemplateService).listTemplates(eq(from), eq(size), any());
    }

    @Test
    public void testDoExecute_CustomPagination() {
        int from = 10;
        int size = 5;
        MLListContextManagementTemplatesRequest request = new MLListContextManagementTemplatesRequest(from, size);
        Task task = mock(Task.class);
        ActionListener<MLListContextManagementTemplatesResponse> listener = mock(ActionListener.class);

        List<ContextManagementTemplate> templates = Arrays.asList(createTestTemplate("template3"));

        // Mock successful template listing with custom pagination
        doAnswer(invocation -> {
            ActionListener<List<ContextManagementTemplate>> listListener = invocation.getArgument(2);
            listListener.onResponse(templates);
            return null;
        }).when(contextManagementTemplateService).listTemplates(eq(from), eq(size), any());

        transportAction.doExecute(task, request, listener);

        ArgumentCaptor<MLListContextManagementTemplatesResponse> responseCaptor = ArgumentCaptor
            .forClass(MLListContextManagementTemplatesResponse.class);
        verify(listener).onResponse(responseCaptor.capture());

        MLListContextManagementTemplatesResponse response = responseCaptor.getValue();
        assertEquals(templates, response.getTemplates());

        // Verify the service was called with custom pagination parameters
        verify(contextManagementTemplateService).listTemplates(eq(from), eq(size), any());
    }

    private ContextManagementTemplate createTestTemplate(String name) {
        Map<String, Object> config = Collections.singletonMap("summary_ratio", 0.3);
        ContextManagerConfig contextManagerConfig = new ContextManagerConfig("SummarizationManager", null, config);

        return ContextManagementTemplate
            .builder()
            .name(name)
            .description("Test template " + name)
            .hooks(Collections.singletonMap("PreLLMEvent", Collections.singletonList(contextManagerConfig)))
            .build();
    }
}
