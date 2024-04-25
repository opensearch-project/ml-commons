/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.opensearch.client.*;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;

public abstract class RestBaseAgentToolsIT extends RestOpenSearchSecureTestCase {

    public static final Gson gson = new Gson();

    /**
     * Update cluster settings to run ml models
     */
    @Before
    public void updateClusterSettings() throws IOException {
        updateClusterSettings("plugins.ml_commons.only_run_on_ml_node", false);
        // default threshold for native circuit breaker is 90, it may be not enough on test runner machine
        updateClusterSettings("plugins.ml_commons.native_memory_threshold", 100);
        updateClusterSettings("plugins.ml_commons.jvm_heap_memory_threshold", 100);
        updateClusterSettings("plugins.ml_commons.allow_registering_model_via_url", true);
        updateClusterSettings("plugins.ml_commons.agent_framework_enabled", true);
    }

    protected void updateClusterSettings(String settingKey, Object value) throws IOException {
        XContentBuilder builder = XContentFactory
            .jsonBuilder()
            .startObject()
            .startObject("persistent")
            .field(settingKey, value)
            .endObject()
            .endObject();
        Response response = makeRequest(
            client(),
            "PUT",
            "_cluster/settings",
            null,
            builder.toString(),
            ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
        );

        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    }

    private Map<String, Object> parseResponseToMap(Response response) throws IOException, ParseException {
        Map<String, Object> responseInMap = XContentHelper
            .convertToMap(XContentType.JSON.xContent(), EntityUtils.toString(response.getEntity()), false);
        return responseInMap;
    }

    private Object parseFieldFromResponse(Response response, String field) throws IOException, ParseException {
        assertNotNull(field);
        Map map = parseResponseToMap(response);
        Object result = map.get(field);
        assertNotNull(result);
        return result;
    }

    protected String deployModel(String modelId) throws IOException, ParseException {
        Response response = makeRequest(client(), "POST", "/_plugins/_ml/models/" + modelId + "/_deploy", null, (String) null, null);
        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
        return parseFieldFromResponse(response, MLTask.TASK_ID_FIELD).toString();
    }

    protected void createIndexWithConfiguration(String indexName, String indexConfiguration) throws Exception {
        Response response = makeRequest(client(), "PUT", indexName, null, indexConfiguration, null);
        Map<String, Object> responseInMap = parseResponseToMap(response);
        assertEquals("true", responseInMap.get("acknowledged").toString());
        assertEquals(indexName, responseInMap.get("index").toString());
    }

    protected void addDocToIndex(String indexName, String docId, List<String> fieldNames, List<Object> fieldContents) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
        for (int i = 0; i < fieldNames.size(); i++) {
            builder.field(fieldNames.get(i), fieldContents.get(i));
        }
        builder.endObject();
        Response response = makeRequest(
            client(),
            "POST",
            "/" + indexName + "/_doc/" + docId + "?refresh=true",
            null,
            builder.toString(),
            null
        );
        assertEquals(RestStatus.CREATED, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    }

    public String createAgent(String requestBody) throws IOException, ParseException {
        Response response = makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, requestBody, null);
        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
        return parseFieldFromResponse(response, AgentMLInput.AGENT_ID_FIELD).toString();
    }

    private String parseStringResponseFromExecuteAgentResponse(Response response) throws IOException, ParseException {
        Map responseInMap = parseResponseToMap(response);
        Optional<String> optionalResult = Optional
            .ofNullable(responseInMap)
            .map(m -> (List) m.get(ModelTensorOutput.INFERENCE_RESULT_FIELD))
            .map(l -> (Map) l.get(0))
            .map(m -> (List) m.get(ModelTensors.OUTPUT_FIELD))
            .map(l -> (Map) l.get(0))
            .map(m -> (String) (m.get(ModelTensor.RESULT_FIELD)));
        return optionalResult.get();
    }

    // execute the agent, and return the String response from the json structure
    // {"inference_results": [{"output": [{"name": "response","result": "the result to return."}]}]}
    public String executeAgent(String agentId, String requestBody) throws IOException, ParseException {
        Response response = makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, requestBody, null);
        return parseStringResponseFromExecuteAgentResponse(response);
    }

    public static Response makeRequest(
        RestClient client,
        String method,
        String endpoint,
        Map<String, String> params,
        String jsonEntity,
        List<Header> headers
    ) throws IOException {
        HttpEntity httpEntity = StringUtils.isBlank(jsonEntity) ? null : new StringEntity(jsonEntity, ContentType.APPLICATION_JSON);
        return makeRequest(client, method, endpoint, params, httpEntity, headers);
    }

    public static Response makeRequest(
        RestClient client,
        String method,
        String endpoint,
        Map<String, String> params,
        HttpEntity entity,
        List<Header> headers
    ) throws IOException {
        return makeRequest(client, method, endpoint, params, entity, headers, false);
    }

    public static Response makeRequest(
        RestClient client,
        String method,
        String endpoint,
        Map<String, String> params,
        HttpEntity entity,
        List<Header> headers,
        boolean strictDeprecationMode
    ) throws IOException {
        Request request = new Request(method, endpoint);

        RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
        if (headers != null) {
            headers.forEach(header -> options.addHeader(header.getName(), header.getValue()));
        }
        options.setWarningsHandler(strictDeprecationMode ? WarningsHandler.STRICT : WarningsHandler.PERMISSIVE);
        request.setOptions(options.build());

        if (params != null) {
            params.forEach(request::addParameter);
        }
        if (entity != null) {
            request.setEntity(entity);
        }
        return client.performRequest(request);
    }
}
