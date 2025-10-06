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
package org.opensearch.ml.memory.action.conversation;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.search.spell.LevenshteinDistance;
import org.junit.Before;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.BytesStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.test.OpenSearchTestCase;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetInteractionsResponseTests extends OpenSearchTestCase {
    List<Interaction> interactions;

    @Before
    public void setup() {
        interactions = List
            .of(
                new Interaction(
                    "id0",
                    Instant.now(),
                    Instant.now(),
                    "cid",
                    "input",
                    "pt",
                    "response",
                    "origin",
                    Collections.singletonMap("metadata", "some meta")
                ),
                new Interaction(
                    "id1",
                    Instant.now(),
                    Instant.now(),
                    "cid",
                    "input",
                    "pt",
                    "response",
                    "origin",
                    Collections.singletonMap("metadata", "some meta")
                ),
                new Interaction(
                    "id2",
                    Instant.now(),
                    Instant.now(),
                    "cid",
                    "input",
                    "pt",
                    "response",
                    "origin",
                    Collections.singletonMap("metadata", "some meta")
                )
            );
    }

    public void testGetInteractionsResponseStreaming() throws IOException {
        GetInteractionsResponse response = new GetInteractionsResponse(interactions, 4, true);
        assert (response.getInteractions().equals(interactions));
        assert (response.getNextToken() == 4);
        assert (response.hasMorePages());
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        response.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        GetInteractionsResponse newResp = new GetInteractionsResponse(in);
        assert (newResp.getInteractions().equals(interactions));
        assert (newResp.getNextToken() == 4);
        assert (newResp.hasMorePages());
    }

    public void testToXContent_MoreTokens() throws IOException {
        GetInteractionsResponse response = new GetInteractionsResponse(interactions.subList(0, 1), 2, true);
        Interaction interaction = response.getInteractions().get(0);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String result = BytesReference.bytes(builder).utf8ToString();
        String expected = "{\"messages\":[{\"memory_id\":\"cid\",\"message_id\":\"id0\",\"create_time\":\""
            + interaction.getCreateTime()
            + "\"update_time\":\""
            + interaction.getUpdatedTime()
            + "\",\"input\":\"input\",\"prompt_template\":\"pt\",\"response\":\"response\",\"origin\":\"origin\",\"additional_info\":{\"metadata\":\"some meta\"}}],\"next_token\":2}";
        // Sometimes there's an extra trailing 0 in the time stringification, so just assert closeness
        LevenshteinDistance ld = new LevenshteinDistance();
        assert (ld.getDistance(result, expected) > 0.95);
    }

    public void testToXContent_NoMoreTokens() throws IOException {
        GetInteractionsResponse response = new GetInteractionsResponse(interactions.subList(0, 1), 2, false);
        Interaction interaction = response.getInteractions().get(0);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String result = BytesReference.bytes(builder).utf8ToString();
        String expected = "{\"messages\":[{\"memory_id\":\"cid\",\"message_id\":\"id0\",\"create_time\":\""
            + interaction.getCreateTime()
            + "\"update_time\":\""
            + interaction.getUpdatedTime()
            + "\",\"input\":\"input\",\"prompt_template\":\"pt\",\"response\":\"response\",\"origin\":\"origin\",\"additional_info\":{\"metadata\":\"some meta\"}}]}";
        // Sometimes there's an extra trailing 0 in the time stringification, so just assert closeness
        LevenshteinDistance ld = new LevenshteinDistance();
        assert (ld.getDistance(result, expected) > 0.95);
    }

}
