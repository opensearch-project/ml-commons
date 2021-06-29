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
import static org.junit.Assert.assertTrue;

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
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        value.toXContent(builder);

        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);
        assertEquals("{\"column_type\":\"BOOLEAN\",\"value\":true}", jsonStr);
    }
}