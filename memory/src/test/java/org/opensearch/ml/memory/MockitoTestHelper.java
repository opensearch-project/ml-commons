/*
 * Copyright Aryn, Inc 2023
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory;

import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.opensearch.core.action.ActionListener;

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
        return ArgumentCaptor.forClass((Class) Map.class);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" }) // ArgumentCaptor.forClass requires raw Class for generic List
    public static <T> ArgumentCaptor<List<T>> forListClass() {
        return ArgumentCaptor.forClass((Class) java.util.List.class);
    }

    public static Map<String, Object> anyStringKeyMap() {
        return ArgumentMatchers.any();
    }
}
