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
package org.opensearch.searchpipelines.questionanswering.generative.llm;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParseException;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.test.OpenSearchTestCase;

public class MessageBlockTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    public void testStreamRoundTrip() throws Exception {
        MessageBlock.TextBlock tb = new MessageBlock.TextBlock("text");
        MessageBlock.ImageBlock ib = new MessageBlock.ImageBlock("jpeg", "data", null);
        MessageBlock.ImageBlock ib2 = new MessageBlock.ImageBlock("jpeg", null, "https://xyz/foo.jpg");
        MessageBlock.DocumentBlock db = new MessageBlock.DocumentBlock("pdf", "doc1", "data");
        List<MessageBlock.AbstractBlock> blocks = List.of(tb, ib, ib2, db);
        MessageBlock mb = new MessageBlock();
        mb.setRole("user");
        mb.setBlockList(blocks);
        BytesStreamOutput bso = new BytesStreamOutput();
        mb.writeTo(bso);
        MessageBlock read = new MessageBlock(bso.bytes().streamInput());
        assertEquals(mb, read);
    }

    public void testFromXContentParseError() throws IOException {
        exceptionRule.expect(XContentParseException.class);

        MessageBlock.TextBlock tb = new MessageBlock.TextBlock("text");
        MessageBlock.ImageBlock ib = new MessageBlock.ImageBlock("jpeg", "data", null);
        // MessageBlock.ImageBlock ib2 = new MessageBlock.ImageBlock("jpeg", null, "https://xyz/foo.jpg");
        MessageBlock.ImageBlock ib2 = new MessageBlock.ImageBlock(Map.of("format", "png", "data", "xyz"));
        MessageBlock.DocumentBlock db = new MessageBlock.DocumentBlock("pdf", "doc1", "data");
        List<MessageBlock.AbstractBlock> blocks = List.of(tb, ib, ib2, db);
        MessageBlock mb = new MessageBlock();
        mb.setRole("user");
        mb.setBlockList(blocks);
        try (XContentBuilder builder = XContentBuilder.builder(randomFrom(XContentType.values()).xContent())) {
            mb.toXContent(builder, ToXContent.EMPTY_PARAMS);
            try (XContentBuilder shuffled = shuffleXContent(builder); XContentParser parser = createParser(shuffled)) {
                // read = TaskResult.PARSER.apply(parser, null);
                MessageBlock.fromXContent(parser);
            }
        } finally {
            // throw new IOException("Error processing [" + mb + "]", e);
        }
    }

    public void testInvalidImageBlock1() {
        exceptionRule.expect(IllegalArgumentException.class);
        MessageBlock.ImageBlock ib = new MessageBlock.ImageBlock(Map.of("format", "png"));
    }

    public void testInvalidImageBlock2() {
        exceptionRule.expect(IllegalArgumentException.class);
        MessageBlock.ImageBlock ib = new MessageBlock.ImageBlock("jpeg", null, null);
    }

    public void testInvalidDocumentBlock1() {
        exceptionRule.expect(NullPointerException.class);
        MessageBlock.DocumentBlock db = new MessageBlock.DocumentBlock(null, null, null);
    }

    public void testInvalidDocumentBlock2() {
        exceptionRule.expect(IllegalStateException.class);
        MessageBlock.DocumentBlock db = new MessageBlock.DocumentBlock(Map.of());
    }

    public void testDocumentBlockCtor1() {
        MessageBlock.DocumentBlock db = new MessageBlock.DocumentBlock(Map.of("format", "pdf", "name", "doc", "data", "xyz"));
        assertEquals(db.format, "pdf");
        assertEquals(db.name, "doc");
        assertEquals(db.data, "xyz");
    }
}
