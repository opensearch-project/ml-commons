/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.ExistsQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.WithModelTool;

public class AgentModelSearcherTests {

    @Test
    public void testConstructor_CollectsModelIds() {
        // Arrange
        WithModelTool.Factory withModelToolFactory1 = mock(WithModelTool.Factory.class);
        when(withModelToolFactory1.getAllModelKeys()).thenReturn(Arrays.asList("modelKey1", "modelKey2"));

        WithModelTool.Factory withModelToolFactory2 = mock(WithModelTool.Factory.class);
        when(withModelToolFactory2.getAllModelKeys()).thenReturn(Collections.singletonList("anotherModelKey"));

        // This tool factory does not implement WithModelTool.Factory
        Tool.Factory regularToolFactory = mock(Tool.Factory.class);

        Map<String, Tool.Factory> toolFactories = new HashMap<>();
        toolFactories.put("withModelTool1", withModelToolFactory1);
        toolFactories.put("withModelTool2", withModelToolFactory2);
        toolFactories.put("regularTool", regularToolFactory);

        // Act
        AgentModelsSearcher searcher = new AgentModelsSearcher(toolFactories);

        // (Optional) We can't directly access relatedModelIdSet,
        // but we can test the behavior indirectly using the search call:
        SearchRequest request = searcher.constructQueryRequestToSearchModelIdInsideAgent("candidateId");

        // Assert
        // Verify the searchRequest uses all keys from the WithModelTool factories
        BoolQueryBuilder boolQueryBuilder = (BoolQueryBuilder) request.source().query();
        // We expect modelKey1, modelKey2, anotherModelKey => total 3 "should" clauses
        assertEquals(2, boolQueryBuilder.must().size());
        for (QueryBuilder query : boolQueryBuilder.must()) {
            BoolQueryBuilder subBoolQueryBuilder = (BoolQueryBuilder) query;
            assertTrue(subBoolQueryBuilder.should().size() == 2 || subBoolQueryBuilder.should().size() == 3);
            if (subBoolQueryBuilder.should().size() == 3) {
                boolQueryBuilder.should().forEach(subQuery -> {
                    assertTrue(subQuery instanceof TermsQueryBuilder);
                    TermsQueryBuilder termsQuery = (TermsQueryBuilder) subQuery;
                    // Each TermsQueryBuilder should contain candidateModelId
                    assertTrue(termsQuery.values().contains("candidateId"));
                });
            } else {
                boolQueryBuilder.should().forEach(subQuery -> {
                    assertTrue(subQuery instanceof BoolQueryBuilder);
                    BoolQueryBuilder boolQuery = (BoolQueryBuilder) subQuery;
                    assertTrue(boolQuery.must().size() == 2 || boolQuery.mustNot().size() == 1);
                    if (boolQuery.must().size() == 2) {
                        boolQuery.must().forEach(existSubQuery -> {
                            assertTrue(existSubQuery instanceof ExistsQueryBuilder || existSubQuery instanceof TermsQueryBuilder);
                            if (existSubQuery instanceof TermsQueryBuilder) {
                                TermsQueryBuilder termsQuery = (TermsQueryBuilder) existSubQuery;
                                assertTrue(termsQuery.fieldName().equals(MLAgent.IS_HIDDEN_FIELD));
                                assertTrue(termsQuery.values().contains(false));
                            } else {
                                ExistsQueryBuilder existsQuery = (ExistsQueryBuilder) existSubQuery;
                                assertTrue(existsQuery.fieldName().equals(MLAgent.IS_HIDDEN_FIELD));
                            }
                        });
                    } else {
                        QueryBuilder mustNotQuery = boolQuery.mustNot().get(0);
                        assertTrue(mustNotQuery instanceof ExistsQueryBuilder);
                        assertEquals(MLAgent.IS_HIDDEN_FIELD, ((ExistsQueryBuilder) mustNotQuery).fieldName());
                    }
                });
            }
        }

    }
}
