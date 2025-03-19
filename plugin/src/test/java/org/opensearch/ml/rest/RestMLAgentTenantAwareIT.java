/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.MLTask.MODEL_ID_FIELD;
import static org.opensearch.ml.common.input.execute.agent.AgentMLInput.AGENT_ID_FIELD;
import static org.opensearch.ml.common.output.model.ModelTensorOutput.INFERENCE_RESULT_FIELD;
import static org.opensearch.ml.rest.RestMLRAGSearchProcessorIT.COHERE_CONNECTOR_BLUEPRINT;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.rest.RestRequest;

public class RestMLAgentTenantAwareIT extends MLCommonsTenantAwareRestTestCase {

    public void testAgentCRUD() throws Exception {
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
        // Register a remote model to use
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
         * Create
         */
        // Register a flow agent with a tenant id
        RestRequest registerAgentRequest = getRestRequestWithHeadersAndContent(tenantId, registerFlowAgentContent("test agent", modelId));
        response = makeRequest(registerAgentRequest, POST, AGENTS_PATH + "_register");
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(AGENT_ID_FIELD));
        String agentId = map.get(AGENT_ID_FIELD).toString();

        /*
         * Get
         */
        // Now try to get that agent
        response = makeRequest(tenantRequest, GET, AGENTS_PATH + agentId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals("test agent", map.get("name"));
        if (multiTenancyEnabled) {
            assertEquals(tenantId, map.get(TENANT_ID_FIELD));
        } else {
            assertNull(map.get(TENANT_ID_FIELD));
        }

        // Now try again with an other ID
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(ResponseException.class, () -> makeRequest(otherTenantRequest, GET, AGENTS_PATH + agentId));
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                assertNotFound(response);
                assertEquals("Failed to find agent with the provided agent id: " + agentId, getErrorReasonFromResponseMap(map));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }
        } else {
            response = makeRequest(otherTenantRequest, GET, AGENTS_PATH + agentId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("test agent", map.get("name"));
        }

        // Now try again with a null ID
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(ResponseException.class, () -> makeRequest(nullTenantRequest, GET, AGENTS_PATH + agentId));
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        } else {
            response = makeRequest(nullTenantRequest, GET, AGENTS_PATH + agentId);
            assertOK(response);
            map = responseToMap(response);
            assertEquals("test agent", map.get("name"));
        }

        /*
         * Update
         */
        // Update Agent not implemented

        /*
         * Execute
         */
        RestRequest executeAgentRequest = getRestRequestWithHeadersAndContent(tenantId, executeAgentContent());
        try {
            // This test relies on the correct api key in the environment variable COHERE_API_KEY
            // If the correct key is present, this call will succeed and produce an LLM response
            response = makeRequest(executeAgentRequest, POST, AGENTS_PATH + agentId + "/_execute");
            assertOK(response);
            map = responseToMap(response);
            assertTrue(map.containsKey(INFERENCE_RESULT_FIELD));
        } catch (ResponseException ex) {
            // Otherwise there will be a 401 error from the external model
            // This is still considered a successful test of calling agent execute
            response = ex.getResponse();
            assertUnauthorized(response);
            map = responseToMap(response);
            assert (getErrorReasonFromResponseMap(map).contains("invalid api token"));
        }

        // Now try again with an other ID
        RestRequest otherTenantExecuteAgentRequest = getRestRequestWithHeadersAndContent(otherTenantId, executeAgentContent());
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(otherTenantExecuteAgentRequest, POST, AGENTS_PATH + agentId + "/_execute")
            );
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                assertNotFound(response);
                assertEquals("Failed to find agent with the provided agent id: " + agentId, getErrorReasonFromResponseMap(map));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }
        } else {
            try {
                response = makeRequest(executeAgentRequest, POST, AGENTS_PATH + agentId + "/_execute");
                assertOK(response);
                map = responseToMap(response);
                assertTrue(map.containsKey(INFERENCE_RESULT_FIELD));
            } catch (ResponseException ex) {
                response = ex.getResponse();
                assertUnauthorized(response);
                map = responseToMap(response);
                assert (getErrorReasonFromResponseMap(map).contains("invalid api token"));
            }
        }

        // Now try again with a null ID
        RestRequest nullTenantExecuteAgentRequest = getRestRequestWithHeadersAndContent(null, executeAgentContent());
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(nullTenantExecuteAgentRequest, POST, AGENTS_PATH + agentId + "/_execute")
            );
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        } else {
            try {
                response = makeRequest(executeAgentRequest, POST, AGENTS_PATH + agentId + "/_execute");
                assertOK(response);
                map = responseToMap(response);
                assertTrue(map.containsKey(INFERENCE_RESULT_FIELD));
            } catch (ResponseException ex) {
                response = ex.getResponse();
                assertUnauthorized(response);
                map = responseToMap(response);
                assert (getErrorReasonFromResponseMap(map).contains("invalid api token"));
            }
        }

        /*
         * Search
         */
        // Register a second flow agent using otherTenantId (Using wrong tenant model ID)
        RestRequest otherAgentRequest = getRestRequestWithHeadersAndContent(
            otherTenantId,
            registerFlowAgentContent("other test agent", modelId)
        );
        response = makeRequest(otherAgentRequest, POST, AGENTS_PATH + "_register");
        assertOK(response);
        map = responseToMap(response);
        assertTrue(map.containsKey(AGENT_ID_FIELD));
        String otherAgentId = map.get(AGENT_ID_FIELD).toString();

        // Verify it
        response = makeRequest(otherTenantRequest, GET, AGENTS_PATH + otherAgentId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals("other test agent", map.get("name"));

        // Retry these tests until they pass. Search requires refresh, can take 15s on DDB
        refreshAllIndices();

        assertBusy(() -> {
            // Search should show only the agent for tenant
            Response restResponse = makeRequest(tenantMatchAllRequest, GET, AGENTS_PATH + "_search");
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
            // Search should show only the agent for other tenant
            Response restResponse = makeRequest(otherTenantMatchAllRequest, GET, AGENTS_PATH + "_search");
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
                () -> makeRequest(nullTenantMatchAllRequest, GET, AGENTS_PATH + "_search")
            );
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        } else {
            response = makeRequest(nullTenantMatchAllRequest, GET, AGENTS_PATH + "_search");
            assertOK(response);
            SearchResponse searchResponse = searchResponseFromResponse(response);
            assertEquals(2, searchResponse.getHits().getTotalHits().value());
            assertNull(searchResponse.getHits().getHits()[0].getSourceAsMap().get(TENANT_ID_FIELD));
            assertNull(searchResponse.getHits().getHits()[1].getSourceAsMap().get(TENANT_ID_FIELD));
        }

        /*
         * Delete
         */
        // Delete the agents
        // First test that we can't delete other tenant agents
        if (multiTenancyEnabled) {
            ResponseException ex = assertThrows(
                ResponseException.class,
                () -> makeRequest(tenantRequest, DELETE, AGENTS_PATH + otherAgentId)
            );
            response = ex.getResponse();
            map = responseToMap(response);
            if (DDB) {
                assertNotFound(response);
                assertEquals("Fail to find ml agent", getErrorReasonFromResponseMap(map));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }

            ex = assertThrows(ResponseException.class, () -> makeRequest(otherTenantRequest, DELETE, AGENTS_PATH + agentId));
            response = ex.getResponse();
            if (DDB) {
                assertNotFound(response);
                assertEquals("Fail to find ml agent", getErrorReasonFromResponseMap(map));
            } else {
                assertForbidden(response);
                assertEquals(NO_PERMISSION_REASON, getErrorReasonFromResponseMap(map));
            }

            // and can't delete without a tenant ID either
            ex = assertThrows(ResponseException.class, () -> makeRequest(nullTenantRequest, DELETE, AGENTS_PATH + agentId));
            response = ex.getResponse();
            assertForbidden(response);
            map = responseToMap(response);
            assertEquals(MISSING_TENANT_REASON, getErrorReasonFromResponseMap(map));
        }

        // Now actually do the deletions. Same result whether multi-tenancy is enabled.

        // Delete from tenant
        response = makeRequest(tenantRequest, DELETE, AGENTS_PATH + agentId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals(agentId, map.get(DOC_ID).toString());

        // Verify the deletion
        ResponseException ex = assertThrows(ResponseException.class, () -> makeRequest(tenantRequest, GET, AGENTS_PATH + agentId));
        response = ex.getResponse();
        assertNotFound(response);
        map = responseToMap(response);
        assertEquals("Failed to find agent with the provided agent id: " + agentId, getErrorReasonFromResponseMap(map));

        // Delete from other tenant
        response = makeRequest(otherTenantRequest, DELETE, AGENTS_PATH + otherAgentId);
        assertOK(response);
        map = responseToMap(response);
        assertEquals(otherAgentId, map.get(DOC_ID).toString());

        // Verify the deletion
        ex = assertThrows(ResponseException.class, () -> makeRequest(otherTenantRequest, GET, AGENTS_PATH + otherAgentId));
        response = ex.getResponse();
        assertNotFound(response);
        map = responseToMap(response);
        assertEquals("Failed to find agent with the provided agent id: " + otherAgentId, getErrorReasonFromResponseMap(map));

        /*
         * Cleanup other resources created
         */
        deleteAndWaitForSearch(tenantId, MODELS_PATH, modelId, 0);
        deleteAndWaitForSearch(tenantId, CONNECTORS_PATH, connectorId, 0);
    }

    private static String registerFlowAgentContent(String name, String modelId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(name).append("\",\n");
        sb.append("  \"type\": \"flow\",\n");
        sb.append("  \"tools\": [");
        sb.append("    {");
        sb.append("      \"type\": \"MLModelTool\",");
        sb.append("      \"parameters\": {");
        sb.append("        \"model_id\": \"").append(modelId).append("\",");
        sb
            .append("        \"prompt\": \"\\n\\n")
            .append("Human: Always answer questions based on the given context first.\\n\\n")
            .append("Context:\\n${parameters.vector_tool.output}\\n\\nHuman:${parameters.question}\\n\\nAssistant:\"");
        sb.append("      }");
        sb.append("    }");
        sb.append("  ]");
        sb.append("}");
        return sb.toString();
    }

    private static String executeAgentContent() {
        return "{\n"
            + "  \"parameters\": {\n"
            + "    \"question\": \"what's the population increase of Seattle from 2021 to 2023\",\n"
            + "    \"inputs\": \""
            + "      The current metro area population of Seattle in 2024 is 3,549,000, a 0.85% increase from 2023."
            + "      The metro area population of Seattle in 2023 was 3,519,000, a 0.86% increase from 2022."
            + "      The metro area population of Seattle in 2022 was 3,489,000, a 0.81% increase from 2021."
            + "      The metro area population of Seattle in 2021 was 3,461,000, a 0.82% increase from 2020.\"\n"
            + "  }\n"
            + "}";
    }
}
