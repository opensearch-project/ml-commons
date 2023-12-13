package org.opensearch.ml.common.agent;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.search.SearchModule;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

public class LLMSpecTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void constructor_NonModelID() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model id is null");

        LLMSpec spec = new LLMSpec(null, Map.of("test_key", "test_value"));
    }

    @Test
    public void writeTo() throws IOException {
        LLMSpec spec = new LLMSpec("test_model", Map.of("test_key", "test_value"));
        BytesStreamOutput output = new BytesStreamOutput();
        spec.writeTo(output);
        LLMSpec spec1 = new LLMSpec(output.bytes().streamInput());

        Assert.assertEquals(spec.getModelId(), spec1.getModelId());
        Assert.assertEquals(spec.getParameters(), spec1.getParameters());
    }

    @Test
    public void writeTo_EmptyParameters() throws IOException {
        LLMSpec spec = new LLMSpec("test_model", Map.of());
        BytesStreamOutput output = new BytesStreamOutput();
        spec.writeTo(output);
        LLMSpec spec1 = new LLMSpec(output.bytes().streamInput());

        Assert.assertEquals(spec.getModelId(), spec1.getModelId());
        Assert.assertEquals(null, spec1.getParameters());
    }

    @Test
    public void toXContent() throws IOException {
        LLMSpec spec = new LLMSpec("test_model", Map.of("test_key", "test_value"));
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        spec.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert.assertEquals("{\"model_id\":\"test_model\",\"parameters\":{\"test_key\":\"test_value\"}}", content);
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = "{\"model_id\":\"test_model\",\"parameters\":{\"test_key\":\"test_value\"}}";
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();
        LLMSpec spec = LLMSpec.parse(parser);

        Assert.assertEquals(spec.getModelId(), "test_model");
        Assert.assertEquals(spec.getParameters(), Map.of("test_key", "test_value"));
    }

    @Test
    public void fromStream() throws IOException {
        LLMSpec spec = new LLMSpec("test_model", Map.of("test_key", "test_value"));
        BytesStreamOutput output = new BytesStreamOutput();
        spec.writeTo(output);
        LLMSpec spec1 = LLMSpec.fromStream(output.bytes().streamInput());

        Assert.assertEquals(spec.getModelId(), spec1.getModelId());
        Assert.assertEquals(spec.getParameters(), spec1.getParameters());
    }
}