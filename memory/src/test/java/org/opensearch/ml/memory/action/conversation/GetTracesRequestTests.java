/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
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

public class GetTracesRequestTests extends OpenSearchTestCase {

    public void testConstructorsAndStreaming() throws IOException {
        GetTracesRequest request = new GetTracesRequest("test-iid");
        assert (request.validate() == null);
        assert (request.getInteractionId().equals("test-iid"));
        assert (request.getFrom() == 0);
        assert (request.getMaxResults() == ActionConstants.DEFAULT_MAX_RESULTS);

        GetTracesRequest req2 = new GetTracesRequest("test-iid2", 3);
        assert (req2.validate() == null);
        assert (req2.getInteractionId().equals("test-iid2"));
        assert (req2.getFrom() == 0);
        assert (req2.getMaxResults() == 3);

        GetTracesRequest req3 = new GetTracesRequest("test-iid3", 4, 5);
        assert (req3.validate() == null);
        assert (req3.getInteractionId().equals("test-iid3"));
        assert (req3.getFrom() == 5);
        assert (req3.getMaxResults() == 4);

        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        request.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        GetTracesRequest req4 = new GetTracesRequest(in);
        assert (req4.validate() == null);
        assert (req4.getInteractionId().equals("test-iid"));
        assert (req4.getFrom() == 0);
        assert (req4.getMaxResults() == ActionConstants.DEFAULT_MAX_RESULTS);
    }

    public void testBadValues_thenFail() {
        String nullstr = null;
        GetTracesRequest request = new GetTracesRequest(nullstr);
        assert (request.validate().validationErrors().get(0).equals("Traces must be retrieved from an interaction"));
        assert (request.validate().validationErrors().size() == 1);

        request = new GetTracesRequest("iid", -2);
        assert (request.validate().validationErrors().size() == 1);
        assert (request.validate().validationErrors().get(0).equals("The number of traces to retrieve must be positive"));

        request = new GetTracesRequest("iid", 2, -2);
        assert (request.validate().validationErrors().size() == 1);
        assert (request.validate().validationErrors().get(0).equals("The starting position must be nonnegative"));
    }

    public void testFromRestRequest() throws IOException {
        Map<String, String> basic = Map.of(ActionConstants.RESPONSE_INTERACTION_ID_FIELD, "iid1");
        Map<String, String> maxResOnly = Map
            .of(ActionConstants.RESPONSE_INTERACTION_ID_FIELD, "iid2", ActionConstants.REQUEST_MAX_RESULTS_FIELD, "4");
        Map<String, String> nextTokOnly = Map
            .of(ActionConstants.RESPONSE_INTERACTION_ID_FIELD, "iid3", ActionConstants.NEXT_TOKEN_FIELD, "6");
        Map<String, String> bothFields = Map
            .of(
                ActionConstants.RESPONSE_INTERACTION_ID_FIELD,
                "iid4",
                ActionConstants.REQUEST_MAX_RESULTS_FIELD,
                "2",
                ActionConstants.NEXT_TOKEN_FIELD,
                "7"
            );
        RestRequest req1 = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(basic).build();
        RestRequest req2 = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(maxResOnly).build();
        RestRequest req3 = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(nextTokOnly).build();
        RestRequest req4 = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(bothFields).build();
        GetTracesRequest gir1 = GetTracesRequest.fromRestRequest(req1);
        GetTracesRequest gir2 = GetTracesRequest.fromRestRequest(req2);
        GetTracesRequest gir3 = GetTracesRequest.fromRestRequest(req3);
        GetTracesRequest gir4 = GetTracesRequest.fromRestRequest(req4);

        assert (gir1.validate() == null && gir2.validate() == null && gir3.validate() == null && gir4.validate() == null);
        assert (gir1.getInteractionId().equals("iid1") && gir2.getInteractionId().equals("iid2"));
        assert (gir3.getInteractionId().equals("iid3") && gir4.getInteractionId().equals("iid4"));
        assert (gir1.getFrom() == 0 && gir2.getFrom() == 0 && gir3.getFrom() == 6 && gir4.getFrom() == 7);
        assert (gir1.getMaxResults() == ActionConstants.DEFAULT_MAX_RESULTS && gir2.getMaxResults() == 4);
        assert (gir3.getMaxResults() == ActionConstants.DEFAULT_MAX_RESULTS && gir4.getMaxResults() == 2);
    }
}
