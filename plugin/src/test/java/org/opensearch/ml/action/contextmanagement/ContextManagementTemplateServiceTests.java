/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

        // Mock cluster service dependencies for proper setup
        org.opensearch.cluster.ClusterState clusterState = mock(org.opensearch.cluster.ClusterState.class);
        org.opensearch.cluster.metadata.Metadata metadata = mock(org.opensearch.cluster.metadata.Metadata.class);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex(anyString())).thenReturn(false); // Default to index not existing

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
    public void testSaveTemplate_NullTemplate() {
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.saveTemplate("test_template", null, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof NullPointerException);
    }

    @Test
    public void testSaveTemplate_ValidTemplate() {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.isValid()).thenReturn(true);
        when(template.getName()).thenReturn("test_template");
        when(template.getCreatedTime()).thenReturn(null);
        when(template.getCreatedBy()).thenReturn(null);

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.saveTemplate("test_template", template, listener);

        // Verify template validation was called - the method will fail due to complex mocking requirements
        // but this covers the validation path and timestamp setting
        verify(template).isValid();
        verify(template).getCreatedTime();
        verify(template).getCreatedBy();
        verify(template).setCreatedTime(any(java.time.Instant.class));
        verify(template).setLastModified(any(java.time.Instant.class));
    }

    @Test
    public void testSaveTemplate_ExceptionInTryBlock() {
        // Test exception handling in the outer try-catch block
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.isValid()).thenThrow(new RuntimeException("Validation error"));

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.saveTemplate("test_template", template, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof RuntimeException);
        assertEquals("Validation error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testGetTemplate_NullTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate(null, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("Template name cannot be null, empty, or whitespace", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTemplate_NullTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate(null, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("Template name cannot be null, empty, or whitespace", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTemplate_EmptyTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate("", listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("Template name cannot be null, empty, or whitespace", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testGetTemplate_ExceptionInTryBlock() {
        // Test exception handling in the outer try-catch block by making threadPool throw
        when(client.threadPool()).thenThrow(new RuntimeException("ThreadPool error"));

        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate("test_template", listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof RuntimeException);
        assertEquals("ThreadPool error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTemplate_ExceptionInTryBlock() {
        // Test exception handling in the outer try-catch block by making threadPool throw
        when(client.threadPool()).thenThrow(new RuntimeException("ThreadPool error"));

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate("test_template", listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof RuntimeException);
        assertEquals("ThreadPool error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testListTemplates_ExceptionInTryBlock() {
        // Test exception handling in the outer try-catch block by making threadPool throw
        when(client.threadPool()).thenThrow(new RuntimeException("ThreadPool error"));

        @SuppressWarnings("unchecked")
        ActionListener<java.util.List<ContextManagementTemplate>> listener = mock(ActionListener.class);

        contextManagementTemplateService.listTemplates(listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof RuntimeException);
        assertEquals("ThreadPool error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testListTemplates_WithPaginationExceptionInTryBlock() {
        // Test exception handling in the outer try-catch block for paginated version
        when(client.threadPool()).thenThrow(new RuntimeException("ThreadPool error"));

        @SuppressWarnings("unchecked")
        ActionListener<java.util.List<ContextManagementTemplate>> listener = mock(ActionListener.class);

        contextManagementTemplateService.listTemplates(10, 50, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof RuntimeException);
        assertEquals("ThreadPool error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testListTemplates_NullListener() {
        // This should not throw an exception, but we can test that the method handles it gracefully
        try {
            contextManagementTemplateService.listTemplates(null);
            // If we get here without exception, that's fine - the method should handle null listeners gracefully
        } catch (Exception e) {
            // If an exception is thrown, it should be a meaningful one
            assertTrue(e instanceof IllegalArgumentException || e instanceof NullPointerException);
        }
    }

    @Test
    public void testGetTemplate_WhitespaceTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate("   ", listener);

        // Whitespace-only template names should now be rejected
        verify(listener).onFailure(any(IllegalArgumentException.class));
        verify(client, never()).threadPool();
    }

    @Test
    public void testGetTemplate_EmptyTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate("", listener);

        verify(listener).onFailure(any(IllegalArgumentException.class));
        verify(client, never()).threadPool();
    }

    @Test
    public void testGetTemplate_TabsAndSpacesTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate("\t  \n  ", listener);

        verify(listener).onFailure(any(IllegalArgumentException.class));
        verify(client, never()).threadPool();
    }

    @Test
    public void testDeleteTemplate_TabsAndSpacesTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate("\t  \n  ", listener);

        verify(listener).onFailure(any(IllegalArgumentException.class));
        verify(client, never()).threadPool();
    }

    @Test
    public void testSaveTemplate_TemplateWithExistingCreatedTime() {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.isValid()).thenReturn(true);
        when(template.getName()).thenReturn("test_template");
        when(template.getCreatedTime()).thenReturn(java.time.Instant.now()); // Already has created time
        when(template.getCreatedBy()).thenReturn("existing_user"); // Already has created by

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.saveTemplate("test_template", template, listener);

        // Verify template validation was called and existing values were checked
        verify(template).isValid();
        verify(template).getCreatedTime();
        verify(template).getCreatedBy();
        // Should call setLastModified but not setCreatedTime or setCreatedBy since they exist
        verify(template).setLastModified(any(java.time.Instant.class));
        verify(template, never()).setCreatedTime(any(java.time.Instant.class));
        verify(template, never()).setCreatedBy(anyString());
    }

    @Test
    public void testSaveTemplate_TemplateWithNullCreatedBy() {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.isValid()).thenReturn(true);
        when(template.getName()).thenReturn("test_template");
        when(template.getCreatedTime()).thenReturn(null);
        when(template.getCreatedBy()).thenReturn(null);

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.saveTemplate("test_template", template, listener);

        // Verify template validation was called
        verify(template).isValid();
        verify(template).getCreatedTime();
        verify(template).getCreatedBy();
        // Should set both created time and last modified
        verify(template).setCreatedTime(any(java.time.Instant.class));
        verify(template).setLastModified(any(java.time.Instant.class));
    }
}
