/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils.mergeMetaDataUtils;

import java.util.Map;

/** The rule always keep the latest one. */
public class LatestRule implements MergeRule {

    @Override
    public boolean isMatch(Map<String, Object> source, Map<String, Object> target) {
        return true;
    }

    @Override
    public void mergeInto(String key, Map<String, Object> source, Map<String, Object> target) {
        target.put(key, source);
    }
}
