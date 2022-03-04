/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.suppliers;

import org.opensearch.test.OpenSearchTestCase;

public class SettableSupplierTests extends OpenSearchTestCase {

    public void testSetGet() {
        Long setCount = 15L;
        SettableSupplier settableSupplier = new SettableSupplier();
        settableSupplier.set(setCount);
        assertEquals("Get/Set fails", setCount, settableSupplier.get());
    }
}
