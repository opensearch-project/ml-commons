/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.META_NAME_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.META_UPDATED_TIME_FIELD;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.BytesStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchModule;

public class UpdateConversationRequestTests {
    Map<String, Object> updateContent = new HashMap<>();

    @Before
    public void setUp() {
        updateContent.put(META_NAME_FIELD, "new name");
    }

    @Test
    public void testConstructor() throws IOException {
        UpdateConversationRequest updateConversationRequest = new UpdateConversationRequest("conversationId", updateContent);
        assert (updateConversationRequest.validate() == null);
        assert (updateConversationRequest.getConversationId().equals("conversationId"));
        assert (updateConversationRequest.getUpdateContent().size() == 1);
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        updateConversationRequest.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        UpdateConversationRequest newRequest = new UpdateConversationRequest(in);
        assert updateConversationRequest.getConversationId().equals(newRequest.getConversationId());
        assert updateConversationRequest.getUpdateContent().equals(newRequest.getUpdateContent());
    }

    @Test
    public void testConstructor_UpdateContentNotAllowed() throws IOException {
        Map<String, Object> updateCont = new HashMap<>();
        updateCont.put(META_UPDATED_TIME_FIELD, Instant.ofEpochMilli(123));
        UpdateConversationRequest updateConversationRequest = new UpdateConversationRequest("conversationId", updateCont);
        assert (updateConversationRequest.validate() == null);
        assert (updateConversationRequest.getConversationId().equals("conversationId"));
        assert (updateConversationRequest.getUpdateContent().size() == 0);
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        updateConversationRequest.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        UpdateConversationRequest newRequest = new UpdateConversationRequest(in);
        assert updateConversationRequest.getConversationId().equals(newRequest.getConversationId());
        assert updateConversationRequest.getUpdateContent().equals(newRequest.getUpdateContent());
        assert (newRequest.getUpdateContent().size() == 0);
    }

    @Test
    public void testConstructor_NullConversationId() throws IOException {
        UpdateConversationRequest updateConversationRequest = new UpdateConversationRequest(null, updateContent);
        assert updateConversationRequest.validate().getMessage().equals("Validation Failed: 1: conversation id can't be null;");
    }

    @Test
    public void testConstructor_NullUpdateContent() throws IOException {
        UpdateConversationRequest updateConversationRequest = new UpdateConversationRequest(null, null);
        assert updateConversationRequest.validate().getMessage().equals("Validation Failed: 1: conversation id can't be null;");
    }

    @Test
    public void testParse_Success() throws IOException {
        String jsonStr = "{\"name\":\"new name\",\"application_type\":\"new type\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        UpdateConversationRequest updateConversationRequest = UpdateConversationRequest.parse(parser, "conversationId");
        assertEquals(updateConversationRequest.getConversationId(), "conversationId");
        assertEquals("new name", updateConversationRequest.getUpdateContent().get("name"));
    }

    @Test
    public void fromActionRequest_Success() {
        UpdateConversationRequest updateConversationRequest = UpdateConversationRequest
            .builder()
            .conversationId("conversationId")
            .updateContent(updateContent)
            .build();
        assertSame(UpdateConversationRequest.fromActionRequest(updateConversationRequest), updateConversationRequest);
    }

    @Test
    public void fromActionRequest_Success_fromActionRequest() {
        UpdateConversationRequest updateConversationRequest = UpdateConversationRequest
            .builder()
            .conversationId("conversationId")
            .updateContent(updateContent)
            .build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                updateConversationRequest.writeTo(out);
            }
        };
        UpdateConversationRequest request = UpdateConversationRequest.fromActionRequest(actionRequest);
        assertNotSame(request, updateConversationRequest);
        assertEquals(updateConversationRequest.getConversationId(), request.getConversationId());
        assertEquals(updateConversationRequest.getUpdateContent(), request.getUpdateContent());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        UpdateConversationRequest.fromActionRequest(actionRequest);
    }
}
