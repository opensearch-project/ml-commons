/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import org.junit.Assert;
import org.junit.Test;

public class RetryBackoffPolicyTests {
    @Test
    public void testParseFromString_thenSuccess() {
        Assert.assertEquals(RetryBackoffPolicy.CONSTANT, RetryBackoffPolicy.from("constant"));
        Assert.assertEquals(RetryBackoffPolicy.CONSTANT, RetryBackoffPolicy.from("CONSTANT"));
        Assert.assertEquals(RetryBackoffPolicy.EXPONENTIAL_EQUAL_JITTER, RetryBackoffPolicy.from("exponential_equal_jitter"));
        Assert.assertEquals(RetryBackoffPolicy.EXPONENTIAL_EQUAL_JITTER, RetryBackoffPolicy.from("EXPONENTIAL_EQUAL_JITTER"));
        Assert.assertEquals(RetryBackoffPolicy.EXPONENTIAL_FULL_JITTER, RetryBackoffPolicy.from("exponential_full_jitter"));
        Assert.assertEquals(RetryBackoffPolicy.EXPONENTIAL_FULL_JITTER, RetryBackoffPolicy.from("EXPONENTIAL_FULL_JITTER"));
    }

    @Test
    public void testParseFromMalformedString_thenFail() {
        Exception exception = Assert.assertThrows(IllegalArgumentException.class, () -> RetryBackoffPolicy.from("test"));
        Assert.assertEquals("Unsupported retry backoff policy", exception.getMessage());
    }
}
