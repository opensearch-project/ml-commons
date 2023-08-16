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

public class GetConversationsRequestTests extends OpenSearchTestCase {
    
    public void testGetConversationsRequestAndStreaming() throws IOException {
        GetConversationsRequest request = new GetConversationsRequest();
        assert(request.validate() == null);
        assert(request.getFrom() == 0 && request.getMaxResults() == ActionConstants.DEFAULT_MAX_RESULTS);
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        request.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        GetConversationsRequest newRequest = new GetConversationsRequest(in);
        assert(newRequest.validate() == null);
        assert(newRequest.getFrom() == request.getFrom() && newRequest.getMaxResults() == request.getMaxResults());
    }

    public void testVariousConstructors() {
        GetConversationsRequest req1 = new GetConversationsRequest(2);
        assert(req1.validate() == null);
        assert(req1.getFrom() == 0 && req1.getMaxResults() == 2);
        GetConversationsRequest req2 = new GetConversationsRequest(5, 2);
        assert(req2.validate() == null);
        assert(req2.getFrom() == 2 && req2.getMaxResults() == 5);
    }

    public void testNegativeOrZeroMaxResults_thenFail() {
        GetConversationsRequest req = new GetConversationsRequest(-3);
        assert(req.validate() != null);
        assert(req.validate().validationErrors().size() == 1);
        assert(req.validate().validationErrors().get(0).equals("Can't list 0 or negative conversations"));
    }

    public void testFromRestRequest() throws IOException {
        Map<String, String> maxResOnly = Map.of(ActionConstants.REQUEST_MAX_RESULTS_FIELD, "4");
        Map<String, String> nextTokOnly = Map.of(ActionConstants.NEXT_TOKEN_FIELD, "6");
        Map<String, String> bothFields = Map.of(ActionConstants.REQUEST_MAX_RESULTS_FIELD, "2",
                                                ActionConstants.NEXT_TOKEN_FIELD, "7");
        RestRequest req1 = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).build();
        RestRequest req2 = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(maxResOnly).build();
        RestRequest req3 = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(nextTokOnly).build();
        RestRequest req4 = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(bothFields).build();
        GetConversationsRequest gcr1 = GetConversationsRequest.fromRestRequest(req1);
        GetConversationsRequest gcr2 = GetConversationsRequest.fromRestRequest(req2);
        GetConversationsRequest gcr3 = GetConversationsRequest.fromRestRequest(req3);
        GetConversationsRequest gcr4 = GetConversationsRequest.fromRestRequest(req4);
        
        assert(gcr1.validate() == null && gcr2.validate() == null && gcr3.validate() == null && gcr4.validate() == null);
        assert(gcr1.getFrom() == 0 && gcr2.getFrom() == 0 && gcr3.getFrom() == 6 && gcr4.getFrom() == 7);
        assert(gcr1.getMaxResults() == ActionConstants.DEFAULT_MAX_RESULTS && gcr2.getMaxResults() == 4);
        assert(gcr3.getMaxResults() == ActionConstants.DEFAULT_MAX_RESULTS && gcr4.getMaxResults() == 2);
    }
}
