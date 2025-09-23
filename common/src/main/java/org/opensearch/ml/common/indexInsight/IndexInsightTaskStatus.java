/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import java.util.Locale;

public enum IndexInsightTaskStatus {
    GENERATING,
    COMPLETED,
    FAILED;

    public static IndexInsightTaskStatus fromString(String status) {
        if (status == null) {
            throw new IllegalArgumentException("Index insight task status can't be null");
        }
        try {
            return IndexInsightTaskStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong index insight task status");
        }
    }
}
