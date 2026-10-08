/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_SEARCH_TEMPLATE_ENABLED;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableMap;

/**
 * End-to-end coverage of the agentic search template APIs against a real cluster: CRUD,
 * pagination, the feature gate, and the param-schema derivation fixes of issue #5035
 * (3.2-3.5), each checked through register and, where the fix is about a query that would
 * otherwise fail, through a real {@code _search/template} call.
 */
public class RestMLAgenticSearchTemplateIT extends MLCommonsRestTestCase {

    private static final String TEMPLATES_URI = "/_plugins/_ml/agentic_search_templates";

    // Template docs live in a system index that the test framework does not wipe, so every
    // test uses ids unique to it and deletes what it registered.
    private String prefix;
    private String index;
    private final Set<String> registered = new HashSet<>();

    @Before
    public void setUpTemplates() throws IOException {
        updateClusterSettings(ML_COMMONS_AGENTIC_SEARCH_TEMPLATE_ENABLED.getKey(), true);
        prefix = "ast_" + randomAlphaOfLength(8).toLowerCase(Locale.ROOT) + "_";
        index = prefix + "products";
        request(
            "PUT",
            "/" + index,
            "{\"mappings\":{\"properties\":{\"title\":{\"type\":\"text\"},\"brand\":{\"type\":\"keyword\"},\"price\":{\"type\":\"float\"}}}}"
        );
        request("PUT", "/" + index + "/_doc/1?refresh=true", "{\"title\":\"laptop pro\",\"brand\":\"acme\",\"price\":999}");
    }

    @After
    public void tearDownTemplates() throws IOException {
        updateClusterSettings(ML_COMMONS_AGENTIC_SEARCH_TEMPLATE_ENABLED.getKey(), true);
        for (String id : registered) {
            try {
                request("DELETE", TEMPLATES_URI + "/" + id, null);
            } catch (ResponseException ignored) {
                // already deleted by the test
            }
        }
        registered.clear();
        deleteIndexWithAdminClient(index);
        updateClusterSettings(ML_COMMONS_AGENTIC_SEARCH_TEMPLATE_ENABLED.getKey(), null);
    }

    // ---- CRUD + pagination -------------------------------------------------

    public void testCrudLifecycle() throws IOException {
        String id = putScript("crud", "{\"size\":{{size}}{{^size}}10{{/size}},\"query\":{\"match\":{\"title\":\"{{q}}\"}}}");

        Map<String, Object> created = register(id, null);
        assertEquals(id, created.get("template_id"));

        Map<String, Object> got = get(id);
        assertEquals(id, got.get("template_id"));
        assertEquals(index, got.get("index_binding"));
        assertEquals("string", param(got, "q").get("type"));
        assertEquals(Boolean.TRUE, param(got, "q").get("required"));
        assertEquals("number", param(got, "size").get("type"));
        assertEquals(Boolean.FALSE, param(got, "size").get("required"));

        // Registering the same id again conflicts.
        assertStatus(409, () -> register(id, null));

        // Metadata-only edit, then a param-schema edit.
        request("PUT", TEMPLATES_URI + "/" + id, "{\"description\":\"edited\"}");
        assertEquals("edited", get(id).get("description"));
        request("PUT", TEMPLATES_URI + "/" + id, "{\"param_schema\":{\"q\":{\"description\":\"Search text.\"}}}");
        assertEquals("Search text.", param(get(id), "q").get("description"));
        // An edit may not introduce a param the body never references.
        assertStatus(400, () -> request("PUT", TEMPLATES_URI + "/" + id, "{\"param_schema\":{\"nope\":{\"type\":\"string\"}}}"));

        request("DELETE", TEMPLATES_URI + "/" + id, null);
        assertStatus(404, () -> get(id));
        assertStatus(404, () -> request("DELETE", TEMPLATES_URI + "/" + id, null));
    }

    @SuppressWarnings("unchecked")
    public void testListPagination() throws IOException {
        Set<String> mine = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            String id = putScript("page" + i, "{\"query\":{\"match\":{\"title\":\"{{q}}\"}}}");
            register(id, null);
            mine.add(id);
        }

