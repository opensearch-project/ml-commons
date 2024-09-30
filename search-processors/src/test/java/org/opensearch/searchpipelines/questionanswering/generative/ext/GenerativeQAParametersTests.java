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
package org.opensearch.searchpipelines.questionanswering.generative.ext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentGenerator;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchpipelines.questionanswering.generative.llm.MessageBlock;
import org.opensearch.test.OpenSearchTestCase;

public class GenerativeQAParametersTests extends OpenSearchTestCase {

    private List<MessageBlock> messageList = null;

    public GenerativeQAParametersTests() {
        Map<String, ?> imageMap = Map.of("image", Map.of("format", "jpg", "url", "https://xyz.com/file.jpg"));
        Map<String, ?> textMap = Map.of("text", "what is this");
        Map<String, ?> contentMap = Map.of();
        Map<String, ?> map = Map.of("role", "user", "content", List.of(textMap, imageMap));
        MessageBlock mb = new MessageBlock(map);
        messageList = List.of(mb);
    }

    public void testGenerativeQAParameters() {
        GenerativeQAParameters params = new GenerativeQAParameters(
            "conversation_id",
            "llm_model",
            "llm_question",
            "system_prompt",
            "user_instructions",
            null,
            null,
            null,
            null
        );
        GenerativeQAParamExtBuilder extBuilder = new GenerativeQAParamExtBuilder();
        extBuilder.setParams(params);
        SearchSourceBuilder srcBulder = SearchSourceBuilder.searchSource().ext(List.of(extBuilder));
        SearchRequest request = new SearchRequest("my_index").source(srcBulder);
        GenerativeQAParameters actual = GenerativeQAParamUtil.getGenerativeQAParameters(request);
        assertEquals(params, actual);
    }

    public void testGenerativeQAParametersWithLlmMessages() {

        GenerativeQAParameters params = new GenerativeQAParameters(
            "conversation_id",
            "llm_model",
            "llm_question",
            "system_prompt",
            "user_instructions",
            null,
            null,
            null,
            null,
            this.messageList
        );
        GenerativeQAParamExtBuilder extBuilder = new GenerativeQAParamExtBuilder();
        extBuilder.setParams(params);
        SearchSourceBuilder srcBulder = SearchSourceBuilder.searchSource().ext(List.of(extBuilder));
        SearchRequest request = new SearchRequest("my_index").source(srcBulder);
        GenerativeQAParameters actual = GenerativeQAParamUtil.getGenerativeQAParameters(request);
        // MessageBlock messageBlock = actual.getMessageBlock();
        assertEquals(params, actual);
    }

    static class DummyStreamOutput extends StreamOutput {

        List<String> list = new ArrayList<>();
        List<Integer> intValues = new ArrayList<>();

        @Override
        public void writeString(String str) {
            System.out.println("Adding string: " + str);
            list.add(str);
        }

        public List<String> getList() {
            return list;
        }

        @Override
        public void writeInt(int i) {
            intValues.add(i);
        }

        public List<Integer> getIntValues() {
            return this.intValues;
        }

        @Override
        public void writeByte(byte b) throws IOException {

        }

        @Override
        public void writeBytes(byte[] b, int offset, int length) throws IOException {

        }

        @Override
        public void flush() throws IOException {

        }

        @Override
        public void close() throws IOException {

        }

        @Override
        public void reset() throws IOException {

        }
    }

    public void testWriteTo() throws IOException {
        String conversationId = "a";
        String llmModel = "b";
        String llmQuestion = "c";
        String systemPrompt = "s";
        String userInstructions = "u";
        int contextSize = 1;
        int interactionSize = 2;
        int timeout = 10;
        String llmResponseField = "text";
        GenerativeQAParameters parameters = new GenerativeQAParameters(
            conversationId,
            llmModel,
            llmQuestion,
            systemPrompt,
            userInstructions,
            contextSize,
            interactionSize,
            timeout,
            llmResponseField,
            messageList
        );
        StreamOutput output = new DummyStreamOutput();
        parameters.writeTo(output);
        List<String> actual = ((DummyStreamOutput) output).getList();
        assertEquals(12, actual.size());
        assertEquals(conversationId, actual.get(0));
        assertEquals(llmModel, actual.get(1));
        assertEquals(llmQuestion, actual.get(2));
        assertEquals(systemPrompt, actual.get(3));
        assertEquals(userInstructions, actual.get(4));
        assertEquals(llmResponseField, actual.get(5));
        List<Integer> intValues = ((DummyStreamOutput) output).getIntValues();
        assertTrue(contextSize == intValues.get(0));
        assertTrue(interactionSize == intValues.get(1));
        assertTrue(timeout == intValues.get(2));
    }

    public void testMisc() {
        String conversationId = "a";
        String llmModel = "b";
        String llmQuestion = "c";
        String systemPrompt = "s";
        String userInstructions = "u";
        GenerativeQAParameters parameters = new GenerativeQAParameters(
            conversationId,
            llmModel,
            llmQuestion,
            systemPrompt,
            userInstructions,
            null,
            null,
            null,
            null
        );
        assertNotEquals(parameters, null);
        assertNotEquals(parameters, "foo");
        assertEquals(
            parameters,
            new GenerativeQAParameters(conversationId, llmModel, llmQuestion, systemPrompt, userInstructions, null, null, null, null)
        );
        assertNotEquals(
            parameters,
            new GenerativeQAParameters("", llmModel, llmQuestion, systemPrompt, userInstructions, null, null, null, null)
        );
        assertNotEquals(
            parameters,
            new GenerativeQAParameters(conversationId, "", llmQuestion, systemPrompt, userInstructions, null, null, null, null)
        );
        // assertNotEquals(parameters, new GenerativeQAParameters(conversationId, llmModel, "", null));
    }

    public void testToXConent() throws IOException {
        String conversationId = "a";
        String llmModel = "b";
        String llmQuestion = "c";
        String systemPrompt = "s";
        String userInstructions = "u";
        GenerativeQAParameters parameters = new GenerativeQAParameters(
            conversationId,
            llmModel,
            llmQuestion,
            systemPrompt,
            userInstructions,
            null,
            null,
            null,
            null,
            messageList
        );
        XContent xc = mock(XContent.class);
        OutputStream os = mock(OutputStream.class);
        XContentGenerator generator = mock(XContentGenerator.class);
        when(xc.createGenerator(any(), any(), any())).thenReturn(generator);
        XContentBuilder builder = new XContentBuilder(xc, os);
        assertNotNull(parameters.toXContent(builder, null));
    }

    public void testToXConentAllOptionalParameters() throws IOException {
        String conversationId = "a";
        String llmModel = "b";
        String llmQuestion = "c";
        String systemPrompt = "s";
        String userInstructions = "u";
        int contextSize = 1;
        int interactionSize = 2;
        int timeout = 10;
        String llmResponseField = "text";
        GenerativeQAParameters parameters = new GenerativeQAParameters(
            conversationId,
            llmModel,
            llmQuestion,
            systemPrompt,
            userInstructions,
            contextSize,
            interactionSize,
            timeout,
            llmResponseField
        );
        XContent xc = mock(XContent.class);
        OutputStream os = mock(OutputStream.class);
        XContentGenerator generator = mock(XContentGenerator.class);
        when(xc.createGenerator(any(), any(), any())).thenReturn(generator);
        XContentBuilder builder = new XContentBuilder(xc, os);
        assertNotNull(parameters.toXContent(builder, null));
    }
}
