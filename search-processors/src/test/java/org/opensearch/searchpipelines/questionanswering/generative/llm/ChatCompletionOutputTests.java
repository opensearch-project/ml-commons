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

import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.test.OpenSearchTestCase;

public class ChatCompletionOutputTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    public void testCtor() {
        ChatCompletionOutput output = new ChatCompletionOutput(List.of("answer"), null);
        assertNotNull(output);
    }

    public void testGettersSetters() {
        String answer = "answer";
        ChatCompletionOutput output = new ChatCompletionOutput(List.of(answer), null);
        assertEquals(answer, (String) output.getAnswers().get(0));
    }

    public void testIllegalArgument1() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("answers and errors can't both be null.");
        new ChatCompletionOutput(null, null);
    }

    public void testIllegalArgument2() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("If answers is not provided, one or more errors must be provided.");
        new ChatCompletionOutput(null, new ArrayList<>());
    }

    public void testIllegalArgument3() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("If errors is not provided, one or more answers must be provided.");
        new ChatCompletionOutput(new ArrayList<>(), null);
    }
}
