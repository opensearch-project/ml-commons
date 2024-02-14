package org.opensearch.ml.engine.memory;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

public class ConversationIndexMessageTest {

    ConversationIndexMessage message;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        message = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .type("test")
            .sessionId("123")
            .question("question")
            .response("response")
            .finalAnswer(false)
            .build();
    }

    @Test
    public void testToString() {
        Assert.assertEquals("Human:question\nAssistant:response", message.toString());
    }

    @Test
    public void toXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        message.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = BytesReference.bytes(builder).utf8ToString();

        Assert.assertTrue(content.contains("\"session_id\":\"123\""));
        Assert.assertTrue(content.contains("\"question\":\"question\""));
        Assert.assertTrue(content.contains("\"response\":\"response\""));
        Assert.assertTrue(content.contains("\"final_answer\":false"));
        Assert.assertTrue(content.contains("\"created_time\":"));
    }
}
