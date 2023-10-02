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
package org.opensearch.searchpipelines.questionanswering.generative.prompt;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.test.OpenSearchTestCase;

public class PromptUtilTests extends OpenSearchTestCase {

    public void testPromptUtilStaticMethods() {
        assertNull(PromptUtil.getQuestionRephrasingPrompt("question", Collections.emptyList()));
    }

    public void testBuildMessageParameter() {
        String systemPrompt = "You are the best.";
        String userInstructions = null;
        String question = "Who am I";
        List<String> contexts = new ArrayList<>();
        List<Interaction> chatHistory = List
            .of(
                Interaction
                    .fromMap(
                        "convo1",
                        Map
                            .of(
                                ConversationalIndexConstants.INTERACTIONS_CREATE_TIME_FIELD,
                                Instant.now().toString(),
                                ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD,
                                "message 1",
                                ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD,
                                "answer1"
                            )
                    ),
                Interaction
                    .fromMap(
                        "convo1",
                        Map
                            .of(
                                ConversationalIndexConstants.INTERACTIONS_CREATE_TIME_FIELD,
                                Instant.now().toString(),
                                ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD,
                                "message 2",
                                ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD,
                                "answer2"
                            )
                    )
            );
        contexts.add("context 1");
        contexts.add("context 2");
        String parameter = PromptUtil.buildMessageParameter(systemPrompt, userInstructions, question, chatHistory, contexts);
        Map<String, String> parameters = Map.of("model", "foo", "messages", parameter);
        assertTrue(isJson(parameter));
    }

    private boolean isJson(String Json) {
        try {
            new JSONObject(Json);
        } catch (JSONException ex) {
            try {
                new JSONArray(Json);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }
}
