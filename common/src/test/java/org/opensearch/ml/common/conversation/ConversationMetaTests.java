/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.conversation;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.search.SearchHit;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

public class ConversationMetaTests {

    ConversationMeta conversationMeta;
    Instant time;

    @Before
    public void setUp() {
        time = Instant.now();
        conversationMeta = new ConversationMeta("test_id", time, time, "test_name", "admin");
    }

    @Test
    public void test_fromSearchHit() throws IOException {
        XContentBuilder content = XContentBuilder.builder(XContentType.JSON.xContent());
        content.startObject();
        content.field(ConversationalIndexConstants.META_CREATED_TIME_FIELD, time);
        content.field(ConversationalIndexConstants.META_UPDATED_TIME_FIELD, time);
        content.field(ConversationalIndexConstants.META_NAME_FIELD, "meta name");
        content.field(ConversationalIndexConstants.USER_FIELD, "admin");
        content.endObject();

        SearchHit[] hits = new SearchHit[1];
        hits[0] = new SearchHit(0, "cId", null, null).sourceRef(BytesReference.bytes(content));

        ConversationMeta conversationMeta = ConversationMeta.fromSearchHit(hits[0]);
        assertEquals(conversationMeta.getId(), "cId");
        assertEquals(conversationMeta.getName(), "meta name");
        assertEquals(conversationMeta.getUser(), "admin");
    }

    @Test
    public void test_fromMap() {
        Map<String, Object> params = Map
                .of(
                        ConversationalIndexConstants.META_CREATED_TIME_FIELD,
                        time.toString(),
                        ConversationalIndexConstants.META_UPDATED_TIME_FIELD,
                        time.toString(),
                        ConversationalIndexConstants.META_NAME_FIELD,
                        "meta name",
                        ConversationalIndexConstants.USER_FIELD,
                        "admin"
                );
        ConversationMeta conversationMeta = ConversationMeta.fromMap("test-conversation-meta", params);
        assertEquals(conversationMeta.getId(), "test-conversation-meta");
        assertEquals(conversationMeta.getName(), "meta name");
        assertEquals(conversationMeta.getUser(), "admin");
    }

    @Test
    public void test_fromStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        conversationMeta.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        ConversationMeta meta = ConversationMeta.fromStream(streamInput);
        assertEquals(meta.getId(), conversationMeta.getId());
        assertEquals(meta.getName(), conversationMeta.getName());
        assertEquals(meta.getUser(), conversationMeta.getUser());
    }

    @Test
    public void test_ToXContent() throws IOException {
        ConversationMeta conversationMeta = new ConversationMeta("test_id", Instant.ofEpochMilli(123), Instant.ofEpochMilli(123), "test meta", "admin");
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        conversationMeta.toXContent(builder, EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        assertEquals(content, "{\"memory_id\":\"test_id\",\"create_time\":\"1970-01-01T00:00:00.123Z\",\"updated_time\":\"1970-01-01T00:00:00.123Z\",\"name\":\"test meta\",\"user\":\"admin\"}");
    }

    @Test
    public void test_toString() {
        ConversationMeta conversationMeta = new ConversationMeta("test_id", Instant.ofEpochMilli(123), Instant.ofEpochMilli(123), "test meta", "admin");
        assertEquals("{id=test_id, name=test meta, created=1970-01-01T00:00:00.123Z, updated=1970-01-01T00:00:00.123Z, user=admin}", conversationMeta.toString());
    }

    @Test
    public void test_equal() {
        ConversationMeta meta = new ConversationMeta("test_id", Instant.ofEpochMilli(123), Instant.ofEpochMilli(123), "test meta", "admin");
        assertEquals(meta.equals(conversationMeta), false);
    }
}
