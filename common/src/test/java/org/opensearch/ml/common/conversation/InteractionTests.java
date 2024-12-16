/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.conversation;

import static org.junit.Assert.assertEquals;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.search.SearchHit;

public class InteractionTests {

    Interaction interaction;
    Instant time;

    @Before
    public void setUp() {
        time = Instant.ofEpochMilli(123);
        interaction = Interaction
            .builder()
            .id("test-interaction-id")
            .createTime(time)
            .conversationId("conversation-id")
            .input("sample inputs")
            .promptTemplate("some prompt template")
            .response("sample responses")
            .origin("amazon bedrock")
            .additionalInfo(Collections.singletonMap("suggestion", "new suggestion"))
            .parentInteractionId("parent id")
            .traceNum(1)
            .build();
    }

    @Test
    public void test_fromMap() {
        Map<String, Object> params = Map
            .of(
                ConversationalIndexConstants.INTERACTIONS_CREATE_TIME_FIELD,
                time.toString(),
                ConversationalIndexConstants.INTERACTIONS_CONVERSATION_ID_FIELD,
                "conversation-id",
                ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD,
                "sample inputs",
                ConversationalIndexConstants.INTERACTIONS_PROMPT_TEMPLATE_FIELD,
                "some prompt template",
                ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD,
                "sample responses",
                ConversationalIndexConstants.INTERACTIONS_ORIGIN_FIELD,
                "amazon bedrock",
                ConversationalIndexConstants.INTERACTIONS_ADDITIONAL_INFO_FIELD,
                Collections.singletonMap("suggestion", "new suggestion"),
                ConversationalIndexConstants.PARENT_INTERACTIONS_ID_FIELD,
                "parent id",
                ConversationalIndexConstants.INTERACTIONS_TRACE_NUMBER_FIELD,
                1
            );
        Interaction interaction = Interaction.fromMap("test-interaction-id", params);
        assertEquals(interaction.getId(), "test-interaction-id");
        assertEquals(interaction.getCreateTime(), time);
        assertEquals(interaction.getInput(), "sample inputs");
        assertEquals(interaction.getPromptTemplate(), "some prompt template");
        assertEquals(interaction.getResponse(), "sample responses");
        assertEquals(interaction.getOrigin(), "amazon bedrock");
        assertEquals(interaction.getAdditionalInfo(), Collections.singletonMap("suggestion", "new suggestion"));
        assertEquals(interaction.getParentInteractionId(), "parent id");
        assertEquals(interaction.getTraceNum().toString(), "1");
    }

    @Test
    public void test_fromSearchHit() throws IOException {
        XContentBuilder content = XContentBuilder.builder(XContentType.JSON.xContent());
        content.startObject();
        content.field(ConversationalIndexConstants.INTERACTIONS_CREATE_TIME_FIELD, time);
        content.field(ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD, "sample inputs");
        content.field(ConversationalIndexConstants.INTERACTIONS_CONVERSATION_ID_FIELD, "conversation-id");
        content.endObject();

        SearchHit[] hits = new SearchHit[1];
        hits[0] = new SearchHit(0, "iId", null, null).sourceRef(BytesReference.bytes(content));

        Interaction interaction = Interaction.fromSearchHit(hits[0]);
        assertEquals(interaction.getId(), "iId");
        assertEquals(interaction.getCreateTime(), time);
        assertEquals(interaction.getInput(), "sample inputs");
    }

    @Test
    public void test_fromStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        interaction.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        Interaction interaction1 = Interaction.fromStream(streamInput);
        assertEquals(interaction1.getId(), interaction.getId());
        assertEquals(interaction1.getParentInteractionId(), interaction.getParentInteractionId());
        assertEquals(interaction1.getResponse(), interaction.getResponse());
        assertEquals(interaction1.getOrigin(), interaction.getOrigin());
        assertEquals(interaction1.getPromptTemplate(), interaction.getPromptTemplate());
        assertEquals(interaction1.getAdditionalInfo(), interaction.getAdditionalInfo());
        assertEquals(interaction1.getTraceNum(), interaction.getTraceNum());
        assertEquals(interaction1.getConversationId(), interaction.getConversationId());
    }

    @Test
    public void test_ToXContent() throws IOException {
        Interaction interaction = Interaction
            .builder()
            .conversationId("conversation id")
            .origin("amazon bedrock")
            .parentInteractionId("parant id")
            .additionalInfo(Collections.singletonMap("suggestion", "new suggestion"))
            .response("sample response")
            .traceNum(1)
            .build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        interaction.toXContent(builder, EMPTY_PARAMS);
        String interactionContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"memory_id\":\"conversation id\",\"message_id\":null,\"create_time\":null,\"response\":\"sample response\",\"origin\":\"amazon bedrock\",\"additional_info\":{\"suggestion\":\"new suggestion\"},\"parent_message_id\":\"parant id\",\"trace_number\":1}",
            interactionContent
        );
    }

    @Test
    public void test_not_equal() {
        Interaction interaction1 = Interaction
            .builder()
            .id("id")
            .conversationId("conversation id")
            .origin("amazon bedrock")
            .parentInteractionId("parent id")
            .additionalInfo(Collections.singletonMap("suggestion", "new suggestion"))
            .traceNum(1)
            .build();
        assertEquals(interaction.equals(interaction1), false);
    }

    @Test
    public void test_Equal() {
        Interaction interaction1 = Interaction
            .builder()
            .id("test-interaction-id")
            .createTime(time)
            .conversationId("conversation-id")
            .input("sample inputs")
            .promptTemplate("some prompt template")
            .response("sample responses")
            .origin("amazon bedrock")
            .additionalInfo(Collections.singletonMap("suggestion", "new suggestion"))
            .parentInteractionId("parent id")
            .traceNum(1)
            .build();
        assertEquals(interaction.equals(interaction1), true);
    }

    @Test
    public void test_toString() {
        Interaction interaction1 = Interaction
            .builder()
            .id("id")
            .conversationId("conversation id")
            .origin("amazon bedrock")
            .parentInteractionId("parent id")
            .additionalInfo(Collections.singletonMap("suggestion", "new suggestion"))
            .traceNum(1)
            .build();
        assertEquals(
            "Interaction{id=id,cid=conversation id,create_time=null,origin=amazon bedrock,input=null,promt_template=null,response=null,additional_info={suggestion=new suggestion},parentInteractionId=parent id,traceNum=1}",
            interaction1.toString()
        );
    }

    @Test
    public void test_ParentInteraction() {
        Interaction parentInteraction = Interaction
            .builder()
            .id("test-interaction-id")
            .createTime(time)
            .conversationId("conversation-id")
            .input("sample inputs")
            .promptTemplate("some prompt template")
            .response("sample responses")
            .origin("amazon bedrock")
            .additionalInfo(Collections.singletonMap("suggestion", "new suggestion"))
            .build();
        assertEquals(
            "Interaction{id=test-interaction-id,cid=conversation-id,create_time=1970-01-01T00:00:00.123Z,origin=amazon bedrock,input=sample inputs,promt_template=some prompt template,response=sample responses,additional_info={suggestion=new suggestion},parentInteractionId=null,traceNum=null}",
            parentInteraction.toString()
        );
    }
}
