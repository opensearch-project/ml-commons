/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.MLModelGroup.MODEL_GROUP_ID_FIELD;
import static org.opensearch.ml.common.MLTask.MODEL_ID_FIELD;
import static org.opensearch.ml.rest.RestMLRAGSearchProcessorIT.COHERE_CONNECTOR_BLUEPRINT;

import java.util.Map;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.RestRequest;

public class RestMLModelGroupTenantAwareIT extends MLCommonsTenantAwareRestTestCase {

    public void testModelGroupCRUD() throws Exception {
        boolean multiTenancyEnabled = isMultiTenancyEnabled();

        /*
         * Create
         */
        // Register a model group with a tenant id
        RestRequest registerModelGroupRequest = getRestRequestWithHeadersAndContent(
            tenantId,
            registerModelGroupContent("test model group")
        );
        Response response = makeRequest(registerModelGroupRequest, POST, MODEL_GROUPS_PATH + "_register");
        assertOK(response);
        Map<String, Object> map = responseToMap(response);
        assertTrue(map.containsKey(MODEL_GROUP_ID_FIELD));
        String modelGroupId = map.get(MODEL_GROUP_ID_FIELD).toString();

        /*
         * Get
         */
        // Now try to get that model group
        response = makeRequest(tenantRequest, GET, MODEL_GROUPS_PATH + modelGroupId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals("test model group", map.get("name"));
        if (multiTenancyEnabled) {
            assertEquals(tenantId, map.get(TENANT_ID_FIELD));
        } else {
            assertNull(map.get(TENANT_ID_FIELD));
        }

        // Now try again with an other ID
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(otherTenantRequest, GET, MODEL_GROUPS_PATH + modelGroupId)
            );
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                assertNotFound(response);
                assertEquals(
                    "Failed to find model group with the provided model group id: " + modelGroupId,
                    getErrorReasonFromResponseMap(map)
                );
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }
        } else {
            response = makeRequest(otherTenantRequest, GET, MODEL_GROUPS_PATH + modelGroupId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("test model group", map.get("name"));
        }

        // Now try again with a null ID
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(nullTenantRequest, GET, MODEL_GROUPS_PATH + modelGroupId)
            );
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        } else {
            response = makeRequest(nullTenantRequest, GET, MODEL_GROUPS_PATH + modelGroupId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("test model group", map.get("name"));
        }

        /*
         * Register Model with Model Group
         */
        // Create a connector to use
        RestRequest createConnectorRequest = getRestRequestWithHeadersAndContent(tenantId, COHERE_CONNECTOR_BLUEPRINT);
        response = makeRequest(createConnectorRequest, POST, CONNECTORS_PATH + "_create");
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(CONNECTOR_ID));
        String connectorId = map.get(CONNECTOR_ID).toString();
        // Create a connector with other tenant
        createConnectorRequest = getRestRequestWithHeadersAndContent(otherTenantId, COHERE_CONNECTOR_BLUEPRINT);
        response = makeRequest(createConnectorRequest, POST, CONNECTORS_PATH + "_create");
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(CONNECTOR_ID));
        String otherConnectorId = map.get(CONNECTOR_ID).toString();

        // Register a remote model with tenant without specifying model group
        RestRequest registerModelRequest = getRestRequestWithHeadersAndContent(
            tenantId,
            registerRemoteModelContent("test model", connectorId, null)
        );
        response = makeRequest(registerModelRequest, POST, MODELS_PATH + "_register");
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(MODEL_ID_FIELD));
        String modelId = map.get(MODEL_ID_FIELD).toString();
        // Now get that model to recover the model group ID
        response = makeRequest(tenantRequest, GET, MODELS_PATH + modelId);
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(MODEL_GROUP_ID_FIELD));
        String autoModelGroupId = map.get(MODEL_GROUP_ID_FIELD).toString();

        // Register a remote model with tenant and specify same model group ID
        RestRequest registerModelInSameGroupRequest = getRestRequestWithHeadersAndContent(
            tenantId,
            registerRemoteModelContent("test model", connectorId, autoModelGroupId)
        );
        response = makeRequest(registerModelInSameGroupRequest, POST, MODELS_PATH + "_register");
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(MODEL_ID_FIELD));
        String sameGroupModelId = map.get(MODEL_ID_FIELD).toString();
        // Now get that model to recover the model group ID
        response = makeRequest(tenantRequest, GET, MODELS_PATH + sameGroupModelId);
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(MODEL_GROUP_ID_FIELD));
        String sameGroupModelGroupId = map.get(MODEL_GROUP_ID_FIELD).toString();

        // Attempt to register a remote model with other tenant and specify same model group ID
        RestRequest registerModelInSameGroupOtherTenantRequest = getRestRequestWithHeadersAndContent(
            otherTenantId,
            registerRemoteModelContent("test model", otherConnectorId, sameGroupModelGroupId)
        );
        String modelInSameGroupOtherTenant = null;
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(registerModelInSameGroupOtherTenantRequest, POST, MODELS_PATH + "_register")
            );
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                // Should probably be 404, see https://github.com/opensearch-project/ml-commons/issues/2958
                assertEquals(RestStatus.INTERNAL_SERVER_ERROR.getStatus(), response.getStatusLine().getStatusCode());
                assertEquals("Fail to find model group", getErrorReasonFromResponseMap(map));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }
        } else {
            response = makeRequest(registerModelInSameGroupOtherTenantRequest, POST, MODELS_PATH + "_register");
            assertOK(response);
            map = responseToMap(response);
            assertTrue(map.containsKey(MODEL_ID_FIELD));
            modelInSameGroupOtherTenant = map.get(MODEL_ID_FIELD).toString();
        }

        /*
         * Update
         */
        // Now attempt to update the model group name
        RestRequest updateRequest = getRestRequestWithHeadersAndContent(tenantId, "{\"name\":\"Updated test model group\"}");
        response = makeRequest(updateRequest, PUT, MODEL_GROUPS_PATH + modelGroupId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals("Updated", map.get("status").toString());

        // Verify the update
        response = makeRequest(tenantRequest, GET, MODEL_GROUPS_PATH + modelGroupId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals("Updated test model group", map.get("name"));

        // Try the update with other tenant ID
        RestRequest otherUpdateRequest = getRestRequestWithHeadersAndContent(
            otherTenantId,
            "{\"name\":\"Other updated test model group\"}"
        );
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(otherUpdateRequest, PUT, MODEL_GROUPS_PATH + modelGroupId)
            );
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                assertNotFound(response);
                assertEquals(
                    "Failed to find model group with the provided model group id: " + modelGroupId,
                    getErrorReasonFromResponseMap(map)
                );
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }
        } else {
            response = makeRequest(otherUpdateRequest, PUT, MODEL_GROUPS_PATH + modelGroupId);
            assertOK(response);
            // Verify the update
            response = makeRequest(otherTenantRequest, GET, MODEL_GROUPS_PATH + modelGroupId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("Other updated test model group", map.get("name"));
        }

        // Try the update with no tenant ID
        RestRequest nullUpdateRequest = getRestRequestWithHeadersAndContent(null, "{\"name\":\"Null updated test model group\"}");
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(nullUpdateRequest, PUT, MODEL_GROUPS_PATH + modelGroupId)
            );
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        } else {
            response = makeRequest(nullUpdateRequest, PUT, MODEL_GROUPS_PATH + modelGroupId);
            assertOK(response);
            // Verify the update
            response = makeRequest(tenantRequest, GET, MODEL_GROUPS_PATH + modelGroupId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("Null updated test model group", map.get("name"));
        }

        // Verify no change from original update when multiTenancy enabled
        if (multiTenancyEnabled) {
            response = makeRequest(tenantRequest, GET, MODEL_GROUPS_PATH + modelGroupId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("Updated test model group", map.get("name"));
        }

        /*
         * Search
         */
        // Now register a second model group with other tenant
        RestRequest otherModelGroupRequest = getRestRequestWithHeadersAndContent(
            otherTenantId,
            registerModelGroupContent("other test model group")
        );
        response = makeRequest(otherModelGroupRequest, POST, MODEL_GROUPS_PATH + "_register");
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(MODEL_GROUP_ID_FIELD));
        String otherModelGroupId = map.get(MODEL_GROUP_ID_FIELD).toString();

        // Verify it
        response = makeRequest(otherTenantRequest, GET, MODEL_GROUPS_PATH + otherModelGroupId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals("other test model group", map.get("name"));

        // Refresh before searching to avoid race conditions
        refreshBeforeSearch(DDB);

        // Search should show only the model groups for tenant (explicit and auto)
        response = makeRequest(tenantMatchAllRequest, GET, MODEL_GROUPS_PATH + "_search");
        assertOK(response);
        SearchResponse searchResponse = searchResponseFromResponse(response);
        if (multiTenancyEnabled) {
            assertEquals(2, searchResponse.getHits().getTotalHits().value());
            assertEquals(tenantId, searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
        } else {
            assertEquals(3, searchResponse.getHits().getTotalHits().value());
            assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
            assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID_FIELD));
        }

        // Search should show only the model group for other tenant
        response = makeRequest(otherTenantMatchAllRequest, GET, MODEL_GROUPS_PATH + "_search");
        assertOK(response);
        searchResponse = searchResponseFromResponse(response);
        if (multiTenancyEnabled) {
            assertEquals(1, searchResponse.getHits().getTotalHits().value());
            assertEquals(otherTenantId, searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
        } else {
            assertEquals(3, searchResponse.getHits().getTotalHits().value());
            assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
            assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID_FIELD));
        }

        // Search should fail without a tenant id
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(nullTenantMatchAllRequest, GET, MODEL_GROUPS_PATH + "_search")
            );
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        } else {
            response = makeRequest(nullTenantMatchAllRequest, GET, MODEL_GROUPS_PATH + "_search");
            assertOK(response);
            searchResponse = searchResponseFromResponse(response);
            assertEquals(3, searchResponse.getHits().getTotalHits().value());
            assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
            assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID_FIELD));
        }

        /*
         * Delete
         */
        // Delete the model groups
        // First test that we can't delete other tenant model groups
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(tenantRequest, DELETE, MODEL_GROUPS_PATH + otherModelGroupId)
            );
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                // Should probably be 404, see https://github.com/opensearch-project/ml-commons/issues/2958
                assertEquals(RestStatus.INTERNAL_SERVER_ERROR.getStatus(), response.getStatusLine().getStatusCode());
                assertEquals("Fail to find model group", getErrorReasonFromResponseMap(map));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }

            ex = assertThrows(ResponseException.class, () -> makeRequest(otherTenantRequest, DELETE, MODEL_GROUPS_PATH + modelGroupId));
            response = ex.getResponse();
            if (DDB) {
                // Should probably be 404, see https://github.com/opensearch-project/ml-commons/issues/2958
                assertEquals(RestStatus.INTERNAL_SERVER_ERROR.getStatus(), response.getStatusLine().getStatusCode());
                assertEquals("Fail to find model group", getErrorReasonFromResponseMap(map));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }

            // and can't delete without a tenant ID either
            ex = assertThrows(ResponseException.class, () -> makeRequest(nullTenantRequest, DELETE, MODEL_GROUPS_PATH + modelGroupId));
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        }

        // Now actually do the deletions. Same result whether multi-tenancy is enabled.

        // Delete from tenant
        response = makeRequest(tenantRequest, DELETE, MODEL_GROUPS_PATH + modelGroupId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals(modelGroupId, map.get(DOC_ID).toString());

        // Verify the deletion
        ResponseException ex = assertThrows(
            ResponseException.class,
            () -> makeRequest(tenantRequest, GET, MODEL_GROUPS_PATH + modelGroupId)
        );
        response = ex.getResponse();
        assertNotFound(response);
        map = responseToMap(response);
        assertEquals("Failed to find model group with the provided model group id: " + modelGroupId, getErrorReasonFromResponseMap(map));

        // Delete from other tenant
        response = makeRequest(otherTenantRequest, DELETE, MODEL_GROUPS_PATH + otherModelGroupId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals(otherModelGroupId, map.get(DOC_ID).toString());

        // Verify the deletion
        ex = assertThrows(ResponseException.class, () -> makeRequest(otherTenantRequest, GET, MODEL_GROUPS_PATH + otherModelGroupId));
        response = ex.getResponse();
        assertNotFound(response);
        map = responseToMap(response);
        assertEquals(
            "Failed to find model group with the provided model group id: " + otherModelGroupId,
            getErrorReasonFromResponseMap(map)
        );

        /*
         * Cleanup other resources created
         */
        int additionalModel = modelInSameGroupOtherTenant != null ? 1 : 0;
        // Need to wait until it's gone from search due to https://github.com/opensearch-project/ml-commons/issues/2932
        deleteAndWaitForSearch(tenantId, MODELS_PATH, modelId, 1 + additionalModel);
        deleteAndWaitForSearch(tenantId, MODELS_PATH, sameGroupModelId, additionalModel);
        if (modelInSameGroupOtherTenant != null) {
            deleteAndWaitForSearch(otherTenantId, MODELS_PATH, modelInSameGroupOtherTenant, 0);
        }
        deleteAndWaitForSearch(tenantId, CONNECTORS_PATH, connectorId, multiTenancyEnabled ? 0 : 1);
        deleteAndWaitForSearch(otherTenantId, CONNECTORS_PATH, otherConnectorId, 0);
    }

    private static String registerModelGroupContent(String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(name).append("\"\n");
        sb.append("}");
        return sb.toString();
    }
}
