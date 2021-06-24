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

package org.opensearch.ml.common.dataframe;

import org.junit.Test;
import org.opensearch.common.Strings;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        builder.startObject();
        value.toXContent(builder);
        builder.endObject();

        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);
        assertEquals("{\"ColumnType\":\"NULL\",\"Value\":null}", jsonStr);
    }
}