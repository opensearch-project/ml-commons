/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import org.junit.Test;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ShortValueTest {

    @Test
    public void shortValue() {
        ShortValue shortValue = new ShortValue((short)2);
        assertEquals(ColumnType.SHORT, shortValue.columnType());
        assertEquals((short)2, shortValue.getValue());
        assertEquals(2.0d, shortValue.doubleValue(), 1e-5);
    }

    @Test
    public void testToXContent() throws IOException {
        ShortValue shortValue = new ShortValue((short)2);
        XContentBuilder builder = XContentFactory.jsonBuilder();
        shortValue.toXContent(builder);

        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{\"column_type\":\"SHORT\",\"value\":2}", jsonStr);
    }
}
