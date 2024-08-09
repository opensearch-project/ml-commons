/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.opensearch.common.xcontent.XContentType.JSON;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.TENANT_ID;
import static org.opensearch.ml.common.input.Constants.TENANT_ID_HEADER;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.message.BasicHeader;
import org.junit.Test;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.input.Constants;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.rest.FakeRestRequest;

import com.google.common.collect.ImmutableList;

public class RestMLConnectorTenantAwareIT extends MLCommonsRestTestCase {
    // ID keys
    private static final String DOC_ID = "_id";
    private static final String CONNECTOR_ID = "connector_id";

    // REST Methods
    private static final String POST = RestRequest.Method.POST.name();
    private static final String GET = RestRequest.Method.GET.name();
    private static final String PUT = RestRequest.Method.PUT.name();
    private static final String DELETE = RestRequest.Method.DELETE.name();
    private static final String CONNECTORS_PATH = "/_plugins/_ml/connectors/";
    private static final String MATCH_ALL_QUERY = "{\"query\":{\"match_all\":{}}}";
    // Expected error messages on failure
    private static final String MISSING_TENANT_REASON = "Tenant ID header is missing";
    private static final String NO_PERMISSION_REASON = "You don't have permission to access this resource";

    private Map<String, String> params = emptyMap();
    private String body = null;
    private List<Header> headers = emptyList();
    private String tenantId = "123:abc";
    private String otherTenantId = "789:xyz";
    private Map<String, List<String>> tenantIdHeaders = Map.of(TENANT_ID_HEADER, singletonList(tenantId));
    private Map<String, List<String>> otherTenantIdHeaders = Map.of(TENANT_ID_HEADER, singletonList(otherTenantId));
    private Map<String, List<String>> nullTenantIdHeaders = emptyMap();

    @Test
    public void testConnectorCRUD() throws IOException, InterruptedException {
        testConnectorCRUDMultitenancyEnabled(true);
        testConnectorCRUDMultitenancyEnabled(false);
    }

    private void testConnectorCRUDMultitenancyEnabled(boolean multiTenancyEnabled) throws IOException, InterruptedException {
        enableMultiTenancy(multiTenancyEnabled);

        // Create a connector with a tenant id
        setFieldsFromRequest(getRestRequestWithHeadersAndContent(tenantId, createConnectorContent()));

        Response response = TestHelper.makeRequest(client(), POST, CONNECTORS_PATH + "_create", params, body, headers);
        Map<String, Object> map = parseResponseToMap(response);
        assertTrue(map.containsKey(CONNECTOR_ID));
        String connectorId = map.get(CONNECTOR_ID).toString();

        // Now try to get that connector
        setFieldsFromRequest(new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withHeaders(tenantIdHeaders).build());

        response = TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + connectorId, params, body, headers);
        map = parseResponseToMap(response);
        assertEquals("OpenAI Connector", map.get("name"));
        if (multiTenancyEnabled) {
            assertEquals(tenantId, map.get(TENANT_ID));
        } else {
            assertNull(map.get(TENANT_ID));
        }

