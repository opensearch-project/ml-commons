/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.apache.hc.core5.http.ParseException;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.opensearch.client.ResponseException;

public class RestSearchIndexToolIT extends RestBaseAgentToolsIT {
    public static String TEST_INDEX_NAME = "test_index";
    private String registerAgentRequestBody;

    private void prepareIndex() throws Exception {
        createIndexWithConfiguration(
            TEST_INDEX_NAME,
            "{\n"
                + "  \"mappings\": {\n"
                + "    \"properties\": {\n"
                + "      \"text\": {\n"
                + "        \"type\": \"text\"\n"
                + "      },\n"
                + "      \"category\": {\n"
                + "        \"type\": \"keyword\"\n"
                + "      },\n"
                + "      \"value\": {\n"
                + "        \"type\": \"long\"\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}"
        );
        addDocToIndex(TEST_INDEX_NAME, "0", List.of("text", "category", "value"), List.of("text doc 1", "billing", 100));
        addDocToIndex(TEST_INDEX_NAME, "1", List.of("text", "category", "value"), List.of("text doc 2", "search", 50));
        addDocToIndex(TEST_INDEX_NAME, "2", List.of("text", "category", "value"), List.of("text doc 3", "billing", 75));
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        prepareIndex();
        registerAgentRequestBody = Files
            .readString(
                Path
                    .of(
                        Objects
                            .requireNonNull(
                                this
                                    .getClass()
                                    .getClassLoader()
                                    .getResource("org/opensearch/ml/rest/tools/register_flow_agent_of_search_index_tool_request_body.json")
                            )
                            .toURI()
                    )
            );
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        deleteExternalIndices();
    }

    public void testSearchIndexToolInFlowAgent_withMatchAllQuery() throws IOException, ParseException {
        String agentId = createAgent(registerAgentRequestBody);
        String agentInput = "{\n"
            + "  \"parameters\": {\n"
            + "    \"input\": {\n"
            + "      \"index\": \"test_index\",\n"
            + "      \"query\": {\n"
            + "        \"query\": {\n"
            + "          \"match_all\": {}\n"
            + "        }\n"
            + "      }\n"
            + "    }  \n"
            + "  }\n"
            + "}\n";
        String result = executeAgent(agentId, agentInput);
        assertEquals(
            "The search index result not equal with expected.",
            "{\"_index\":\"test_index\",\"_source\":{\"text\":\"text doc 1\",\"category\":\"billing\",\"value\":100},\"_id\":\"0\",\"_score\":1.0}\n"
                + "{\"_index\":\"test_index\",\"_source\":{\"text\":\"text doc 2\",\"category\":\"search\",\"value\":50},\"_id\":\"1\",\"_score\":1.0}\n"
                + "{\"_index\":\"test_index\",\"_source\":{\"text\":\"text doc 3\",\"category\":\"billing\",\"value\":75},\"_id\":\"2\",\"_score\":1.0}\n",
            result
        );
    }

    public void testSearchIndexToolInFlowAgent_withEmptyIndexField_thenThrowException() throws IOException, ParseException {
        String agentId = createAgent(registerAgentRequestBody);
        String agentInput = "{\n"
            + "  \"parameters\": {\n"
            + "    \"input\": {\n"
            + "      \"query\": {\n"
            + "        \"query\": {\n"
            + "          \"match_all\": {}\n"
            + "        }\n"
            + "      }\n"
            + "    }  \n"
            + "  }\n"
            + "}\n";
        Exception exception = assertThrows(ResponseException.class, () -> executeAgent(agentId, agentInput));
        MatcherAssert
            .assertThat(
                exception.getMessage(),
                containsString("SearchIndexTool's two parameters: index and query are required and should be in valid format")
            );
    }

    public void testSearchIndexToolInFlowAgent_withEmptyQueryField_thenThrowException() throws IOException, ParseException {
        String agentId = createAgent(registerAgentRequestBody);
        String agentInput = "{\n"
            + "  \"parameters\": {\n"
            + "    \"input\": {\n"
            + "      \"index\": \"test_index\"\n"
            + "    }  \n"
            + "  }\n"
            + "}\n";
        Exception exception = assertThrows(ResponseException.class, () -> executeAgent(agentId, agentInput));
        MatcherAssert
            .assertThat(
                exception.getMessage(),
                containsString("SearchIndexTool's two parameters: index and query are required and should be in valid format")
            );
    }

    public void testSearchIndexToolInFlowAgent_withTermsAggregationAndSubAggregation() throws IOException, ParseException {
        String agentId = createAgent(registerAgentRequestBody);
        String agentInput = "{\n"
            + "  \"parameters\": {\n"
            + "    \"input\": {\n"
            + "      \"index\": \"test_index\",\n"
            + "      \"query\": {\n"
            + "        \"size\": 0,\n"
            + "        \"aggs\": {\n"
            + "          \"by_category\": {\n"
            + "            \"terms\": {\n"
            + "              \"field\": \"category\",\n"
            + "              \"size\": 10\n"
            + "            },\n"
            + "            \"aggs\": {\n"
            + "              \"max_value\": {\n"
            + "                \"max\": {\n"
            + "                  \"field\": \"value\"\n"
            + "                }\n"
            + "              }\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}\n";
        String result = executeAgent(agentId, agentInput);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        MatcherAssert.assertThat(result, containsString("\"aggregations\""));
        MatcherAssert.assertThat(result, not(containsString("\"aggregations\":{\"aggregations\"")));
        MatcherAssert.assertThat(result, containsString("by_category"));
        MatcherAssert.assertThat(result, containsString("billing"));
        MatcherAssert.assertThat(result, containsString("search"));
        MatcherAssert.assertThat(result, containsString("max_value"));
        MatcherAssert.assertThat(result, not(containsString("_shards")));
    }

    public void testSearchIndexToolInFlowAgent_withIllegalQueryField_thenThrowException() throws IOException, ParseException {
        String agentId = createAgent(registerAgentRequestBody);
        String agentInput = "{\n"
            + "  \"parameters\": {\n"
            + "    \"input\": {\n"
            + "      \"index\": \"test_index\",\n"
            + "      \"query\": \"Invalid Query\"\n"
            + "    }  \n"
            + "  }\n"
            + "}\n";
        Exception exception = assertThrows(ResponseException.class, () -> executeAgent(agentId, agentInput));
        MatcherAssert.assertThat(exception.getMessage(), containsString("ParsingException"));
    }
}
