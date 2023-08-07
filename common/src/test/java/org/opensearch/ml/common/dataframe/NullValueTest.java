/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import org.junit.Test;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

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
        XContentBuilder builder = XContentFactory.jsonBuilder();
        value.toXContent(builder, ToXContent.EMPTY_PARAMS);

        assertNotNull(builder);
        String jsonStr = StringUtils.xContentBuilderToString(builder);
        assertEquals("{\"column_type\":\"NULL\"}", jsonStr);
    }
}
