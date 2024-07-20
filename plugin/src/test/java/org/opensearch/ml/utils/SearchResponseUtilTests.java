/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;

import org.apache.lucene.search.TotalHits;
import org.junit.Test;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;

public class SearchResponseUtilTests {
    @Test
    public void testReplaceHits() {
        SearchHit[] originalHits = new SearchHit[10];
        SearchHits originalSearchHits = new SearchHits(originalHits, new TotalHits(10, TotalHits.Relation.EQUAL_TO), 0.5f);
        SearchResponse originalResponse = new SearchResponse(
            new InternalSearchResponse(
                originalSearchHits,
                InternalAggregations.EMPTY,
                new Suggest(Collections.emptyList()),
                new SearchProfileShardResults(Collections.emptyMap()),
                false,
                false,
                1
            ),
            "",
            1,
            1,
            0,
            0,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );

        SearchHit[] newHits = new SearchHit[10];

        SearchResponse newResponse = SearchResponseUtil.replaceHits(newHits, originalResponse);

        assertNotNull(newResponse);
        assertEquals(newHits.length, newResponse.getHits().getHits().length);
    }

    @Test
    public void testReplaceHitsWithSearchHits() throws IOException {
        // Arrange
        SearchHit[] originalHits = new SearchHit[10];
        SearchHits originalSearchHits = new SearchHits(originalHits, new TotalHits(10, TotalHits.Relation.EQUAL_TO), 0.5f);
        SearchResponse originalResponse = new SearchResponse(
            new InternalSearchResponse(
                originalSearchHits,
                InternalAggregations.EMPTY,
                new Suggest(Collections.emptyList()),
                new SearchProfileShardResults(Collections.emptyMap()),
                false,
                false,
                1
            ),
            "",
            1,
            1,
            0,
            0,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );

        SearchHit[] newHits = new SearchHit[15];
        SearchHits newSearchHits = new SearchHits(newHits, new TotalHits(15, TotalHits.Relation.EQUAL_TO), 0.7f);

        SearchResponse newResponse = SearchResponseUtil.replaceHits(newSearchHits, originalResponse);

        assertNotNull(newResponse);
        assertEquals(newHits.length, newResponse.getHits().getHits().length);
        assertEquals(15, newResponse.getHits().getTotalHits().value);
        assertEquals(TotalHits.Relation.EQUAL_TO, newResponse.getHits().getTotalHits().relation);
        assertEquals(0.7f, newResponse.getHits().getMaxScore(), 0.0001f);
    }

    @Test
    public void testReplaceHitsWithNonWriteableAggregations() {
        SearchHit[] originalHits = new SearchHit[10];
        SearchHits originalSearchHits = new SearchHits(originalHits, new TotalHits(10, TotalHits.Relation.EQUAL_TO), 0.5f);

        Aggregations nonWriteableAggregations = mock(Aggregations.class);
        SearchResponse originalResponse = mock(SearchResponse.class);
        when(originalResponse.getHits()).thenReturn(originalSearchHits);
        when(originalResponse.getAggregations()).thenReturn(nonWriteableAggregations);
        when(originalResponse.getSuggest()).thenReturn(new Suggest(Collections.emptyList()));
        when(originalResponse.isTimedOut()).thenReturn(false);
        when(originalResponse.isTerminatedEarly()).thenReturn(false);
        when(originalResponse.getProfileResults()).thenReturn(Collections.emptyMap());
        when(originalResponse.getNumReducePhases()).thenReturn(1);
        when(originalResponse.getTook()).thenReturn(new TimeValue(100));
        SearchHit[] newHits = new SearchHit[15];

        SearchResponse newResponse = SearchResponseUtil
            .replaceHits(new SearchHits(newHits, new TotalHits(15, TotalHits.Relation.EQUAL_TO), 0.7f), originalResponse);

        assertNotNull(newResponse);
        assertEquals(newHits.length, newResponse.getHits().getHits().length);
        assertEquals(15, newResponse.getHits().getTotalHits().value);
        assertEquals(TotalHits.Relation.EQUAL_TO, newResponse.getHits().getTotalHits().relation);
        assertEquals(0.7f, newResponse.getHits().getMaxScore(), 0.0001f);
    }
}
