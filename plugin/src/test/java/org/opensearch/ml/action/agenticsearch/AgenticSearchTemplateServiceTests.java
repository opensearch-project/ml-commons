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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
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
        service.register("missing_tmpl", "my-index", "desc", null, null, listener);

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
        service.register("tmpl", "my-index", "desc", null, null, listener);

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
        service.register("tmpl", "my-index", "desc", null, null, listener);

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

    @Test
    public void register_existingId_failsConflict() {
        stubStoredScript(TEMPLATE_BODY);
        stubIndexMapping(ImmutableMap.of("properties", ImmutableMap.of("title", ImmutableMap.of("type", "text"))));
        stubRenderSucceeds();
        doAnswer((Answer<Void>) inv -> {
            ActionListener<Boolean> l = inv.getArgument(1);
            l.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());
        doAnswer((Answer<Void>) inv -> {
            ActionListener<IndexResponse> l = inv.getArgument(1);
            l.onFailure(new org.opensearch.index.engine.VersionConflictEngineException(null, "tmpl", "already exists"));
            return null;
        }).when(client).index(any(IndexRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.register("tmpl", "my-index", "desc", null, null, listener);

        assertStatus(listener, RestStatus.CONFLICT, "already exists");
    }

    @Test
    public void register_setsOpTypeCreate() throws Exception {
        stubStoredScript(TEMPLATE_BODY);
        stubIndexMapping(ImmutableMap.of("properties", ImmutableMap.of("title", ImmutableMap.of("type", "text"))));
        stubRenderSucceeds();
        doAnswer((Answer<Void>) inv -> {
            ActionListener<Boolean> l = inv.getArgument(1);
            l.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());
        ArgumentCaptor<IndexRequest> reqCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        doAnswer((Answer<Void>) inv -> {
            ActionListener<IndexResponse> l = inv.getArgument(1);
            l.onResponse(mock(IndexResponse.class));
            return null;
        }).when(client).index(any(IndexRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.register("tmpl", "my-index", "desc", null, null, listener);

        verify(client).index(reqCaptor.capture(), any());
        assertEquals(org.opensearch.action.DocWriteRequest.OpType.CREATE, reqCaptor.getValue().opType());
    }

    @Test
    public void register_withProvidedSchema_storesItWithoutDeriving() throws Exception {
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

        // A caller-supplied schema is stored as sent, not derived. lex_query is marked
        // optional here though the body makes it required, so the stored value confirms
        // the provided schema was used.
        Map<String, Object> provided = ImmutableMap
            .of(
                "lex_query",
                ImmutableMap.of("type", "string", "required", false),
                "size",
                ImmutableMap.of("type", "number", "required", false)
            );

        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.register("tmpl", "my-index", "desc", provided, null, listener);

        ArgumentCaptor<AgenticSearchTemplate> captor = ArgumentCaptor.forClass(AgenticSearchTemplate.class);
        verify(listener).onResponse(captor.capture());
        Map<String, Object> schema = captor.getValue().getParamSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> lex = (Map<String, Object>) schema.get("lex_query");
        assertEquals(Boolean.FALSE, lex.get("required"));
    }

    @Test
    public void register_withInvalidProvidedSchema_failsValidation() {
        stubStoredScript(TEMPLATE_BODY);
        stubIndexMapping(ImmutableMap.of("properties", ImmutableMap.of("title", ImmutableMap.of("type", "text"))));

        // An enum value that does not fit the declared numeric type is rejected before store.
        Map<String, Object> provided = ImmutableMap
            .of("size", ImmutableMap.of("type", "number", "enum", java.util.Arrays.asList("not-a-number")));

        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.register("tmpl", "my-index", "desc", provided, null, listener);

        ArgumentCaptor<Exception> ex = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(ex.capture());
        assertTrue(ex.getValue() instanceof IllegalArgumentException);
        verify(client, never()).index(any(IndexRequest.class), any());
    }

    @Test
    public void register_withProvidedSchema_preflightRenderFailure_propagates() {
        stubStoredScript(TEMPLATE_BODY);
        stubIndexMapping(ImmutableMap.of("properties", ImmutableMap.of("title", ImmutableMap.of("type", "text"))));
        // Params exist in the body, but rendering throws, so pre-flight fails and
        // nothing is stored.
        when(scriptService.compile(any(Script.class), any())).thenThrow(new IllegalArgumentException("bad mustache"));

        Map<String, Object> provided = ImmutableMap.of("lex_query", ImmutableMap.of("type", "string", "required", true));

        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.register("tmpl", "my-index", "desc", provided, null, listener);

        ArgumentCaptor<Exception> ex = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(ex.capture());
        assertTrue(ex.getValue() instanceof IllegalArgumentException);
        assertTrue(ex.getValue().getMessage().contains("failed to render"));
        verify(client, never()).index(any(IndexRequest.class), any());
    }

    @Test
    public void register_withProvidedSchema_unknownParam_failsBadRequest() {
        stubStoredScript(TEMPLATE_BODY);
        stubIndexMapping(ImmutableMap.of("properties", ImmutableMap.of("title", ImmutableMap.of("type", "text"))));

        // A param the Mustache body does not reference is rejected before store.
        Map<String, Object> provided = ImmutableMap.of("not_in_body", ImmutableMap.of("type", "string", "required", false));

        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.register("tmpl", "my-index", "desc", provided, null, listener);

        ArgumentCaptor<Exception> ex = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(ex.capture());
        assertTrue(ex.getValue() instanceof IllegalArgumentException);
        assertTrue(ex.getValue().getMessage().contains("not a parameter of template body"));
        verify(client, never()).index(any(IndexRequest.class), any());
    }

    // ---- structural enrichment ---------------------------------------------

    @Test
    public void applyStructuralEnrichment_boolAndArrayGetGenericDescriptions() {
        // Array and boolean params carry no locatable clause, so they get a generic
        // type-only description.
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("flag", specMap("boolean", false, ""));
        schema.put("extra", specMap("array", false, ""));
        TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);

        service.applyStructuralEnrichment(schema, markers, new LinkedHashMap<>(), null);

        assertEquals("Set to true to enable the optional flag clause.", descOf(schema, "flag"));
        assertEquals("A JSON array or object passed as a raw JSON string.", descOf(schema, "extra"));
    }

    @Test
    public void applyStructuralEnrichment_doesNotOverwriteCallerDescriptionOrEnum() {
        // A param that already carries a description and an enum is left untouched, even
        // though its slot would otherwise classify as a sort order with an asc/desc enum.
        Map<String, Object> schema = new LinkedHashMap<>();
        Map<String, Object> sortOrder = specMap("string", false, "Caller-set sort direction.");
        sortOrder.put("enum", Arrays.asList("ASC", "DESC"));
        schema.put("sort_order", sortOrder);
        TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);
        Object marker = markers.renderParams().get("sort_order");
        Map<String, Object> rendered = ImmutableMap.of("sort", List.of(ImmutableMap.of("price", ImmutableMap.of("order", marker))));

        service.applyStructuralEnrichment(schema, markers, rendered, null);

        assertEquals("Caller-set sort direction.", descOf(schema, "sort_order"));
        assertEquals(Arrays.asList("ASC", "DESC"), ((Map<?, ?>) schema.get("sort_order")).get("enum"));
    }

    @Test
    public void register_enrichmentRuns_addsDescriptionFromRender() {
        // End-to-end through register: the marker render locates lex_query in a match on
        // title, so the stored schema gets a full-text description.
        stubStoredScript(TEMPLATE_BODY);
        stubIndexMapping(ImmutableMap.of("properties", ImmutableMap.of("title", ImmutableMap.of("type", "text"))));
        stubRenderEchoesMarkerIntoMatch();
        stubStoreSucceeds();

        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.register("tmpl", "my-index", "desc", null, null, listener);

        ArgumentCaptor<AgenticSearchTemplate> captor = ArgumentCaptor.forClass(AgenticSearchTemplate.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Full-text query matched against the title field.", descOf(captor.getValue().getParamSchema(), "lex_query"));
    }

    @Test
    public void register_enrichmentRenderFails_stillStoresBaseSchema() {
        // If enrichment's marker render is not parseable, enrichment is skipped and the base
        // derived schema is stored; registration must still succeed (never fail on enrichment).
        stubStoredScript(TEMPLATE_BODY);
        stubIndexMapping(ImmutableMap.of("properties", ImmutableMap.of("title", ImmutableMap.of("type", "text"))));
        stubRenderMarkerFailsSampleSucceeds();
        stubStoreSucceeds();

        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.register("tmpl", "my-index", "desc", null, null, listener);

        ArgumentCaptor<AgenticSearchTemplate> captor = ArgumentCaptor.forClass(AgenticSearchTemplate.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("", descOf(captor.getValue().getParamSchema(), "lex_query"));
    }

    @Test
    public void register_derivesTemplateDescriptionWhenCallerOmitsIt() {
        // No caller description: the derive path assembles a template-level one from the
        // body's recovered clauses (here, a full-text match on title).
        stubStoredScript(TEMPLATE_BODY);
        stubIndexMapping(ImmutableMap.of("properties", ImmutableMap.of("title", ImmutableMap.of("type", "text"))));
        stubRenderEchoesMarkerIntoMatch();
        stubStoreSucceeds();

        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.register("tmpl", "my-index", null, null, null, listener);

        ArgumentCaptor<AgenticSearchTemplate> captor = ArgumentCaptor.forClass(AgenticSearchTemplate.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Full-text search over title.", captor.getValue().getDescription());
    }

    @Test
    public void register_keepsCallerDescriptionOverDerived() {
        // A caller-supplied description always wins over the derived one.
        stubStoredScript(TEMPLATE_BODY);
        stubIndexMapping(ImmutableMap.of("properties", ImmutableMap.of("title", ImmutableMap.of("type", "text"))));
        stubRenderEchoesMarkerIntoMatch();
        stubStoreSucceeds();

        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.register("tmpl", "my-index", "Caller-authored description.", null, null, listener);

        ArgumentCaptor<AgenticSearchTemplate> captor = ArgumentCaptor.forClass(AgenticSearchTemplate.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Caller-authored description.", captor.getValue().getDescription());
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

    // ---- get ---------------------------------------------------------------

    @Test
    public void getTemplate_success_parsesStoredDoc() {
        stubGet(TEMPLATE_DOC, true);
        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.getTemplate("tmpl", listener);

        ArgumentCaptor<AgenticSearchTemplate> captor = ArgumentCaptor.forClass(AgenticSearchTemplate.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("tmpl", captor.getValue().getTemplateId());
    }

    @Test
    public void getTemplate_notFound_failsNotFound() {
        stubGet(null, false);
        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.getTemplate("missing", listener);
        assertStatus(listener, RestStatus.NOT_FOUND, "not found");
    }

    @Test
    public void getTemplate_indexNotFound_failsNotFound() {
        stubGetFailure(new IndexNotFoundException("idx"));
        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.getTemplate("tmpl", listener);
        assertStatus(listener, RestStatus.NOT_FOUND, "not found");
    }

    @Test
    public void getTemplate_otherException_propagates() {
        stubGetFailure(new RuntimeException("boom"));
        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplate> listener = mock(ActionListener.class);
        service.getTemplate("tmpl", listener);
        ArgumentCaptor<Exception> ex = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(ex.capture());
        assertEquals("boom", ex.getValue().getMessage());
    }

    // ---- list --------------------------------------------------------------

    @Test
    public void listTemplates_success_returnsParsedTemplatesAndTotal() {
        stubSearch(new BytesArray(TEMPLATE_DOC), 5L);
        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplateService.MLListResult> listener = mock(ActionListener.class);
        service.listTemplates(0, 10, listener);

        ArgumentCaptor<AgenticSearchTemplateService.MLListResult> captor = ArgumentCaptor
            .forClass(AgenticSearchTemplateService.MLListResult.class);
        verify(listener).onResponse(captor.capture());
        assertEquals(1, captor.getValue().templates.size());
        assertEquals(5L, captor.getValue().total);
        assertEquals("tmpl", captor.getValue().templates.get(0).getTemplateId());
    }

    @Test
    public void listTemplates_indexNotFound_returnsEmpty() {
        stubSearchFailure(new IndexNotFoundException("idx"));
        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplateService.MLListResult> listener = mock(ActionListener.class);
        service.listTemplates(0, 10, listener);

        ArgumentCaptor<AgenticSearchTemplateService.MLListResult> captor = ArgumentCaptor
            .forClass(AgenticSearchTemplateService.MLListResult.class);
        verify(listener).onResponse(captor.capture());
        assertTrue(captor.getValue().templates.isEmpty());
        assertEquals(0L, captor.getValue().total);
    }

    @Test
    public void listTemplates_otherException_propagates() {
        stubSearchFailure(new RuntimeException("search boom"));
        @SuppressWarnings("unchecked")
        ActionListener<AgenticSearchTemplateService.MLListResult> listener = mock(ActionListener.class);
        service.listTemplates(0, 10, listener);
        ArgumentCaptor<Exception> ex = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(ex.capture());
        assertEquals("search boom", ex.getValue().getMessage());
    }

    // ---- delete ------------------------------------------------------------

    @Test
    public void deleteTemplate_deleted_returnsTrue() {
        stubDelete(DeleteResponse.Result.DELETED, null);
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);
        service.deleteTemplate("tmpl", listener);
        verify(listener).onResponse(true);
    }

    @Test
    public void deleteTemplate_notFoundResult_failsNotFound() {
        stubDelete(DeleteResponse.Result.NOT_FOUND, null);
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);
        service.deleteTemplate("tmpl", listener);
        assertStatus(listener, RestStatus.NOT_FOUND, "not found");
    }

    @Test
    public void deleteTemplate_indexNotFound_failsNotFound() {
        stubDelete(null, new IndexNotFoundException("idx"));
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);
        service.deleteTemplate("tmpl", listener);
        assertStatus(listener, RestStatus.NOT_FOUND, "not found");
    }

    @Test
    public void deleteTemplate_otherException_propagates() {
        stubDelete(null, new RuntimeException("del boom"));
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> listener = mock(ActionListener.class);
        service.deleteTemplate("tmpl", listener);
        ArgumentCaptor<Exception> ex = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(ex.capture());
        assertEquals("del boom", ex.getValue().getMessage());
    }

    // ---- update: not-found -------------------------------------------------

    @Test
    public void update_noParamSchema_documentMissing_failsNotFound() {
        AgenticSearchTemplate patch = AgenticSearchTemplate.builder().templateId("tmpl").description("d").build();
        doAnswer((Answer<Void>) inv -> {
            ActionListener<UpdateResponse> l = inv.getArgument(1);
            l.onFailure(new org.opensearch.index.engine.DocumentMissingException(null, "tmpl"));
            return null;
        }).when(client).update(any(UpdateRequest.class), any());

        @SuppressWarnings("unchecked")
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        service.updateTemplate("tmpl", patch, listener);
        assertStatus(listener, RestStatus.NOT_FOUND, "not found");
    }

    // ---- helpers -----------------------------------------------------------

    // A stored template doc as persisted in the system index (parsed by get/list).
    private static final String TEMPLATE_DOC = "{\"template_id\":\"tmpl\",\"index_binding\":\"my-index\","
        + "\"param_schema\":{\"lex_query\":{\"type\":\"string\",\"required\":true}}}";

    private void stubGet(String source, boolean exists) {
        GetResponse response = mock(GetResponse.class);
        when(response.isExists()).thenReturn(exists);
        if (source != null) {
            when(response.getSourceAsBytesRef()).thenReturn(new BytesArray(source));
        }
        doAnswer((Answer<Void>) inv -> {
            ActionListener<GetResponse> l = inv.getArgument(1);
            l.onResponse(response);
            return null;
        }).when(client).get(any(GetRequest.class), any());
    }

    private void stubGetFailure(Exception e) {
        doAnswer((Answer<Void>) inv -> {
            ActionListener<GetResponse> l = inv.getArgument(1);
            l.onFailure(e);
            return null;
        }).when(client).get(any(GetRequest.class), any());
    }

    private void stubSearch(BytesArray hitSource, long total) {
        SearchResponse response = mock(SearchResponse.class);
        SearchHit hit = new SearchHit(1);
        hit.sourceRef(hitSource);
        SearchHits hits = new SearchHits(
            new SearchHit[] { hit },
            new org.apache.lucene.search.TotalHits(total, org.apache.lucene.search.TotalHits.Relation.EQUAL_TO),
            1.0f
        );
        when(response.getHits()).thenReturn(hits);
        doAnswer((Answer<Void>) inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(response);
            return null;
        }).when(client).search(any(SearchRequest.class), any());
    }

    private void stubSearchFailure(Exception e) {
        doAnswer((Answer<Void>) inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onFailure(e);
            return null;
        }).when(client).search(any(SearchRequest.class), any());
    }

    private void stubDelete(DeleteResponse.Result result, Exception failure) {
        doAnswer((Answer<Void>) inv -> {
            ActionListener<DeleteResponse> l = inv.getArgument(1);
            if (failure != null) {
                l.onFailure(failure);
            } else {
                DeleteResponse response = mock(DeleteResponse.class);
                when(response.getResult()).thenReturn(result);
                l.onResponse(response);
            }
            return null;
        }).when(client).delete(any(DeleteRequest.class), any());
    }

    private static void assertStatus(ActionListener<?> listener, RestStatus expected, String messageSubstring) {
        ArgumentCaptor<Exception> ex = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(ex.capture());
        assertTrue(ex.getValue() instanceof org.opensearch.OpenSearchStatusException);
        assertEquals(expected, ((org.opensearch.OpenSearchStatusException) ex.getValue()).status());
        assertTrue(ex.getValue().getMessage().contains(messageSubstring));
    }

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

    private void stubStoreSucceeds() {
        doAnswer((Answer<Void>) inv -> {
            ActionListener<Boolean> l = inv.getArgument(1);
            l.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLIndexIfAbsent(any(), any());
        doAnswer((Answer<Void>) inv -> {
            ActionListener<IndexResponse> l = inv.getArgument(1);
            l.onResponse(mock(IndexResponse.class));
            return null;
        }).when(client).index(any(IndexRequest.class), any());
    }

    /**
     * Render stub where the marker (all-filled) render returns invalid JSON so enrichment
     * degrades, while sample-value renders (pre-flight, defaults) stay valid so registration
     * still succeeds.
     */
    private void stubRenderMarkerFailsSampleSucceeds() {
        TemplateScript.Factory factory = mock(TemplateScript.Factory.class);
        when(scriptService.compile(any(Script.class), any())).thenReturn(factory);
        when(factory.newInstance(any())).thenAnswer((Answer<TemplateScript>) inv -> {
            Map<String, Object> params = inv.getArgument(0);
            TemplateScript ts = mock(TemplateScript.class);
            when(ts.execute()).thenReturn(firstStringMarker(params) != null ? "NOT JSON" : "{\"query\":{\"match_all\":{}}}");
            return ts;
        });
    }

    /**
     * Render stub where the marker render echoes the first string marker into a match on
     * title, so enrichment locates that param and writes a full-text description.
     */
    private void stubRenderEchoesMarkerIntoMatch() {
        TemplateScript.Factory factory = mock(TemplateScript.Factory.class);
        when(scriptService.compile(any(Script.class), any())).thenReturn(factory);
        when(factory.newInstance(any())).thenAnswer((Answer<TemplateScript>) inv -> {
            Map<String, Object> params = inv.getArgument(0);
            TemplateScript ts = mock(TemplateScript.class);
            String marker = firstStringMarker(params);
            when(ts.execute())
                .thenReturn(marker != null ? "{\"query\":{\"match\":{\"title\":\"" + marker + "\"}}}" : "{\"query\":{\"match_all\":{}}}");
            return ts;
        });
    }

    private static String firstStringMarker(Map<String, Object> params) {
        for (Object v : params.values()) {
            if (v instanceof String && ((String) v).startsWith(TemplateStructureAnalyzer.MARKER_PREFIX)) {
                return (String) v;
            }
        }
        return null;
    }

    private static Map<String, Object> specMap(String type, boolean required, String description) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("type", type);
        spec.put("required", required);
        if (description != null) {
            spec.put("description", description);
        }
        return spec;
    }

    @SuppressWarnings("unchecked")
    private static String descOf(Map<String, Object> schema, String param) {
        return (String) ((Map<String, Object>) schema.get(param)).get("description");
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
