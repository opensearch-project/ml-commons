package org.opensearch.searchpipelines.questionanswering.generative.llm;

import org.opensearch.test.OpenSearchTestCase;

public class OpenSearchChatCompletionOutputTests extends OpenSearchTestCase {

    public void testCtor() {
        OpenSearchChatCompletionOutput output = new OpenSearchChatCompletionOutput("answer");
        assertNotNull(output);
    }

    public void testGettersSetters() {
        String answer = "answer";
        OpenSearchChatCompletionOutput output = new OpenSearchChatCompletionOutput(answer);
        assertEquals(answer, output.getAnswer());
    }
}
