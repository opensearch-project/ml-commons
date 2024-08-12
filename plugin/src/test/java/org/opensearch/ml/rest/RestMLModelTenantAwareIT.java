/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.TENANT_ID;
import static org.opensearch.ml.common.MLTask.MODEL_ID_FIELD;

import java.io.IOException;
import java.util.Map;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.rest.RestRequest;

public class RestMLModelTenantAwareIT extends MLCommonsTenantAwareRestTestCase {

    private static final String MODELS_PATH = "/_plugins/_ml/models/";

    public void testModelCRUD() throws IOException, InterruptedException {
        testModelCRUDMultitenancyEnabled(true);
        testModelCRUDMultitenancyEnabled(false);
    }

    private void testModelCRUDMultitenancyEnabled(boolean multiTenancyEnabled) throws IOException, InterruptedException {
        enableMultiTenancy(multiTenancyEnabled);

        /*
         * Create
         */
        // Create a connector to use
        RestRequest createConnectorRequest = getRestRequestWithHeadersAndContent(tenantId, createConnectorContent());
        Response response = makeRequest(createConnectorRequest, POST, CONNECTORS_PATH + "_create");
        assertOK(response);
        Map<String, Object> map = responseToMap(response);
        assertTrue(map.containsKey(CONNECTOR_ID));
        String connectorId = map.get(CONNECTOR_ID).toString();

        // Register a remote model with a tenant id
        RestRequest registerModelRequest = getRestRequestWithHeadersAndContent(
            tenantId,
            registerRemoteModelContent("test model", connectorId)
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
            assertEquals(tenantId, map.get(TENANT_ID));
        } else {
            assertNull(map.get(TENANT_ID));
        }

        // Now try again with an other ID
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(ResponseException.class, () -> makeRequest(otherTenantRequest, GET, MODELS_PATH + modelId));
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
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
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
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
            registerRemoteModelContent("other test model", connectorId)
        );
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(wrongTenantModelRequest, POST, MODELS_PATH + "_register")
            );
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
        } else {
            response = makeRequest(wrongTenantModelRequest, POST, MODELS_PATH + "_register");
            assertOK(response);
        }

        // Create a second connector from other tenant
        createConnectorRequest = getRestRequestWithHeadersAndContent(otherTenantId, createConnectorContent());
        response = makeRequest(createConnectorRequest, POST, CONNECTORS_PATH + "_create");
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(CONNECTOR_ID));
        String otherConnectorId = map.get(CONNECTOR_ID).toString();

        // Now register a model with it
        RestRequest otherModelRequest = getRestRequestWithHeadersAndContent(
            otherTenantId,
            registerRemoteModelContent("other test model", otherConnectorId)
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

        // Search should show only the model for tenant
        response = makeRequest(tenantMatchAllRequest, GET, MODELS_PATH + "_search");
        assertOK(response);
        SearchResponse searchResponse = searchResponseFromResponse(response);
        if (multiTenancyEnabled) {
            // TODO Change to 1 when https://github.com/opensearch-project/ml-commons/pull/2803 is merged
            assertEquals(2, searchResponse.getHits().getTotalHits().value);
            assertEquals(tenantId, searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID));
        } else {
            assertEquals(3, searchResponse.getHits().getTotalHits().value);
            assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID));
            assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID));
        }

        // Search should show only the model for other tenant
        response = makeRequest(otherTenantMatchAllRequest, GET, MODELS_PATH + "_search");
        assertOK(response);
        searchResponse = searchResponseFromResponse(response);
        if (multiTenancyEnabled) {
            // TODO Change to 1 when https://github.com/opensearch-project/ml-commons/pull/2803 is merged
            assertEquals(2, searchResponse.getHits().getTotalHits().value);
            // TODO change [1] to [0]
            assertEquals(otherTenantId, searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID));
        } else {
            assertEquals(3, searchResponse.getHits().getTotalHits().value);
            assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID));
            assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID));
        }

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
            searchResponse = searchResponseFromResponse(response);
            assertEquals(3, searchResponse.getHits().getTotalHits().value);
            assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID));
            assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID));
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
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));

            ex = assertThrows(ResponseException.class, () -> makeRequest(otherTenantRequest, DELETE, MODELS_PATH + modelId));
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));

            // and can't delete without a tenant ID either
            ex = assertThrows(ResponseException.class, () -> makeRequest(nullTenantRequest, DELETE, MODELS_PATH + modelId));
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        }

        // Now actually do the deletions. Same result whether multi-tenancy is enabled.
        /*
         * TODO: Deletion currently failing due to IllegalStateException: Model is not all cleaned up, please try again.
         * Caused by: OpenSearchStatusException: Failed to delete all model chunks, Bulk failure while deleting model of -9iYQ5EBZ_lf6RWAq7U5
         
        // Delete from tenant
        response = makeRequest(tenantRequest, DELETE, MODELS_PATH + modelId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals(modelId, map.get(DOC_ID).toString());
        
        // Verify the deletion
        ResponseException ex = assertThrows(ResponseException.class, () -> makeRequest(tenantGetRequest, GET, MODELS_PATH + modelId));
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
        ex = assertThrows(ResponseException.class, () -> makeRequest(otherGetRequest, GET, MODELS_PATH + otherModelId));
        response = ex.getResponse();
        assertNotFound(response);
        map = responseToMap(response);
        assertEquals("Failed to find model with the provided model id: " + otherModelId, getErrorReasonFromResponseMap(map));
         */

        // Cleanup (since deletions may linger in search results)
        deleteIndexWithAdminClient(ML_MODEL_INDEX);
        // We test connector deletion elsewhere, just wipe the index
        deleteIndexWithAdminClient(ML_CONNECTOR_INDEX);
    }

    private static String registerRemoteModelContent(String description, String connectorId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"openAI-gpt-3.5-turbo\",\n");
        sb.append("  \"function_name\": \"remote\",\n");
        sb.append("  \"description\": \"").append(description).append("\",\n");
        sb.append("  \"connector_id\": \"").append(connectorId).append("\"\n");
        sb.append("}");
        return sb.toString();
    }
}
