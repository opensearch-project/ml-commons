package org.opensearch.searchpipelines.questionanswering.generative;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.test.OpenSearchTestCase;

import java.io.EOFException;
import java.io.IOException;

public class GenerativeQAParamExtBuilderTests extends OpenSearchTestCase {

    public void testCtor() throws IOException {
        GenerativeQAParamExtBuilder builder = new GenerativeQAParamExtBuilder();
        GenerativeQAParameters parameters = new GenerativeQAParameters();
        builder.setParams(parameters);
        assertEquals(parameters, builder.getParams());

        GenerativeQAParamExtBuilder builder1 = new GenerativeQAParamExtBuilder(new StreamInput() {
            @Override
            public byte readByte() throws IOException {
                return 0;
            }

            @Override
            public void readBytes(byte[] b, int offset, int len) throws IOException {

            }

            @Override
            public void close() throws IOException {

            }

            @Override
            public int available() throws IOException {
                return 0;
            }

            @Override
            protected void ensureCanReadBytes(int length) throws EOFException {

            }

            @Override
            public int read() throws IOException {
                return 0;
            }
        });

        assertNotNull(builder1);
    }

    public void testMiscMethods() {
        GenerativeQAParameters param1 = new GenerativeQAParameters("a", "b", "c");
        GenerativeQAParameters param2 = new GenerativeQAParameters("a", "b", "d");
        GenerativeQAParamExtBuilder builder1 = new GenerativeQAParamExtBuilder();
        GenerativeQAParamExtBuilder builder2 = new GenerativeQAParamExtBuilder();
        builder1.setParams(param1);
        builder2.setParams(param2);
        assertNotEquals(builder1, builder2);
        assertNotEquals(builder1.hashCode(), builder2.hashCode());
    }
}
