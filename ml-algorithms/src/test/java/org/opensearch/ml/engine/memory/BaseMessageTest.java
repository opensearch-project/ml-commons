package org.opensearch.ml.engine.memory;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

public class BaseMessageTest {

    @Test
    public void testToString() {
        BaseMessage message = new BaseMessage("test", "test");
        Assert.assertEquals("test: test", message.toString());
    }

    @Test
    public void toXContent() throws IOException {
        BaseMessage baseMessage = new BaseMessage("test", "test");
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        baseMessage.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = BytesReference.bytes(builder).utf8ToString();

        Assert.assertEquals("{\"type\":\"test\",\"content\":\"test\"}", content);
    }
}
