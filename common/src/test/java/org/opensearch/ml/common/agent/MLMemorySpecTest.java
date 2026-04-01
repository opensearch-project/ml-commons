package org.opensearch.ml.common.agent;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.search.SearchModule;

public class MLMemorySpecTest {

    @Test
    public void writeTo() throws IOException {
        MLMemorySpec spec = new MLMemorySpec("test", "123", 0, null);
        BytesStreamOutput output = new BytesStreamOutput();
        spec.writeTo(output);
        MLMemorySpec spec1 = new MLMemorySpec(output.bytes().streamInput());

        Assert.assertEquals(spec.getType(), spec1.getType());
        Assert.assertEquals(spec.getSessionId(), spec1.getSessionId());
        Assert.assertEquals(spec.getWindowSize(), spec1.getWindowSize());
    }

    @Test
    public void toXContent() throws IOException {
        MLMemorySpec spec = new MLMemorySpec("test", "123", 0, null);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        spec.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert.assertEquals("{\"type\":\"test\",\"window_size\":0,\"session_id\":\"123\"}", content);
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = "{\"type\":\"test\",\"window_size\":0,\"session_id\":\"123\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        MLMemorySpec spec = MLMemorySpec.parse(parser);

        Assert.assertEquals(spec.getType(), "test");
        Assert.assertEquals(spec.getWindowSize(), Integer.valueOf(0));
        Assert.assertEquals(spec.getSessionId(), "123");
    }

    @Test
    public void fromStream() throws IOException {
        MLMemorySpec spec = new MLMemorySpec("test", "123", 0, null);
        BytesStreamOutput output = new BytesStreamOutput();
        spec.writeTo(output);
        MLMemorySpec spec1 = MLMemorySpec.fromStream(output.bytes().streamInput());

        Assert.assertEquals(spec.getType(), spec1.getType());
        Assert.assertEquals(spec.getSessionId(), spec1.getSessionId());
        Assert.assertEquals(spec.getWindowSize(), spec1.getWindowSize());
    }
}
