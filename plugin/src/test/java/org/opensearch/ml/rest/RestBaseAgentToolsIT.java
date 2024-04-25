/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.hc.core5.http.ParseException;
import org.opensearch.client.*;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.utils.TestHelper;

public abstract class RestBaseAgentToolsIT extends RestOpenSearchSecureTestCase {

    private Object parseFieldFromResponse(Response response, String field) throws IOException, ParseException {
        assertNotNull(field);
        Map map = parseResponseToMap(response);
        Object result = map.get(field);
        assertNotNull(result);
        return result;
    }

    protected void createIndexWithConfiguration(String indexName, String indexConfiguration) throws Exception {
        Response response = TestHelper.makeRequest(client(), "PUT", indexName, null, indexConfiguration, null);
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
        Response response = TestHelper
            .makeRequest(client(), "POST", "/" + indexName + "/_doc/" + docId + "?refresh=true", null, builder.toString(), null);
        assertEquals(RestStatus.CREATED, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    }

    public String createAgent(String requestBody) throws IOException, ParseException {
        Response response = TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, requestBody, null);
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
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, requestBody, null);
        return parseStringResponseFromExecuteAgentResponse(response);
    }
}
