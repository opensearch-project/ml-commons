/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.opensearch.common.xcontent.XContentType.JSON;
import static org.opensearch.ml.common.input.Constants.TENANT_ID_HEADER;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
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

public abstract class MLCommonsTenantAwareRestTestCase extends MLCommonsRestTestCase {

    // Toggle to run DDB tests
    // TODO: Get this from a property
    protected static final boolean DDB = false;

    protected static final String DOC_ID = "_id";

    // REST methods
    protected static final String POST = RestRequest.Method.POST.name();
    protected static final String GET = RestRequest.Method.GET.name();
    protected static final String PUT = RestRequest.Method.PUT.name();
    protected static final String DELETE = RestRequest.Method.DELETE.name();

    // REST paths; some subclasses need multiple of these
    protected static final String AGENTS_PATH = "/_plugins/_ml/agents/";
    protected static final String CONNECTORS_PATH = "/_plugins/_ml/connectors/";
    protected static final String MODELS_PATH = "/_plugins/_ml/models/";
    protected static final String MODEL_GROUPS_PATH = "/_plugins/_ml/model_groups/";

    // REST body
    protected static final String MATCH_ALL_QUERY = "{\"query\":{\"match_all\":{}}}";
    protected static final String AGGREGATION_QUERY = "{\n"
        + "  \"size\": 0,\n"
        + "  \"aggs\": {\n"
        + "    \"unique_model_names\": {\n"
        + "      \"terms\": {\n"
        + "        \"field\": \"name.keyword\",\n"
        + "        \"size\": 10000\n"
        + "      }\n"
        + "    }\n"
        + "  }\n"
        + "}";

    protected static final String EMPTY_CONTENT = "{}";

    // REST Response error reasons
    protected static final String MISSING_TENANT_REASON = "Tenant ID header is missing or has no value";
    protected static final String NO_PERMISSION_REASON = "You don't have permission to access this resource";
    protected static final String DEPLOYED_REASON =
        "Model cannot be deleted in deploying or deployed state. Try undeploy model first then delete";

    // Common constants and fields used in subclasses
    protected static final String CONNECTOR_ID = "connector_id";

    protected String tenantId = randomAlphaOfLength(5);
    protected String otherTenantId = randomAlphaOfLength(6);

    protected final RestRequest tenantRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
        .withHeaders(Map.of(TENANT_ID_HEADER, singletonList(tenantId)))
        .build();
    protected final RestRequest otherTenantRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
        .withHeaders(Map.of(TENANT_ID_HEADER, singletonList(otherTenantId)))
        .build();
    protected final RestRequest nullTenantRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
        .withHeaders(emptyMap())
        .build();

    protected final RestRequest tenantMatchAllRequest = getRestRequestWithHeadersAndContent(tenantId, MATCH_ALL_QUERY);
    protected final RestRequest otherTenantMatchAllRequest = getRestRequestWithHeadersAndContent(otherTenantId, MATCH_ALL_QUERY);
    protected final RestRequest nullTenantMatchAllRequest = getRestRequestWithHeadersAndContent(null, MATCH_ALL_QUERY);
    protected final RestRequest tenantAggregationRequest = getRestRequestWithHeadersAndContent(tenantId, AGGREGATION_QUERY);

    protected static boolean isMultiTenancyEnabled() throws IOException {
        // pass -Dtests.rest.tenantaware=true on gradle command line to enable
        return Boolean.parseBoolean(System.getProperty(ML_COMMONS_MULTI_TENANCY_ENABLED.getKey()))
            || Boolean.parseBoolean(System.getenv(ML_COMMONS_MULTI_TENANCY_ENABLED.getKey()));
    }

    protected static Response makeRequest(RestRequest request, String method, String path) throws IOException {
        return TestHelper
            .makeRequest(client(), method, path, request.params(), request.content().utf8ToString(), getHeadersFromRequest(request));
    }

    private static List<Header> getHeadersFromRequest(RestRequest request) {
        return request
            .getHeaders()
            .entrySet()
            .stream()
            .map(e -> new BasicHeader(e.getKey(), e.getValue().stream().collect(Collectors.joining(","))))
            .collect(Collectors.toList());
    }

