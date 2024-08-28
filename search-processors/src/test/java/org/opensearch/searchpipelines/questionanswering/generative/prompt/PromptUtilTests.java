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
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.searchpipelines.questionanswering.generative.llm.Llm;
import org.opensearch.searchpipelines.questionanswering.generative.llm.MessageBlock;
import org.opensearch.test.OpenSearchTestCase;

public class PromptUtilTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

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
        String parameter = PromptUtil
            .buildMessageParameter(Llm.ModelProvider.BEDROCK_CONVERSE, systemPrompt, userInstructions, question, chatHistory, contexts);
        Map<String, String> parameters = Map.of("model", "foo", "messages", parameter);
        assertTrue(isJson(parameter));
    }

    public void testBuildMessageParameterForOpenAI() {
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
        String parameter = PromptUtil
            .buildMessageParameter(Llm.ModelProvider.OPENAI, systemPrompt, userInstructions, question, chatHistory, contexts);
        Map<String, String> parameters = Map.of("model", "foo", "messages", parameter);
        assertTrue(isJson(parameter));
    }

    public void testBuildBedrockInputParameter() {
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
        String parameter = PromptUtil.buildSingleStringPrompt(systemPrompt, userInstructions, question, chatHistory, contexts);
        assertTrue(parameter.contains(systemPrompt));
    }

    public void testBuildBedrockConverseInputParameter() {
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
        MessageBlock.TextBlock tb = new MessageBlock.TextBlock("text");
        MessageBlock.ImageBlock ib = new MessageBlock.ImageBlock("jpeg", "data", null);
        MessageBlock.DocumentBlock db = new MessageBlock.DocumentBlock("pdf", "file1", "data");
        List<MessageBlock.AbstractBlock> blocks = List.of(tb, ib, db);
        MessageBlock mb = new MessageBlock();
        mb.setBlockList(blocks);
        List<MessageBlock> llmMessages = List.of(mb);
        String parameter = PromptUtil
            .buildMessageParameter(
                Llm.ModelProvider.BEDROCK_CONVERSE,
                systemPrompt,
                userInstructions,
                question,
                chatHistory,
                contexts,
                llmMessages
            );
        assertTrue(parameter.contains(systemPrompt));
    }

    public void testBuildOpenAIInputParameter() {
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
        MessageBlock.TextBlock tb = new MessageBlock.TextBlock("text");
        MessageBlock.ImageBlock ib = new MessageBlock.ImageBlock("jpeg", "data", null);
        MessageBlock.ImageBlock ib2 = new MessageBlock.ImageBlock("jpeg", null, "https://xyz/foo.jpg");
        List<MessageBlock.AbstractBlock> blocks = List.of(tb, ib, ib2);
        MessageBlock mb = new MessageBlock();
        mb.setBlockList(blocks);
        List<MessageBlock> llmMessages = List.of(mb);
        String parameter = PromptUtil
            .buildMessageParameter(Llm.ModelProvider.OPENAI, systemPrompt, userInstructions, question, chatHistory, contexts, llmMessages);
        assertTrue(parameter.contains(systemPrompt));
    }

    public void testGetPromptTemplate() {
        String systemPrompt = "you are a helpful assistant.";
        String userInstructions = "lay out your answer as a sequence of steps.";
        String actual = PromptUtil.getPromptTemplate(systemPrompt, userInstructions);
        assertTrue(actual.contains(systemPrompt));
        assertTrue(actual.contains(userInstructions));
    }

    public void testMessageCtor() {
        PromptUtil.Message message = new PromptUtil.Message(PromptUtil.ChatRole.USER, new PromptUtil.OpenAIContent());
        assertEquals(message.getChatRole(), PromptUtil.ChatRole.USER);
    }

    public void testBedrockContentCtor() {
        PromptUtil.Content content = new PromptUtil.BedrockContent("text", "foo");
        assertTrue(content.toJson().toString().contains("foo"));
    }

    public void testMessageArrayBuilderCtor1() {
        exceptionRule.expect(IllegalArgumentException.class);
        PromptUtil.MessageArrayBuilder builder = new PromptUtil.MessageArrayBuilder(Llm.ModelProvider.COHERE);
    }

    public void testMessageArrayBuilderInvalidUsage1() {
        exceptionRule.expect(RuntimeException.class);
        PromptUtil.MessageArrayBuilder builder = new PromptUtil.MessageArrayBuilder(Llm.ModelProvider.OPENAI);
        builder.addTextContent("boom");
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
