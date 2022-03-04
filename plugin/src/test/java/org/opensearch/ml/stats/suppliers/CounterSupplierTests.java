/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.suppliers;

import org.opensearch.test.OpenSearchTestCase;

public class CounterSupplierTests extends OpenSearchTestCase {

    public void testGetAndIncrement() {
        CounterSupplier counterSupplier = new CounterSupplier();
        assertEquals("get returns incorrect value", (Long) 0L, counterSupplier.get());
        counterSupplier.increment();
        assertEquals("get returns incorrect value", (Long) 1L, counterSupplier.get());
    }
}
