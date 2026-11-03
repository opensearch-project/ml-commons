/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.search.spell.LevenshteinDistance;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.BytesStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.test.OpenSearchTestCase;

public class GetTracesResponseTests extends OpenSearchTestCase {
    List<Interaction> traces;

    @Before
    public void setup() {
        traces = List
            .of(
                new Interaction(
                    "id0",
                    Instant.now(),
                    Instant.now(),
                    "cid",
                    "input",
                    "pt",
                    "response",
                    "origin",
                    Collections.singletonMap("metadata", "some meta"),
                    "parent_id",
                    1
                ),
                new Interaction(
                    "id1",
                    Instant.now(),
                    Instant.now(),
                    "cid",
                    "input",
                    "pt",
                    "response",
                    "origin",
                    Collections.singletonMap("metadata", "some meta"),
                    "parent_id",
                    2
                ),
                new Interaction(
                    "id2",
                    Instant.now(),
                    Instant.now(),
                    "cid",
                    "input",
                    "pt",
                    "response",
                    "origin",
                    Collections.singletonMap("metadata", "some meta"),
                    "parent_id",
                    3

                )
            );
    }

    public void testGetInteractionsResponseStreaming() throws IOException {
        GetTracesResponse response = new GetTracesResponse(traces, 4, true);
        assert (response.getTraces().equals(traces));
        assert (response.getNextToken() == 4);
        assert (response.hasMorePages());
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        response.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        GetTracesResponse newResp = new GetTracesResponse(in);
        assert (newResp.getTraces().equals(traces));
        assert (newResp.getNextToken() == 4);
        assert (newResp.hasMorePages());
    }

    public void testToXContent_MoreTokens() throws IOException {
        GetTracesResponse response = new GetTracesResponse(traces.subList(0, 1), 2, true);
        Interaction trace = response.getTraces().get(0);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String result = BytesReference.bytes(builder).utf8ToString();
        String expected = "{\"traces\":[{\"memory_id\":\"cid\",\"message_id\":\"id0\",\"create_time\":"
            + trace.getCreateTime()
            + "\"update_time\":\""
            + trace.getUpdatedTime()
            + ",\"input\":\"input\",\"prompt_template\":\"pt\",\"response\":\"response\",\"origin\":\"origin\",\"additional_info\":{\"metadata\":\"some meta\"},\"parent_message_id\":\"parent_id\",\"trace_number\":1}],\"next_token\":2}";
        // Sometimes there's an extra trailing 0 in the time stringification, so just assert closeness
        LevenshteinDistance ld = new LevenshteinDistance();
        assert (ld.getDistance(result, expected) > 0.95);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_NullTraces() {
        GetTracesResponse response = new GetTracesResponse(null, 0, false);
    }
}
