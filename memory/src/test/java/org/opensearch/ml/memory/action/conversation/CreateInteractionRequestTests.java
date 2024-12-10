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
import java.util.Collections;
import java.util.HashMap;
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
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;

import com.google.gson.Gson;

public class CreateInteractionRequestTests extends OpenSearchTestCase {

    Gson gson;

    @Before
    public void setup() {
        gson = new Gson();
    }

    public void testConstructorsAndStreaming() throws IOException {
        CreateInteractionRequest request = new CreateInteractionRequest(
            "cid",
            "input",
            "pt",
            "response",
            "origin",
            Collections.singletonMap("metadata", "some meta")
        );
        assert (request.validate() == null);
        assert (request.getConversationId().equals("cid"));
        assert (request.getInput().equals("input"));
        assert (request.getResponse().equals("response"));
        assert (request.getOrigin().equals("origin"));

        BytesStreamOutput outbytes = new BytesStreamOutput();
        StreamOutput osso = new OutputStreamStreamOutput(outbytes);
        request.writeTo(osso);
        StreamInput in = new BytesStreamInput(BytesReference.toBytes(outbytes.bytes()));
        CreateInteractionRequest newReq = new CreateInteractionRequest(in);
        assert (newReq.validate() == null);
        assert (newReq.getConversationId().equals("cid"));
        assert (newReq.getInput().equals("input"));
        assert (newReq.getResponse().equals("response"));
        assert (newReq.getOrigin().equals("origin"));
    }

    public void testNullCID_thenFail() {
        CreateInteractionRequest request = new CreateInteractionRequest(
            null,
            "input",
            "pt",
            "response",
            "origin",
            Collections.singletonMap("metadata", "some meta")
        );
        assert (request.validate() != null);
        assert (request.validate().validationErrors().size() == 1);
        assert (request.validate().validationErrors().get(0).equals("Interaction MUST belong to a conversation ID"));
    }

    public void testFromRestRequest() throws IOException {
        Map<String, Object> params = Map
            .of(
                ActionConstants.INPUT_FIELD,
                "input",
                ActionConstants.PROMPT_TEMPLATE_FIELD,
                "pt",
                ActionConstants.AI_RESPONSE_FIELD,
                "response",
                ActionConstants.RESPONSE_ORIGIN_FIELD,
                "origin",
                ActionConstants.ADDITIONAL_INFO_FIELD,
                Collections.singletonMap("metadata", "some meta")
            );

        RestRequest rrequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(Map.of(ActionConstants.MEMORY_ID, "cid"))
            .withContent(new BytesArray(gson.toJson(params)), MediaTypeRegistry.JSON)
            .build();
        CreateInteractionRequest request = CreateInteractionRequest.fromRestRequest(rrequest);

        assert (request.validate() == null);
        assert (request.getConversationId().equals("cid"));
        assert (request.getInput().equals("input"));
        assert (request.getPromptTemplate().equals("pt"));
        assert (request.getResponse().equals("response"));
        assert (request.getOrigin().equals("origin"));
        assert (request.getAdditionalInfo().equals(Collections.singletonMap("metadata", "some meta")));
    }

    public void testFromRestRequest_Trace() throws IOException {
        Map<String, Object> params = Map
            .of(
                ActionConstants.INPUT_FIELD,
                "input",
                ActionConstants.PROMPT_TEMPLATE_FIELD,
                "pt",
                ActionConstants.AI_RESPONSE_FIELD,
                "response",
                ActionConstants.RESPONSE_ORIGIN_FIELD,
                "origin",
                ActionConstants.ADDITIONAL_INFO_FIELD,
                Collections.singletonMap("metadata", "some meta"),
                ActionConstants.PARENT_INTERACTION_ID_FIELD,
                "parentId",
                ActionConstants.TRACE_NUMBER_FIELD,
                1
            );

        RestRequest rrequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(Map.of(ActionConstants.MEMORY_ID, "tid"))
            .withContent(new BytesArray(gson.toJson(params)), MediaTypeRegistry.JSON)
            .build();
        CreateInteractionRequest request = CreateInteractionRequest.fromRestRequest(rrequest);

        assert (request.validate() == null);
        assert (request.getConversationId().equals("tid"));
        assert (request.getInput().equals("input"));
        assert (request.getPromptTemplate().equals("pt"));
        assert (request.getResponse().equals("response"));
        assert (request.getOrigin().equals("origin"));
        assert (request.getAdditionalInfo().equals(Collections.singletonMap("metadata", "some meta")));
        assert (request.getParentIid().equals("parentId"));
        assert (request.getTraceNumber().equals(1));
    }

    public void testFromRestRequest_UnknownFields_ThenFail() throws IOException {
        Map<String, Object> params = Map
            .of(
                ActionConstants.INPUT_FIELD,
                "input",
                ActionConstants.PROMPT_TEMPLATE_FIELD,
                "pt",
                ActionConstants.AI_RESPONSE_FIELD,
                "response",
                ActionConstants.RESPONSE_ORIGIN_FIELD,
                "origin",
                ActionConstants.ADDITIONAL_INFO_FIELD,
                Collections.singletonMap("metadata", "some meta"),
                "unknown_field",
                "some value"
            );

        RestRequest rrequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(Map.of(ActionConstants.MEMORY_ID, "cid"))
            .withContent(new BytesArray(gson.toJson(params)), MediaTypeRegistry.JSON)
            .build();

        try {
            CreateInteractionRequest request = CreateInteractionRequest.fromRestRequest(rrequest);
            fail("Expected IllegalArgumentException due to unknown field");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Invalid field [unknown_field] found in request body");
        } catch (Exception e) {
            fail("Expected IllegalArgumentException due to unknown field, got " + e.getClass().getName());
        }
    }

    public void testFromRestRequest_AllFieldsEmpty_ThenFail() throws IOException {
        Map<String, Object> params = new HashMap<>();

        params.put(ActionConstants.INPUT_FIELD, "");
        params.put(ActionConstants.PROMPT_TEMPLATE_FIELD, null);
        params.put(ActionConstants.AI_RESPONSE_FIELD, " ");
        params.put(ActionConstants.RESPONSE_ORIGIN_FIELD, null);
        params.put(ActionConstants.ADDITIONAL_INFO_FIELD, Collections.emptyMap());

        RestRequest rrequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(Map.of(ActionConstants.MEMORY_ID, "cid"))
            .withContent(new BytesArray(gson.toJson(params)), MediaTypeRegistry.JSON)
            .build();

        try {
            CreateInteractionRequest request = CreateInteractionRequest.fromRestRequest(rrequest);
            fail("Expected IllegalArgumentException due to all fields empty");
        } catch (IllegalArgumentException e) {
            assertEquals(
                e.getMessage(),
                "At least one of the following parameters must be non-empty: input, prompt_template, response, origin, additional_info"
            );
        } catch (Exception e) {
            fail("Expected IllegalArgumentException due to all fields empty, got " + e.getClass().getName());
        }
    }

}
