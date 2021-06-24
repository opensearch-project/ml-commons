package org.opensearch.ml.common.dataframe;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ColumnMetaTest {
    ColumnMeta columnMeta;

    @Before
    public void setup() {
        columnMeta = new ColumnMeta.ColumnMetaBuilder().name("columnMetaName").columnType(ColumnType.STRING).build();
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
        builder.startObject();
        columnMeta.toXContent(builder);
        builder.endObject();

        assertNotNull(builder);
        String jsonStr = Strings.toString(builder);
        assertEquals("{\"Name\":\"columnMetaName\",\"ColumnType\":\"STRING\"}", jsonStr);
    }
}
