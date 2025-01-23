/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.TOOL_PARAMETERS_PREFIX;

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
        assertEquals(3, boolQueryBuilder.must().size());

        for (QueryBuilder query : boolQueryBuilder.must()) {
            if (query instanceof TermsQueryBuilder) {
                TermsQueryBuilder termsQuery = (TermsQueryBuilder) query;
                String fieldName = termsQuery.fieldName();
                assertTrue(fieldName.equals(MLAgent.IS_HIDDEN_FIELD));
                assertTrue(termsQuery.values().contains(false));
            } else if (query instanceof ExistsQueryBuilder) {
                ExistsQueryBuilder existsQuery = (ExistsQueryBuilder) query;
                String fieldName = existsQuery.fieldName();
                assertTrue(fieldName.equals(MLAgent.IS_HIDDEN_FIELD));
            } else {
                assertTrue(query instanceof BoolQueryBuilder);
                assertEquals(3, boolQueryBuilder.must().size());
                boolQueryBuilder.should().forEach(subQuery -> {
                    assertTrue(subQuery instanceof TermsQueryBuilder);
                    TermsQueryBuilder termsQuery = (TermsQueryBuilder) subQuery;
                    String fieldName = termsQuery.fieldName();

                    // The field name should be 'TOOL_PARAMETERS_PREFIX + keyField'
                    // We had "modelKeyA" and "modelKeyB" as keys:
                    boolean isCorrectField = fieldName.equals(TOOL_PARAMETERS_PREFIX + "modelKey1")
                        || fieldName.equals(TOOL_PARAMETERS_PREFIX + "modelKey2")
                        || fieldName.equals(TOOL_PARAMETERS_PREFIX + "anotherModelKey");
                    assertTrue(isCorrectField);

                    // Each TermsQueryBuilder should contain candidateModelId
                    assertTrue(termsQuery.values().contains("candidateId"));
                });

            }
        }
    }
}
