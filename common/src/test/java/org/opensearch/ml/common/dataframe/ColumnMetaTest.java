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

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.TestHelper;

import java.io.IOException;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ColumnMetaTest {
    ColumnMeta columnMeta;

    Function<XContentParser, ColumnMeta> function;

    @Before
    public void setup() {
        columnMeta = new ColumnMeta.ColumnMetaBuilder().name("columnMetaName").columnType(ColumnType.STRING).build();
        function = parser -> {
            try {
                return ColumnMeta.parse(parser);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse ColumnMeta", e);
            }
        };
    }

    @Test
    public void writeToAndReadStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        columnMeta.writeTo(bytesStreamOutput);

        ColumnMeta newColumnMeta = new ColumnMeta(bytesStreamOutput.bytes().streamInput());
        assertNotNull(newColumnMeta);
        assertEquals("columnMetaName", newColumnMeta.getName());
        assertEquals(ColumnType.STRING, newColumnMeta.getColumnType());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        columnMeta.toXContent(builder);

        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);
        assertEquals("{\"name\":\"columnMetaName\",\"column_type\":\"STRING\"}", jsonStr);
    }

    @Test
    public void testParse() throws IOException {
        ColumnMeta columnMeta = new ColumnMeta("test", ColumnType.DOUBLE);
        TestHelper.testParse(columnMeta, function);
    }

    @Test
    public void testParse_WrongExtraField() throws IOException {
        ColumnMeta columnMeta = new ColumnMeta("test", ColumnType.DOUBLE);
        String jsonStr = "{\"name\":\"test\",\"column_type\":\"DOUBLE\",\"wrong\":\"abc\"}";
        TestHelper.testParseFromString(columnMeta, jsonStr, function);
    }
}
