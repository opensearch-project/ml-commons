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

    MLToolSpec spec = new MLToolSpec(
        "test",
        "test",
        "test",
        Map.of("test", "test"),
        Collections.emptyMap(),
        false,
        Map.of("test", "test"),
        null,
        null
    );

    @Test
    public void writeTo() throws IOException {

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
        MLToolSpec spec = new MLToolSpec(
            "test_type",
            "test_name",
            "test_desc",
            Map.of("test_key", "test_value"),
            Collections.emptyMap(),
            false,
            null,
            null,
            null
        );
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
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        spec.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert
            .assertEquals(
                "{\"type\":\"test\",\"name\":\"test\",\"description\":\"test\",\"parameters\":{\"test\":\"test\"},\"include_output_in_agent_response\":false,\"config\":{\"test\":\"test\"}}",
                content
            );
    }

    @Test
    public void toXContentEmptyConfigMap() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        spec.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert
            .assertEquals(
                "{\"type\":\"test\",\"name\":\"test\",\"description\":\"test\",\"parameters\":{\"test\":\"test\"},\"include_output_in_agent_response\":false,\"config\":{\"test\":\"test\"}}",
                content
            );
    }

    @Test
    public void toXContentNullConfigMap() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        spec.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert
            .assertEquals(
                "{\"type\":\"test\",\"name\":\"test\",\"description\":\"test\",\"parameters\":{\"test\":\"test\"},\"include_output_in_agent_response\":false,\"config\":{\"test\":\"test\"}}",
                content
            );
    }

    @Test
    public void toXContentEmptyName() throws IOException {
        MLToolSpec specWithEmptyName = new MLToolSpec(
            "test_type",
            "",  // Empty name
            "test_desc",
            Map.of("test_key", "test_value"),
            Collections.emptyMap(),
            false,
            Map.of("config_key", "config_value"),
            null,
            null
        );

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        specWithEmptyName.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert.assertFalse(content.contains("name"));
        Assert
            .assertEquals(
                "{\"type\":\"test_type\",\"description\":\"test_desc\",\"parameters\":{\"test_key\":\"test_value\"},\"include_output_in_agent_response\":false,\"config\":{\"config_key\":\"config_value\"}}",
                content
            );
    }

    @Test
    public void toXContentEmptyDescription() throws IOException {
        MLToolSpec specWithEmptyDesc = new MLToolSpec(
            "test_type",
            "test_name",
            "",  // Empty description
            Map.of("test_key", "test_value"),
            Collections.emptyMap(),
            false,
            Map.of("config_key", "config_value"),
            null,
            null
        );

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        specWithEmptyDesc.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert.assertFalse(content.contains("description"));
        Assert
            .assertEquals(
                "{\"type\":\"test_type\",\"name\":\"test_name\",\"parameters\":{\"test_key\":\"test_value\"},\"include_output_in_agent_response\":false,\"config\":{\"config_key\":\"config_value\"}}",
                content
            );
    }

    @Test
    public void toXContentEmptyParameters() throws IOException {
        MLToolSpec specWithEmptyParams = new MLToolSpec(
            "test_type",
            "test_name",
            "test_desc",
            Collections.emptyMap(),  // Empty parameters
            Collections.emptyMap(),
            false,
            Map.of("config_key", "config_value"),
            null,
            null
        );

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        specWithEmptyParams.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert.assertFalse(content.contains("parameters"));
        Assert
            .assertEquals(
                "{\"type\":\"test_type\",\"name\":\"test_name\",\"description\":\"test_desc\",\"include_output_in_agent_response\":false,\"config\":{\"config_key\":\"config_value\"}}",
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
        assertFalse(spec.isIncludeOutputInAgentResponse());
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
        assertFalse(spec.isIncludeOutputInAgentResponse());
        assertNull(spec.getConfigMap());
    }

    @Test
    public void fromStream() throws IOException {
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
            Collections.emptyMap(),
            false,
            Collections.emptyMap(),
            null,
            null
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
        MLToolSpec spec = new MLToolSpec(
            "test_type",
            "test_name",
            "test_desc",
            Map.of("test_key", "test_value"),
            Collections.emptyMap(),
            false,
            null,
            null,
            null
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
}
