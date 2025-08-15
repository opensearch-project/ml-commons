/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils.mergeMetaDataUtils;

import java.util.Map;

/** This rule will merge two array/struct object and merge their properties */
public class DeepMergeRule implements MergeRule {

    @Override
    public boolean isMatch(Map<String, Object> source, Map<String, Object> target) {
        return source != null
            && target != null
            && source.get("properties") != null
            && target.get("properties") != null
            && source.getOrDefault("type", "object").equals(target.getOrDefault("type", "object"));
    }

    @Override
    public void mergeInto(String key, Map<String, Object> source, Map<String, Object> target) {
        Map<String, Object> existing = (Map<String, Object>) target.get(key);
        MergeRuleHelper.merge((Map<String, Object>) source.get("properties"), (Map<String, Object>) existing.get("properties"));
        target.put(key, existing);
    }
}
