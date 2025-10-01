/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.handler;

import static org.opensearch.core.rest.RestStatus.INTERNAL_SERVER_ERROR;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryStringQueryBuilder;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
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
        final QueryBuilder queryBuilder = MLSearchHandler.rewriteQueryBuilder(new QueryStringQueryBuilder(""), List.of("group1", "group2"));
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
        final QueryBuilder queryBuilder = MLSearchHandler.rewriteQueryBuilder(null, List.of("group1", "group2"));
        final String queryString = queryBuilder.toString();
        Assert.assertEquals(expectedQueryString, queryString);
    }

    // ---------- rewriteQueryBuilder (legacy backend-roles gate) ----------

    public void testRewriteQueryBuilder_emptyGroupIds_withNullExisting() {
        QueryBuilder qb = MLSearchHandler.rewriteQueryBuilder(null, Collections.emptyList());
        String s = qb.toString();
        // should only allow documents where model_group_id does NOT exist
        assertTrue(s.contains("\"must_not\""));
        assertTrue(s.contains("\"exists\" : {"));
        assertTrue(s.contains("\"field\" : \"model_group_id\""));
    }

    public void testRewriteQueryBuilder_emptyGroupIds_withNonBoolExisting() {
        QueryBuilder existing = new QueryStringQueryBuilder("name:foo");
        QueryBuilder qb = MLSearchHandler.rewriteQueryBuilder(existing, Collections.emptyList());
        String s = qb.toString();
        // existing query must be preserved under "must"
        assertTrue(s.contains("\"query_string\""));
        // must also include "must_not exists model_group_id"
        assertTrue(s.contains("\"must_not\""));
        assertTrue(s.contains("\"field\" : \"model_group_id\""));
    }

    public void testRewriteQueryBuilder_nonEmptyGroupIds_withBoolExisting() {
        BoolQueryBuilder existing = new BoolQueryBuilder();
        existing.must(new QueryStringQueryBuilder("title:bar"));
        QueryBuilder qb = MLSearchHandler.rewriteQueryBuilder(existing, List.of("g1", "g2"));
        String s = qb.toString();
        // accessControlledBoolQuery should be added under "must" with terms + missing
        assertTrue(s.contains("\"terms\""));
        assertTrue(s.contains("\"model_group_id\""));
        assertTrue(s.contains("\"must_not\""));
        assertTrue(s.contains("\"exists\""));
    }

    // ---------- rewriteQueryBuilderRSC (resource-sharing client gate) ----------

    public void testRewriteQueryBuilderRSC_emptyIds_returnsDenyAll_whenExistingNull() {
        QueryBuilder qb = MLSearchHandler.rewriteQueryBuilderRSC(null, Collections.emptyList());
        String s = qb.toString();
        // deny-all: bool must_not match_all
        assertTrue(s.contains("\"must_not\""));
        assertTrue(s.contains("\"match_all\""));
    }

    public void testRewriteQueryBuilderRSC_nonEmptyIds_whenExistingNull() {
        QueryBuilder qb = MLSearchHandler.rewriteQueryBuilderRSC(null, List.of("g1", "g2"));
        String s = qb.toString();
        // gate only: (terms OR missing) with minimum_should_match 1
        assertTrue(s.contains("\"terms\""));
        assertTrue(s.contains("\"model_group_id\""));
        assertTrue(s.contains("\"minimum_should_match\" : \"1\""));
        assertTrue(s.contains("\"must_not\"")); // missing branch
        assertTrue(s.contains("\"exists\""));
    }

    public void testRewriteQueryBuilderRSC_wrapsNonBoolExisting() {
        QueryBuilder existing = new QueryStringQueryBuilder("desc:baz");
        QueryBuilder qb = MLSearchHandler.rewriteQueryBuilderRSC(existing, List.of("g1"));
        String s = qb.toString();
        // wrapped into bool.must(existing).filter(gate)
        assertTrue(s.contains("\"query_string\""));
        assertTrue(s.contains("\"filter\""));
        assertTrue(s.contains("\"terms\""));
    }

    public void testRewriteQueryBuilderRSC_addsFilterToBoolExisting() {
        BoolQueryBuilder existing = new BoolQueryBuilder();
        existing.must(new MatchAllQueryBuilder());
        QueryBuilder qb = MLSearchHandler.rewriteQueryBuilderRSC(existing, List.of("g1"));
        String s = qb.toString();
        // existing bool should now contain filter gate
        assertTrue(s.contains("\"filter\""));
        assertTrue(s.contains("\"terms\""));
        assertTrue(s.contains("\"model_group_id\""));
    }

    // ---------- wrapRestActionListener (exception mapping) ----------

    public void testWrapRestActionListener_passThroughOpenSearchStatusException() {
        AtomicReference<Exception> seen = new AtomicReference<>();
        ActionListener<Object> base = ActionListener.wrap(r -> fail("should not succeed"), seen::set);
        ActionListener<Object> wrapped = MLSearchHandler.wrapRestActionListener(base, "General error");

        wrapped.onFailure(new OpenSearchStatusException("nope", RestStatus.FORBIDDEN));
        assertTrue(seen.get() instanceof OpenSearchStatusException);
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) seen.get()).status());
        assertEquals("nope", seen.get().getMessage());
    }

    public void testWrapRestActionListener_unwrapsProperRootCause() {
        AtomicReference<Exception> seen = new AtomicReference<>();
        ActionListener<Object> base = ActionListener.wrap(r -> fail("should not succeed"), seen::set);
        ActionListener<Object> wrapped = MLSearchHandler.wrapRestActionListener(base, "General error");

        Exception cause = new OpenSearchStatusException("forbidden cause", RestStatus.FORBIDDEN);
        wrapped.onFailure(new RuntimeException(cause));
        assertTrue(seen.get() instanceof OpenSearchStatusException);
        assertEquals("forbidden cause", seen.get().getMessage());
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) seen.get()).status());
    }

    public void testWrapRestActionListener_wrapsUnknownAs500WithGeneralMessage() {
        AtomicReference<Exception> seen = new AtomicReference<>();
        ActionListener<Object> base = ActionListener.wrap(r -> fail("should not succeed"), seen::set);
        ActionListener<Object> wrapped = MLSearchHandler.wrapRestActionListener(base, "Fail to search model version");

        wrapped.onFailure(new RuntimeException("boom"));
        assertTrue(seen.get() instanceof OpenSearchStatusException);
        OpenSearchStatusException ose = (OpenSearchStatusException) seen.get();
        assertEquals(INTERNAL_SERVER_ERROR, ose.status());
        assertEquals("Fail to search model version", ose.getMessage());
    }

    // ---------- tiny unit checks for helpers ----------

    public void testIsProperExceptionToReturn() {
        assertTrue(MLSearchHandler.isProperExceptionToReturn(new OpenSearchStatusException("x", RestStatus.BAD_REQUEST)));
        assertTrue(MLSearchHandler.isProperExceptionToReturn(new IndexNotFoundException("idx")));
        assertFalse(MLSearchHandler.isProperExceptionToReturn(new RuntimeException("x")));
    }

    public void testIsBadRequest() {
        assertTrue(MLSearchHandler.isBadRequest(new IllegalArgumentException("bad")));
        assertTrue(MLSearchHandler.isBadRequest(new MLResourceNotFoundException("not found")));
        assertFalse(MLSearchHandler.isBadRequest(new RuntimeException("boom")));
    }
}
