/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.util.Locale;

public enum RetryBackoffPolicy {
    CONSTANT,
    EXPONENTIAL_EQUAL_JITTER,
    EXPONENTIAL_FULL_JITTER;

    public static RetryBackoffPolicy from(String value) {
        try {
            return RetryBackoffPolicy.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Unsupported retry backoff policy");
        }
    }
}
