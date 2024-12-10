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

import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.APPLICATION_TYPE_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.META_ADDITIONAL_INFO_FIELD;

import java.io.IOException;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.OpenSearchParseException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.BytesStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;

import com.google.gson.Gson;

public class CreateConversationRequestTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    Gson gson;

    @Before
    public void setup() {
        gson = new Gson();
    }

    public void testConstructorsAndStreaming_Named() throws IOException {
        CreateConversationRequest request = new CreateConversationRequest("test-name");
        assert (request.validate() == null);
        assert (request.getName().equals("test-name"));
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        request.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        CreateConversationRequest newRequest = new CreateConversationRequest(in);
        assert (newRequest.getName().equals(request.getName()));
    }

    public void testConstructorsAndStreaming_Unnamed() throws IOException {
        CreateConversationRequest request = new CreateConversationRequest();
        assert (request.validate() == null);
        assert (request.getName() == null);
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        request.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        CreateConversationRequest newRequest = new CreateConversationRequest(in);
        assert (newRequest.getName() == null);
    }

    public void testEmptyRestRequest() throws IOException {
        RestRequest req = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).build();
        CreateConversationRequest request = CreateConversationRequest.fromRestRequest(req);
        assert (request.getName() == null);
    }

    public void testNamedRestRequest() throws IOException {
        String name = "test-name";
        RestRequest req = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withContent(new BytesArray(gson.toJson(Map.of(ActionConstants.REQUEST_CONVERSATION_NAME_FIELD, name))), MediaTypeRegistry.JSON)
            .build();
        CreateConversationRequest request = CreateConversationRequest.fromRestRequest(req);
        assert (request.getName().equals(name));
    }

    public void testNamedRestRequest_WithAppType() throws IOException {
        String name = "test-name";
        String appType = "conversational-search";
        RestRequest req = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withContent(
                new BytesArray(gson.toJson(Map.of(ActionConstants.REQUEST_CONVERSATION_NAME_FIELD, name, APPLICATION_TYPE_FIELD, appType))),
                MediaTypeRegistry.JSON
            )
            .build();
        CreateConversationRequest request = CreateConversationRequest.fromRestRequest(req);
        assert (request.getName().equals(name));
        assert (request.getApplicationType().equals(appType));
    }

    public void testRestRequest_NullName() throws IOException {
        RestRequest req = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withContent(new BytesArray("{\"name\":null}"), MediaTypeRegistry.JSON)
            .build();
        CreateConversationRequest request = CreateConversationRequest.fromRestRequest(req);
        Assert.assertNull(request.getName());
    }

    public void testRestRequest_WithAdditionalInfo() throws IOException {
        String name = "test-name";
        Map<String, Object> additionalInfo = Map.of("key1", "value1", "key2", 123);
        RestRequest req = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withContent(
                new BytesArray(
                    gson.toJson(Map.of(ActionConstants.REQUEST_CONVERSATION_NAME_FIELD, name, META_ADDITIONAL_INFO_FIELD, additionalInfo))
                ),
                MediaTypeRegistry.JSON
            )
            .build();
        CreateConversationRequest request = CreateConversationRequest.fromRestRequest(req);
        assert (request.getName().equals(name));
        Assert.assertNull(request.getApplicationType());
        Assert.assertEquals("value1", request.getAdditionalInfos().get("key1"));
        Assert.assertEquals(123, request.getAdditionalInfos().get("key2"));
    }

    public void testRestRequest_UnknownFields_ThenFail() throws IOException {
        String name = "test-name";
        RestRequest req = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withContent(
                new BytesArray(gson.toJson(Map.of(ActionConstants.REQUEST_CONVERSATION_NAME_FIELD, name, "unknown_field", "some value"))),
                MediaTypeRegistry.JSON
            )
            .build();

        try {
            CreateConversationRequest request = CreateConversationRequest.fromRestRequest(req);
            fail("Expected IllegalArgumentException due to unknown field");
        } catch (OpenSearchParseException e) {
            assertEquals(e.getMessage(), "Invalid field [unknown_field] found in request body");
        } catch (Exception e) {
            fail("Expected OpenSearchParseException due to unknown field, got " + e.getClass().getName());
        }

    }
}
