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

import java.io.IOException;
import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RowTest {
    Row row;

    @Before
    public void setup() {
        row = new Row(1);
        row.setValue(0, ColumnValueBuilder.build(0));
    }

    @Test
    public void setAndGetValue() {
        row.setValue(0, ColumnValueBuilder.build(1));
        assertEquals(1, row.getValue(0).intValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setValue_Exception_IndexLessThanZero() {
        row.setValue(-1, ColumnValueBuilder.build(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void setValue_Exception_IndexBiggerThanSize() {
        row.setValue(1, ColumnValueBuilder.build(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getValue_Exception_IndexLessThanZero() {
        row.getValue(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getValue_Exception_IndexBiggerThanSize() {
        row.getValue(row.size());
    }

    @Test
    public void iterator() {
        Iterator<ColumnValue> iterator = row.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(0, iterator.next().intValue());
    }

    @Test
    public void size() {
        assertEquals(1, row.size());
    }

    @Test
    public void writeToAndReadStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        row.writeTo(bytesStreamOutput);
        row = new Row(bytesStreamOutput.bytes().streamInput());
        assertEquals(1, row.size());
    }

    @Test
    public void remove() {
        row = new Row(2);
        row.setValue(0, ColumnValueBuilder.build(0));
        row.setValue(1, ColumnValueBuilder.build(false));
        row = row.remove(1);
        assertEquals(1, row.size());
        assertEquals(0, row.getValue(0).intValue());
    }

    @Test
    public void select() {
        row = new Row(2);
        row.setValue(0, ColumnValueBuilder.build(0));
        row.setValue(1, ColumnValueBuilder.build(false));
        row = row.select(new int[]{1});
        assertEquals(1, row.size());
        assertFalse(row.getValue(0).booleanValue());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        row.toXContent(builder);

        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);

        assertEquals("{\"values\":[{\"column_type\":\"INTEGER\",\"value\":0}]}", jsonStr);
    }
}