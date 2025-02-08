/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.MLModel.MODEL_STATE_FIELD;
import static org.opensearch.ml.common.MLTask.MODEL_ID_FIELD;
import static org.opensearch.ml.rest.RestMLRAGSearchProcessorIT.COHERE_CONNECTOR_BLUEPRINT;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.rest.RestRequest;

public class RestMLModelUndeployTenantAwareIT extends MLCommonsTenantAwareRestTestCase {

    // Tests the client.bulk API used for undeploying models
    public void testModelDeployUndeploy() throws Exception {
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

        /*
         * Create
         */
        // Register and deploy a remote model with a tenant id
        RestRequest registerModelRequest = getRestRequestWithHeadersAndContent(
            tenantId,
            registerRemoteModelContent("test model", connectorId, null)
        );
        response = makeRequest(registerModelRequest, POST, MODELS_PATH + "_register?deploy=true");
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(MODEL_ID_FIELD));
        String modelId = map.get(MODEL_ID_FIELD).toString();

        /*
         * Get
         */
        // Now get that model and confirm it's deployed
        assertBusy(() -> {
            Response getResponse = makeRequest(tenantRequest, GET, MODELS_PATH + modelId);
            assertOK(getResponse);
            Map<String, Object> responseMap = responseToMap(getResponse);
            assertEquals("DEPLOYED", responseMap.get(MODEL_STATE_FIELD).toString());
            if (multiTenancyEnabled) {
                assertEquals(tenantId, responseMap.get(TENANT_ID_FIELD));
            } else {
                assertNull(responseMap.get(TENANT_ID_FIELD));
            }
        }, 30, TimeUnit.SECONDS);

        /*
         * Test delete/deploy interaction
         */
        // Attempt to delete, should fail because it's deployed
        ResponseException ex = assertThrows(ResponseException.class, () -> makeRequest(tenantRequest, DELETE, MODELS_PATH + modelId));
        response = ex.getResponse();
        assertBadRequest(response);
        map = responseToMap(response);
        assertEquals(DEPLOYED_REASON, getErrorReasonFromResponseMap(map));

        // Verify still exists
        response = makeRequest(tenantRequest, GET, MODELS_PATH + modelId);
        assertOK(response);

        /*
         * Undeploy
         */
        // Undeploy the model which uses the bulk API
        if (multiTenancyEnabled) {
            // Try with the wrong tenant
            ex = assertThrows(ResponseException.class, () -> makeRequest(otherTenantRequest, POST, MODELS_PATH + modelId + "/_undeploy"));
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                assertNotFound(response);
                assertTrue(getErrorReasonFromResponseMap(map).startsWith("Failed to find model"));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }

            // Try with a null tenant
            ex = assertThrows(ResponseException.class, () -> makeRequest(nullTenantRequest, POST, MODELS_PATH + modelId + "/_undeploy"));
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        }

        // Now do with correct tenant
        response = makeRequest(tenantRequest, POST, MODELS_PATH + modelId + "/_undeploy");
        assertOK(response);
        // This is an MLUndeployControllerNodeResponse
        map = responseToMap(response);
        // This map's keys are the nodes, and the values are a map with "stats" key
        // One of these is a map object with modelId as key and "undeployed" as value
        String expectedValue = modelId + "=undeployed";
        assertTrue(map.toString().contains(expectedValue));

        // Verify the undeploy update
        response = makeRequest(tenantRequest, GET, MODELS_PATH + modelId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals("UNDEPLOYED", map.get(MODEL_STATE_FIELD).toString());
        if (multiTenancyEnabled) {
            assertEquals(tenantId, map.get(TENANT_ID_FIELD));
        } else {
            assertNull(map.get(TENANT_ID_FIELD));
        }

        /*
         * Delete
         */
        // Delete, should now succeed because it's deployed
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

        /*
         * Cleanup other resources created
         */
        deleteAndWaitForSearch(tenantId, CONNECTORS_PATH, connectorId, 0);
    }
}
