/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.opensearch.ml.stats;

import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.test.OpenSearchTestCase;
import org.junit.Assert;
import org.junit.Test;

import java.util.function.Supplier;

public class MLStatTests extends OpenSearchTestCase {
    @Test
    public void testIsClusterLevel() {
        MLStat<String> stat1 = new MLStat<>(true, new TestSupplier());
        Assert.assertTrue("isCluster returns the wrong value", stat1.isClusterLevel());
        MLStat<String> stat2 = new MLStat<>(false, new TestSupplier());
        Assert.assertTrue("isCluster returns the wrong value", !stat2.isClusterLevel());
    }

    @Test
    public void testGetValue() {
        MLStat<Long> stat1 = new MLStat<>(false, new CounterSupplier());
        Assert.assertEquals("GetValue returns the incorrect value", 0L, (long)(stat1.getValue()));

        MLStat<String> stat2 = new MLStat<>(false, new TestSupplier());
        Assert.assertEquals("GetValue returns the incorrect value", "test", stat2.getValue());
    }

    @Test
    public void testIncrementDecrement() {
        MLStat<Long> stat = new MLStat<>(false, new CounterSupplier());

        for (Long i = 0L; i < 100; i++) {
            Assert.assertEquals("increment does not work", i, stat.getValue());
            stat.increment();
        }

        for (Long i = 100L; i > 0; i--) {
            Assert.assertEquals("decrement does not work", i, stat.getValue());
            stat.decrement();
        }

        // Ensure that no problems occur for a stat that cannot be incremented/decremented
        MLStat<String> nonIncDecStat = new MLStat<>(false, new TestSupplier());
        nonIncDecStat.increment();
        nonIncDecStat.decrement();

    }

    private class TestSupplier implements Supplier<String> {
        TestSupplier() {}

        public String get() {
            return "test";
        }
    }
}
