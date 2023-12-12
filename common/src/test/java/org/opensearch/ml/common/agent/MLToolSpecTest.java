package org.opensearch.ml.common.agent;

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

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

public class MLToolSpecTest {

    @Test
    public void writeTo() throws IOException {
        MLToolSpec spec = new MLToolSpec("test", "test", "test", Map.of("test", "test"), false);
        BytesStreamOutput output = new BytesStreamOutput();
        spec.writeTo(output);
        MLToolSpec spec1 = new MLToolSpec(output.bytes().streamInput());

        Assert.assertEquals(spec.getType(), spec1.getType());
        Assert.assertEquals(spec.getName(), spec1.getName());
        Assert.assertEquals(spec.getParameters(), spec1.getParameters());
        Assert.assertEquals(spec.getDescription(), spec1.getDescription());
        Assert.assertEquals(spec.isIncludeOutputInAgentResponse(), spec1.isIncludeOutputInAgentResponse());
    }

    @Test
    public void toXContent() throws IOException {
        MLToolSpec spec = new MLToolSpec("test", "test", "test", Map.of("test", "test"), false);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        spec.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert.assertEquals("{\"type\":\"test\",\"name\":\"test\",\"description\":\"test\",\"parameters\":{\"test\":\"test\"},\"include_output_in_agent_response\":false}", content);
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = "{\"type\":\"test\",\"name\":\"test\",\"description\":\"test\",\"parameters\":{\"test\":\"test\"},\"include_output_in_agent_response\":false}";
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();
        MLToolSpec spec = MLToolSpec.parse(parser);

        Assert.assertEquals(spec.getType(), "test");
        Assert.assertEquals(spec.getName(), "test");
        Assert.assertEquals(spec.getDescription(), "test");
        Assert.assertEquals(spec.getParameters(), Map.of("test", "test"));
        Assert.assertEquals(spec.isIncludeOutputInAgentResponse(), false);
    }
}