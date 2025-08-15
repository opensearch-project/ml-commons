/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils.mergeMetaDataUtils;

import java.util.List;
import java.util.Map;

public class MergeRuleHelper {
    private static final List<MergeRule> RULES = List
        .of(
            new DeepMergeRule(),
            new LatestRule() // must come last
        );

    public static MergeRule selectRule(Map<String, Object> source, Map<String, Object> target) {
        MergeRule resultRule = RULES.stream().filter(rule -> rule.isMatch(source, target)).findFirst().orElseThrow(); // logically
        // unreachable if
        // fallback exists
        return resultRule;
    }

    public static void merge(Map<String, Object> source, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> sourceValue = (Map<String, Object>) entry.getValue();
            Map<String, Object> targetValue = (Map<String, Object>) target.get(key);
            MergeRuleHelper.selectRule(sourceValue, targetValue).mergeInto(key, sourceValue, target);
        }
    }
}
