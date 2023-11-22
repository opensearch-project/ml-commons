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

public class FloatValueTest {

    @Test
    public void floatValue() {
        FloatValue floatValue = new FloatValue(2.1f);
        assertEquals(ColumnType.FLOAT, floatValue.columnType());
        assertEquals(2.1f, floatValue.getValue());
        assertEquals(2.1f, floatValue.floatValue(), 1e-5);
        assertEquals(2.1d, floatValue.doubleValue(), 1e-5);
    }

    @Test
    public void testToXContent() throws IOException {
        FloatValue floatValue = new FloatValue(2.1f);
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        floatValue.toXContent(builder);

        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{\"column_type\":\"FLOAT\",\"value\":2.1}", jsonStr);
    }
}
