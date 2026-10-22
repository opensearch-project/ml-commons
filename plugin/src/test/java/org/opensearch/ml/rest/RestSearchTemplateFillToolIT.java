/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.commons.rest.SecureRestClientBuilder;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.utils.TestHelper;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Integration tests for {@code SearchTemplateFillTool}: a flow agent fills a registered agentic search
 * template through a forced tool call and the cluster renders it, falling back to a model-written query
 * when the model declines or the fill fails.
 *
 * <p>The LLM is a mock HTTP server in the test JVM that speaks the Bedrock Converse and OpenAI chat
 * completions formats. When a request forces a tool it answers with a tool call filled by keyword rules
 * over the question; otherwise (the direct-DSL fallback) it answers with a recognisable query. Every
 * request is recorded so tests can check what the cluster sent.
 */
@SuppressWarnings("unchecked")
public class RestSearchTemplateFillToolIT extends MLCommonsRestTestCase {

    // The base class's instance gson field would shadow a static import in the static helpers.
    private static final Gson JSON = StringUtils.gson;

    private static final String INDEX = "template_fill_products";
    private static final String OTHER_INDEX = "template_fill_other";
    private static final String PRODUCT_TEMPLATE = "template_fill_product_search";
    private static final String SIMILAR_TEMPLATE = "template_fill_similar_search";
    private static final String FEATURE_SETTING = "plugins.ml_commons.agentic_search_template_enabled";
    private static final String BEDROCK = "bedrock/converse/claude";
    private static final String OPENAI = "openai/v1/chat/completions";
    private static final String BEDROCK_FILTER = "$.output.message.content[0].text";
    private static final String OPENAI_FILTER = "$.choices[0].message.content";

    private static final String FALLBACK_MARKER = "FALLBACK_DSL_WRITTEN_BY_MODEL";
    private static final String FALLBACK_DSL = "{\"query\":{\"match\":{\"title\":\"" + FALLBACK_MARKER + "\"}},\"size\":3}";

    // The blog's product template: required query_text and size, optional category filter and sort.
    private static final String PRODUCT_BODY = "{\"query\":{\"bool\":{\"must\":[{\"match\":{\"title\":\"{{query_text}}\"}}],"
        + "\"filter\":[{{#category}}{\"term\":{\"category\":\"{{category}}\"}}{{/category}}]}},"
        + "{{#sort_by}}\"sort\":[{\"{{sort_by}}\":\"desc\"}],{{/sort_by}}\"size\":{{size}}}";
    private static final String SIMILAR_BODY =
        "{\"query\":{\"match\":{\"title\":{\"query\":\"{{query_text}}\",\"fuzziness\":\"AUTO\"}}},\"size\":10}";

    private static final String EXPENSIVE_SHOES = "the 5 most expensive shoes in the footwear category";
    private static final Map<String, Object> EXPENSIVE_SHOES_QUERY = JSON
        .fromJson(
            "{\"query\":{\"bool\":{\"must\":[{\"match\":{\"title\":\"shoes\"}}],\"filter\":[{\"term\":{\"category\":\"footwear\"}}]}},"
                + "\"sort\":[{\"price\":\"desc\"}],\"size\":5}",
            Map.class
        );

    private HttpServer mockLlm;
    private final List<Map<String, Object>> llmRequests = new CopyOnWriteArrayList<>();
    private String bedrockModelId;
    private String openaiModelId;

    @Before
    public void setUpTemplateFill() throws Exception {
        mockLlm = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        mockLlm.createContext("/", this::handleLlmRequest);
        mockLlm.start();

        updateClusterSettings(FEATURE_SETTING, true);
        updateClusterSettings("plugins.ml_commons.trusted_connector_endpoints_regex", List.of("^.*$"));
        updateClusterSettings("plugins.ml_commons.connector.private_ip_enabled", true);

        createProductIndex();
        storeTemplate(PRODUCT_TEMPLATE, PRODUCT_BODY);
        storeTemplate(SIMILAR_TEMPLATE, SIMILAR_BODY);
        registerTemplate(PRODUCT_TEMPLATE, null);
        registerTemplate(SIMILAR_TEMPLATE, "Fuzzy search for products similar to a name");

        bedrockModelId = registerMockModel("bedrock", bedrockConnector());
        openaiModelId = registerMockModel("openai", openaiConnector());
    }

