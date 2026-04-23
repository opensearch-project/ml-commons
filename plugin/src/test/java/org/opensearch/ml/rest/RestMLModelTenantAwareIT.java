/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.MLTask.MODEL_ID_FIELD;
import static org.opensearch.ml.rest.RestMLRAGSearchProcessorIT.COHERE_CONNECTOR_BLUEPRINT;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.rest.RestRequest;

public class RestMLModelTenantAwareIT extends MLCommonsTenantAwareRestTestCase {

    public void testModelCRUD() throws Exception {
        boolean multiTenancyEnabled = isMultiTenancyEnabled();

        /*
         * Setup
         */
        // Create a connector to use
        RestRequest createConnectorRequest = getRestRequestWithHeadersAndContent(tenantId, COHERE_CONNECTOR_BLUEPRINT);
        Response response = makeRequest(createConnectorRequest, POST, CONNECTORS_PATH + "_create");
        assertOK(response);
        Map<String, Object> map = responseToMap(response);
        assertTrue(map.containsKey(CONNECTOR_ID));
        String connectorId = map.get(CONNECTOR_ID).toString();
        // Create a second connector with same tenant for deletion tests later
        response = makeRequest(createConnectorRequest, POST, CONNECTORS_PATH + "_create");
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(CONNECTOR_ID));
        String secondConnectorId = map.get(CONNECTOR_ID).toString();

