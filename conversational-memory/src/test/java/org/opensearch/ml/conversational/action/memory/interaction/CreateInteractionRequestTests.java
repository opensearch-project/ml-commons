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
package org.opensearch.ml.conversational.action.memory.interaction;

import java.io.IOException;
import java.util.Map;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.BytesStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.conversational.ActionConstants;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;

public class CreateInteractionRequestTests extends OpenSearchTestCase {

    public void testConstructorsAndStreaming() throws IOException {
        CreateInteractionRequest request = new CreateInteractionRequest("cid", "input", "prompt", "response", "agent", "attributes");
        assert (request.validate() == null);
        assert (request.getConversationId().equals("cid"));
        assert (request.getInput().equals("input"));
        assert (request.getPrompt().equals("prompt"));
        assert (request.getResponse().equals("response"));
        assert (request.getAgent().equals("agent"));
        assert (request.getAttributes().equals("attributes"));

        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        request.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        CreateInteractionRequest newReq = new CreateInteractionRequest(in);
        assert (newReq.validate() == null);
        assert (newReq.getConversationId().equals("cid"));
        assert (newReq.getInput().equals("input"));
        assert (newReq.getPrompt().equals("prompt"));
        assert (newReq.getResponse().equals("response"));
        assert (newReq.getAgent().equals("agent"));
        assert (newReq.getAttributes().equals("attributes"));
    }

    public void testNullCID_thenFail() {
        CreateInteractionRequest request = new CreateInteractionRequest(null, "input", "prompt", "response", "agent", "attributes");
        assert (request.validate() != null);
        assert (request.validate().validationErrors().size() == 1);
        assert (request.validate().validationErrors().get(0).equals("Interaction MUST belong to a conversation ID"));
    }

    public void testFromRestRequest() throws IOException {
        Map<String, String> params = Map
            .of(
                ActionConstants.CONVERSATION_ID_FIELD,
                "cid",
                ActionConstants.INPUT_FIELD,
                "input",
                ActionConstants.PROMPT_FIELD,
                "prompt",
                ActionConstants.AI_RESPONSE_FIELD,
                "response",
                ActionConstants.AI_AGENT_FIELD,
                "agent",
                ActionConstants.INTER_ATTRIBUTES_FIELD,
                "attributes"
            );
        RestRequest rrequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
        CreateInteractionRequest request = CreateInteractionRequest.fromRestRequest(rrequest);
        assert (request.validate() == null);
        assert (request.getConversationId().equals("cid"));
        assert (request.getInput().equals("input"));
        assert (request.getPrompt().equals("prompt"));
        assert (request.getResponse().equals("response"));
        assert (request.getAgent().equals("agent"));
        assert (request.getAttributes().equals("attributes"));
    }
}