    @After
    public void tearDownTemplateFill() throws IOException {
        if (mockLlm != null) {
            mockLlm.stop(0);
        }
        updateClusterSettings(FEATURE_SETTING, null);
        updateClusterSettings("plugins.ml_commons.trusted_connector_endpoints_regex", null);
        updateClusterSettings("plugins.ml_commons.connector.private_ip_enabled", null);
        for (String templateId : List.of(PRODUCT_TEMPLATE, SIMILAR_TEMPLATE)) {
            try {
                TestHelper.makeRequest(client(), "DELETE", "/_scripts/" + templateId, null, "", null);
            } catch (ResponseException e) {
                // Already gone.
            }
        }
    }

    // ---- tests -------------------------------------------------------------

    public void testSingleTemplate_fillsRendersAndSearches() throws IOException {
        String agentId = registerFillAgent(bedrockModelId, BEDROCK, List.of(PRODUCT_TEMPLATE), BEDROCK_FILTER);

        Map<String, Object> query = JSON.fromJson(execute(agentId, EXPENSIVE_SHOES, INDEX), Map.class);
        assertEquals(EXPENSIVE_SHOES_QUERY, query);

        // One forced call, no fallback.
        assertEquals(1, llmRequests.size());
        Map<String, Object> toolConfig = (Map<String, Object>) llmRequests.get(0).get("toolConfig");
        assertEquals(Map.of("tool", Map.of("name", "FillTemplate")), toolConfig.get("toolChoice"));

        // The rendered query runs and returns the footwear shoes by price, highest first.
        List<String> titles = searchTitles(INDEX, JSON.toJson(query));
        assertEquals(List.of("leather dress shoes", "trail running shoes", "canvas shoes"), titles);
    }

    public void testDecline_fallsBackToModelWrittenQuery() throws IOException {
        String agentId = registerFillAgent(bedrockModelId, BEDROCK, List.of(PRODUCT_TEMPLATE), BEDROCK_FILTER);

        String result = execute(agentId, "what is the average price per category", INDEX);
        assertTrue(result, result.contains(FALLBACK_MARKER));
        // The forced fill call, then the fallback call with no tool config.
        assertEquals(2, llmRequests.size());
        assertFalse(llmRequests.get(1).containsKey("toolConfig"));
    }

    public void testInvalidFill_fallsBack() throws IOException {
        String agentId = registerFillAgent(bedrockModelId, BEDROCK, List.of(PRODUCT_TEMPLATE), BEDROCK_FILTER);

        // The mock sends a non-numeric size for this question, which fails validation.
        String result = execute(agentId, "badfill 5 shoes", INDEX);
        assertTrue(result, result.contains(FALLBACK_MARKER));
    }

    public void testTemplateBoundToAnotherIndex_isSkipped() throws IOException {
        createIndex(OTHER_INDEX, "{\"mappings\":{\"properties\":{\"title\":{\"type\":\"text\"}}}}");
        String agentId = registerFillAgent(bedrockModelId, BEDROCK, List.of(PRODUCT_TEMPLATE), BEDROCK_FILTER);

        String result = execute(agentId, EXPENSIVE_SHOES, OTHER_INDEX);
        assertTrue(result, result.contains(FALLBACK_MARKER));
        // Only the fallback call: no fill was attempted.
        assertEquals(1, llmRequests.size());
        assertFalse(llmRequests.get(0).containsKey("toolConfig"));
    }

    public void testCandidateSet_picksAndFillsOneTemplate() throws IOException {
        String agentId = registerFillAgent(bedrockModelId, BEDROCK, List.of(PRODUCT_TEMPLATE, SIMILAR_TEMPLATE), BEDROCK_FILTER);

        Map<String, Object> similar = JSON.fromJson(execute(agentId, "shoes similar to 10 trail runners", INDEX), Map.class);
        Map<String, Object> title = (Map<String, Object>) ((Map<String, Object>) similar.get("query")).get("match");
        assertEquals("AUTO", ((Map<String, Object>) title.get("title")).get("fuzziness"));

        Map<String, Object> toolConfig = (Map<String, Object>) llmRequests.get(0).get("toolConfig");
        assertEquals(Map.of("tool", Map.of("name", "SelectAndFillTemplate")), toolConfig.get("toolChoice"));
        Map<String, Object> properties = toolProperties(llmRequests.get(0));
        assertEquals(
            List.of(PRODUCT_TEMPLATE, SIMILAR_TEMPLATE, "none"),
            ((Map<String, Object>) properties.get("template_id")).get("enum")
        );

        // The same agent picks the product template for a filter-and-sort question.
        assertEquals(EXPENSIVE_SHOES_QUERY, JSON.fromJson(execute(agentId, EXPENSIVE_SHOES, INDEX), Map.class));
        // And falls back when no candidate fits.
        assertTrue(execute(agentId, "average price per category", INDEX).contains(FALLBACK_MARKER));
    }

