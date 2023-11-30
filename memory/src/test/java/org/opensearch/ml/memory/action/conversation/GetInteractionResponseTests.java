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
import java.time.Instant;
import java.util.Collections;

import org.apache.lucene.search.spell.LevenshteinDistance;
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

public class GetInteractionResponseTests extends OpenSearchTestCase {

    public void testConstructorAndStreaming() throws IOException {
        Interaction interaction = new Interaction(
            "iid",
            Instant.now(),
            "cid",
            "inp",
            "pt",
            "rsp",
            "ogn",
            Collections.singletonMap("meta", "some meta")
        );
        GetInteractionResponse response = new GetInteractionResponse(interaction);
        assert (response.getInteraction().equals(interaction));

        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        response.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        GetInteractionResponse newResponse = new GetInteractionResponse(in);
        assert (newResponse.getInteraction().equals(interaction));
    }

    public void testToXContent() throws IOException {
        Interaction interaction = new Interaction(
            "iid",
            Instant.now(),
            "cid",
            "inp",
            "pt",
            "rsp",
            "ogn",
            Collections.singletonMap("meta", "some meta")
        );
        GetInteractionResponse response = new GetInteractionResponse(interaction);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String result = BytesReference.bytes(builder).utf8ToString();
        String expected = "{\"conversation_id\":\"cid\",\"interaction_id\":\"iid\",\"create_time\":\""
            + interaction.getCreateTime()
            + "\",\"input\":\"inp\",\"prompt_template\":\"pt\",\"response\":\"rsp\",\"origin\":\"ogn\",\"additional_info\":{\"meta\":\"some meta\"}}";
        // Sometimes there's an extra trailing 0 in the time stringification, so just assert closeness
        LevenshteinDistance ld = new LevenshteinDistance();
        assert (ld.getDistance(result, expected) > 0.95);
    }
}
