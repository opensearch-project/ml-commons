package org.opensearch.ml.common.agent;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

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

public class MLToolSpecTest {

    @Test
    public void writeTo() throws IOException {
        MLToolSpec spec = new MLToolSpec(
            "test_type",
            "test_name",
            "test_desc",
            Map.of("test_key", "test_value"),
            false,
            Map.of("configKey", "configValue")
        );
        BytesStreamOutput output = new BytesStreamOutput();
        spec.writeTo(output);
        MLToolSpec spec1 = new MLToolSpec(output.bytes().streamInput());

        Assert.assertEquals(spec.getType(), spec1.getType());
        Assert.assertEquals(spec.getName(), spec1.getName());
        Assert.assertEquals(spec.getParameters(), spec1.getParameters());
        Assert.assertEquals(spec.getDescription(), spec1.getDescription());
        Assert.assertEquals(spec.isIncludeOutputInAgentResponse(), spec1.isIncludeOutputInAgentResponse());
        Assert.assertEquals(spec.getConfigMap(), spec1.getConfigMap());
    }

    @Test
    public void writeToEmptyConfigMap() throws IOException {
        MLToolSpec spec = new MLToolSpec(
            "test_type",
            "test_name",
            "test_desc",
            Map.of("test_key", "test_value"),
            false,
            Collections.emptyMap()
        );
        BytesStreamOutput output = new BytesStreamOutput();
        spec.writeTo(output);
        MLToolSpec spec1 = new MLToolSpec(output.bytes().streamInput());

        Assert.assertEquals(spec.getType(), spec1.getType());
        Assert.assertEquals(spec.getName(), spec1.getName());
        Assert.assertEquals(spec.getParameters(), spec1.getParameters());
        Assert.assertEquals(spec.getDescription(), spec1.getDescription());
        Assert.assertEquals(spec.isIncludeOutputInAgentResponse(), spec1.isIncludeOutputInAgentResponse());
        Assert.assertEquals(spec.getConfigMap(), spec1.getConfigMap());
    }

    @Test
    public void writeToNullConfigMap() throws IOException {
        MLToolSpec spec = new MLToolSpec("test_type", "test_name", "test_desc", Map.of("test_key", "test_value"), false, null);
        BytesStreamOutput output = new BytesStreamOutput();
        spec.writeTo(output);
        MLToolSpec spec1 = new MLToolSpec(output.bytes().streamInput());

        Assert.assertEquals(spec.getType(), spec1.getType());
        Assert.assertEquals(spec.getName(), spec1.getName());
        Assert.assertEquals(spec.getParameters(), spec1.getParameters());
        Assert.assertEquals(spec.getDescription(), spec1.getDescription());
        Assert.assertEquals(spec.isIncludeOutputInAgentResponse(), spec1.isIncludeOutputInAgentResponse());
        Assert.assertNull(spec1.getConfigMap());
    }

    @Test
    public void toXContent() throws IOException {
        MLToolSpec spec = new MLToolSpec(
            "test_type",
            "test_name",
            "test_desc",
            Map.of("test_key", "test_value"),
            false,
            Map.of("configKey", "configValue")
        );
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        spec.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert
            .assertEquals(
                "{\"type\":\"test_type\",\"name\":\"test_name\",\"description\":\"test_desc\",\"parameters\":{\"test_key\":\"test_value\"},\"include_output_in_agent_response\":false,\"config\":{\"configKey\":\"configValue\"}}",
                content
            );
    }

    @Test
    public void toXContentEmptyConfigMap() throws IOException {
        MLToolSpec spec = new MLToolSpec(
            "test_type",
            "test_name",
            "test_desc",
            Map.of("test_key", "test_value"),
            false,
            Collections.emptyMap()
        );
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        spec.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert
            .assertEquals(
                "{\"type\":\"test_type\",\"name\":\"test_name\",\"description\":\"test_desc\",\"parameters\":{\"test_key\":\"test_value\"},\"include_output_in_agent_response\":false}",
                content
            );
    }

    @Test
    public void toXContentNullConfigMap() throws IOException {
        MLToolSpec spec = new MLToolSpec("test_type", "test_name", "test_desc", Map.of("test_key", "test_value"), false, null);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        spec.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert
            .assertEquals(
                "{\"type\":\"test_type\",\"name\":\"test_name\",\"description\":\"test_desc\",\"parameters\":{\"test_key\":\"test_value\"},\"include_output_in_agent_response\":false}",
                content
            );
    }

