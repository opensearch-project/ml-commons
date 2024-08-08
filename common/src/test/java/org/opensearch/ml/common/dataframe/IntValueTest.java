/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.XContentBuilder;

public class IntValueTest {

    @Test
    public void intValue() {
        IntValue intValue = new IntValue(2);
        assertEquals(ColumnType.INTEGER, intValue.columnType());
        assertEquals(2, intValue.getValue());
        assertEquals(2, intValue.intValue());
    }

    @Test
    public void testToXContent() throws IOException {
        IntValue intValue = new IntValue(2);
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        intValue.toXContent(builder);

        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{\"column_type\":\"INTEGER\",\"value\":2}", jsonStr);
    }
}
