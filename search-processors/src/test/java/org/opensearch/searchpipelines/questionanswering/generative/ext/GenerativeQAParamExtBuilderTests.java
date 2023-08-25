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

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParamExtBuilder;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParameters;
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