    @Test
    public void parse() throws IOException {
        String jsonStr =
            "{\"type\":\"test_type\",\"name\":\"test_name\",\"description\":\"test_desc\",\"parameters\":{\"test_key\":\"test_value\"},\"include_output_in_agent_response\":false,\"config\":{\"configKey\":\"configValue\"}}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        MLToolSpec spec = MLToolSpec.parse(parser);

        Assert.assertEquals(spec.getType(), "test_type");
        Assert.assertEquals(spec.getName(), "test_name");
        Assert.assertEquals(spec.getDescription(), "test_desc");
        Assert.assertEquals(spec.getParameters(), Map.of("test_key", "test_value"));
        Assert.assertEquals(spec.isIncludeOutputInAgentResponse(), false);
        Assert.assertEquals(spec.getConfigMap(), Map.of("configKey", "configValue"));
    }

    @Test
    public void parseEmptyConfigMap() throws IOException {
        String jsonStr =
            "{\"type\":\"test_type\",\"name\":\"test_name\",\"description\":\"test_desc\",\"parameters\":{\"test_key\":\"test_value\"},\"include_output_in_agent_response\":false}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
        parser.nextToken();
        MLToolSpec spec = MLToolSpec.parse(parser);

        Assert.assertEquals(spec.getType(), "test_type");
        Assert.assertEquals(spec.getName(), "test_name");
        Assert.assertEquals(spec.getDescription(), "test_desc");
        Assert.assertEquals(spec.getParameters(), Map.of("test_key", "test_value"));
        Assert.assertEquals(spec.isIncludeOutputInAgentResponse(), false);
        Assert.assertEquals(spec.getConfigMap(), null);
    }

    @Test
    public void fromStream() throws IOException {
        MLToolSpec spec = new MLToolSpec(
            "test_type",
            "test_name",
            "test_desc",
            Map.of("test_key", "test_value"),
            false,
            Map.of("configKey", "configValue")
        );
        BytesStreamOutput output = new BytesStreamOutput();
        spec.writeTo(output);
        MLToolSpec spec1 = MLToolSpec.fromStream(output.bytes().streamInput());

        Assert.assertEquals(spec.getType(), spec1.getType());
        Assert.assertEquals(spec.getName(), spec1.getName());
        Assert.assertEquals(spec.getParameters(), spec1.getParameters());
        Assert.assertEquals(spec.getDescription(), spec1.getDescription());
        Assert.assertEquals(spec.isIncludeOutputInAgentResponse(), spec1.isIncludeOutputInAgentResponse());
        Assert.assertEquals(spec.getConfigMap(), spec1.getConfigMap());
    }

    @Test
    public void fromStreamEmptyConfigMap() throws IOException {
        MLToolSpec spec = new MLToolSpec(
            "test_type",
            "test_name",
            "test_desc",
            Map.of("test_key", "test_value"),
            false,
            Collections.emptyMap()
        );
        BytesStreamOutput output = new BytesStreamOutput();
        spec.writeTo(output);
        MLToolSpec spec1 = MLToolSpec.fromStream(output.bytes().streamInput());

        Assert.assertEquals(spec.getType(), spec1.getType());
        Assert.assertEquals(spec.getName(), spec1.getName());
        Assert.assertEquals(spec.getParameters(), spec1.getParameters());
        Assert.assertEquals(spec.getDescription(), spec1.getDescription());
        Assert.assertEquals(spec.isIncludeOutputInAgentResponse(), spec1.isIncludeOutputInAgentResponse());
        Assert.assertEquals(spec.getConfigMap(), spec1.getConfigMap());
    }

    @Test
    public void fromStreamNullConfigMap() throws IOException {
        MLToolSpec spec = new MLToolSpec("test_type", "test_name", "test_desc", Map.of("test_key", "test_value"), false, null);
        BytesStreamOutput output = new BytesStreamOutput();
        spec.writeTo(output);
        MLToolSpec spec1 = MLToolSpec.fromStream(output.bytes().streamInput());

        Assert.assertEquals(spec.getType(), spec1.getType());
        Assert.assertEquals(spec.getName(), spec1.getName());
        Assert.assertEquals(spec.getParameters(), spec1.getParameters());
        Assert.assertEquals(spec.getDescription(), spec1.getDescription());
        Assert.assertEquals(spec.isIncludeOutputInAgentResponse(), spec1.isIncludeOutputInAgentResponse());
        Assert.assertEquals(spec.getConfigMap(), spec1.getConfigMap());
    }
}
