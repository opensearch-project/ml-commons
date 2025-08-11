/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import java.util.Locale;

public enum MLIndexInsightType {
    STATISTICAL_DATA,
    FIELD_DESCRIPTION,
    INDEX_DESCRIPTION,
    LOG_RELATED_INDEX_CHECK;

    public static MLIndexInsightType fromString(String type) {
        if (type == null) {
            throw new IllegalArgumentException("ML index insight type can't be null");
        }
        try {
            return MLIndexInsightType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong index insight type");
        }
    }
}
