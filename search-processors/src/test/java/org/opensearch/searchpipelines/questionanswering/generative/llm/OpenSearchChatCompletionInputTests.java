package org.opensearch.searchpipelines.questionanswering.generative.llm;

import org.opensearch.test.OpenSearchTestCase;

import java.util.Collections;
import java.util.List;

public class OpenSearchChatCompletionInputTests extends OpenSearchTestCase {

    public void testCtor() {
        String model = "model";
        String question = "question";

        OpenSearchChatCompletionInput input = new OpenSearchChatCompletionInput(model, question, Collections.emptyList(), Collections.emptyList());

        assertNotNull(input);
    }

    public void testGettersSetters() {
        String model = "model";
        String question = "question";
        List<String> history  = List.of("hello");
        List<String> contexts = List.of("result1", "result2");
        OpenSearchChatCompletionInput input = new OpenSearchChatCompletionInput(model, question, history, contexts);
        assertEquals(model, input.getModel());
        assertEquals(question, input.getQuestion());
        assertEquals(history.get(0), input.getChatHistory().get(0));
        assertEquals(contexts.get(0), input.getContexts().get(0));
        assertEquals(contexts.get(1), input.getContexts().get(1));
    }
}
