package org.opensearch.ml.engine.memory;

import org.junit.Assert;
import org.junit.Test;

public class ConversationMessageTest {

    @Test
    public void testToString() {
        ConversationMessage message = ConversationMessage
            .conversationMessageBuilder()
            .type("test")
            .content("test")
            .finalAnswer(false)
            .build();
        Assert.assertEquals("test: test", message.toString());
    }
}
