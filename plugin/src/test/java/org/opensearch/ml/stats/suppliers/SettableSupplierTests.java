/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.stats.suppliers;

import org.junit.Test;
import org.opensearch.test.OpenSearchTestCase;

public class SettableSupplierTests extends OpenSearchTestCase {
    @Test
    public void testSetGet() {
        Long setCount = 15L;
        SettableSupplier settableSupplier = new SettableSupplier();
        settableSupplier.set(setCount);
        assertEquals("Get/Set fails", setCount, settableSupplier.get());
    }
}
