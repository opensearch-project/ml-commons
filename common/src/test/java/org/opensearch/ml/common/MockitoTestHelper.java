/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.mockito.Mockito.mock;

import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.opensearch.core.action.ActionListener;

/**
 * Type-safe Mockito helpers for common test patterns.
 */
public final class MockitoTestHelper {

    private MockitoTestHelper() {}

    @SuppressWarnings("unchecked") // Mockito mock() cannot preserve ActionListener generic type
    public static <T> ActionListener<T> mockActionListener() {
        return mock(ActionListener.class);
    }

    public static <T> ActionListener<T> anyActionListener() {
        return ArgumentMatchers.<ActionListener<T>>any();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" }) // ArgumentCaptor.forClass requires raw Class for generic Map
    public static <K, V> ArgumentCaptor<Map<K, V>> forMapClass() {
        return ArgumentCaptor.forClass((Class) java.util.Map.class);
    }
}
