/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.handler;

import java.util.List;

import org.junit.Assert;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryStringQueryBuilder;
import org.opensearch.test.OpenSearchTestCase;

public class MLSearchHandlerTests extends OpenSearchTestCase {

    public void testRewriteQueryBuilder_accessControlIgnored() {
        final String expectedQueryString = "{\n"
            + "  \"bool\" : {\n"
            + "    \"must\" : [\n"
            + "      {\n"
            + "        \"query_string\" : {\n"
            + "          \"query\" : \"\",\n"
            + "          \"fields\" : [ ],\n"
            + "          \"type\" : \"best_fields\",\n"
            + "          \"default_operator\" : \"or\",\n"
            + "          \"max_determinized_states\" : 10000,\n"
            + "          \"enable_position_increments\" : true,\n"
            + "          \"fuzziness\" : \"AUTO\",\n"
            + "          \"fuzzy_prefix_length\" : 0,\n"
            + "          \"fuzzy_max_expansions\" : 50,\n"
            + "          \"phrase_slop\" : 0,\n"
            + "          \"escape\" : false,\n"
            + "          \"auto_generate_synonyms_phrase_query\" : true,\n"
            + "          \"fuzzy_transpositions\" : true,\n"
            + "          \"boost\" : 1.0\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        \"bool\" : {\n"
            + "          \"must_not\" : [\n"
            + "            {\n"
            + "              \"exists\" : {\n"
            + "                \"field\" : \"model_group_id\",\n"
            + "                \"boost\" : 1.0\n"
            + "              }\n"
            + "            }\n"
            + "          ],\n"
            + "          \"adjust_pure_negative\" : true,\n"
            + "          \"boost\" : 1.0\n"
            + "        }\n"
            + "      }\n"
            + "    ],\n"
            + "    \"adjust_pure_negative\" : true,\n"
            + "    \"boost\" : 1.0\n"
            + "  }\n"
            + "}";
        final QueryBuilder queryBuilder = MLSearchHandler
            .rewriteQueryBuilderLegacy(new QueryStringQueryBuilder(""), List.of("group1", "group2"));
        final String queryString = queryBuilder.toString();
        Assert.assertEquals(expectedQueryString, queryString);
    }

    public void testRewriteQueryBuilder_accessControlUsed_withNullQuery() {
        final String expectedQueryString = "{\n"
            + "  \"bool\" : {\n"
            + "    \"should\" : [\n"
            + "      {\n"
            + "        \"terms\" : {\n"
            + "          \"model_group_id\" : [\n"
            + "            \"group1\",\n"
            + "            \"group2\"\n"
            + "          ],\n"
            + "          \"boost\" : 1.0\n"
            + "        }\n"
            + "      },\n"
            + "      {\n"
            + "        \"bool\" : {\n"
            + "          \"must_not\" : [\n"
            + "            {\n"
            + "              \"exists\" : {\n"
            + "                \"field\" : \"model_group_id\",\n"
            + "                \"boost\" : 1.0\n"
            + "              }\n"
            + "            }\n"
            + "          ],\n"
            + "          \"adjust_pure_negative\" : true,\n"
            + "          \"boost\" : 1.0\n"
            + "        }\n"
            + "      }\n"
            + "    ],\n"
            + "    \"adjust_pure_negative\" : true,\n"
            + "    \"boost\" : 1.0\n"
            + "  }\n"
            + "}";
        final QueryBuilder queryBuilder = MLSearchHandler.rewriteQueryBuilderLegacy(null, List.of("group1", "group2"));
        final String queryString = queryBuilder.toString();
        Assert.assertEquals(expectedQueryString, queryString);
    }
}
