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
package org.opensearch.ml.conversational.action.memory.conversation;

import java.io.IOException;
import java.util.Map;

import org.junit.Before;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.BytesStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.conversational.ActionConstants;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;

import com.google.gson.Gson;

public class CreateConversationRequestTests extends OpenSearchTestCase {

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

}
