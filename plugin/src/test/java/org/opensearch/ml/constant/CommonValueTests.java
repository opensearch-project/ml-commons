/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.constant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class CommonValueTests {

    @Test
    public void testActionPrefix() {
        assertEquals("cluster:admin/opensearch/ml/", CommonValue.ACTION_PREFIX);
    }

    @Test
    public void testConstructor() {
        // Test constructor to achieve full line coverage
        CommonValue commonValue = new CommonValue();
        assertNotNull(commonValue);
    }
}
