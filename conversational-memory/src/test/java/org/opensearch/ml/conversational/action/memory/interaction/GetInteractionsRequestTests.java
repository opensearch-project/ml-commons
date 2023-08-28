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

public class GetInteractionsRequestTests extends OpenSearchTestCase {

    public void testConstructorsAndStreaming() throws IOException {
        GetInteractionsRequest request = new GetInteractionsRequest("test-cid");
        assert (request.validate() == null);
        assert (request.getConversationId().equals("test-cid"));
        assert (request.getFrom() == 0);
        assert (request.getMaxResults() == ActionConstants.DEFAULT_MAX_RESULTS);

        GetInteractionsRequest req2 = new GetInteractionsRequest("test-cid2", 3);
        assert (req2.validate() == null);
        assert (req2.getConversationId().equals("test-cid2"));
        assert (req2.getFrom() == 0);
        assert (req2.getMaxResults() == 3);

        GetInteractionsRequest req3 = new GetInteractionsRequest("test-cid3", 4, 5);
        assert (req3.validate() == null);
        assert (req3.getConversationId().equals("test-cid3"));
        assert (req3.getFrom() == 5);
        assert (req3.getMaxResults() == 4);

        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        request.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        GetInteractionsRequest req4 = new GetInteractionsRequest(in);
        assert (req4.validate() == null);
        assert (req4.getConversationId().equals("test-cid"));
        assert (req4.getFrom() == 0);
        assert (req4.getMaxResults() == ActionConstants.DEFAULT_MAX_RESULTS);
    }

    public void testBadValues_thenFail() {
        String nullstr = null;
        GetInteractionsRequest request = new GetInteractionsRequest(nullstr);
        assert (request.validate().validationErrors().get(0).equals("Interactions must be retrieved from a conversation"));
        assert (request.validate().validationErrors().size() == 1);

        request = new GetInteractionsRequest("cid", -2);
        assert (request.validate().validationErrors().size() == 1);
        assert (request.validate().validationErrors().get(0).equals("The number of interactions to retrieve must be positive"));

        request = new GetInteractionsRequest("cid", 2, -2);
        assert (request.validate().validationErrors().size() == 1);
        assert (request.validate().validationErrors().get(0).equals("The starting position must be nonnegative"));
    }

    public void testMultipleBadValues_thenFailMultipleWays() {
        String nullstr = null;
        GetInteractionsRequest request = new GetInteractionsRequest(nullstr, -2);
        assert (request.validate().validationErrors().size() == 2);
        assert (request.validate().validationErrors().get(0).equals("Interactions must be retrieved from a conversation"));
        assert (request.validate().validationErrors().get(1).equals("The number of interactions to retrieve must be positive"));

        request = new GetInteractionsRequest(nullstr, 3, -2);
        assert (request.validate().validationErrors().size() == 2);
        assert (request.validate().validationErrors().get(0).equals("Interactions must be retrieved from a conversation"));
        assert (request.validate().validationErrors().get(1).equals("The starting position must be nonnegative"));

        request = new GetInteractionsRequest("cid", -2, -2);
        assert (request.validate().validationErrors().size() == 2);
        assert (request.validate().validationErrors().get(0).equals("The number of interactions to retrieve must be positive"));
        assert (request.validate().validationErrors().get(1).equals("The starting position must be nonnegative"));

        request = new GetInteractionsRequest(nullstr, -3, -4);
        assert (request.validate().validationErrors().size() == 3);
        assert (request.validate().validationErrors().get(0).equals("Interactions must be retrieved from a conversation"));
        assert (request.validate().validationErrors().get(1).equals("The number of interactions to retrieve must be positive"));
        assert (request.validate().validationErrors().get(2).equals("The starting position must be nonnegative"));
    }

    public void testFromRestRequest() throws IOException {
        Map<String, String> basic = Map.of(ActionConstants.CONVERSATION_ID_FIELD, "cid1");
        Map<String, String> maxResOnly = Map
            .of(ActionConstants.CONVERSATION_ID_FIELD, "cid2", ActionConstants.REQUEST_MAX_RESULTS_FIELD, "4");
        Map<String, String> nextTokOnly = Map.of(ActionConstants.CONVERSATION_ID_FIELD, "cid3", ActionConstants.NEXT_TOKEN_FIELD, "6");
        Map<String, String> bothFields = Map
            .of(
                ActionConstants.CONVERSATION_ID_FIELD,
                "cid4",
                ActionConstants.REQUEST_MAX_RESULTS_FIELD,
                "2",
                ActionConstants.NEXT_TOKEN_FIELD,
                "7"
            );
        RestRequest req1 = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(basic).build();
        RestRequest req2 = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(maxResOnly).build();
        RestRequest req3 = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(nextTokOnly).build();
        RestRequest req4 = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(bothFields).build();
        GetInteractionsRequest gir1 = GetInteractionsRequest.fromRestRequest(req1);
        GetInteractionsRequest gir2 = GetInteractionsRequest.fromRestRequest(req2);
        GetInteractionsRequest gir3 = GetInteractionsRequest.fromRestRequest(req3);
        GetInteractionsRequest gir4 = GetInteractionsRequest.fromRestRequest(req4);

        assert (gir1.validate() == null && gir2.validate() == null && gir3.validate() == null && gir4.validate() == null);
        assert (gir1.getConversationId().equals("cid1") && gir2.getConversationId().equals("cid2"));
        assert (gir3.getConversationId().equals("cid3") && gir4.getConversationId().equals("cid4"));
        assert (gir1.getFrom() == 0 && gir2.getFrom() == 0 && gir3.getFrom() == 6 && gir4.getFrom() == 7);
        assert (gir1.getMaxResults() == ActionConstants.DEFAULT_MAX_RESULTS && gir2.getMaxResults() == 4);
        assert (gir3.getMaxResults() == ActionConstants.DEFAULT_MAX_RESULTS && gir4.getMaxResults() == 2);
    }
}
