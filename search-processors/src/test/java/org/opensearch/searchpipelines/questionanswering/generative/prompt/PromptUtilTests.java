package org.opensearch.searchpipelines.questionanswering.generative.prompt;

import org.opensearch.test.OpenSearchTestCase;

import java.util.Collections;

public class PromptUtilTests extends OpenSearchTestCase {

    public void testPromptUtilStaticMethods() {
        assertNull(PromptUtil.getChatCompletionPrompt("question", Collections.emptyList(), Collections.emptyList()));
        assertNull(PromptUtil.getQuestionRephrasingPrompt("question", Collections.emptyList()));
    }

    public void testCtor() {
        PromptUtil util = new PromptUtil();
        assertNotNull(util);
    }
}
