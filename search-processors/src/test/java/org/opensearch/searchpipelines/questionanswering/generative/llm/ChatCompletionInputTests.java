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
package org.opensearch.searchpipelines.questionanswering.generative.llm;

import org.opensearch.test.OpenSearchTestCase;

import java.util.Collections;
import java.util.List;

public class ChatCompletionInputTests extends OpenSearchTestCase {

    public void testCtor() {
        String model = "model";
        String question = "question";

        ChatCompletionInput input = new ChatCompletionInput(model, question, Collections.emptyList(), Collections.emptyList());

        assertNotNull(input);
    }

    public void testGettersSetters() {
        String model = "model";
        String question = "question";
        List<String> history  = List.of("hello");
        List<String> contexts = List.of("result1", "result2");
        ChatCompletionInput input = new ChatCompletionInput(model, question, history, contexts);
        assertEquals(model, input.getModel());
        assertEquals(question, input.getQuestion());
        assertEquals(history.get(0), input.getChatHistory().get(0));
        assertEquals(contexts.get(0), input.getContexts().get(0));
        assertEquals(contexts.get(1), input.getContexts().get(1));
    }
}
