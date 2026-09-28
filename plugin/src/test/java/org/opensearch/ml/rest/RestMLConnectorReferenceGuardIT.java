/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Locale;

import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.utils.TestHelper;

/**
 * Regression tests for https://github.com/opensearch-project/ml-commons/issues/5032.
 *
 * The "is this connector still referenced by a model?" guard in the update and delete connector transport actions used
 * an analysed match query on the `connector_id` field, which is dynamically mapped as `text`. The standard analyser
 * splits on '-' and lowercases, so a connector whose id merely shared a single token with a referenced connector id was
 * treated as still referenced: it became permanently undeletable and un-updatable through the public API.
 *
 * Note this is not specific to custom ids: auto-generated OpenSearch document ids are URL-safe Base64, so roughly a
 * third of them contain '-' and are tokenised too. Custom ids simply make collisions far more likely, because
 * human-readable ids share meaningful words ("conn", "prod") rather than random segments. '_' does NOT split — it is
 * Word_Break=ExtendNumLet in UAX #29 — so snake_case ids are unaffected and this test uses hyphenated ids.
 */
public class RestMLConnectorReferenceGuardIT extends MLCommonsRestTestCase {

    @Before
    public void setupConnectorAccessControl() throws IOException {
        RestMLRemoteInferenceIT.disableClusterConnectorAccessControl();
    }

    public void testConnectorReferenceGuardOnlyMatchesTheExactConnectorId() throws IOException, InterruptedException {
        // Both ids must share at least one analysed token ("vfy", "guard" and "conn" here) to reproduce the bug. The
        // random suffix keeps the test rerunnable against a persistent cluster (-Dtests.rest.cluster=...): custom
        // connector ids are created with overwriteIfExists(false), so a leftover id from an aborted run would otherwise
        // fail with 409 before the guard is ever exercised.
        String suffix = randomAlphaOfLength(8);
        String referencedConnectorId = "vfy-guard-conn-referenced-" + suffix;
        String idleConnectorId = "vfy-guard-conn-idle-" + suffix;

        // A connector that a model really does reference.
        assertEquals(referencedConnectorId, registerConnector(connectorBody(referencedConnectorId)));
        Response registerModelResponse = RestMLRemoteInferenceIT
            .registerRemoteModel("vfy-guard-model-group-" + suffix, "vfy-guard-model-" + suffix, referencedConnectorId);
        String modelId = (String) parseResponseToMap(registerModelResponse).get("model_id");
        assertNotNull(modelId);

        // A second connector that no model has ever referenced, but whose id shares tokens with the referenced one.
        assertEquals(idleConnectorId, registerConnector(connectorBody(idleConnectorId)));

        Response updateResponse = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_plugins/_ml/connectors/" + idleConnectorId,
                null,
                "{\"description\":\"updated description\"}",
                null
            );
        assertEquals(RestStatus.OK, TestHelper.restStatus(updateResponse));

        Response deleteResponse = TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/connectors/" + idleConnectorId, null, "", null);
        assertEquals("deleted", parseResponseToMap(deleteResponse).get("result"));

        // The guard must not fail open: the connector that IS referenced still cannot be deleted.
        ResponseException exception = expectThrows(
            ResponseException.class,
            () -> TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/connectors/" + referencedConnectorId, null, "", null)
        );
        assertEquals(RestStatus.CONFLICT.getStatus(), exception.getResponse().getStatusLine().getStatusCode());
        assertTrue(exception.getMessage().contains("models are still using this connector"));

        // No explicit cleanup: MLCommonsRestTestCase.wipeAllODFEIndices() drops every ML index after each test method.
    }

    private String connectorBody(String connectorId) {
        return String.format(Locale.ROOT, """
            {
              "connector_id": "%s",
              "name": "reference guard connector",
              "description": "connector used to verify the model reference guard",
              "version": 1,
              "protocol": "http",
              "parameters": {
                "endpoint": "api.openai.com",
                "model": "gpt-3.5-turbo-instruct"
              },
              "credential": {
                "openAI_key": "placeholder-not-used-by-this-test"
              },
              "actions": [
                {
                  "action_type": "predict",
                  "method": "POST",
                  "url": "https://${parameters.endpoint}/v1/completions",
                  "request_body": "{ \\"model\\": \\"${parameters.model}\\", \\"prompt\\": \\"${parameters.prompt}\\" }"
                }
              ]
            }
            """, connectorId);
    }
}
