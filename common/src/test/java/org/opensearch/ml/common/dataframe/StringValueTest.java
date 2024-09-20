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
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

public class StringValueTest {
    @Test
    public void stringValue() {
        StringValue value = new StringValue("str");
        assertEquals(ColumnType.STRING, value.columnType());
        assertEquals("str", value.stringValue());
        assertEquals("str", value.getValue());
    }

    @Test(expected = NullPointerException.class)
    public void stringValue_NullPointerException() {
        new StringValue(null);
    }

    @Test
    public void testToXContent() throws IOException {
        StringValue value = new StringValue("str");
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        value.toXContent(builder, ToXContent.EMPTY_PARAMS);

        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{\"column_type\":\"STRING\",\"value\":\"str\"}", jsonStr);
    }
}