        // Create a third connector from other tenant
        createConnectorRequest = getRestRequestWithHeadersAndContent(otherTenantId, COHERE_CONNECTOR_BLUEPRINT);
        response = makeRequest(createConnectorRequest, POST, CONNECTORS_PATH + "_create");
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(CONNECTOR_ID));
        String otherConnectorId = map.get(CONNECTOR_ID).toString();

        /*
         * Create
         */
        // Register a remote model with a tenant id
        RestRequest registerModelRequest = getRestRequestWithHeadersAndContent(
            tenantId,
            registerRemoteModelContent("test model", connectorId, null)
        );
        response = makeRequest(registerModelRequest, POST, MODELS_PATH + "_register");
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(MODEL_ID_FIELD));
        String modelId = map.get(MODEL_ID_FIELD).toString();

        /*
         * Get
         */
        // Now try to get that model
        response = makeRequest(tenantRequest, GET, MODELS_PATH + modelId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals("test model", map.get("description"));
        if (multiTenancyEnabled) {
            assertEquals(tenantId, map.get(TENANT_ID_FIELD));
        } else {
            assertNull(map.get(TENANT_ID_FIELD));
        }

        // Now try again with an other ID
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(ResponseException.class, () -> makeRequest(otherTenantRequest, GET, MODELS_PATH + modelId));
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                assertNotFound(response);
                assertEquals("Failed to find model with the provided model id: " + modelId, getErrorReasonFromResponseMap(map));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }
        } else {
            response = makeRequest(otherTenantRequest, GET, MODELS_PATH + modelId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("test model", map.get("description"));
        }

        // Now try again with a null ID
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(ResponseException.class, () -> makeRequest(nullTenantRequest, GET, MODELS_PATH + modelId));
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        } else {
            response = makeRequest(nullTenantRequest, GET, MODELS_PATH + modelId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("test model", map.get("description"));
        }

        /*
         * Update
         */
        // Now attempt to update the model name
        RestRequest updateRequest = getRestRequestWithHeadersAndContent(tenantId, "{\"description\":\"Updated test model\"}");
        response = makeRequest(updateRequest, PUT, MODELS_PATH + modelId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals(modelId, map.get(DOC_ID).toString());

        // Verify the update
        response = makeRequest(tenantRequest, GET, MODELS_PATH + modelId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals("Updated test model", map.get("description"));

        // Try the update with other tenant ID
        RestRequest otherUpdateRequest = getRestRequestWithHeadersAndContent(
            otherTenantId,
            "{\"description\":\"Other updated test model\"}"
        );
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(ResponseException.class, () -> makeRequest(otherUpdateRequest, PUT, MODELS_PATH + modelId));
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                assertNotFound(response);
                assertEquals("Failed to find model to update with the provided model id: " + modelId, getErrorReasonFromResponseMap(map));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }
        } else {
            response = makeRequest(otherUpdateRequest, PUT, MODELS_PATH + modelId);
            assertOK(response);
            // Verify the update
            response = makeRequest(otherTenantRequest, GET, MODELS_PATH + modelId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("Other updated test model", map.get("description"));
        }

        // Try the update with no tenant ID
        RestRequest nullUpdateRequest = getRestRequestWithHeadersAndContent(null, "{\"description\":\"Null updated test model\"}");
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(ResponseException.class, () -> makeRequest(nullUpdateRequest, PUT, MODELS_PATH + modelId));
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        } else {
            response = makeRequest(nullUpdateRequest, PUT, MODELS_PATH + modelId);
            assertOK(response);
            // Verify the update
            response = makeRequest(tenantRequest, GET, MODELS_PATH + modelId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("Null updated test model", map.get("description"));
        }

        // Verify no change from original update when multiTenancy enabled
        if (multiTenancyEnabled) {
            response = makeRequest(tenantRequest, GET, MODELS_PATH + modelId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("Updated test model", map.get("description"));
        }

        /*
         * Search
         */
        // Attempt to register a second remote model using otherTenantId but tenantId-owned connector
        RestRequest wrongTenantModelRequest = getRestRequestWithHeadersAndContent(
            otherTenantId,
            registerRemoteModelContent("other test model", connectorId, null)
        );
        String wrongTenantModelId = null;
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(wrongTenantModelRequest, POST, MODELS_PATH + "_register")
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
            response = makeRequest(wrongTenantModelRequest, POST, MODELS_PATH + "_register");
            assertOK(response);
            map = responseToMap(response);
            assertTrue(map.containsKey(MODEL_ID_FIELD));
            wrongTenantModelId = map.get(MODEL_ID_FIELD).toString();
        }

        // Now register a model with correct connector
        RestRequest otherModelRequest = getRestRequestWithHeadersAndContent(
            otherTenantId,
            registerRemoteModelContent("other test model", otherConnectorId, null)
        );
        response = makeRequest(otherModelRequest, POST, MODELS_PATH + "_register");
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(MODEL_ID_FIELD));
        String otherModelId = map.get(MODEL_ID_FIELD).toString();

        // Verify it
        response = makeRequest(otherTenantRequest, GET, MODELS_PATH + otherModelId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals("other test model", map.get("description"));

        // Retry these tests until they pass. Search requires refresh, can take 15s on DDB
        refreshAllIndices();

        assertBusy(() -> {
            // Search should show only the model for tenant
            Response restResponse = makeRequest(tenantMatchAllRequest, GET, MODELS_PATH + "_search");
            assertOK(restResponse);
            SearchResponse searchResponse = searchResponseFromResponse(restResponse);
            if (multiTenancyEnabled) {
                assertEquals(1, searchResponse.getHits().getTotalHits().value());
                assertEquals(tenantId, searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
            } else {
                assertEquals(3, searchResponse.getHits().getTotalHits().value());
                assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
                assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID_FIELD));
            }
        }, 30, TimeUnit.SECONDS);

        assertBusy(() -> {
            // Search should show only the model for other tenant
            Response restResponse = makeRequest(otherTenantMatchAllRequest, GET, MODELS_PATH + "_search");
            assertOK(restResponse);
            SearchResponse searchResponse = searchResponseFromResponse(restResponse);
            if (multiTenancyEnabled) {
                assertEquals(1, searchResponse.getHits().getTotalHits().value());
                assertEquals(otherTenantId, searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
            } else {
                assertEquals(3, searchResponse.getHits().getTotalHits().value());
                assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
                assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID_FIELD));
            }
        }, 30, TimeUnit.SECONDS);

        // Search should fail without a tenant id
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(nullTenantMatchAllRequest, GET, MODELS_PATH + "_search")
            );
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        } else {
            response = makeRequest(nullTenantMatchAllRequest, GET, MODELS_PATH + "_search");
            assertOK(response);
            SearchResponse searchResponse = searchResponseFromResponse(response);
            assertEquals(3, searchResponse.getHits().getTotalHits().value());
            assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
            assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID_FIELD));
        }

        // Test search with aggregations to verify no error is thrown
        Response restResponse = makeRequest(tenantAggregationRequest, GET, MODELS_PATH + "_search");
        assertOK(restResponse);
        // We can't (easily) parse the JSON from this response into a SearchResponse for the same reason as the bug we're testing
        // So we test the string values instead
        String responseJson = TestHelper.httpEntityToString(restResponse.getEntity());
        assertTrue(responseJson.contains("\"aggregations\":{\"unique_model_names\""));
        if (multiTenancyEnabled) {
            assertTrue(responseJson.contains("\"value\":1,\"relation\":\"eq\""));
            assertTrue(responseJson.contains("\"key\":\"remote model for connector_id "));
            assertTrue(responseJson.contains("\"doc_count\":1"));
        } else {
            assertTrue(responseJson.contains("\"value\":3,\"relation\":\"eq\""));
            // one connector will have two doc_count, other will have one
            assertTrue(responseJson.contains("\"key\":\"remote model for connector_id "));
            assertTrue(responseJson.contains("\"doc_count\":2"));
            assertTrue(responseJson.contains("\"doc_count\":1"));
        }

        /*
         * Delete
         */
        // Delete the models
        // First test that we can't delete other tenant models
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(tenantRequest, DELETE, MODELS_PATH + otherModelId)
            );
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                assertNotFound(response);
                assertEquals("Failed to find model", getErrorReasonFromResponseMap(map));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }

            ex = assertThrows(ResponseException.class, () -> makeRequest(otherTenantRequest, DELETE, MODELS_PATH + modelId));
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                assertNotFound(response);
                assertEquals("Failed to find model", getErrorReasonFromResponseMap(map));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }

            // and can't delete without a tenant ID either
            ex = assertThrows(ResponseException.class, () -> makeRequest(nullTenantRequest, DELETE, MODELS_PATH + modelId));
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        }

        // Try to delete connector while model exists
        ResponseException ex = assertThrows(
            ResponseException.class,
            () -> makeRequest(tenantRequest, DELETE, CONNECTORS_PATH + connectorId)
        );
        response = ex.getResponse();
        assertEquals(RestStatus.CONFLICT.getStatus(), response.getStatusLine().getStatusCode());
        // but it should work with the connector not in a model
        response = makeRequest(tenantRequest, DELETE, CONNECTORS_PATH + secondConnectorId);
        assertOK(response);

        // Now actually do the deletions. Same result whether multi-tenancy is enabled.
        // Verify still exists
        response = makeRequest(tenantRequest, GET, MODELS_PATH + modelId);
        assertOK(response);

        // Delete from tenant
        response = makeRequest(tenantRequest, DELETE, MODELS_PATH + modelId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals(modelId, map.get(DOC_ID).toString());

        // Verify the deletion
        ex = assertThrows(ResponseException.class, () -> makeRequest(tenantRequest, GET, MODELS_PATH + modelId));
        response = ex.getResponse();
        assertNotFound(response);
        map = responseToMap(response);
        assertEquals("Failed to find model with the provided model id: " + modelId, getErrorReasonFromResponseMap(map));

        // Delete from other tenant
        response = makeRequest(otherTenantRequest, DELETE, MODELS_PATH + otherModelId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals(otherModelId, map.get(DOC_ID).toString());

        // Verify the deletion
        ex = assertThrows(ResponseException.class, () -> makeRequest(otherTenantRequest, GET, MODELS_PATH + otherModelId));
        response = ex.getResponse();
        assertNotFound(response);
        map = responseToMap(response);
        assertEquals("Failed to find model with the provided model id: " + otherModelId, getErrorReasonFromResponseMap(map));

        // Delete the model created with null tenant if applicable
        if (wrongTenantModelId != null) {
            response = makeRequest(otherTenantRequest, DELETE, MODELS_PATH + wrongTenantModelId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals(wrongTenantModelId, map.get(DOC_ID).toString());

            // Verify the deletion
            final String deletedModelId = wrongTenantModelId;
            ex = assertThrows(ResponseException.class, () -> makeRequest(otherTenantRequest, GET, MODELS_PATH + deletedModelId));
            response = ex.getResponse();
            assertNotFound(response);
            map = responseToMap(response);
            assertEquals("Failed to find model with the provided model id: " + deletedModelId, getErrorReasonFromResponseMap(map));
        }

        /*
         * Cleanup other resources created
         */
        deleteAndWaitForSearch(tenantId, CONNECTORS_PATH, connectorId, multiTenancyEnabled ? 0 : 1);
        deleteAndWaitForSearch(otherTenantId, CONNECTORS_PATH, otherConnectorId, 0);
    }
}