        // Other test classes may have left templates behind, so page through everything
        // and check ours each appear exactly once.
        Set<String> seen = new HashSet<>();
        int from = 0;
        int total;
        do {
            Map<String, Object> page = parseResponseToMap(request("GET", TEMPLATES_URI + "?from=" + from + "&size=2", null));
            total = ((Number) page.get("total")).intValue();
            List<Map<String, Object>> templates = (List<Map<String, Object>>) page.get("templates");
            assertTrue(templates.size() <= 2);
            for (Map<String, Object> t : templates) {
                assertTrue("duplicate across pages: " + t.get("template_id"), seen.add((String) t.get("template_id")));
            }
            from += 2;
        } while (from < total);
        assertTrue(total >= 3);
        assertTrue(seen.containsAll(mine));

        assertStatus(400, () -> request("GET", TEMPLATES_URI + "?size=0", null));
        assertStatus(400, () -> request("GET", TEMPLATES_URI + "?from=-1", null));
    }

    public void testFeatureGateOff_rejected() throws IOException {
        updateClusterSettings(ML_COMMONS_AGENTIC_SEARCH_TEMPLATE_ENABLED.getKey(), false);
        ResponseException e = expectThrows(ResponseException.class, () -> request("GET", TEMPLATES_URI, null));
        assertTrue(e.getResponse().getStatusLine().getStatusCode() >= 400);
        assertTrue(e.getMessage().contains(ML_COMMONS_AGENTIC_SEARCH_TEMPLATE_ENABLED.getKey()));
    }

    // ---- #5035 3.2: quoted triple-stache -----------------------------------

    public void testQuotedTripleStache_isStringAndAdvertisedValueSearches() throws IOException {
        String id = putScript("triple", "{\"query\":{\"match\":{\"title\":\"{{{q}}}\"}}}");
        register(id, null);
        Map<String, Object> q = param(get(id), "q");
        assertEquals("string", q.get("type"));

        // A string is what the schema advertises, and it runs.
        Map<String, Object> hits = searchTemplate(id, "{\"q\":\"laptop\"}");
        assertEquals(1, ((Number) ((Map<?, ?>) hits.get("total")).get("value")).intValue());
    }

    // ---- #5035 3.3: quote inside a comment ---------------------------------

    public void testQuoteInsideComment_registersWithCorrectTypes() throws IOException {
        String id = putScript("comment", "{{! dont use a \" quote here }}{\"query\":{\"match\":{\"title\":\"{{q}}\"}},\"size\":{{n}}}");
        register(id, null);
        Map<String, Object> got = get(id);
        assertEquals("string", param(got, "q").get("type"));
        assertEquals("number", param(got, "n").get("type"));

        Map<String, Object> hits = searchTemplate(id, "{\"q\":\"laptop\",\"n\":5}");
        assertEquals(1, ((Number) ((Map<?, ?>) hits.get("total")).get("value")).intValue());
    }

    // ---- #5035 3.4: empty / scalar / non-object renders --------------------

    public void testBodyRenderingToScalar_rejected() throws IOException {
        String id = putScript("scalar", "{{n}}");
        assertBadRequest(() -> register(id, null), "must render to a JSON object");
    }

    public void testCommentOnlyBody_rejected() throws IOException {
        String id = putScript("commentonly", "{{! only a comment }}");
        assertBadRequest(() -> register(id, null), "declares no parameters");
    }

    public void testBodyEmptyWithoutOptionalSection_rejected() throws IOException {
        String id = putScript("sectiononly", "{{#a}}{\"query\":{\"match_all\":{}}}{{/a}}");
        assertBadRequest(() -> register(id, null), "must render to a JSON object (required-only)");
    }

    public void testMalformedDelimiterChange_rejected() throws IOException {
        String id = putScript("delim", "{{=<% %>}}{\"size\":<%n%>}");
        assertBadRequest(() -> register(id, null), "Malformed delimiter change");
    }

    public void testBodyRenderingToEmptyObject_registers() throws IOException {
        // Every clause optional: the required-only pass renders {}, a valid search body.
        String id = putScript("emptyobj", "{ {{#sized}}\"size\":1{{/sized}} }");
        register(id, null);
        assertEquals("boolean", param(get(id), "sized").get("type"));
    }

    // ---- #5035 3.5: list iteration and toJson fail loud --------------------

    public void testImplicitIterator_derivePathRejected_suppliedSchemaRegisters() throws IOException {
        String id = putScript("iter", "{\"query\":{\"terms\":{\"brand\":[{{#brands}}\"{{.}}\",{{/brands}}\"zzz\"]}}}");
        assertBadRequest(() -> register(id, null), "'brands' is iterated as a list");

        register(id, "{\"brands\":{\"type\":\"array\",\"required\":false,\"description\":\"Brands to match.\"}}");
        assertEquals("array", param(get(id), "brands").get("type"));
        Map<String, Object> hits = searchTemplate(id, "{\"brands\":[\"acme\",\"globex\"]}");
        assertEquals(1, ((Number) ((Map<?, ?>) hits.get("total")).get("value")).intValue());
    }

    public void testToJsonSection_derivePathRejected_suppliedSchemaRegisters() throws IOException {
        String id = putScript("tojson", "{\"query\":{\"terms\":{\"brand\":{{#toJson}}brands{{/toJson}}}}}");
        assertBadRequest(() -> register(id, null), "{{#toJson}}");

        register(id, "{\"brands\":{\"type\":\"array\",\"required\":true}}");
        Map<String, Object> hits = searchTemplate(id, "{\"brands\":[\"acme\"]}");
        assertEquals(1, ((Number) ((Map<?, ?>) hits.get("total")).get("value")).intValue());
    }

    // ---- helpers -----------------------------------------------------------

    private interface Call {
        void run() throws IOException;
    }

    private Response request(String method, String endpoint, String body) throws IOException {
        return TestHelper.makeRequest(client(), method, endpoint, ImmutableMap.of(), body, null);
    }

    /** Store a Mustache body in _scripts under a test-unique id and return that id. */
    private String putScript(String name, String body) throws IOException {
        String id = prefix + name;
        String payload = "{\"script\":{\"lang\":\"mustache\",\"source\":" + gson.toJson(body) + "}}";
        request("POST", "/_scripts/" + id, payload);
        return id;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> register(String id, String paramSchemaJson) throws IOException {
        String payload = "{\"template_id\":\""
            + id
            + "\",\"index\":\""
            + index
            + "\""
            + (paramSchemaJson == null ? "" : ",\"param_schema\":" + paramSchemaJson)
            + "}";
        Map<String, Object> response = parseResponseToMap(request("POST", TEMPLATES_URI, payload));
        registered.add(id);
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String id) throws IOException {
        return parseResponseToMap(request("GET", TEMPLATES_URI + "/" + id, null));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> param(Map<String, Object> template, String name) {
        Map<String, Object> schema = (Map<String, Object>) template.get("param_schema");
        assertNotNull("no param_schema in " + template, schema);
        Map<String, Object> spec = (Map<String, Object>) schema.get(name);
        assertNotNull("no param '" + name + "' in " + schema, spec);
        return spec;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> searchTemplate(String id, String paramsJson) throws IOException {
        Response response = request("POST", "/" + index + "/_search/template", "{\"id\":\"" + id + "\",\"params\":" + paramsJson + "}");
        return (Map<String, Object>) parseResponseToMap(response).get("hits");
    }

    private static void assertStatus(int expected, Call call) {
        ResponseException e = expectThrows(ResponseException.class, call::run);
        assertEquals(e.getMessage(), expected, e.getResponse().getStatusLine().getStatusCode());
    }

    private static void assertBadRequest(Call call, String messageSubstring) {
        ResponseException e = expectThrows(ResponseException.class, call::run);
        assertEquals(e.getMessage(), 400, e.getResponse().getStatusLine().getStatusCode());
        assertTrue(e.getMessage(), e.getMessage().contains(messageSubstring));
    }
}
