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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.opensearch.action.admin.cluster.storedscripts.GetStoredScriptRequest;
import org.opensearch.action.admin.cluster.storedscripts.GetStoredScriptResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.get.MultiGetItemResponse;
import org.opensearch.action.get.MultiGetRequest;
import org.opensearch.action.get.MultiGetResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.AliasMetadata;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
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

/**
 * Tests for the query-time {@link org.opensearch.ml.common.agenticsearch.AgenticSearchTemplateResolver}
 * methods of {@link AgenticSearchTemplateService}: schema lookup and rendering.
 */
public class AgenticSearchTemplateResolverTests extends OpenSearchTestCase {

    @Mock
    private MLIndicesHandler mlIndicesHandler;
    @Mock
    private Client client;
    @Mock
    private AdminClient adminClient;
    @Mock
    private ClusterAdminClient clusterAdminClient;
    @Mock
    private ClusterService clusterService;
    @Mock
    private ScriptService scriptService;
    @Mock
    private ThreadPool threadPool;

    private ThreadContext threadContext;
    private AgenticSearchTemplateService service;

    private static final String CALLER_MARKER = "_test_caller_marker";
    private static final String DOC = "{\"template_id\":\"product_search\",\"index_binding\":\"products\","
        + "\"param_schema\":{\"q\":{\"type\":\"string\",\"required\":true}}}";

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        threadContext = new ThreadContext(Settings.builder().build());
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);
        service = new AgenticSearchTemplateService(mlIndicesHandler, client, clusterService, scriptService, NamedXContentRegistry.EMPTY);
    }

    private static MultiGetItemResponse found(String id, String source) {
        GetResponse get = mock(GetResponse.class);
        when(get.isExists()).thenReturn(true);
        when(get.getSourceAsBytesRef()).thenReturn(new BytesArray(source));
        MultiGetItemResponse item = mock(MultiGetItemResponse.class);
        when(item.getId()).thenReturn(id);
        when(item.getResponse()).thenReturn(get);
        return item;
    }

    private static MultiGetItemResponse missing(String id) {
        GetResponse get = mock(GetResponse.class);
        when(get.isExists()).thenReturn(false);
        MultiGetItemResponse item = mock(MultiGetItemResponse.class);
        when(item.getId()).thenReturn(id);
        when(item.getResponse()).thenReturn(get);
        return item;
    }

    private static MultiGetItemResponse failed(String id) {
        MultiGetItemResponse item = mock(MultiGetItemResponse.class);
        when(item.getId()).thenReturn(id);
        when(item.isFailed()).thenReturn(true);
        return item;
    }

    private void stubMultiGet(AtomicReference<Object> markerSeen, MultiGetItemResponse... items) {
        doAnswer((Answer<Void>) inv -> {
            markerSeen.set(threadContext.getTransient(CALLER_MARKER));
            ActionListener<MultiGetResponse> l = inv.getArgument(1);
            l.onResponse(new MultiGetResponse(items));
            return null;
        }).when(client).multiGet(any(MultiGetRequest.class), any());
    }

    private void stubStoredScript(String body, AtomicReference<Object> markerSeen) {
        GetStoredScriptResponse response = mock(GetStoredScriptResponse.class);
        when(response.getSource()).thenReturn(body == null ? null : new StoredScriptSource("mustache", body, Collections.emptyMap()));
        doAnswer((Answer<Void>) inv -> {
            markerSeen.set(threadContext.getTransient(CALLER_MARKER));
            ActionListener<GetStoredScriptResponse> l = inv.getArgument(1);
            l.onResponse(response);
            return null;
        }).when(clusterAdminClient).getStoredScript(any(GetStoredScriptRequest.class), any());
    }

    private void stubRender(String rendered, AtomicReference<Map<String, Object>> paramsSeen) {
        TemplateScript.Factory factory = mock(TemplateScript.Factory.class);
        when(scriptService.compile(any(Script.class), any())).thenReturn(factory);
        when(factory.newInstance(any())).thenAnswer((Answer<TemplateScript>) inv -> {
            paramsSeen.set(inv.getArgument(0));
            TemplateScript script = mock(TemplateScript.class);
            when(script.execute()).thenReturn(rendered);
            return script;
        });
    }

    // ---- getTemplates -------------------------------------------------------

    @Test
    public void getTemplates_returnsRegisteredAndSkipsMissing_asPlugin() {
        AtomicReference<Object> markerSeen = new AtomicReference<>();
        AtomicReference<Object> scriptMarkerSeen = new AtomicReference<>();
        stubMultiGet(markerSeen, found("product_search", DOC), missing("gone"), failed("broken"));
        stubStoredScript("{}", scriptMarkerSeen);
        threadContext.putTransient(CALLER_MARKER, "caller");

        AtomicReference<Map<String, AgenticSearchTemplate>> result = new AtomicReference<>();
        service
            .getTemplates(
                List.of("product_search", "gone", "broken"),
                "products",
                ActionListener.wrap(result::set, e -> fail(e.getMessage()))
            );

        assertEquals(List.of("product_search"), List.copyOf(result.get().keySet()));
        AgenticSearchTemplate template = result.get().get("product_search");
        assertEquals("products", template.getIndexBinding());
        assertTrue(template.getParamSchema().containsKey("q"));
        // The schema docs live in a system index, so the read runs stashed (without the caller's transient).
        assertNull(markerSeen.get());
        // ...while the stored-script access check runs as the caller.
        assertEquals("caller", scriptMarkerSeen.get());
        // ...and the caller's context is restored afterwards.
        assertEquals("caller", threadContext.getTransient(CALLER_MARKER));
    }

    @Test
    public void getTemplates_dropsTemplatesTheCallerCannotRead() {
        String other = DOC.replace("product_search", "secret_search");
        stubMultiGet(new AtomicReference<>(), found("product_search", DOC), found("secret_search", other));
        doAnswer((Answer<Void>) inv -> {
            GetStoredScriptRequest request = inv.getArgument(0);
            ActionListener<GetStoredScriptResponse> l = inv.getArgument(1);
            if (request.id().equals("secret_search")) {
                l.onFailure(new org.opensearch.OpenSearchSecurityException("no permissions for [cluster:admin/script/get]"));
            } else {
                GetStoredScriptResponse response = mock(GetStoredScriptResponse.class);
                when(response.getSource()).thenReturn(new StoredScriptSource("mustache", "{}", Collections.emptyMap()));
                l.onResponse(response);
            }
            return null;
        }).when(clusterAdminClient).getStoredScript(any(GetStoredScriptRequest.class), any());

        AtomicReference<Map<String, AgenticSearchTemplate>> result = new AtomicReference<>();
        service
            .getTemplates(
                List.of("product_search", "secret_search"),
                "products",
                ActionListener.wrap(result::set, e -> fail(e.getMessage()))
            );
        assertEquals(List.of("product_search"), List.copyOf(result.get().keySet()));
    }

    @Test
    public void getTemplates_dropsTemplatesBoundToAnotherIndex_withoutCheckingScripts() {
        stubMultiGet(new AtomicReference<>(), found("product_search", DOC));
        when(clusterService.state()).thenReturn(ClusterState.builder(new ClusterName("test")).build());

        AtomicReference<Map<String, AgenticSearchTemplate>> result = new AtomicReference<>();
        service.getTemplates(List.of("product_search"), "other_index", ActionListener.wrap(result::set, e -> fail(e.getMessage())));
        assertTrue(result.get().isEmpty());
        verify(clusterAdminClient, never()).getStoredScript(any(GetStoredScriptRequest.class), any());
    }

    @Test
    public void indexMatches_resolvesAliasesAndPatternsWithinTheBinding() {
        Metadata metadata = Metadata
            .builder()
            .put(indexMetadata("products-2026").putAlias(AliasMetadata.builder("products_alias")))
            .put(indexMetadata("products-2025"))
            .put(indexMetadata("other"))
            .build();
        when(clusterService.state()).thenReturn(ClusterState.builder(new ClusterName("test")).metadata(metadata).build());

        assertTrue(service.indexMatches(null, "anything"));
        assertTrue(service.indexMatches("products-2026", "products-2026"));
        assertTrue(service.indexMatches("products-2026", "products_alias"));
        assertTrue(service.indexMatches("products-*", "products-2025"));
        assertTrue(service.indexMatches("products-*", "products_alias"));
        assertFalse(service.indexMatches("products-2026", "products-*"));
        assertFalse(service.indexMatches("products-2026", "other"));
        assertFalse(service.indexMatches("products-2026", "missing"));
    }

    private static IndexMetadata.Builder indexMetadata(String name) {
        return IndexMetadata
            .builder(name)
            .settings(
                Settings
                    .builder()
                    .put(IndexMetadata.SETTING_VERSION_CREATED, org.opensearch.Version.CURRENT)
                    .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
                    .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
            );
    }

    @Test
    public void getTemplates_missingIndex_returnsEmpty() {
        doAnswer((Answer<Void>) inv -> {
            ActionListener<MultiGetResponse> l = inv.getArgument(1);
            l.onFailure(new IndexNotFoundException(".plugins-ml-agentic-search-templates"));
            return null;
        }).when(client).multiGet(any(MultiGetRequest.class), any());

        AtomicReference<Map<String, AgenticSearchTemplate>> result = new AtomicReference<>();
        service.getTemplates(List.of("a"), "products", ActionListener.wrap(result::set, e -> fail(e.getMessage())));
        assertTrue(result.get().isEmpty());
    }

    @Test
    public void getTemplates_otherFailure_propagates() {
        doAnswer((Answer<Void>) inv -> {
            ActionListener<MultiGetResponse> l = inv.getArgument(1);
            l.onFailure(new RuntimeException("boom"));
            return null;
        }).when(client).multiGet(any(MultiGetRequest.class), any());

        AtomicReference<Exception> failure = new AtomicReference<>();
        service.getTemplates(List.of("a"), "products", ActionListener.wrap(r -> fail("expected failure"), failure::set));
        assertEquals("boom", failure.get().getMessage());
    }

    @Test
    public void getTemplates_noIds_skipsTheRead() {
        AtomicReference<Map<String, AgenticSearchTemplate>> result = new AtomicReference<>();
        service.getTemplates(List.of(), "products", ActionListener.wrap(result::set, e -> fail(e.getMessage())));
        assertTrue(result.get().isEmpty());
        verify(client, never()).multiGet(any(MultiGetRequest.class), any());
    }

    // ---- renderTemplate -----------------------------------------------------

    @Test
    public void renderTemplate_readsScriptAsCaller_andRenders() {
        AtomicReference<Object> markerSeen = new AtomicReference<>();
        AtomicReference<Map<String, Object>> paramsSeen = new AtomicReference<>();
        stubStoredScript("{\"query\":{\"match\":{\"title\":\"{{q}}\"}}}", markerSeen);
        stubRender("{\"query\":{\"match\":{\"title\":\"shoes\"}}}", paramsSeen);
        threadContext.putTransient(CALLER_MARKER, "caller");

        AtomicReference<String> result = new AtomicReference<>();
        service.renderTemplate("product_search", Map.of("q", "shoes"), ActionListener.wrap(result::set, e -> fail(e.getMessage())));

        assertEquals("{\"query\":{\"match\":{\"title\":\"shoes\"}}}", result.get());
        assertEquals(Map.of("q", "shoes"), paramsSeen.get());
        assertEquals("caller", markerSeen.get());
        ArgumentCaptor<GetStoredScriptRequest> request = ArgumentCaptor.forClass(GetStoredScriptRequest.class);
        verify(clusterAdminClient).getStoredScript(request.capture(), any());
        assertEquals("product_search", request.getValue().id());
    }

    @Test
    public void renderTemplate_invalidJson_fails() {
        stubStoredScript("{{q}}", new AtomicReference<>());
        stubRender("not json", new AtomicReference<>());

        AtomicReference<Exception> failure = new AtomicReference<>();
        service.renderTemplate("product_search", Map.of("q", "x"), ActionListener.wrap(r -> fail("expected failure"), failure::set));
        assertTrue(failure.get() instanceof IllegalArgumentException);
        assertTrue(failure.get().getMessage().contains("did not render a valid query"));
    }

    @Test
    public void renderTemplate_trailingContent_fails() {
        stubStoredScript("{{{q}}}", new AtomicReference<>());
        stubRender("{\"query\":{\"match_all\":{}}} {\"extra\":1}", new AtomicReference<>());

        AtomicReference<Exception> failure = new AtomicReference<>();
        service.renderTemplate("product_search", Map.of("q", "x"), ActionListener.wrap(r -> fail("expected failure"), failure::set));
        assertTrue(failure.get() instanceof IllegalArgumentException);
    }

    @Test
    public void renderTemplate_listenerThrowing_isNotAnsweredTwice() {
        stubStoredScript("{}", new AtomicReference<>());
        stubRender("{\"query\":{\"match_all\":{}}}", new AtomicReference<>());
        AtomicInteger calls = new AtomicInteger();
        ActionListener<String> throwing = new ActionListener<>() {
            @Override
            public void onResponse(String rendered) {
                calls.incrementAndGet();
                throw new IllegalStateException("caller failed");
            }

            @Override
            public void onFailure(Exception e) {
                calls.incrementAndGet();
            }
        };
        try {
            service.renderTemplate("product_search", Map.of(), throwing);
        } catch (IllegalStateException e) {
            // Whether the caller's own exception propagates is not the contract; a single answer is.
        }
        assertEquals(1, calls.get());
    }

    @Test
    public void renderTemplate_noStoredScript_fails() {
        stubStoredScript(null, new AtomicReference<>());

        AtomicReference<Exception> failure = new AtomicReference<>();
        service.renderTemplate("missing", Map.of(), ActionListener.wrap(r -> fail("expected failure"), failure::set));
        assertTrue(failure.get().getMessage().contains("No stored search template"));
        verify(scriptService, never()).compile(any(Script.class), any());
    }
}
