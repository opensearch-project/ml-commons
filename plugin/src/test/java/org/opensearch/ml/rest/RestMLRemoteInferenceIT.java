/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

public class RestMLRemoteInferenceIT extends MLCommonsRestTestCase {

    private final String completionModelConnectorEntity = "{\n"
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
    
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    
    private Response createConnector(String input) throws IOException {
        return TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/connectors/_create",
                null,
                TestHelper.toHttpEntity(input),
                null
            );
    }

    private Response registerRemoteModel(String name, String connectorId) throws IOException {
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

        String registerModelEntity = "{\n"
            + "  \"name\": \"" + name + "\",\n"
            + "  \"function_name\": \"remote\",\n"
            + "  \"model_group_id\": \"" + modelGroupId + "\",\n"
            + "  \"version\": \"1.0.0\",\n"
            + "  \"description\": \"test model\",\n"
            + "  \"connector_id\": \"" + connectorId + "\"\n"
            + "}";
        return TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/models/_register",
                null,
                TestHelper.toHttpEntity(registerModelEntity),
                null
            );
    }

    private Response deployRemoteModel(String modelId) throws IOException {
        return TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/models/" + modelId + "/_deploy",
                null,
                "",
                null
            );
    }

    private Response predictRemoteModel(String modelId, String input) throws IOException {
        return TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/models/" + modelId + "/_predict",
                null,
                input,
                null
            );
    }

    private Response undeployRemoteModel(String modelId) throws IOException {
        String undeployEntity = "{\n"
            + "  \"SYqCMdsFTumUwoHZcsgiUg\": {\n"
            + "    \"stats\": {\n"
            + "      \"" + modelId + "\": \"undeployed\"\n"
            + "    }\n"
            + "  }\n"
            + "}";
        return TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/models/" + modelId + "/_undeploy",
                null,
                undeployEntity,
                null
            );
    }

    private Map parseResponseToMap(Response response) throws IOException {
        HttpEntity entity = response.getEntity();
        assertNotNull(response);
        String entityString = TestHelper.httpEntityToString(entity);
        return gson.fromJson(entityString, Map.class);
    }

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

    private Response getTask(String taskId) throws IOException {
        return TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/tasks/" + taskId, null, "", null);
    }


}
