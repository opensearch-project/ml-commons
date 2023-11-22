/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

public class NullValueTest {

    @Test
    public void getValue() {
        NullValue value = new NullValue();
        assertNull(value.getValue());
        assertEquals(ColumnType.NULL, value.columnType());
    }

    @Test
    public void testToXContent() throws IOException {
        NullValue value = new NullValue();
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        value.toXContent(builder, ToXContent.EMPTY_PARAMS);

        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{\"column_type\":\"NULL\"}", jsonStr);
    }
}
