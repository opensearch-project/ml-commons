/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.hamcrest.Matchers.containsString;

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
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}"
        );
        addDocToIndex(TEST_INDEX_NAME, "0", List.of("text"), List.of("text doc 1"));
        addDocToIndex(TEST_INDEX_NAME, "1", List.of("text"), List.of("text doc 2"));
        addDocToIndex(TEST_INDEX_NAME, "2", List.of("text"), List.of("text doc 3"));
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
            "{\"_index\":\"test_index\",\"_source\":{\"text\":\"text doc 1\"},\"_id\":\"0\",\"_score\":1.0}\n"
                + "{\"_index\":\"test_index\",\"_source\":{\"text\":\"text doc 2\"},\"_id\":\"1\",\"_score\":1.0}\n"
                + "{\"_index\":\"test_index\",\"_source\":{\"text\":\"text doc 3\"},\"_id\":\"2\",\"_score\":1.0}\n",
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
