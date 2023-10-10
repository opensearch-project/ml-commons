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
import java.util.Map;

import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.BytesStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;

public class GetInteractionRequestTests extends OpenSearchTestCase {

    public void testConstructorAndStreaming() throws IOException {
        GetInteractionRequest request = new GetInteractionRequest("cid", "iid");
        assert (request.validate() == null);
        assert (request.getConversationId().equals("cid"));
        assert (request.getInteractionId().equals("iid"));

        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        request.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        GetInteractionRequest newRequest = new GetInteractionRequest(in);
        assert (newRequest.validate() == null);
        assert (newRequest.getConversationId().equals("cid"));
        assert (newRequest.getInteractionId().equals("iid"));
    }

    public void testMalformedRequest_ThenInvalid() {
        GetInteractionRequest bad1 = new GetInteractionRequest(null, "iid");
        GetInteractionRequest bad2 = new GetInteractionRequest("cid", null);
        GetInteractionRequest bad3 = new GetInteractionRequest(null, null);
        ActionRequestValidationException exc1 = bad1.validate();
        ActionRequestValidationException exc2 = bad2.validate();
        ActionRequestValidationException exc3 = bad3.validate();

        assert (exc1 != null);
        assert (exc1.validationErrors().size() == 1);
        assert (exc1.validationErrors().get(0).equals("Get Interaction Request must have a conversation id"));

        assert (exc2 != null);
        assert (exc2.validationErrors().size() == 1);
        assert (exc2.validationErrors().get(0).equals("Get Interaction Request must have an interaction id"));

        assert (exc3 != null);
        assert (exc3.validationErrors().size() == 2);
        assert (exc3.validationErrors().get(0).equals("Get Interaction Request must have a conversation id"));
        assert (exc3.validationErrors().get(1).equals("Get Interaction Request must have an interaction id"));
    }

    public void testFromRestRequest() throws IOException {
        Map<String, String> params = Map
            .of(ActionConstants.CONVERSATION_ID_FIELD, "testcid", ActionConstants.RESPONSE_INTERACTION_ID_FIELD, "testiid");
        RestRequest rrequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
        GetInteractionRequest request = GetInteractionRequest.fromRestRequest(rrequest);
        assert (request.validate() == null);
        assert (request.getConversationId().equals("testcid"));
        assert (request.getInteractionId().equals("testiid"));
    }
}
