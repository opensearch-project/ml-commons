/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_ADDITIONAL_INFO_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD;

import java.io.IOException;
import java.io.UncheckedIOException;
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

public class UpdateInteractionRequestTests {

    Map<String, Object> updateContent = new HashMap<>();

    @Before
    public void setUp() {
        updateContent.put(INTERACTIONS_ADDITIONAL_INFO_FIELD, Map.of("feedback", "thumbs up!"));
    }

    @Test
    public void testConstructor() throws IOException {
        UpdateInteractionRequest updateInteractionRequest = new UpdateInteractionRequest("interaction_id", updateContent);
        assert updateInteractionRequest.validate() == null;
        assert updateInteractionRequest.getInteractionId().equals("interaction_id");
        assert updateInteractionRequest.getUpdateContent().size() == 1;
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        updateInteractionRequest.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        UpdateInteractionRequest newRequest = new UpdateInteractionRequest(in);
        assert updateInteractionRequest.getInteractionId().equals(newRequest.getInteractionId());
        assert updateInteractionRequest.getUpdateContent().equals(newRequest.getUpdateContent());
    }

    @Test
    public void testConstructor_UpdateContentNotAllowed() throws IOException {
        updateContent.put(INTERACTIONS_RESPONSE_FIELD, "response");
        UpdateInteractionRequest updateInteractionRequest = new UpdateInteractionRequest("interaction_id", updateContent);
        assert (updateInteractionRequest.validate() == null);
        assert (updateInteractionRequest.getInteractionId().equals("interaction_id"));
        assert (updateInteractionRequest.getUpdateContent().size() == 1);
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        updateInteractionRequest.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        UpdateInteractionRequest newRequest = new UpdateInteractionRequest(in);
        assert updateInteractionRequest.getInteractionId().equals(newRequest.getInteractionId());
        assert updateInteractionRequest.getUpdateContent().equals(newRequest.getUpdateContent());
        assert (newRequest.getUpdateContent().size() == 1);
    }

    @Test
    public void testConstructor_NullInteractionId() throws IOException {
        UpdateInteractionRequest updateInteractionRequest = new UpdateInteractionRequest(null, updateContent);
        assert updateInteractionRequest.validate().getMessage().equals("Validation Failed: 1: interaction id can't be null;");
    }

    @Test
    public void testConstructor_NullUpdateContent() throws IOException {
        UpdateInteractionRequest updateInteractionRequest = new UpdateInteractionRequest(null, null);
        assert updateInteractionRequest.validate().getMessage().equals("Validation Failed: 1: interaction id can't be null;");
    }

    @Test
    public void testParse_Success() throws IOException {
        String jsonStr = "{\"additional_info\": {\n" + "      \"feedback\": \"thumbs up!\"\n" + "    }}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        UpdateInteractionRequest updateInteractionRequest = UpdateInteractionRequest.parse(parser, "interaction_id");
        assertEquals(updateInteractionRequest.getInteractionId(), "interaction_id");
        assertEquals(Map.of("feedback", "thumbs up!"), updateInteractionRequest.getUpdateContent().get(INTERACTIONS_ADDITIONAL_INFO_FIELD));
    }

    @Test
    public void testParse_UpdateContentNotAllowed() throws IOException {
        String jsonStr = "{\"response\": \"new response!\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        UpdateInteractionRequest updateInteractionRequest = UpdateInteractionRequest.parse(parser, "interaction_id");
        assertEquals(updateInteractionRequest.getInteractionId(), "interaction_id");
        assertEquals(0, updateInteractionRequest.getUpdateContent().size());
        assertNotEquals(null, updateInteractionRequest.getUpdateContent());
    }

    @Test
    public void fromActionRequest_Success() {
        UpdateInteractionRequest updateInteractionRequest = UpdateInteractionRequest
            .builder()
            .interactionId("interaction_id")
            .updateContent(updateContent)
            .build();
        assertSame(UpdateInteractionRequest.fromActionRequest(updateInteractionRequest), updateInteractionRequest);
    }

    @Test
    public void fromActionRequest_Success_fromActionRequest() {
        UpdateInteractionRequest updateInteractionRequest = UpdateInteractionRequest
            .builder()
            .interactionId("interaction_id")
            .updateContent(updateContent)
            .build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                updateInteractionRequest.writeTo(out);
            }
        };
        UpdateInteractionRequest request = UpdateInteractionRequest.fromActionRequest(actionRequest);
        assertNotSame(request, updateInteractionRequest);
        assertEquals(updateInteractionRequest.getInteractionId(), request.getInteractionId());
        assertEquals(updateInteractionRequest.getUpdateContent(), request.getUpdateContent());
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
        UpdateInteractionRequest.fromActionRequest(actionRequest);
    }
}
