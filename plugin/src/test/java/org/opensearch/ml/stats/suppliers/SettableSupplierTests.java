package org.opensearch.ml.stats.suppliers;

import org.opensearch.test.OpenSearchTestCase;
import org.junit.Test;

public class SettableSupplierTests extends OpenSearchTestCase {
    @Test
    public void testSetGet() {
        Long setCount = 15L;
        SettableSupplier settableSupplier = new SettableSupplier();
        settableSupplier.set(setCount);
        assertEquals("Get/Set fails", setCount, settableSupplier.get());
    }
}
