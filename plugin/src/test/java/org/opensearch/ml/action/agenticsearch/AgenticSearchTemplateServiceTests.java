/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.opensearch.action.admin.cluster.storedscripts.GetStoredScriptRequest;
import org.opensearch.action.admin.cluster.storedscripts.GetStoredScriptResponse;
import org.opensearch.action.admin.indices.get.GetIndexRequest;
import org.opensearch.action.admin.indices.get.GetIndexResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.agenticsearch.AgenticSearchTemplate;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptService;
import org.opensearch.script.StoredScriptSource;
import org.opensearch.script.TemplateScript;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.ClusterAdminClient;
import org.opensearch.transport.client.IndicesAdminClient;

import com.google.common.collect.ImmutableMap;

/**
 * Tests for {@link AgenticSearchTemplateService} register and update: the
 * derive/validate/store path, the no-stored-script and missing-index error cases, and
 * the version gate on the validated schema update.
 */
public class AgenticSearchTemplateServiceTests extends OpenSearchTestCase {

    @Mock
    private MLIndicesHandler mlIndicesHandler;
    @Mock
    private Client client;
    @Mock
    private AdminClient adminClient;
    @Mock
    private ClusterAdminClient clusterAdminClient;
    @Mock
    private IndicesAdminClient indicesAdminClient;
    @Mock
    private ClusterService clusterService;
    @Mock
    private ScriptService scriptService;
    @Mock
    private ThreadPool threadPool;

    private AgenticSearchTemplateService service;

