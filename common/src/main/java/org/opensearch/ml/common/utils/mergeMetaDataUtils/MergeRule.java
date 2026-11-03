/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils.mergeMetaDataUtils;

import java.util.Map;

/**
 * The Interface to merge index schemas. Need to implement isMatch: Whether match this rule,
 * mergeInto, how to merge the source type to target map.
 */
public interface MergeRule {
    boolean isMatch(Map<String, Object> source, Map<String, Object> target);

    void mergeInto(String key, Map<String, Object> source, Map<String, Object> target);
}
