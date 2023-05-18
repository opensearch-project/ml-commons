/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import org.junit.Test;
import org.opensearch.core.common.Strings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        intValue.toXContent(builder);

        assertNotNull(builder);
        String jsonStr = org.opensearch.common.Strings.toString(builder);
        assertEquals("{\"column_type\":\"INTEGER\",\"value\":2}", jsonStr);
    }
}