    protected static RestRequest getRestRequestWithHeadersAndContent(String tenantId, String requestContent) {
        Map<String, List<String>> headers = new HashMap<>();
        if (tenantId != null) {
            headers.put(Constants.TENANT_ID_HEADER, singletonList(tenantId));
        }
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withHeaders(headers)
            .withContent(new BytesArray(requestContent), JSON)
            .build();
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> responseToMap(Response response) throws IOException {
        return parseResponseToMap(response);
    }

    @SuppressWarnings("unchecked")
    protected static String getErrorReasonFromResponseMap(Map<String, Object> map) {
        // Two possible cases:
        String type = ((Map<String, String>) map.get("error")).get("type");

        // {
        // "error": {
        // "root_cause": [
        // {
        // "type": "status_exception",
        // "reason": "You don't have permission to access this resource"
        // }
        // ],
        // "type": "status_exception",
        // "reason": "You don't have permission to access this resource"
        // },
        // "status": 403
        // }
        if ("status_exception".equals(type)) {
            return ((Map<String, String>) map.get("error")).get("reason");
        }

        // Due to https://github.com/opensearch-project/ml-commons/issues/2958
        if ("m_l_resource_not_found_exception".equals(type)) {
            return ((Map<String, String>) map.get("error")).get("reason");
        }

        // {
        // "error": {
        // "reason": "System Error",
        // "details": "You don't have permission to access this resource",
        // "type": "OpenSearchStatusException"
        // },
        // "status": 403
        // }
        return ((Map<String, String>) map.get("error")).get("details");
    }

    protected static SearchResponse searchResponseFromResponse(Response response) throws IOException {
        XContentParser parser = JsonXContent.jsonXContent
            .createParser(
                NamedXContentRegistry.EMPTY,
                DeprecationHandler.IGNORE_DEPRECATIONS,
                TestHelper.httpEntityToString(response.getEntity()).getBytes(UTF_8)
            );
        return SearchResponse.fromXContent(parser);
    }

    protected static void assertBadRequest(Response response) {
        assertEquals(RestStatus.BAD_REQUEST.getStatus(), response.getStatusLine().getStatusCode());
    }

    protected static void assertNotFound(Response response) {
        assertEquals(RestStatus.NOT_FOUND.getStatus(), response.getStatusLine().getStatusCode());
    }

    protected static void assertForbidden(Response response) {
        assertEquals(RestStatus.FORBIDDEN.getStatus(), response.getStatusLine().getStatusCode());
    }

    protected static void assertUnauthorized(Response response) {
        assertEquals(RestStatus.UNAUTHORIZED.getStatus(), response.getStatusLine().getStatusCode());
    }

    protected void refreshBeforeSearch(boolean extraDelay) {
        try {
            refreshAllIndices();
            Thread.sleep(extraDelay ? 60000L : 5000L);
        } catch (IOException | InterruptedException e) {
            // ignore
        }
    }

    /**
     * Delete the specified document and wait until a search matches only the specified number of hits
     * @param tenantId The tenant ID to filter the search by
     * @param restPath The base path for the REST API
     * @param id The document ID to be appended to the REST API for deletion
     * @param hits The number of hits to expect after the deletion is processed
     * @throws Exception on failures with building or making the request
     */
    protected static void deleteAndWaitForSearch(String tenantId, String restPath, String id, int hits) throws Exception {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withHeaders(Map.of(TENANT_ID_HEADER, singletonList(tenantId)))
            .build();
        // First process the deletion. Dependent resources (e.g. model with connector) may cause 409 status until they are deleted
        assertBusy(() -> {
            try {
                Response deleteResponse = makeRequest(request, DELETE, restPath + id);
                // first successful deletion should produce an OK
                assertOK(deleteResponse);
            } catch (ResponseException e) {
                // repeat deletions can produce a 404, treat as a success
                assertNotFound(e.getResponse());
            }
        }, 20, TimeUnit.SECONDS);
        // Deletion processed, now wait for it to disappear from search
        RestRequest searchRequest = getRestRequestWithHeadersAndContent(tenantId, MATCH_ALL_QUERY);
        assertBusy(() -> {
            Response response = makeRequest(searchRequest, GET, restPath + "_search");
            assertOK(response);
            SearchResponse searchResponse = searchResponseFromResponse(response);
            assertEquals(hits, searchResponse.getHits().getTotalHits().value());
        }, 20, TimeUnit.SECONDS);
    }

    protected static String registerRemoteModelContent(String description, String connectorId, String modelGroupId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"remote model for connector_id ").append(connectorId).append("\",\n");
        sb.append("  \"function_name\": \"remote\",\n");
        sb.append("  \"description\": \"").append(description).append("\",\n");
        if (modelGroupId != null) {
            sb.append("  \"model_group_id\": \"").append(modelGroupId).append("\",\n");
        }
        sb.append("  \"connector_id\": \"").append(connectorId).append("\"\n");
        sb.append("}");
        return sb.toString();
    }
}
