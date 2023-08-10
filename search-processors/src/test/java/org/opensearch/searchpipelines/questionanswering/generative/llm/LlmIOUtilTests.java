package org.opensearch.searchpipelines.questionanswering.generative.llm;

import org.opensearch.test.OpenSearchTestCase;

import java.util.Collections;

public class LlmIOUtilTests extends OpenSearchTestCase {

    public void testChatCompletionInput() {
        ChatCompletionInput input = LlmIOUtil.createChatCompletionInput("model", "question", Collections.emptyList(), Collections.emptyList());
        assertTrue(input instanceof OpenSearchChatCompletionInput);
    }
}
