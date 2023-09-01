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

public class DeleteConversationRequestTests extends OpenSearchTestCase {

    public void testDeleteConversationRequestStreaming() throws IOException {
        DeleteConversationRequest request = new DeleteConversationRequest("test-id");
        assert (request.validate() == null);
        assert (request.getId().equals("test-id"));
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        request.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        DeleteConversationRequest newReq = new DeleteConversationRequest(in);
        assert (newReq.validate() == null);
        assert (newReq.getId().equals("test-id"));
    }

    public void testNullIdIsInvalid() {
        String nullId = null;
        DeleteConversationRequest request = new DeleteConversationRequest(nullId);
        assert (request.validate() != null);
        assert (request.validate().validationErrors().size() == 1);
        assert (request.validate().validationErrors().get(0).equals("conversation id must not be null"));
    }

    public void testFromRestRequest() throws IOException {
        RestRequest rreq = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(Map.of(ActionConstants.CONVERSATION_ID_FIELD, "deleteme"))
            .build();
        DeleteConversationRequest req = DeleteConversationRequest.fromRestRequest(rreq);
        assert (req.validate() == null);
        assert (req.getId().equals("deleteme"));
    }
}
