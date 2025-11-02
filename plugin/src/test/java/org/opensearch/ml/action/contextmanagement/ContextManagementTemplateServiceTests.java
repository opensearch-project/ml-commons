/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class ContextManagementTemplateServiceTests extends OpenSearchTestCase {

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private Client client;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    private ContextManagementTemplateService contextManagementTemplateService;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        // Create a real ThreadContext instead of mocking it
        Settings settings = Settings.builder().build();
        ThreadContext threadContext = new ThreadContext(settings);

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        contextManagementTemplateService = new ContextManagementTemplateService(mlIndicesHandler, client, clusterService);
    }

    @Test
    public void testConstructor() {
        assertNotNull(contextManagementTemplateService);
    }

    @Test
    public void testSaveTemplate_InvalidTemplate() {
        String templateName = "test_template";
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.isValid()).thenReturn(false);
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.saveTemplate(templateName, template, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("Invalid context management template", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testListTemplates_WithPagination() {
        @SuppressWarnings("unchecked")
        ActionListener<java.util.List<ContextManagementTemplate>> listener = mock(ActionListener.class);

        contextManagementTemplateService.listTemplates(5, 20, listener);

        // Verify that the method was called - the actual OpenSearch interaction would be complex to mock
        // This at least exercises the method signature and basic flow
        verify(client).threadPool();
    }

    @Test
    public void testListTemplates_DefaultPagination() {
        @SuppressWarnings("unchecked")
        ActionListener<java.util.List<ContextManagementTemplate>> listener = mock(ActionListener.class);

        contextManagementTemplateService.listTemplates(listener);

        // Verify that the method was called - this exercises the default pagination path
        verify(client).threadPool();
    }

    @Test
    public void testGetTemplate_NullTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate(null, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("Template name cannot be null or empty", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testGetTemplate_EmptyTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate("", listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("Template name cannot be null or empty", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTemplate_NullTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate(null, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("Template name cannot be null or empty", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTemplate_EmptyTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate("", listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("Template name cannot be null or empty", exceptionCaptor.getValue().getMessage());
    }
}
