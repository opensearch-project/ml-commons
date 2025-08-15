/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.rest.RestMLRAGSearchProcessorIT.COHERE_CONNECTOR_BLUEPRINT;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.rest.RestRequest;

public class RestMLConnectorTenantAwareIT extends MLCommonsTenantAwareRestTestCase {

    public void testConnectorCRUD() throws Exception {
        boolean multiTenancyEnabled = isMultiTenancyEnabled();

        /*
         * Create
         */
        // Create a connector with a tenant id
        RestRequest createConnectorRequest = getRestRequestWithHeadersAndContent(tenantId, COHERE_CONNECTOR_BLUEPRINT);
        Response response = makeRequest(createConnectorRequest, POST, CONNECTORS_PATH + "_create");
        assertOK(response);
        Map<String, Object> map = responseToMap(response);
        assertTrue(map.containsKey(CONNECTOR_ID));
        String connectorId = map.get(CONNECTOR_ID).toString();

        /*
         * Get
         */
        // Now try to get that connector
        response = makeRequest(tenantRequest, GET, CONNECTORS_PATH + connectorId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals("Cohere Chat Model", map.get("name"));
        if (multiTenancyEnabled) {
            assertEquals(tenantId, map.get(TENANT_ID_FIELD));
        } else {
            assertNull(map.get(TENANT_ID_FIELD));
        }

        // Now try again with an other ID
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(otherTenantRequest, GET, CONNECTORS_PATH + connectorId)
            );
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                assertNotFound(response);
                assertEquals("Failed to find connector with the provided connector id: " + connectorId, getErrorReasonFromResponseMap(map));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }
        } else {
            response = makeRequest(otherTenantRequest, GET, CONNECTORS_PATH + connectorId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("Cohere Chat Model", map.get("name"));
        }

        // Now try again with a null ID
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(nullTenantRequest, GET, CONNECTORS_PATH + connectorId)
            );
            response = ex.getResponse();
            map = responseToMap(response);
            assertForbidden(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        } else {
            response = makeRequest(nullTenantRequest, GET, CONNECTORS_PATH + connectorId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("Cohere Chat Model", map.get("name"));
        }

        /*
         * Update
         */
        // Now attempt to update the connector name
        RestRequest updateRequest = getRestRequestWithHeadersAndContent(tenantId, "{\"name\":\"Updated name\"}");
        response = makeRequest(updateRequest, PUT, CONNECTORS_PATH + connectorId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals(connectorId, map.get(DOC_ID).toString());

        // Verify the update
        response = makeRequest(tenantRequest, GET, CONNECTORS_PATH + connectorId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals("Updated name", map.get("name"));

        // Try the update with other tenant ID
        RestRequest otherUpdateRequest = getRestRequestWithHeadersAndContent(otherTenantId, "{\"name\":\"Other tenant name\"}");
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(otherUpdateRequest, PUT, CONNECTORS_PATH + connectorId)
            );
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                assertNotFound(response);
                assertEquals("Failed to find connector with the provided connector id: " + connectorId, getErrorReasonFromResponseMap(map));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }
        } else {
            response = makeRequest(otherUpdateRequest, PUT, CONNECTORS_PATH + connectorId);
            assertOK(response);
            // Verify the update
            response = makeRequest(otherTenantRequest, GET, CONNECTORS_PATH + connectorId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("Other tenant name", map.get("name"));
        }

        // Try the update with no tenant ID
        RestRequest nullUpdateRequest = getRestRequestWithHeadersAndContent(null, "{\"name\":\"Null tenant name\"}");
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(nullUpdateRequest, PUT, CONNECTORS_PATH + connectorId)
            );
            response = ex.getResponse();
            map = responseToMap(response);
            assertForbidden(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        } else {
            response = makeRequest(nullUpdateRequest, PUT, CONNECTORS_PATH + connectorId);
            assertOK(response);
            // Verify the update
            response = makeRequest(tenantRequest, GET, CONNECTORS_PATH + connectorId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("Null tenant name", map.get("name"));
        }

        // Verify no change from original update when multiTenancy enabled
        if (multiTenancyEnabled) {
            response = makeRequest(tenantRequest, GET, CONNECTORS_PATH + connectorId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("Updated name", map.get("name"));
        }

        /*
         * Search
         */
        // Create a second connector using otherTenantId
        RestRequest otherConnectorRequest = getRestRequestWithHeadersAndContent(otherTenantId, COHERE_CONNECTOR_BLUEPRINT);
        response = makeRequest(otherConnectorRequest, POST, CONNECTORS_PATH + "_create");
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(CONNECTOR_ID));
        String otherConnectorId = map.get(CONNECTOR_ID).toString();

        // Verify it
        response = makeRequest(otherTenantRequest, GET, CONNECTORS_PATH + otherConnectorId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals("Cohere Chat Model", map.get("name"));

        // Retry these tests until they pass. Search requires refresh, can take 15s on DDB
        refreshAllIndices();

        assertBusy(() -> {
            // Search should show only the connector for tenant
            Response restResponse = makeRequest(tenantMatchAllRequest, GET, CONNECTORS_PATH + "_search");
            assertOK(restResponse);
            SearchResponse searchResponse = searchResponseFromResponse(restResponse);
            if (multiTenancyEnabled) {
                assertEquals(1, searchResponse.getHits().getTotalHits().value());
                assertEquals(tenantId, searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
            } else {
                assertEquals(2, searchResponse.getHits().getTotalHits().value());
                assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
                assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID_FIELD));
            }
        }, 20, TimeUnit.SECONDS);

        assertBusy(() -> {
            // Search should show only the connector for other tenant
            Response restResponse = makeRequest(otherTenantMatchAllRequest, GET, CONNECTORS_PATH + "_search");
            assertOK(restResponse);
            SearchResponse searchResponse = searchResponseFromResponse(restResponse);
            if (multiTenancyEnabled) {
                assertEquals(1, searchResponse.getHits().getTotalHits().value());
                assertEquals(otherTenantId, searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
            } else {
                assertEquals(2, searchResponse.getHits().getTotalHits().value());
                assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
                assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID_FIELD));
            }
        }, 20, TimeUnit.SECONDS);

        // Search should fail without a tenant id
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(nullTenantMatchAllRequest, GET, CONNECTORS_PATH + "_search")
            );
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        } else {
            response = makeRequest(nullTenantMatchAllRequest, GET, CONNECTORS_PATH + "_search");
            assertOK(response);
            SearchResponse searchResponse = searchResponseFromResponse(response);
            assertEquals(2, searchResponse.getHits().getTotalHits().value());
            assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
            assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID_FIELD));
        }

        /*
         * Delete
         */
        // Delete the connectors
        // First test that we can't delete other tenant connectors
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(tenantRequest, DELETE, CONNECTORS_PATH + otherConnectorId)
            );
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                assertNotFound(response);
                assertEquals(
                    "Failed to find connector with the provided connector id: " + otherConnectorId,
                    getErrorReasonFromResponseMap(map)
                );
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }

            ex = assertThrows(ResponseException.class, () -> makeRequest(otherTenantRequest, DELETE, CONNECTORS_PATH + connectorId));
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                assertNotFound(response);
                assertEquals("Failed to find connector with the provided connector id: " + connectorId, getErrorReasonFromResponseMap(map));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }

            // and can't delete without a tenant ID either
            ex = assertThrows(ResponseException.class, () -> makeRequest(nullTenantRequest, DELETE, CONNECTORS_PATH + connectorId));
            response = ex.getResponse();
            map = responseToMap(response);
            assertForbidden(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        }

        // Now actually do the deletions. Same result whether multi-tenancy is enabled.
        // Delete from tenant
        response = makeRequest(tenantRequest, DELETE, CONNECTORS_PATH + connectorId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals(connectorId, map.get(DOC_ID).toString());

        // Verify the deletion
        ResponseException ex = assertThrows(ResponseException.class, () -> makeRequest(tenantRequest, GET, CONNECTORS_PATH + connectorId));
        response = ex.getResponse();
        assertNotFound(response);
        map = responseToMap(response);
        assertEquals("Failed to find connector with the provided connector id: " + connectorId, getErrorReasonFromResponseMap(map));

        // Delete from other tenant
        response = makeRequest(otherTenantRequest, DELETE, CONNECTORS_PATH + otherConnectorId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals(otherConnectorId, map.get(DOC_ID).toString());

        // Verify the deletion
        ex = assertThrows(ResponseException.class, () -> makeRequest(otherTenantRequest, GET, CONNECTORS_PATH + otherConnectorId));
        response = ex.getResponse();
        assertNotFound(response);
        map = responseToMap(response);
        assertEquals("Failed to find connector with the provided connector id: " + otherConnectorId, getErrorReasonFromResponseMap(map));
    }
}
