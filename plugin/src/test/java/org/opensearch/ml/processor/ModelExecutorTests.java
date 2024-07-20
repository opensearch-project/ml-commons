/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ModelExecutorTests {

    @Test
    public void testIsEmptyOrNullList_NullList() {

        List<String> nullList = null;
        boolean result = ModelExecutor.isEmptyOrNullList(nullList);
        assertTrue(result);
    }

    @Test
    public void testIsEmptyOrNullList_EmptyList() {

        List<String> emptyList = new ArrayList<>();
        boolean result = ModelExecutor.isEmptyOrNullList(emptyList);
        assertTrue(result);
    }

    @Test
    public void testIsEmptyOrNullList_ListWithNonNullElements() {

        List<String> nonNullList = Arrays.asList("apple", "banana", "orange");
        boolean result = ModelExecutor.isEmptyOrNullList(nonNullList);
        assertFalse(result);
    }

    @Test
    public void testIsEmptyOrNullList_ListWithNullElements() {

        List<String> nullList = Arrays.asList(null, null, null);
        boolean result = ModelExecutor.isEmptyOrNullList(nullList);
        assertTrue(result);
    }

    @Test
    public void testIsEmptyOrNullList_ListWithMixedElements() {

        List<String> mixedList = Arrays.asList("apple", null, "orange", null);
        boolean result = ModelExecutor.isEmptyOrNullList(mixedList);
        assertFalse(result);
    }

}
