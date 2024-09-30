package org.opensearch.ml.common.transport.undeploy;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.*;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchModule;

public class MLUndeployModelInputTest {
    private MLUndeployModelInput input;
    private final String[] modelIds = new String[] { "modelId1", "modelId2", "modelId3" };
    private final String[] nodeIds = new String[] { "nodeId1", "nodeId2", "nodeId3" };
    private final String expectedInputStr = "{\"model_ids\":[\"modelId1\",\"modelId2\",\"modelId3\"],"
        + "\"node_ids\":[\"nodeId1\",\"nodeId2\",\"nodeId3\"]}";

    @Before
    public void setUp() throws Exception {
        input = MLUndeployModelInput.builder().modelIds(modelIds).nodeIds(nodeIds).build();
    }

    @Test
    public void testToXContent() throws Exception {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals(expectedInputStr, jsonStr);
    }

    @Test
    public void testParse() throws Exception {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                expectedInputStr
            );
        parser.nextToken();
        MLUndeployModelInput parsedInput = MLUndeployModelInput.parse(parser);
        assertArrayEquals(new String[] { "modelId1", "modelId2", "modelId3" }, parsedInput.getModelIds());
        assertArrayEquals(new String[] { "nodeId1", "nodeId2", "nodeId3" }, parsedInput.getNodeIds());
    }

    @Test
    public void testParseWithInvalidField() throws Exception {
        String withInvalidFieldInputStr = "{\"void\":\"void\","
            + "\"model_ids\":[\"modelId1\",\"modelId2\",\"modelId3\"],"
            + "\"node_ids\":[\"nodeId1\",\"nodeId2\",\"nodeId3\"]}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                withInvalidFieldInputStr
            );
        parser.nextToken();
        MLUndeployModelInput parsedInput = MLUndeployModelInput.parse(parser);
        assertArrayEquals(new String[] { "modelId1", "modelId2", "modelId3" }, parsedInput.getModelIds());
        assertArrayEquals(new String[] { "nodeId1", "nodeId2", "nodeId3" }, parsedInput.getNodeIds());
    }

    @Test
    public void readInputStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLUndeployModelInput parsedInput = new MLUndeployModelInput(streamInput);
        assertArrayEquals(input.getModelIds(), parsedInput.getModelIds());
        assertArrayEquals(input.getNodeIds(), parsedInput.getNodeIds());
    }
}
