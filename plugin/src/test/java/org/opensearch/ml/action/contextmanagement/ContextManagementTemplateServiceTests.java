/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.index.IndexNotFoundException;
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
    private ThreadContext threadContext;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        // Create a real ThreadContext instead of mocking it
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);

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
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Invalid context management"));
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
        assertEquals("Validation error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testGetTemplate_NullTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate(null, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("context management name cannot be null, empty, or whitespace", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testGetTemplate_EmptyTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate("", listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("context management name cannot be null, empty, or whitespace", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testGetTemplate_WhitespaceTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate("   ", listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("context management name cannot be null, empty, or whitespace", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTemplate_NullTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate(null, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("context management name cannot be null, empty, or whitespace", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateTemplate_NullTemplateName() {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        @SuppressWarnings("unchecked")
        ActionListener<org.opensearch.action.update.UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate(null, template, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("context management name cannot be null, empty, or whitespace", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateTemplate_InvalidTemplate() {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.isValid()).thenReturn(false);
        @SuppressWarnings("unchecked")
        ActionListener<org.opensearch.action.update.UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("test_template", template, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Invalid context management"));
    }

    @Test
    public void testListTemplates_NegativeFrom() {
        @SuppressWarnings("unchecked")
        ActionListener<java.util.List<ContextManagementTemplate>> listener = mock(ActionListener.class);

        contextManagementTemplateService.listTemplates(-1, 10, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("[from] parameter cannot be negative, found [-1]", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testListTemplates_ZeroSize() {
        @SuppressWarnings("unchecked")
        ActionListener<java.util.List<ContextManagementTemplate>> listener = mock(ActionListener.class);

        contextManagementTemplateService.listTemplates(0, 0, listener);

        // The service doesn't validate size parameter, so this test should pass without exception
        verify(client).threadPool();
    }

    @Test
    public void testSaveTemplate_ExceptionInValidation() {
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
    public void testSaveTemplate_ThreadContextException() {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.isValid()).thenReturn(true);

        ThreadPool mockThreadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(mockThreadPool);
        when(mockThreadPool.getThreadContext()).thenThrow(new RuntimeException("ThreadContext error"));

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.saveTemplate("test_template", template, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("ThreadContext error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateTemplate_ThreadContextException() {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.isValid()).thenReturn(true);

        ThreadPool mockThreadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(mockThreadPool);
        when(mockThreadPool.getThreadContext()).thenThrow(new RuntimeException("ThreadContext error"));

        @SuppressWarnings("unchecked")
        ActionListener<org.opensearch.action.update.UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("test_template", template, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("ThreadContext error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testGetTemplate_Success() throws Exception {
        String templateName = "test_template";
        String templateJson = "{\"name\":\"test_template\",\"description\":\"test\",\"type\":\"SUMMARIZATION\",\"config\":{}}";
        BytesReference bytesRef = new BytesArray(templateJson);

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsBytesRef()).thenReturn(bytesRef);

        doAnswer((Answer<Void>) invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate(templateName, listener);

        verify(listener).onResponse(any(ContextManagementTemplate.class));
    }

    @Test
    public void testGetTemplate_NotFound() {
        String templateName = "nonexistent_template";

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);

        doAnswer((Answer<Void>) invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate(templateName, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("not found"));
    }

    @Test
    public void testGetTemplate_IndexNotFoundException() {
        String templateName = "test_template";

        doAnswer((Answer<Void>) invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("index not found"));
            return null;
        }).when(client).get(any(GetRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate(templateName, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("not found"));
    }

    @Test
    public void testGetTemplate_ParseException() throws Exception {
        String templateName = "test_template";
        String invalidJson = "{invalid json}";
        BytesReference bytesRef = new BytesArray(invalidJson);

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsBytesRef()).thenReturn(bytesRef);

        doAnswer((Answer<Void>) invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate(templateName, listener);

        verify(listener).onFailure(any(Exception.class));
    }

    @Test
    public void testGetTemplate_OtherException() {
        String templateName = "test_template";

        doAnswer((Answer<Void>) invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Some error"));
            return null;
        }).when(client).get(any(GetRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate(templateName, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("Some error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testListTemplates_OtherException() {
        doAnswer((Answer<Void>) invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Search error"));
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<List<ContextManagementTemplate>> listener = mock(ActionListener.class);

        contextManagementTemplateService.listTemplates(0, 10, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("Search error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTemplate_Success() {
        String templateName = "test_template";

        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(deleteResponse.getResult()).thenReturn(DeleteResponse.Result.DELETED);

        doAnswer((Answer<Void>) invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(DeleteRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate(templateName, listener);

        verify(listener).onResponse(true);
    }

    @Test
    public void testDeleteTemplate_NotFound() {
        String templateName = "test_template";

        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(deleteResponse.getResult()).thenReturn(DeleteResponse.Result.NOT_FOUND);

        doAnswer((Answer<Void>) invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(DeleteRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate(templateName, listener);

        verify(listener).onResponse(false);
    }

    @Test
    public void testDeleteTemplate_IndexNotFoundException() {
        String templateName = "test_template";

        doAnswer((Answer<Void>) invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("index not found"));
            return null;
        }).when(client).delete(any(DeleteRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate(templateName, listener);

        verify(listener).onResponse(false);
    }

    @Test
    public void testDeleteTemplate_OtherException() {
        String templateName = "test_template";

        doAnswer((Answer<Void>) invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Delete error"));
            return null;
        }).when(client).delete(any(DeleteRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate(templateName, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("Delete error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTemplate_EmptyTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate("", listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("context management name cannot be null, empty, or whitespace", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTemplate_WhitespaceTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate("   ", listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("context management name cannot be null, empty, or whitespace", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateTemplate_Success() throws Exception {
        String templateName = "test_template";
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.isValid()).thenReturn(true);
        when(template.toXContent(any(), any())).thenReturn(org.opensearch.common.xcontent.json.JsonXContent.contentBuilder());

        UpdateResponse updateResponse = mock(UpdateResponse.class);

        doAnswer((Answer<Void>) invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate(templateName, template, listener);

        verify(listener).onResponse(updateResponse);
        verify(template).setLastModified(any(Instant.class));
    }

    @Test
    public void testUpdateTemplate_IndexNotFoundException() throws Exception {
        String templateName = "test_template";
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.isValid()).thenReturn(true);
        when(template.toXContent(any(), any())).thenReturn(org.opensearch.common.xcontent.json.JsonXContent.contentBuilder());

        doAnswer((Answer<Void>) invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("index not found"));
            return null;
        }).when(client).update(any(UpdateRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate(templateName, template, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("not found"));
    }

    @Test
    public void testUpdateTemplate_OtherException() throws Exception {
        String templateName = "test_template";
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.isValid()).thenReturn(true);
        when(template.toXContent(any(), any())).thenReturn(org.opensearch.common.xcontent.json.JsonXContent.contentBuilder());

        doAnswer((Answer<Void>) invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Update error"));
            return null;
        }).when(client).update(any(UpdateRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate(templateName, template, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("Update error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateTemplate_EmptyTemplateName() {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("", template, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("context management name cannot be null, empty, or whitespace", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testUpdateTemplate_WhitespaceTemplateName() {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("   ", template, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("context management name cannot be null, empty, or whitespace", exceptionCaptor.getValue().getMessage());
    }

    @Test
    public void testSaveTemplate_WithCreatedBy() throws Exception {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.isValid()).thenReturn(true);
        when(template.getName()).thenReturn("test_template");
        when(template.getCreatedTime()).thenReturn(Instant.now());
        when(template.getCreatedBy()).thenReturn("existing_user");
        when(template.toXContent(any(), any())).thenReturn(org.opensearch.common.xcontent.json.JsonXContent.contentBuilder());

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.saveTemplate("test_template", template, listener);

        verify(template).getCreatedBy();
        verify(template, never()).setCreatedBy(anyString());
    }

    @Test
    public void testSaveTemplate_NameWithSpaces() {
        ContextManagementTemplate template = ContextManagementTemplate
            .builder()
            .name("test template")
            .hooks(java.util.Map.of("pre_tool", java.util.List.of()))
            .build();

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.saveTemplate("test template", template, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("must not contain spaces"));
    }

    @Test
    public void testSaveTemplate_NameWithCapitalLetters() {
        ContextManagementTemplate template = ContextManagementTemplate
            .builder()
            .name("TestTemplate")
            .hooks(java.util.Map.of("pre_tool", java.util.List.of()))
            .build();

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.saveTemplate("TestTemplate", template, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("capital letters"));
    }

    @Test
    public void testSaveTemplate_NameTooLong() {
        String longName = "a".repeat(50);
        ContextManagementTemplate template = ContextManagementTemplate
            .builder()
            .name(longName)
            .hooks(java.util.Map.of("pre_tool", java.util.List.of()))
            .build();

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.saveTemplate(longName, template, listener);

        ArgumentCaptor<IllegalArgumentException> exceptionCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("less than 50 characters"));
    }

    @Test
    public void testSaveTemplate_ValidName() {
        String validName = "valid_template_name";
        ContextManagementTemplate template = ContextManagementTemplate
            .builder()
            .name(validName)
            .hooks(java.util.Map.of("pre_tool", java.util.List.of()))
            .build();

        assertTrue(template.isValid());
    }
}
