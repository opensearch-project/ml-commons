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

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.BytesStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.test.OpenSearchTestCase;

public class DeleteConversationResponseTests extends OpenSearchTestCase {
    
    public void testDeleteConversationResponseStreaming() throws IOException {
        DeleteConversationResponse response = new DeleteConversationResponse(true);
        assert(response.wasSuccessful());
        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        response.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        DeleteConversationResponse newResp = new DeleteConversationResponse(in);
        assert(newResp.wasSuccessful());
    }

    public void testToXContent() throws IOException {
        DeleteConversationResponse response = new DeleteConversationResponse(false);
        assert(!response.wasSuccessful());
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        String expected = "{\"success\":false}";
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String result = BytesReference.bytes(builder).utf8ToString();
        assert(result.equals(expected));
    }
}