        // Now try again with a other ID
        setFieldsFromRequest(new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withHeaders(otherTenantIdHeaders).build());

        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + connectorId, params, body, headers)
            );
            response = ex.getResponse();
            assertEquals(RestStatus.FORBIDDEN.getStatus(), response.getStatusLine().getStatusCode());
            map = parseResponseToMap(response);
            assertEquals(NO_PERMISSION_REASON, ((Map<String, String>) map.get("error")).get("reason"));
        } else {
            response = TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + connectorId, params, body, headers);
            // Headers ignored, full response
            map = parseResponseToMap(response);
            assertEquals("OpenAI Connector", map.get("name"));
        }

        // Now try again with a null ID
        setFieldsFromRequest(new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withHeaders(nullTenantIdHeaders).build());

        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + connectorId, params, body, headers)
            );
            response = ex.getResponse();
            assertEquals(RestStatus.FORBIDDEN.getStatus(), response.getStatusLine().getStatusCode());
            map = parseResponseToMap(response);
            assertEquals(MISSING_TENANT_REASON, ((Map<String, String>) map.get("error")).get("reason"));
        } else {
            response = TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + connectorId, params, body, headers);
            // Headers ignored, full response
            map = parseResponseToMap(response);
            assertEquals("OpenAI Connector", map.get("name"));
        }

        // Now attempt to update the connector name
        setFieldsFromRequest(getRestRequestWithHeadersAndContent(tenantId, "{\"name\":\"Updated name\"}"));

        response = TestHelper.makeRequest(client(), PUT, CONNECTORS_PATH + connectorId, params, body, headers);
        map = parseResponseToMap(response);
        assertEquals(connectorId, map.get(DOC_ID).toString());

        // Verfify the update
        setFieldsFromRequest(new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withHeaders(tenantIdHeaders).build());

        response = TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + connectorId, params, body, headers);
        map = parseResponseToMap(response);
        assertEquals("Updated name", map.get("name"));

        // Try the update with other tenant ID
        setFieldsFromRequest(getRestRequestWithHeadersAndContent(otherTenantId, "{\"name\":\"Other tenant name\"}"));

        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> TestHelper.makeRequest(client(), PUT, CONNECTORS_PATH + connectorId, params, body, headers)
            );
            response = ex.getResponse();
            assertEquals(RestStatus.FORBIDDEN.getStatus(), response.getStatusLine().getStatusCode());
            map = parseResponseToMap(response);
            assertEquals(NO_PERMISSION_REASON, ((Map<String, String>) map.get("error")).get("reason"));
        } else {
            response = TestHelper.makeRequest(client(), PUT, CONNECTORS_PATH + connectorId, params, body, headers);
            // Verfify the update
            response = TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + connectorId, params, body, headers);
            map = parseResponseToMap(response);
            assertEquals("Other tenant name", map.get("name"));
        }

        // Try the update with no tenant ID
        setFieldsFromRequest(getRestRequestWithHeadersAndContent(null, "{\"name\":\"Null tenant name\"}"));

        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> TestHelper.makeRequest(client(), PUT, CONNECTORS_PATH + connectorId, params, body, headers)
            );
            response = ex.getResponse();
            assertEquals(RestStatus.FORBIDDEN.getStatus(), response.getStatusLine().getStatusCode());
            map = parseResponseToMap(response);
            assertEquals(MISSING_TENANT_REASON, ((Map<String, String>) map.get("error")).get("reason"));
        } else {
            response = TestHelper.makeRequest(client(), PUT, CONNECTORS_PATH + connectorId, params, body, headers);
            // Verfify the update
            response = TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + connectorId, params, body, headers);
            map = parseResponseToMap(response);
            assertEquals("Null tenant name", map.get("name"));
        }

        // Verify no change from original update when multiTenancy enabled
        if (multiTenancyEnabled) {
            setFieldsFromRequest(new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withHeaders(tenantIdHeaders).build());

            response = TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + connectorId, params, body, headers);
            map = parseResponseToMap(response);
            assertEquals("Updated name", map.get("name"));
        }

        // Create a second connector using otherTenantId
        setFieldsFromRequest(getRestRequestWithHeadersAndContent(otherTenantId, createConnectorContent()));

        response = TestHelper.makeRequest(client(), POST, CONNECTORS_PATH + "_create", params, body, headers);
        map = parseResponseToMap(response);
        assertTrue(map.containsKey(CONNECTOR_ID));
        String otherConnectorId = map.get(CONNECTOR_ID).toString();

        // Verify it
        setFieldsFromRequest(new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withHeaders(otherTenantIdHeaders).build());

        response = TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + otherConnectorId, params, body, headers);
        map = parseResponseToMap(response);
        assertEquals("OpenAI Connector", map.get("name"));

        // Search should show only the connector for tenant
        setFieldsFromRequest(getRestRequestWithHeadersAndContent(tenantId, MATCH_ALL_QUERY));

        response = TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + "_search", params, body, headers);
        XContentParser parser = JsonXContent.jsonXContent
            .createParser(
                NamedXContentRegistry.EMPTY,
                DeprecationHandler.IGNORE_DEPRECATIONS,
                TestHelper.httpEntityToString(response.getEntity()).getBytes(UTF_8)
            );
        SearchResponse searchResponse = SearchResponse.fromXContent(parser);
        if (multiTenancyEnabled) {
            // TODO Change to 1 when https://github.com/opensearch-project/ml-commons/pull/2803 is merged
            assertEquals(2, searchResponse.getHits().getTotalHits().value);
            assertEquals(tenantId, searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID));
        } else {
            assertEquals(2, searchResponse.getHits().getTotalHits().value);
            assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID));
            assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID));
        }

        // Search should show only the connector for other tenant
        setFieldsFromRequest(getRestRequestWithHeadersAndContent(otherTenantId, MATCH_ALL_QUERY));

        response = TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + "_search", params, body, headers);
        parser = JsonXContent.jsonXContent
            .createParser(
                NamedXContentRegistry.EMPTY,
                DeprecationHandler.IGNORE_DEPRECATIONS,
                TestHelper.httpEntityToString(response.getEntity()).getBytes(UTF_8)
            );
        searchResponse = SearchResponse.fromXContent(parser);
        if (multiTenancyEnabled) {
            // TODO Change to 1 when https://github.com/opensearch-project/ml-commons/pull/2803 is merged
            assertEquals(2, searchResponse.getHits().getTotalHits().value);
            // TODO change [1] to [0]
            assertEquals(otherTenantId, searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID));
        } else {
            assertEquals(2, searchResponse.getHits().getTotalHits().value);
            assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID));
            assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID));
        }

        // Search should fail without a tenant id
        setFieldsFromRequest(getRestRequestWithHeadersAndContent(null, MATCH_ALL_QUERY));

        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + "_search", params, body, headers)
            );
            response = ex.getResponse();
            assertEquals(RestStatus.FORBIDDEN.getStatus(), response.getStatusLine().getStatusCode());
            map = parseResponseToMap(response);
            assertEquals(MISSING_TENANT_REASON, ((Map<String, String>) map.get("error")).get("reason"));
        } else {
            response = TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + "_search", params, body, headers);
            parser = JsonXContent.jsonXContent
                .createParser(
                    NamedXContentRegistry.EMPTY,
                    DeprecationHandler.IGNORE_DEPRECATIONS,
                    TestHelper.httpEntityToString(response.getEntity()).getBytes(UTF_8)
                );
            searchResponse = SearchResponse.fromXContent(parser);
            assertEquals(2, searchResponse.getHits().getTotalHits().value);
            assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID));
            assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID));
        }

        // Delete the connectors

        // First test that we can't delete other tenant connectors
        if (multiTenancyEnabled) {
            setFieldsFromRequest(getRestRequestWithHeadersAndContent(tenantId, "{}"));

            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> TestHelper.makeRequest(client(), DELETE, CONNECTORS_PATH + otherConnectorId, params, body, headers)
            );
            response = ex.getResponse();
            assertEquals(RestStatus.FORBIDDEN.getStatus(), response.getStatusLine().getStatusCode());
            map = parseResponseToMap(response);
            assertEquals(NO_PERMISSION_REASON, ((Map<String, String>) map.get("error")).get("reason"));

            setFieldsFromRequest(getRestRequestWithHeadersAndContent(otherTenantId, "{}"));

            ex = assertThrows(
                ResponseException.class,
                () -> TestHelper.makeRequest(client(), DELETE, CONNECTORS_PATH + connectorId, params, body, headers)
            );
            response = ex.getResponse();
            assertEquals(RestStatus.FORBIDDEN.getStatus(), response.getStatusLine().getStatusCode());
            map = parseResponseToMap(response);
            assertEquals(NO_PERMISSION_REASON, ((Map<String, String>) map.get("error")).get("reason"));

            // and can't delete without a tenant ID either
            setFieldsFromRequest(getRestRequestWithHeadersAndContent(null, "{}"));

            ex = assertThrows(
                ResponseException.class,
                () -> TestHelper.makeRequest(client(), DELETE, CONNECTORS_PATH + connectorId, params, body, headers)
            );
            response = ex.getResponse();
            assertEquals(RestStatus.FORBIDDEN.getStatus(), response.getStatusLine().getStatusCode());
            map = parseResponseToMap(response);
            assertEquals(MISSING_TENANT_REASON, ((Map<String, String>) map.get("error")).get("reason"));

        }

        // Now actually do the deletions. Same result whether multi-tenancy is enabled.
        // Delete from tenant
        setFieldsFromRequest(getRestRequestWithHeadersAndContent(tenantId, "{}"));
        response = TestHelper.makeRequest(client(), DELETE, CONNECTORS_PATH + connectorId, params, body, headers);
        map = parseResponseToMap(response);
        assertEquals(connectorId, map.get(DOC_ID).toString());

        // Verify the deletion
        setFieldsFromRequest(new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withHeaders(tenantIdHeaders).build());
        ResponseException ex = assertThrows(
            ResponseException.class,
            () -> TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + connectorId, params, body, headers)
        );
        response = ex.getResponse();
        assertEquals(RestStatus.NOT_FOUND.getStatus(), response.getStatusLine().getStatusCode());
        map = parseResponseToMap(response);
        assertEquals(
            "Failed to find connector with the provided connector id: " + connectorId,
            ((Map<String, String>) map.get("error")).get("reason")
        );

        // Delete from other tenant
        setFieldsFromRequest(getRestRequestWithHeadersAndContent(otherTenantId, "{}"));
        response = TestHelper.makeRequest(client(), DELETE, CONNECTORS_PATH + otherConnectorId, params, body, headers);
        map = parseResponseToMap(response);
        assertEquals(otherConnectorId, map.get(DOC_ID).toString());

        // Verify the deletion
        setFieldsFromRequest(new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withHeaders(tenantIdHeaders).build());
        ex = assertThrows(
            ResponseException.class,
            () -> TestHelper.makeRequest(client(), GET, CONNECTORS_PATH + otherConnectorId, params, body, headers)
        );
        response = ex.getResponse();
        assertEquals(RestStatus.NOT_FOUND.getStatus(), response.getStatusLine().getStatusCode());
        map = parseResponseToMap(response);
        assertEquals(
            "Failed to find connector with the provided connector id: " + otherConnectorId,
            ((Map<String, String>) map.get("error")).get("reason")
        );

        // Cleanup (since deletions may linger in search results)
        deleteIndexWithAdminClient(ML_CONNECTOR_INDEX);
    }

    private void enableMultiTenancy(boolean multiTenancyEnabled) throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"plugins.ml_commons.multi_tenancy_enabled\":" + multiTenancyEnabled + "}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    private void setFieldsFromRequest(RestRequest request) {
        params = request.params();
        body = request.content().utf8ToString();
        headers = getHeadersFromRequest(request);
    }

    private static List<Header> getHeadersFromRequest(RestRequest request) {
        return request
            .getHeaders()
            .entrySet()
            .stream()
            .map(e -> new BasicHeader(e.getKey(), e.getValue().stream().collect(Collectors.joining(","))))
            .collect(Collectors.toList());
    }

    private static RestRequest getRestRequestWithHeadersAndContent(String tenantId, String requestContent) {
        Map<String, List<String>> headers = new HashMap<>();
        if (tenantId != null) {
            headers.put(Constants.TENANT_ID_HEADER, singletonList(tenantId));
        }
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withHeaders(headers)
            .withContent(new BytesArray(requestContent), JSON)
            .build();
        return request;
    }

    private static String createConnectorContent() {
        return "{\n"
            + "    \"name\": \"OpenAI Connector\",\n"
            + "    \"description\": \"The connector to public OpenAI model service for GPT 3.5\",\n"
            + "    \"version\": 1,\n"
            + "    \"protocol\": \"http\",\n"
            + "    \"parameters\": {\n"
            + "        \"endpoint\": \"api.openai.com\",\n"
            + "        \"auth\": \"API_Key\",\n"
            + "        \"content_type\": \"application/json\",\n"
            + "        \"max_tokens\": 7,\n"
            + "        \"temperature\": 0,\n"
            + "        \"model\": \"gpt-3.5-turbo-instruct\"\n"
            + "    },\n"
            + "    \"credential\": {\n"
            + "        \"openAI_key\": \"xxxxxxxx\"\n"
            + "    },\n"
            + "    \"actions\": [\n"
            + "        {\n"
            + "            \"action_type\": \"predict\",\n"
            + "            \"method\": \"POST\",\n"
            + "            \"url\": \"https://${parameters.endpoint}/v1/completions\",\n"
            + "            \"headers\": {\n"
            + "                \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
            + "            },\n"
            + "            \"request_body\": \"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"prompt\\\": \\\"${parameters.prompt}\\\", \\\"max_tokens\\\": ${parameters.max_tokens}, \\\"temperature\\\": ${parameters.temperature} }\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";
    }
}