    // A minimal but real Mustache _search body: one required root value (lex_query) and
    // one optional section (size with an inverted-section default). Renders to legal JSON
    // both all-filled and required-only, so pre-flight passes.
    private static final String TEMPLATE_BODY = "{\"size\":{{size}}{{^size}}10{{/size}},"
        + "\"query\":{\"multi_match\":{\"query\":\"{{lex_query}}\",\"fields\":[\"title\"]}}}";

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        ThreadContext threadContext = new ThreadContext(Settings.builder().build());
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);
        service = new AgenticSearchTemplateService(mlIndicesHandler, client, clusterService, scriptService, NamedXContentRegistry.EMPTY);
    }

    // ---- register ----------------------------------------------------------

    @Test
    public void register_noStoredScript_failsBadRequest() {
        stubStoredScript(null); // no _scripts body

        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.register("missing_tmpl", "my-index", "desc", null, listener);

        ArgumentCaptor<Exception> ex = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(ex.capture());
        assertTrue(ex.getValue() instanceof org.opensearch.OpenSearchStatusException);
        assertEquals(RestStatus.BAD_REQUEST, ((org.opensearch.OpenSearchStatusException) ex.getValue()).status());
        assertTrue(ex.getValue().getMessage().contains("No stored search template"));
    }

    @Test
    public void register_missingTargetIndex_failsBadRequest() {
        stubStoredScript(TEMPLATE_BODY);
        // A missing target index is a bad request, not a missing template resource.
        doAnswer((Answer<Void>) inv -> {
            ActionListener<GetIndexResponse> l = inv.getArgument(1);
            l.onFailure(new IndexNotFoundException("my-index"));
            return null;
        }).when(indicesAdminClient).getIndex(any(GetIndexRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.register("tmpl", "my-index", "desc", null, listener);

        ArgumentCaptor<Exception> ex = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(ex.capture());
        assertTrue(ex.getValue() instanceof org.opensearch.OpenSearchStatusException);
        assertEquals(RestStatus.BAD_REQUEST, ((org.opensearch.OpenSearchStatusException) ex.getValue()).status());
        assertTrue(ex.getValue().getMessage().contains("Index does not exist"));
    }

    @Test
    public void register_success_derivesSchemaAndStores() throws Exception {
        stubStoredScript(TEMPLATE_BODY);
        stubIndexMapping(ImmutableMap.of("properties", ImmutableMap.of("title", ImmutableMap.of("type", "text"))));
        stubRenderSucceeds();
        doAnswer((Answer<Void>) inv -> {
            ActionListener<Boolean> l = inv.getArgument(1);
            l.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());
        IndexResponse indexResponse = mock(IndexResponse.class);
        doAnswer((Answer<Void>) inv -> {
            ActionListener<IndexResponse> l = inv.getArgument(1);
            l.onResponse(indexResponse);
            return null;
        }).when(client).index(any(IndexRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.register("tmpl", "my-index", "desc", null, listener);

        ArgumentCaptor<AgenticSearchTemplate> captor = ArgumentCaptor.forClass(AgenticSearchTemplate.class);
        verify(listener).onResponse(captor.capture());
        AgenticSearchTemplate stored = captor.getValue();
        assertEquals("tmpl", stored.getTemplateId());
        assertEquals("my-index", stored.getIndexBinding());
        // Derived from the body: lex_query is a required root value, size is optional.
        Map<String, Object> schema = stored.getParamSchema();
        assertTrue(schema.containsKey("lex_query"));
        assertTrue(schema.containsKey("size"));
        @SuppressWarnings("unchecked")
        Map<String, Object> lex = (Map<String, Object>) schema.get("lex_query");
        assertEquals(Boolean.TRUE, lex.get("required"));
        @SuppressWarnings("unchecked")
        Map<String, Object> size = (Map<String, Object>) schema.get("size");
        assertEquals(Boolean.FALSE, size.get("required"));
    }

    // ---- update: optimistic concurrency ------------------------------------

    @Test
    public void update_noParamSchema_isBlindMergeWithoutVersionGate() {
        // A patch with no param_schema does not read first, so the write is not gated.
        AgenticSearchTemplate patch = AgenticSearchTemplate.builder().templateId("tmpl").description("new desc").build();
        UpdateResponse updateResponse = mock(UpdateResponse.class);
        ArgumentCaptor<UpdateRequest> reqCaptor = ArgumentCaptor.forClass(UpdateRequest.class);
        doAnswer((Answer<Void>) inv -> {
            ActionListener<UpdateResponse> l = inv.getArgument(1);
            l.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        service.updateTemplate("tmpl", patch, listener);

        verify(client).update(reqCaptor.capture(), any());
        // No version gate: seqNo stays at the unassigned sentinel.
        assertEquals(org.opensearch.index.seqno.SequenceNumbers.UNASSIGNED_SEQ_NO, reqCaptor.getValue().ifSeqNo());
        verify(client, never()).get(any(GetRequest.class), any());
        verify(listener).onResponse(updateResponse);
    }

    @Test
    public void update_withParamSchema_gatesWriteOnReadSeqNoAndPrimaryTerm() {
        // A schema edit reads the stored doc, so the write is gated on that read's
        // seqNo/primaryTerm.
        String stored = "{\"template_id\":\"tmpl\",\"param_schema\":{"
            + "\"lex_query\":{\"type\":\"string\",\"required\":true},"
            + "\"size\":{\"type\":\"number\",\"required\":false}}}";
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsBytesRef()).thenReturn(new BytesArray(stored));
        when(getResponse.getSeqNo()).thenReturn(7L);
        when(getResponse.getPrimaryTerm()).thenReturn(3L);
        doAnswer((Answer<Void>) inv -> {
            ActionListener<GetResponse> l = inv.getArgument(1);
            l.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any());
        stubStoredScript(TEMPLATE_BODY);
        stubRenderSucceeds();
        UpdateResponse updateResponse = mock(UpdateResponse.class);
        ArgumentCaptor<UpdateRequest> reqCaptor = ArgumentCaptor.forClass(UpdateRequest.class);
        doAnswer((Answer<Void>) inv -> {
            ActionListener<UpdateResponse> l = inv.getArgument(1);
            l.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), any());

        // Edit a description on an existing param: a valid, renderable merge.
        AgenticSearchTemplate patch = AgenticSearchTemplate
            .builder()
            .templateId("tmpl")
            .paramSchema(ImmutableMap.of("size", ImmutableMap.of("description", "How many results.")))
            .build();

        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        service.updateTemplate("tmpl", patch, listener);

        verify(client).update(reqCaptor.capture(), any());
        assertEquals(7L, reqCaptor.getValue().ifSeqNo());
        assertEquals(3L, reqCaptor.getValue().ifPrimaryTerm());
        verify(listener).onResponse(updateResponse);
    }

    // ---- helpers -----------------------------------------------------------

    /** Stub the script-compile chain so pre-flight rendering returns a fixed legal body. */
    private void stubRenderSucceeds() {
        TemplateScript.Factory factory = mock(TemplateScript.Factory.class);
        TemplateScript templateScript = mock(TemplateScript.class);
        when(scriptService.compile(any(Script.class), any())).thenReturn(factory);
        when(factory.newInstance(any())).thenReturn(templateScript);
        when(templateScript.execute()).thenReturn("{\"query\":{\"match_all\":{}}}");
    }

    private void stubStoredScript(String body) {
        GetStoredScriptResponse response = mock(GetStoredScriptResponse.class);
        when(response.getSource())
            .thenReturn(body == null ? null : new StoredScriptSource("mustache", body, java.util.Collections.emptyMap()));
        doAnswer((Answer<Void>) inv -> {
            ActionListener<GetStoredScriptResponse> l = inv.getArgument(1);
            l.onResponse(response);
            return null;
        }).when(clusterAdminClient).getStoredScript(any(GetStoredScriptRequest.class), any());
    }

    private void stubIndexMapping(Map<String, Object> mappingSource) {
        GetIndexResponse response = mock(GetIndexResponse.class);
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);
        when(mappingMetadata.getSourceAsMap()).thenReturn(mappingSource);
        Map<String, MappingMetadata> mappings = ImmutableMap.of("my-index", mappingMetadata);
        when(response.mappings()).thenReturn(mappings);
        doAnswer((Answer<Void>) inv -> {
            ActionListener<GetIndexResponse> l = inv.getArgument(1);
            l.onResponse(response);
            return null;
        }).when(indicesAdminClient).getIndex(any(GetIndexRequest.class), any());
    }
}
