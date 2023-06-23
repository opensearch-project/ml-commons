/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

public class RestMLRemoteInferenceIT extends MLCommonsRestTestCase {
    
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    private void disableClusterConnectorAccessControl() throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"plugins.ml_commons.connector_access_control_enabled\":false}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, response.getStatusLine().getStatusCode());
    }
    
    public void testCreateConnectorCompletionModel() throws IOException {
        Response response = createConnectorCompletionModel();
        Map responseMap = parseResponseToMap(response);
        assertEquals("CREATED", (String) responseMap.get("status"));
    }

    public void testGetConnector() throws IOException {
        Response response = createConnectorCompletionModel();
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = TestHelper
            .makeRequest(
                client(),
                "GET",
                "/_plugins/_ml/connectors/" + connectorId,
                null,
                "",
                null
            );
        responseMap = parseResponseToMap(response);
        assertEquals("OpenAI Connector", (String) responseMap.get("name"));
        assertEquals("1", (String) responseMap.get("version"));
        assertEquals("The connector to public OpenAI model service for GPT 3.5", (String) responseMap.get("description"));
        assertEquals("http/v1", (String) responseMap.get("protocol"));
        assertEquals("CREATED", (String) responseMap.get("connector_state"));
    }

    public void testDeleteConnector() throws IOException {
        Response response = createConnectorCompletionModel();
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        response = TestHelper
            .makeRequest(
                client(),
                "DELETE",
                "/_plugins/_ml/connectors/" + connectorId,
                null,
                "",
                null
            );
        responseMap = parseResponseToMap(response);
        assertEquals("deleted", (String) responseMap.get("result"));
    }

    public void testSearchConnectors() throws IOException {
        createConnectorCompletionModel();
        String searchEntity = "{\n"
            + "  \"query\": {\n"
            + "    \"match_all\": {}\n"
            + "  },\n"
            + "  \"size\": 1000\n"
            + "}";
        Response response = TestHelper
            .makeRequest(
                client(),
                "GET",
                "/_plugins/_ml/connectors/_search",
                null,
                TestHelper.toHttpEntity(searchEntity),
                null
            );
        Map responseMap = parseResponseToMap(response);
        assertEquals((Double) 1.0, (Double) ((Map) ((Map) responseMap.get("hits")).get("total")).get("value"));
        
    }

    private Response createConnectorCompletionModel() throws IOException {
        String createConnectorEntity = "{\n"
            + "\"name\": \"OpenAI Connector\",\n"
            + "\"description\": \"The connector to public OpenAI model service for GPT 3.5\",\n"
            + "\"version\": 1,\n"
            + "\"protocol\": \"http/v1\",\n"
            + "\"parameters\": {\n"
            + "    \"endpoint\": \"api.openai.com\",\n"
            + "    \"auth\": \"API_Key\",\n"
            + "    \"content_type\": \"application/json\",\n"
            + "    \"max_tokens\": 7,\n"
            + "    \"temperature\": 0,\n"
            + "    \"model\": \"text-davinci-003\"\n"
            + "  },\n"
            + "  \"credential\": {\n"
            + "    \"openAI_key\": \"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\"\n"
            + "  },\n"
            + "  \"actions\": [\n"
            + "    {\n"
            + "      \"predict\": {\n"
            + "        \"method\": \"POST\",\n"
            + "        \"url\": \"https://${parameters.endpoint}/v1/completions\",\n"
            + "        \"headers\": {\n"
            + "          \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
            + "        },\n"
            + "        \"request_body\": \"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"prompt\\\": \\\"${parameters.prompt}\\\",  \\\"max_tokens\\\": ${parameters.max_tokens},  \\\"temperature\\\": ${parameters.temperature} }\"\n"
            + "      }\n"
            + "    },\n"
            + "    {\n"
            + "      \"metadata\": {\n"
            + "        \"method\": \"GET\",\n"
            + "        \"url\": \"https://${parameters.endpoint}/v1/models/{model}\",\n"
            + "        \"headers\": {\n"
            + "          \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

        return TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/connectors/_create",
                null,
                TestHelper.toHttpEntity(createConnectorEntity),
                null
            );
    }

    public void registerRemoteModel() throws IOException {
        String registerModelGroupEntity = "{\n"
            + "  \"name\": \"remote_model_group\",\n"
            + "  \"description\": \"This is an example description\"\n"
            + "}";
        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/model_groups/_register",
                null,
                TestHelper.toHttpEntity(registerModelGroupEntity),
                null
                );
        Map responseMap = parseResponseToMap(response);
        assertEquals((String) responseMap.get("status"), "CREATED");
        String modelGroupId = (String) responseMap.get("model_group_id");
        String registerModelEntity = "{\n";

    }

    private Map parseResponseToMap(Response response) throws IOException {
        HttpEntity entity = response.getEntity();
        assertNotNull(response);
        String entityString = TestHelper.httpEntityToString(entity);
        return gson.fromJson(entityString, Map.class);
    }


}