    public void testOpenAiInterface() throws IOException {
        String agentId = registerFillAgent(openaiModelId, OPENAI, List.of(PRODUCT_TEMPLATE), OPENAI_FILTER);

        // The mock reports the forced call as finish_reason=stop, as OpenAI does.
        assertEquals(EXPENSIVE_SHOES_QUERY, JSON.fromJson(execute(agentId, EXPENSIVE_SHOES, INDEX), Map.class));
        assertEquals(1, llmRequests.size());
        assertEquals(Map.of("type", "function", "function", Map.of("name", "FillTemplate")), llmRequests.get(0).get("tool_choice"));

        assertTrue(execute(agentId, "average price per category", INDEX).contains(FALLBACK_MARKER));
    }

    public void testSchemaEdit_reachesTheNextFill() throws IOException {
        String agentId = registerFillAgent(bedrockModelId, BEDROCK, List.of(PRODUCT_TEMPLATE), BEDROCK_FILTER);
        TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_plugins/_ml/agentic_search_templates/" + PRODUCT_TEMPLATE,
                null,
                "{\"param_schema\":{\"query_text\":{\"description\":\"Product words only.\"}}}",
                null
            );

        execute(agentId, EXPENSIVE_SHOES, INDEX);
        Map<String, Object> queryText = (Map<String, Object>) toolProperties(llmRequests.get(0)).get("query_text");
        assertEquals("Product words only.", queryText.get("description"));
    }

    public void testRegistration_rejectsMisconfiguredTool() throws IOException {
        ResponseException unsupported = expectThrows(
            ResponseException.class,
            () -> registerFillAgent(bedrockModelId, "bedrock/converse/deepseek_r1", List.of(PRODUCT_TEMPLATE), null)
        );
        assertEquals(400, unsupported.getResponse().getStatusLine().getStatusCode());

        String noTemplateIds = "{\"name\":\"no-ids\",\"type\":\"flow\",\"tools\":[{\"type\":\"SearchTemplateFillTool\","
            + "\"parameters\":{\"model_id\":\""
            + bedrockModelId
            + "\",\"_llm_interface\":\""
            + BEDROCK
            + "\"}}]}";
        ResponseException missingIds = expectThrows(
            ResponseException.class,
            () -> TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, noTemplateIds, null)
        );
        assertEquals(400, missingIds.getResponse().getStatusLine().getStatusCode());

        // Template ids in parameters could be replaced by an execute request, so they are refused there.
        Map<String, Object> idsInParametersAgent = Map
            .of(
                "name",
                "ids-in-params",
                "type",
                "flow",
                "tools",
                List
                    .of(
                        Map
                            .of(
                                "type",
                                "SearchTemplateFillTool",
                                "parameters",
                                Map
                                    .of(
                                        "model_id",
                                        bedrockModelId,
                                        "_llm_interface",
                                        BEDROCK,
                                        "template_ids",
                                        JSON.toJson(List.of(PRODUCT_TEMPLATE))
                                    )
                            )
                    )
            );
        String idsInParameters = JSON.toJson(idsInParametersAgent);
        ResponseException idsInParams = expectThrows(
            ResponseException.class,
            () -> TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, idsInParameters, null)
        );
        assertEquals(400, idsInParams.getResponse().getStatusLine().getStatusCode());
    }

    public void testExecuteParameters_cannotOverrideTemplateIds() throws IOException {
        String agentId = registerFillAgent(bedrockModelId, BEDROCK, List.of(PRODUCT_TEMPLATE), BEDROCK_FILTER);

        String body = JSON
            .toJson(
                Map
                    .of(
                        "parameters",
                        Map.of("question", EXPENSIVE_SHOES, "index_name", INDEX, "template_ids", JSON.toJson(List.of(SIMILAR_TEMPLATE)))
                    )
            );
        Response response = TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, body, null);
        assertEquals(EXPENSIVE_SHOES_QUERY, JSON.fromJson(resultOf(response), Map.class));
        // The configured template was filled, not the one named in the request.
        assertEquals(
            Map.of("tool", Map.of("name", "FillTemplate")),
            ((Map<String, Object>) llmRequests.get(0).get("toolConfig")).get("toolChoice")
        );
        assertTrue(toolProperties(llmRequests.get(0)).containsKey("category"));
    }

    public void testAliasOfTheBoundIndex_matches() throws IOException {
        TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_aliases",
                null,
                "{\"actions\":[{\"add\":{\"index\":\"" + INDEX + "\",\"alias\":\"template_fill_alias\"}}]}",
                null
            );
        String agentId = registerFillAgent(bedrockModelId, BEDROCK, List.of(PRODUCT_TEMPLATE), BEDROCK_FILTER);

        assertEquals(EXPENSIVE_SHOES_QUERY, JSON.fromJson(execute(agentId, EXPENSIVE_SHOES, "template_fill_alias"), Map.class));
        assertEquals(1, llmRequests.size());
    }

    /**
     * With the security plugin, a user who can run the agent and search the index but cannot read stored
     * scripts is not offered the template: the tool falls back without a fill call. Granting script read
     * lets the same user fill. Runs only in security-enabled test runs.
     */
    public void testCallerWithoutScriptAccess_isNotOfferedTheTemplate() throws IOException {
        if (!isHttps()) {
            return;
        }
        String agentId = registerFillAgent(bedrockModelId, BEDROCK, List.of(PRODUCT_TEMPLATE), BEDROCK_FILTER);
        String user = "template_fill_searcher";
        String password = "Tf-" + UUID.randomUUID() + "-9aZ!";
        createUser(user, password, List.of());
        putRole(
            "template_fill_search_role",
            "{\"cluster_permissions\":[\"cluster:admin/opensearch/ml/*\"],\"index_permissions\":[{\"index_patterns\":[\""
                + INDEX
                + "\"],\"allowed_actions\":[\"indices:data/read/search\",\"indices:admin/mappings/get\",\"indices:admin/get\"]}]}"
        );
        createRoleMapping("template_fill_search_role", List.of(user));
        try (
            RestClient userClient = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), user, password)
                .setSocketTimeout(60000)
                .build()
        ) {
            String body = JSON.toJson(Map.of("parameters", Map.of("question", EXPENSIVE_SHOES, "index_name", INDEX)));
            Response response = TestHelper
                .makeRequest(userClient, "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, body, null);
            assertTrue(resultOf(response).contains(FALLBACK_MARKER));
            // Only the fallback call: the template's schema was never sent to the model.
            assertEquals(1, llmRequests.size());
            assertFalse(llmRequests.get(0).containsKey("toolConfig"));

            putRole("template_fill_script_role", "{\"cluster_permissions\":[\"cluster:admin/script/get\"]}");
            createRoleMapping("template_fill_script_role", List.of(user));
            llmRequests.clear();
            response = TestHelper.makeRequest(userClient, "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, body, null);
            assertEquals(EXPENSIVE_SHOES_QUERY, JSON.fromJson(resultOf(response), Map.class));
        } finally {
            deleteUser(user);
        }
    }

    public void testFeatureFlag_gatesRegistrationAndExecution() throws IOException {
        String agentId = registerFillAgent(bedrockModelId, BEDROCK, List.of(PRODUCT_TEMPLATE), BEDROCK_FILTER);
        updateClusterSettings(FEATURE_SETTING, false);

        ResponseException registration = expectThrows(
            ResponseException.class,
            () -> registerFillAgent(bedrockModelId, BEDROCK, List.of(PRODUCT_TEMPLATE), BEDROCK_FILTER)
        );
        assertEquals(403, registration.getResponse().getStatusLine().getStatusCode());
        assertTrue(TestHelper.httpEntityToString(registration.getResponse().getEntity()).contains(FEATURE_SETTING));

        // An agent registered while the feature was on stops filling, without calling the model.
        ResponseException execution = expectThrows(ResponseException.class, () -> execute(agentId, EXPENSIVE_SHOES, INDEX));
        assertEquals(403, execution.getResponse().getStatusLine().getStatusCode());
        assertTrue(llmRequests.isEmpty());

        updateClusterSettings(FEATURE_SETTING, true);
        assertEquals(EXPENSIVE_SHOES_QUERY, JSON.fromJson(execute(agentId, EXPENSIVE_SHOES, INDEX), Map.class));
    }

    // ---- cluster helpers ---------------------------------------------------

    private void createProductIndex() throws IOException {
        createIndex(
            INDEX,
            "{\"mappings\":{\"properties\":{\"title\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\"}}},"
                + "\"category\":{\"type\":\"keyword\"},\"price\":{\"type\":\"float\"}}}}"
        );
        Object[][] docs = {
            { "trail running shoes", "footwear", 120.0 },
            { "leather dress shoes", "footwear", 210.0 },
            { "canvas shoes", "footwear", 45.0 },
            { "shoes shaped keychain", "accessories", 9.0 },
            { "rain boots", "footwear", 80.0 } };
        StringBuilder bulk = new StringBuilder();
        for (Object[] doc : docs) {
            bulk.append("{\"index\":{\"_index\":\"").append(INDEX).append("\"}}\n");
            bulk.append(JSON.toJson(Map.of("title", doc[0], "category", doc[1], "price", doc[2]))).append("\n");
        }
        TestHelper.makeRequest(client(), "POST", "/_bulk?refresh=true", null, bulk.toString(), null);
    }

    private void createIndex(String index, String body) throws IOException {
        TestHelper.makeRequest(client(), "PUT", "/" + index, null, body, null);
    }

    private void storeTemplate(String templateId, String body) throws IOException {
        String request = JSON.toJson(Map.of("script", Map.of("lang", "mustache", "source", body)));
        TestHelper.makeRequest(client(), "PUT", "/_scripts/" + templateId, null, request, null);
    }

    private void registerTemplate(String templateId, String description) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("template_id", templateId);
        body.put("index", INDEX);
        if (description != null) {
            body.put("description", description);
        }
        TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agentic_search_templates", null, JSON.toJson(body), null);
    }

    private String registerMockModel(String name, String connector) throws Exception {
        String connectorId = registerConnector(connector);
        Response response = RestMLRemoteInferenceIT
            .registerRemoteModel("template_fill_" + name + "_" + UUID.randomUUID(), "template_fill_" + name, connectorId);
        String modelId = (String) parseResponseToMap(response).get("model_id");
        response = RestMLRemoteInferenceIT.deployRemoteModel(modelId);
        waitForTask((String) parseResponseToMap(response).get("task_id"), MLTaskState.COMPLETED);
        return modelId;
    }

    /** The agentic-search tutorial's Bedrock Converse connector, pointed at the mock. */
    private String bedrockConnector() {
        return "{\"name\":\"Mock Bedrock Converse\",\"version\":1,\"protocol\":\"aws_sigv4\","
            + "\"parameters\":{\"region\":\"us-east-1\",\"service_name\":\"bedrock\",\"model\":\"claude\"},"
            + "\"credential\":{\"access_key\":\"x\",\"secret_key\":\"y\"},"
            + "\"actions\":[{\"action_type\":\"predict\",\"method\":\"POST\","
            + "\"url\":\""
            + mockUrl()
            + "/model/${parameters.model}/converse\","
            + "\"headers\":{\"content-type\":\"application/json\"},"
            + "\"request_body\":\"{ \\\"system\\\": [{\\\"text\\\": \\\"${parameters.system_prompt}\\\"}], "
            + "\\\"messages\\\": [{\\\"role\\\":\\\"user\\\",\\\"content\\\":[{\\\"text\\\":\\\"${parameters.user_prompt}\\\"}]}]"
            + "${parameters.tool_configs:-} }\"}]}";
    }

    private String openaiConnector() {
        return "{\"name\":\"Mock OpenAI\",\"version\":1,\"protocol\":\"http\","
            + "\"parameters\":{\"model\":\"gpt\"},\"credential\":{\"openAI_key\":\"k\"},"
            + "\"actions\":[{\"action_type\":\"predict\",\"method\":\"POST\","
            + "\"url\":\""
            + mockUrl()
            + "/v1/chat/completions\","
            + "\"headers\":{\"Authorization\":\"Bearer ${credential.openAI_key}\"},"
            + "\"request_body\":\"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"messages\\\": "
            + "[{\\\"role\\\":\\\"system\\\",\\\"content\\\":\\\"${parameters.system_prompt}\\\"},"
            + "{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\"${parameters.user_prompt}\\\"}]${parameters.tool_configs:-} }\"}]}";
    }

    private String mockUrl() {
        return String.format(Locale.ROOT, "http://127.0.0.1:%d", mockLlm.getAddress().getPort());
    }

    private String registerFillAgent(String modelId, String llmInterface, List<String> templateIds, String responseFilter)
        throws IOException {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("model_id", modelId);
        parameters.put("_llm_interface", llmInterface);
        if (responseFilter != null) {
            // Read by the direct-DSL fallback; the fill call overrides it.
            parameters.put("response_filter", responseFilter);
        }
        // Template ids go in config, which execute parameters cannot override.
        Map<String, String> config = Map.of("template_ids", JSON.toJson(templateIds));
        Map<String, Object> agent = Map
            .of(
                "name",
                "template-fill-agent",
                "type",
                "flow",
                "tools",
                List.of(Map.of("type", "SearchTemplateFillTool", "parameters", parameters, "config", config))
            );
        Response response = TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, JSON.toJson(agent), null);
        return (String) parseResponseToMap(response).get("agent_id");
    }

    /** Execute the agent the way neural-search's agentic query translator does, and return the query it generated. */
    private String execute(String agentId, String question, String index) throws IOException {
        String body = JSON.toJson(Map.of("parameters", Map.of("question", question, "index_name", index)));
        Response response = TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, body, null);
        return resultOf(response);
    }

    private void putRole(String role, String body) throws IOException {
        TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_opendistro/_security/api/roles/" + role,
                null,
                TestHelper.toHttpEntity(body),
                List.of(new BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
            );
    }

    /** The generated query in an agent execute response. */
    private static String resultOf(Response response) throws IOException {
        Map<String, Object> result = parseResponseToMap(response);
        List<Map<String, Object>> inferenceResults = (List<Map<String, Object>>) result.get("inference_results");
        List<Map<String, Object>> output = (List<Map<String, Object>>) inferenceResults.get(0).get("output");
        return (String) output.get(0).get("result");
    }

    private List<String> searchTitles(String index, String query) throws IOException {
        Response response = TestHelper.makeRequest(client(), "POST", "/" + index + "/_search", null, query, null);
        Map<String, Object> hits = (Map<String, Object>) parseResponseToMap(response).get("hits");
        List<String> titles = new ArrayList<>();
        for (Map<String, Object> hit : (List<Map<String, Object>>) hits.get("hits")) {
            titles.add((String) ((Map<String, Object>) hit.get("_source")).get("title"));
        }
        return titles;
    }

    /** The properties of the tool schema in a recorded Bedrock request. */
    private static Map<String, Object> toolProperties(Map<String, Object> request) {
        Map<String, Object> toolConfig = (Map<String, Object>) request.get("toolConfig");
        Map<String, Object> toolSpec = (Map<String, Object>) ((List<Map<String, Object>>) toolConfig.get("tools")).get(0).get("toolSpec");
        Map<String, Object> schema = (Map<String, Object>) ((Map<String, Object>) toolSpec.get("inputSchema")).get("json");
        return (Map<String, Object>) schema.get("properties");
    }

    // ---- mock LLM ----------------------------------------------------------

    private void handleLlmRequest(HttpExchange exchange) throws IOException {
        Map<String, Object> request;
        try (InputStream in = exchange.getRequestBody()) {
            request = JSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), Map.class);
        }
        llmRequests.add(request);
        String path = exchange.getRequestURI().getPath();
        Map<String, Object> response;
        if (path.endsWith("/converse")) {
            response = bedrockResponse(request);
        } else if (path.endsWith("/chat/completions")) {
            response = openaiResponse(request);
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] bytes = JSON.toJson(response).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, Object> bedrockResponse(Map<String, Object> request) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
        List<Map<String, Object>> content = (List<Map<String, Object>>) messages.get(messages.size() - 1).get("content");
        String userText = (String) content.get(0).get("text");
        Map<String, Object> toolConfig = (Map<String, Object>) request.get("toolConfig");

        Map<String, Object> reply;
        String stopReason;
        if (toolConfig != null && toolConfig.get("toolChoice") != null) {
            String tool = (String) ((Map<String, Object>) ((Map<String, Object>) toolConfig.get("toolChoice")).get("tool")).get("name");
            Map<String, Object> toolSpec = (Map<String, Object>) ((List<Map<String, Object>>) toolConfig.get("tools"))
                .get(0)
                .get("toolSpec");
            Map<String, Object> schema = (Map<String, Object>) ((Map<String, Object>) toolSpec.get("inputSchema")).get("json");
            reply = Map.of("toolUse", Map.of("toolUseId", "tooluse_1", "name", tool, "input", fill(userText, schema)));
            stopReason = "tool_use";
        } else {
            reply = Map.of("text", FALLBACK_DSL);
            stopReason = "end_turn";
        }
        return Map
            .of(
                "output",
                Map.of("message", Map.of("role", "assistant", "content", List.of(reply))),
                "stopReason",
                stopReason,
                "usage",
                Map.of("inputTokens", 10, "outputTokens", 5, "totalTokens", 15)
            );
    }

    private static Map<String, Object> openaiResponse(Map<String, Object> request) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
        String userText = (String) messages.get(messages.size() - 1).get("content");
        Object toolChoice = request.get("tool_choice");

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        if (toolChoice instanceof Map) {
            String tool = (String) ((Map<String, Object>) ((Map<String, Object>) toolChoice).get("function")).get("name");
            Map<String, Object> function = (Map<String, Object>) ((List<Map<String, Object>>) request.get("tools")).get(0).get("function");
            Map<String, Object> schema = (Map<String, Object>) function.get("parameters");
            String arguments = JSON.toJson(fill(userText, schema));
            message
                .put(
                    "tool_calls",
                    List.of(Map.of("id", "call_1", "type", "function", "function", Map.of("name", tool, "arguments", arguments)))
                );
        } else {
            message.put("content", FALLBACK_DSL);
        }
        // A forced tool choice is reported as a normal stop, as OpenAI does.
        return Map.of("choices", List.of(Map.of("index", 0, "finish_reason", "stop", "message", message)));
    }

    /** Fill a tool schema from the question with keyword rules. */
    private static Map<String, Object> fill(String userText, Map<String, Object> schema) {
        // Only the question line: the candidate-set prompt also lists template descriptions.
        String question = userText.split("\n")[0].toLowerCase(Locale.ROOT);
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> out = new LinkedHashMap<>();
        if (question.contains("average") || question.contains("per category")) {
            out.put("cannot_express", true);
            if (properties.containsKey("template_id")) {
                out.put("template_id", "none");
            }
            return out;
        }

        String prefix = "";
        if (properties.containsKey("template_id")) {
            List<String> choices = (List<String>) ((Map<String, Object>) properties.get("template_id")).get("enum");
            int position = question.contains("similar") && choices.size() > 2 ? 1 : 0;
            out.put("template_id", choices.get(position));
            // Candidates' params carry positional prefixes in candidate order.
            prefix = "t" + position + "__";
        }
        if (properties.containsKey("cannot_express")) {
            out.put("cannot_express", false);
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String name = entry.getKey();
            if (name.equals("cannot_express") || name.equals("template_id") || !name.startsWith(prefix)) {
                continue;
            }
            String param = name.substring(prefix.length());
            Map<String, Object> spec = (Map<String, Object>) entry.getValue();
            if (param.equals("query_text")) {
                out.put(name, question.contains("boots") ? "boots" : "shoes");
            } else if (param.equals("category") && question.contains("footwear")) {
                out.put(name, "footwear");
            } else if (param.equals("sort_by") && question.contains("expensive")) {
                List<String> allowed = (List<String>) spec.get("enum");
                out.put(name, allowed == null || allowed.contains("price") ? "price" : allowed.get(0));
            } else if (param.equals("size")) {
                Matcher number = Pattern.compile("\\b(\\d+)\\b").matcher(question);
                if (number.find()) {
                    // "badfill" makes the model send an invalid value, to exercise validation.
                    out.put(name, question.contains("badfill") ? "lots" : Integer.parseInt(number.group(1)));
                }
            }
        }
        return out;
    }
}
