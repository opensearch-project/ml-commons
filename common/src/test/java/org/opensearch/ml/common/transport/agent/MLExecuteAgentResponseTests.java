/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class MLExecuteAgentResponseTests {

    private List<Map<String, List<Map<String, String>>>> inferenceResults;
    private List<Map<String, List<Map<String, String>>>> inferenceResultsWithName;

    @Before
    public void setUp() {
        Map<String, String> outputMap = Collections.singletonMap("result", "test result");
        List<Map<String, String>> outputList = Collections.singletonList(outputMap);
        Map<String, List<Map<String, String>>> inferenceResult = Collections.singletonMap("output", outputList);
        inferenceResults = Collections.singletonList(inferenceResult);

        Map<String, String> outputMapWithName = new HashMap<>();
        outputMapWithName.put("name", "response");
        outputMapWithName
            .put(
                "result",
                "{\"size\":0.0,\"query\":{\"bool\":{\"must\":[{\"term\":{\"variety\":\"setosa\"}}]}},\"aggs\":{\"setosa_count\":{\"value_count\":{\"field\":\"variety\"}}}}"
            );
        List<Map<String, String>> outputListWithName = Collections.singletonList(outputMapWithName);
        Map<String, List<Map<String, String>>> inferenceResultWithName = Collections.singletonMap("output", outputListWithName);
        inferenceResultsWithName = Collections.singletonList(inferenceResultWithName);
    }

    @Test
    public void testConstructorAndSerialization() throws IOException {
        MLExecuteAgentResponse response = new MLExecuteAgentResponse(inferenceResults);
        BytesStreamOutput out = new BytesStreamOutput();
        response.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLExecuteAgentResponse newResponse = new MLExecuteAgentResponse(in);
        assertEquals(inferenceResults.size(), newResponse.getInferenceResults().size());
        assertEquals(
            inferenceResults.get(0).get("output").get(0).get("result"),
            newResponse.getInferenceResults().get(0).get("output").get(0).get("result")
        );
        assertNull(newResponse.getInferenceResults().get(0).get("output").get(0).get("name"));
    }

    @Test
    public void testConstructorAndSerializationWithName() throws IOException {
        MLExecuteAgentResponse response = new MLExecuteAgentResponse(inferenceResultsWithName);
        BytesStreamOutput out = new BytesStreamOutput();
        response.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLExecuteAgentResponse newResponse = new MLExecuteAgentResponse(in);
        assertEquals(inferenceResultsWithName.size(), newResponse.getInferenceResults().size());
        assertEquals(
            inferenceResultsWithName.get(0).get("output").get(0).get("name"),
            newResponse.getInferenceResults().get(0).get("output").get(0).get("name")
        );
        assertEquals(
            inferenceResultsWithName.get(0).get("output").get(0).get("result"),
            newResponse.getInferenceResults().get(0).get("output").get(0).get("result")
        );
    }
}
