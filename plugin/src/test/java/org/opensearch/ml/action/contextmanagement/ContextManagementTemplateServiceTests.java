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
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.engine.VersionConflictEngineException;
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

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
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
        when(template.isValidName()).thenReturn(true);
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
        when(template.isValidName()).thenThrow(new RuntimeException("Validation error"));

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

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Context management name cannot be null"));
    }

    @Test
    public void testGetTemplate_EmptyTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate("", listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Context management name cannot be null"));
    }

    @Test
    public void testGetTemplate_WhitespaceTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<ContextManagementTemplate> listener = mock(ActionListener.class);

        contextManagementTemplateService.getTemplate("   ", listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Context management name cannot be null"));
    }

    @Test
    public void testDeleteTemplate_NullTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate(null, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Context management name cannot be null"));
    }

    @Test
    public void testUpdateTemplate_NullTemplateName() {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        @SuppressWarnings("unchecked")
        ActionListener<org.opensearch.action.update.UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate(null, template, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Context management name cannot be null"));
    }

    @Test
    public void testUpdateTemplate_InvalidTemplate() {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.isValid()).thenReturn(false);
        @SuppressWarnings("unchecked")
        ActionListener<org.opensearch.action.update.UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("test_template", template, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Invalid context management"));
    }

    @Test
    public void testListTemplates_NegativeFrom() {
        @SuppressWarnings("unchecked")
        ActionListener<java.util.List<ContextManagementTemplate>> listener = mock(ActionListener.class);

        contextManagementTemplateService.listTemplates(-1, 10, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
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
        when(template.isValidName()).thenThrow(new RuntimeException("Validation error"));

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
        when(template.isValidName()).thenReturn(true);
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
        when(template.isValidName()).thenReturn(true);
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

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
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

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
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

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Context management name cannot be null"));
    }

    @Test
    public void testDeleteTemplate_WhitespaceTemplateName() {
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.deleteTemplate("   ", listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Context management name cannot be null"));
    }

    private static final String STORED_TEMPLATE_JSON = "{\"name\":\"test_template\",\"description\":\"stored description\","
        + "\"hooks\":{\"PRE_LLM\":[{\"type\":\"SlidingWindowManager\",\"config\":{\"max_messages\":6}}],"
        + "\"POST_TOOL\":[{\"type\":\"ToolsOutputTruncateManager\",\"config\":{\"max_output_length\":100}}]},"
        + "\"created_time\":1000,\"last_modified\":1000,\"created_by\":\"creator\"}";

    private void mockStoredTemplate(String sourceJson, long seqNo, long primaryTerm) {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(sourceJson != null);
        if (sourceJson != null) {
            when(getResponse.getSourceAsBytesRef()).thenReturn(new BytesArray(sourceJson));
            when(getResponse.getSeqNo()).thenReturn(seqNo);
            when(getResponse.getPrimaryTerm()).thenReturn(primaryTerm);
        }
        doAnswer((Answer<Void>) invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any());
    }

    private void mockIndexResponse() {
        IndexResponse indexResponse = new IndexResponse(new ShardId("index", "uuid", 0), "test_template", 3, 1, 2, false);
        doAnswer((Answer<Void>) invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(client).index(any(IndexRequest.class), any());
    }

    private static ContextManagementTemplate templateWithHooks(String description, String hooksJson) throws Exception {
        String json = "{\"name\":\"test_template\""
            + (description != null ? ",\"description\":\"" + description + "\"" : "")
            + (hooksJson != null ? ",\"hooks\":" + hooksJson : "")
            + "}";
        XContentParser parser = JsonXContent.jsonXContent
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        return ContextManagementTemplate.parse(parser);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTemplate_ReplacesHooksAndKeepsUnsetFields() throws Exception {
        mockStoredTemplate(STORED_TEMPLATE_JSON, 7L, 2L);
        mockIndexResponse();
        ContextManagementTemplate template = templateWithHooks(
            null,
            "{\"POST_TOOL\":[{\"type\":\"ToolsOutputTruncateManager\",\"config\":{\"max_output_length\":200}}]}"
        );
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("test_template", template, listener);

        ArgumentCaptor<IndexRequest> requestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(client).index(requestCaptor.capture(), any());
        verify(client, never()).update(any(UpdateRequest.class), any());
        IndexRequest indexRequest = requestCaptor.getValue();
        assertEquals("test_template", indexRequest.id());
        assertEquals(7L, indexRequest.ifSeqNo());
        assertEquals(2L, indexRequest.ifPrimaryTerm());

        Map<String, Object> source = indexRequest.sourceAsMap();
        Map<String, Object> hooks = (Map<String, Object>) source.get("hooks");
        assertEquals(Set.of("POST_TOOL"), hooks.keySet());
        Map<String, Object> postTool = ((List<Map<String, Object>>) hooks.get("POST_TOOL")).get(0);
        assertEquals(200, ((Map<String, Object>) postTool.get("config")).get("max_output_length"));
        assertEquals("stored description", source.get("description"));
        assertEquals(1000L, ((Number) source.get("created_time")).longValue());
        assertEquals("creator", source.get("created_by"));
        assertTrue(((Number) source.get("last_modified")).longValue() > 1000L);

        ArgumentCaptor<UpdateResponse> responseCaptor = ArgumentCaptor.forClass(UpdateResponse.class);
        verify(listener).onResponse(responseCaptor.capture());
        assertEquals("test_template", responseCaptor.getValue().getId());
        assertEquals(3L, responseCaptor.getValue().getSeqNo());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTemplate_WithoutHooksKeepsStoredHooks() throws Exception {
        mockStoredTemplate(STORED_TEMPLATE_JSON, 7L, 2L);
        mockIndexResponse();
        ContextManagementTemplate template = templateWithHooks("new description", null);
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("test_template", template, listener);

        ArgumentCaptor<IndexRequest> requestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(client).index(requestCaptor.capture(), any());
        Map<String, Object> source = requestCaptor.getValue().sourceAsMap();
        assertEquals("new description", source.get("description"));
        assertEquals(Set.of("PRE_LLM", "POST_TOOL"), ((Map<String, Object>) source.get("hooks")).keySet());
        verify(listener).onResponse(any(UpdateResponse.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTemplate_NotFound() throws Exception {
        mockStoredTemplate(null, 0L, 0L);
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("test_template", templateWithHooks("d", null), listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.NOT_FOUND, ((OpenSearchStatusException) exceptionCaptor.getValue()).status());
        verify(client, never()).index(any(IndexRequest.class), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTemplate_IndexNotFoundException() throws Exception {
        doAnswer((Answer<Void>) invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("index not found"));
            return null;
        }).when(client).get(any(GetRequest.class), any());
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("test_template", templateWithHooks("d", null), listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.NOT_FOUND, ((OpenSearchStatusException) exceptionCaptor.getValue()).status());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTemplate_GetException() throws Exception {
        doAnswer((Answer<Void>) invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Get error"));
            return null;
        }).when(client).get(any(GetRequest.class), any());
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("test_template", templateWithHooks("d", null), listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("Get error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTemplate_StoredTemplateUnparseable() throws Exception {
        mockStoredTemplate("not json", 7L, 2L);
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("test_template", templateWithHooks("d", null), listener);

        verify(listener).onFailure(any(Exception.class));
        verify(client, never()).index(any(IndexRequest.class), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTemplate_IndexException() throws Exception {
        mockStoredTemplate(STORED_TEMPLATE_JSON, 7L, 2L);
        doAnswer((Answer<Void>) invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Update error"));
            return null;
        }).when(client).index(any(IndexRequest.class), any());
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("test_template", templateWithHooks("d", null), listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertEquals("Update error", exceptionCaptor.getValue().getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateTemplate_ConcurrentUpdateReturnsConflict() throws Exception {
        mockStoredTemplate(STORED_TEMPLATE_JSON, 7L, 2L);
        doAnswer((Answer<Void>) invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(new VersionConflictEngineException(new ShardId("index", "uuid", 0), "test_template", "version conflict"));
            return null;
        }).when(client).index(any(IndexRequest.class), any());
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("test_template", templateWithHooks("d", null), listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.CONFLICT, ((OpenSearchStatusException) exceptionCaptor.getValue()).status());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("updated concurrently"));
    }

    @Test
    public void testUpdateTemplate_EmptyTemplateName() {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("", template, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Context management name cannot be null"));
    }

    @Test
    public void testUpdateTemplate_WhitespaceTemplateName() {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);

        contextManagementTemplateService.updateTemplate("   ", template, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Context management name cannot be null"));
    }

    @Test
    public void testSaveTemplate_WithCreatedBy() throws Exception {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.isValidName()).thenReturn(true);
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

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Invalid context management"));
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

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Invalid context management"));
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

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("between 1 and 49 characters"));
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

    @Test
    public void testSaveTemplate_NameWithSpecialCharacters() {
        ContextManagementTemplate template = ContextManagementTemplate
            .builder()
            .name("test@template#name")
            .hooks(java.util.Map.of("pre_tool", java.util.List.of()))
            .build();

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.saveTemplate("test@template#name", template, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Invalid context management"));
    }

    @Test
    public void testSaveTemplate_NameWithControlCharacters() {
        ContextManagementTemplate template = ContextManagementTemplate
            .builder()
            .name("test\ntemplate")
            .hooks(java.util.Map.of("pre_tool", java.util.List.of()))
            .build();

        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);

        contextManagementTemplateService.saveTemplate("test\ntemplate", template, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Invalid context management"));
    }
}
