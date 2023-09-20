/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.searchpipelines.questionanswering.generative.ext;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentHelper;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.test.OpenSearchTestCase;

import java.io.EOFException;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GenerativeQAParamExtBuilderTests extends OpenSearchTestCase {

    public void testCtor() throws IOException {
        GenerativeQAParamExtBuilder builder = new GenerativeQAParamExtBuilder();
        GenerativeQAParameters parameters = new GenerativeQAParameters("conversation_id", "model_id", "question", null, null, null);
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

    public void testMiscMethods() throws IOException {
        GenerativeQAParameters param1 = new GenerativeQAParameters("a", "b", "c", null, null, null);
        GenerativeQAParameters param2 = new GenerativeQAParameters("a", "b", "d", null, null, null);
        GenerativeQAParamExtBuilder builder1 = new GenerativeQAParamExtBuilder();
        GenerativeQAParamExtBuilder builder2 = new GenerativeQAParamExtBuilder();
        builder1.setParams(param1);
        builder2.setParams(param2);
        assertEquals(builder1, builder1);
        assertNotEquals(builder1, param1);
        assertNotEquals(builder1, builder2);
        assertNotEquals(builder1.hashCode(), builder2.hashCode());

        StreamOutput so = mock(StreamOutput.class);
        builder1.writeTo(so);
        verify(so, times(2)).writeOptionalString(any());
        verify(so, times(1)).writeString(any());
    }

    public void testParse() throws IOException {
        XContentParser xcParser = mock(XContentParser.class);
        when(xcParser.nextToken()).thenReturn(XContentParser.Token.START_OBJECT).thenReturn(XContentParser.Token.END_OBJECT);
        GenerativeQAParamExtBuilder builder = GenerativeQAParamExtBuilder.parse(xcParser);
        assertNotNull(builder);
        assertNotNull(builder.getParams());
    }

    public void testXContentRoundTrip() throws IOException {
        GenerativeQAParameters param1 = new GenerativeQAParameters("a", "b", "c", null, null, null);
        GenerativeQAParamExtBuilder extBuilder = new GenerativeQAParamExtBuilder();
        extBuilder.setParams(param1);
        XContentType xContentType = randomFrom(XContentType.values());
        BytesReference serialized = XContentHelper.toXContent(extBuilder, xContentType, true);
        XContentParser parser = createParser(xContentType.xContent(), serialized);
        GenerativeQAParamExtBuilder deserialized = GenerativeQAParamExtBuilder.parse(parser);
        assertEquals(extBuilder, deserialized);
        GenerativeQAParameters parameters = deserialized.getParams();
        assertTrue(GenerativeQAParameters.SIZE_NULL_VALUE == parameters.getContextSize());
        assertTrue(GenerativeQAParameters.SIZE_NULL_VALUE == parameters.getInteractionSize());
        assertTrue(GenerativeQAParameters.SIZE_NULL_VALUE == parameters.getTimeout());
    }

    public void testXContentRoundTripAllValues() throws IOException {
        GenerativeQAParameters param1 = new GenerativeQAParameters("a", "b", "c", 1, 2, 3);
        GenerativeQAParamExtBuilder extBuilder = new GenerativeQAParamExtBuilder();
        extBuilder.setParams(param1);
        XContentType xContentType = randomFrom(XContentType.values());
        BytesReference serialized = XContentHelper.toXContent(extBuilder, xContentType, true);
        XContentParser parser = createParser(xContentType.xContent(), serialized);
        GenerativeQAParamExtBuilder deserialized = GenerativeQAParamExtBuilder.parse(parser);
        assertEquals(extBuilder, deserialized);
    }

    public void testStreamRoundTrip() throws IOException {
        GenerativeQAParameters param1 = new GenerativeQAParameters("a", "b", "c", null, null, null);
        GenerativeQAParamExtBuilder extBuilder = new GenerativeQAParamExtBuilder();
        extBuilder.setParams(param1);
        BytesStreamOutput bso = new BytesStreamOutput();
        extBuilder.writeTo(bso);
        GenerativeQAParamExtBuilder deserialized = new GenerativeQAParamExtBuilder(bso.bytes().streamInput());
        assertEquals(extBuilder, deserialized);
        GenerativeQAParameters parameters = deserialized.getParams();
        assertTrue(GenerativeQAParameters.SIZE_NULL_VALUE == parameters.getContextSize());
        assertTrue(GenerativeQAParameters.SIZE_NULL_VALUE == parameters.getInteractionSize());
        assertTrue(GenerativeQAParameters.SIZE_NULL_VALUE == parameters.getTimeout());
    }

    public void testStreamRoundTripAllValues() throws IOException {
        GenerativeQAParameters param1 = new GenerativeQAParameters("a", "b", "c", 1, 2, 3);
        GenerativeQAParamExtBuilder extBuilder = new GenerativeQAParamExtBuilder();
        extBuilder.setParams(param1);
        BytesStreamOutput bso = new BytesStreamOutput();
        extBuilder.writeTo(bso);
        GenerativeQAParamExtBuilder deserialized = new GenerativeQAParamExtBuilder(bso.bytes().streamInput());
        assertEquals(extBuilder, deserialized);
    }
}
