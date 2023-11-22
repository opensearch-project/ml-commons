/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.XContentBuilder;

public class BooleanValueTest {
    @Test
    public void booleanValue() {
        BooleanValue value = new BooleanValue(true);
        assertEquals(ColumnType.BOOLEAN, value.columnType());
        assertTrue(value.booleanValue());
        assertEquals(true, value.getValue());
    }

    @Test
    public void testToXContent() throws IOException {
        BooleanValue value = new BooleanValue(true);
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        value.toXContent(builder);

        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{\"column_type\":\"BOOLEAN\",\"value\":true}", jsonStr);
    }
}
