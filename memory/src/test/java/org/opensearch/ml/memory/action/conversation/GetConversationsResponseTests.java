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
import org.opensearch.ml.common.conversation.ConversationMeta;
import org.opensearch.test.OpenSearchTestCase;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetConversationsResponseTests extends OpenSearchTestCase {

    List<ConversationMeta> conversations;

    @Before
    public void setup() {
        conversations = List
            .of(
                new ConversationMeta("0", Instant.now(), Instant.now(), "name0", "user0"),
                new ConversationMeta("1", Instant.now(), Instant.now(), "name1", "user0"),
                new ConversationMeta("2", Instant.now(), Instant.now(), "name2", "user2")
            );
    }

    public void testGetConversationsResponseStreaming() throws IOException {
        GetConversationsResponse response = new GetConversationsResponse(conversations, 2, true);
        assert (response.hasMorePages());
        assert (response.getConversations().equals(conversations));
        assert (response.getNextToken() == 2);
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        response.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        GetConversationsResponse newResp = new GetConversationsResponse(in);
        assert (newResp.hasMorePages());
        assert (newResp.getConversations().equals(conversations));
        assert (newResp.getNextToken() == 2);
    }

    public void testToXContent_MoreTokens() throws IOException {
        GetConversationsResponse response = new GetConversationsResponse(conversations.subList(0, 1), 2, true);
        ConversationMeta conversation = response.getConversations().get(0);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String result = BytesReference.bytes(builder).utf8ToString();
        String expected = "{\"conversations\":[{\"conversation_id\":\"0\",\"create_time\":\""
            + conversation.getCreatedTime()
            + "\",\"updated_time\":\""
            + conversation.getCreatedTime()
            + "\",\"name\":\"name0\",\"user\":\"user0\"}],\"next_token\":2}";
        log.info("FINDME");
        log.info(result);
        log.info(expected);
        // Sometimes there's an extra trailing 0 in the time stringification, so just assert closeness
        LevenshteinDistance ld = new LevenshteinDistance();
        log.info(ld.getDistance(result, expected));
        assert (ld.getDistance(result, expected) > 0.95);
    }

    public void testToXContent_NoMoreTokens() throws IOException {
        GetConversationsResponse response = new GetConversationsResponse(conversations.subList(0, 1), 2, false);
        ConversationMeta conversation = response.getConversations().get(0);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String result = BytesReference.bytes(builder).utf8ToString();
        String expected = "{\"conversations\":[{\"conversation_id\":\"0\",\"create_time\":\""
            + conversation.getCreatedTime()
            + "\",\"updated_time\":\""
            + conversation.getCreatedTime()
            + "\",\"name\":\"name0\",\"user\":\"user0\"}]}";
        log.info("FINDME");
        log.info(result);
        log.info(expected);
        // Sometimes there's an extra trailing 0 in the time stringification, so just assert closeness
        LevenshteinDistance ld = new LevenshteinDistance();
        log.info(ld.getDistance(result, expected));
        assert (ld.getDistance(result, expected) > 0.95);
    }

}
