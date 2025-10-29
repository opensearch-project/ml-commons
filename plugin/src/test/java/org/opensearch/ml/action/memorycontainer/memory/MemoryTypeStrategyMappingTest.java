/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;
import org.opensearch.ml.helper.MemoryContainerHelper;

/**
 * Test for verifying strategy type to MemoryType mapping
 */
public class MemoryTypeStrategyMappingTest {

    private MemoryOperationsService memoryOperationsService;

    @Before
    public void setUp() {
        MemoryContainerHelper helper = mock(MemoryContainerHelper.class);
        memoryOperationsService = new MemoryOperationsService(helper);
    }

    @Test
    public void testSemanticStrategyMapsToSemanticType() throws Exception {
        MemoryStrategy strategy = MemoryStrategy
            .builder()
            .id("semantic_12345678")
            .type(MemoryStrategyType.SEMANTIC)
            .enabled(true)
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        MemoryStrategyType result = invokeGetMemoryTypeFromStrategy(strategy);
        assertEquals(MemoryStrategyType.SEMANTIC, result);
    }

    @Test
    public void testUserPreferenceStrategyMapsToUserPreferenceType() throws Exception {
        MemoryStrategy strategy = MemoryStrategy
            .builder()
            .id("user_preference_87654321")
            .type(MemoryStrategyType.USER_PREFERENCE)
            .enabled(true)
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        MemoryStrategyType result = invokeGetMemoryTypeFromStrategy(strategy);
        assertEquals(MemoryStrategyType.USER_PREFERENCE, result);
    }

    @Test
    public void testSummaryStrategyMapsToSummaryType() throws Exception {
        MemoryStrategy strategy = MemoryStrategy
            .builder()
            .id("summary_11223344")
            .type(MemoryStrategyType.SUMMARY)
            .enabled(true)
            .namespace(Arrays.asList("session_id"))
            .strategyConfig(new HashMap<>())
            .build();

        MemoryStrategyType result = invokeGetMemoryTypeFromStrategy(strategy);
        assertEquals(MemoryStrategyType.SUMMARY, result);
    }

    @Test
    public void testUpperCaseStrategyTypesWorkCorrectly() throws Exception {
        // Test uppercase SUMMARY
        MemoryStrategy summaryStrategy = MemoryStrategy
            .builder()
            .id("summary_uppercase")
            .type(MemoryStrategyType.SUMMARY)
            .enabled(true)
            .namespace(Arrays.asList("session_id"))
            .strategyConfig(new HashMap<>())
            .build();

        assertEquals(MemoryStrategyType.SUMMARY, invokeGetMemoryTypeFromStrategy(summaryStrategy));

        // Test uppercase USER_PREFERENCE
        MemoryStrategy userPrefStrategy = MemoryStrategy
            .builder()
            .id("user_preference_uppercase")
            .type(MemoryStrategyType.USER_PREFERENCE)
            .enabled(true)
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        assertEquals(MemoryStrategyType.USER_PREFERENCE, invokeGetMemoryTypeFromStrategy(userPrefStrategy));

        // Test uppercase SEMANTIC
        MemoryStrategy semanticStrategy = MemoryStrategy
            .builder()
            .id("semantic_uppercase")
            .type(MemoryStrategyType.SEMANTIC)
            .enabled(true)
            .namespace(Arrays.asList("agent_id"))
            .strategyConfig(new HashMap<>())
            .build();

        assertEquals(MemoryStrategyType.SEMANTIC, invokeGetMemoryTypeFromStrategy(semanticStrategy));
    }

    @Test
    public void testUnknownStrategyDefaultsToSemantic() throws Exception {
        MemoryStrategy strategy = MemoryStrategy
            .builder()
            .id("unknown_55667788")
            .type(null)
            .enabled(true)
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        MemoryStrategyType result = invokeGetMemoryTypeFromStrategy(strategy);
        assertEquals(MemoryStrategyType.SEMANTIC, result);
    }

    /**
     * Use reflection to invoke the private getMemoryTypeFromStrategy method
     */
    private MemoryStrategyType invokeGetMemoryTypeFromStrategy(MemoryStrategy strategy) throws Exception {
        Method method = MemoryOperationsService.class.getDeclaredMethod("getMemoryTypeFromStrategy", MemoryStrategy.class);
        method.setAccessible(true);
        return (MemoryStrategyType) method.invoke(memoryOperationsService, strategy);
    }
}
