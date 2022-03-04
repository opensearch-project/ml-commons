/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import java.util.function.Supplier;

import org.junit.Assert;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.stats.suppliers.SettableSupplier;
import org.opensearch.test.OpenSearchTestCase;

public class MLStatTests extends OpenSearchTestCase {

    public void testIsClusterLevel() {
        MLStat<String> stat1 = new MLStat<>(true, new TestSupplier());
        Assert.assertTrue("isCluster returns the wrong value", stat1.isClusterLevel());
        MLStat<String> stat2 = new MLStat<>(false, new TestSupplier());
        Assert.assertTrue("isCluster returns the wrong value", !stat2.isClusterLevel());
    }

    public void testSetGetValue() {
        MLStat<Long> stat1 = new MLStat<>(false, new CounterSupplier());
        Assert.assertEquals("GetValue returns the incorrect value", 0L, (long) (stat1.getValue()));
        stat1.setValue(1L);
        Assert.assertEquals("GetValue returns the incorrect value", 0L, (long) (stat1.getValue()));

        MLStat<String> stat2 = new MLStat<>(false, new TestSupplier());
        Assert.assertEquals("GetValue returns the incorrect value", "test", stat2.getValue());
        stat2.setValue(1L);
        Assert.assertEquals("GetValue returns the incorrect value", "test", stat2.getValue());

        MLStat<Long> stat3 = new MLStat<>(false, new SettableSupplier());
        Assert.assertEquals("GetValue returns the incorrect value", 0L, (long) stat3.getValue());
        stat3.setValue(1L);
        Assert.assertEquals("GetValue returns the incorrect value", 1L, (long) stat3.getValue());
    }

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
