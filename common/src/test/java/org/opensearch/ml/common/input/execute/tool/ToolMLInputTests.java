/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.search.SearchModule;

public class ToolMLInputTests {

    private ToolMLInput toolMLInput;
    private Map<String, String> parameters;
    private final String json = "{\"tool_name\":\"TestTool\",\"parameters\":{\"question\":\"test question\",\"model_id\":\"test_model\"}}";

    @Before
    public void setUp() throws IOException {
        parameters = new HashMap<>();
        parameters.put("question", "test question");
        parameters.put("model_id", "test_model");

        XContentParser parser = createParser(json);
        toolMLInput = new ToolMLInput(parser, FunctionName.TOOL);
    }

    @Test
    public void readInputStreamSuccess() throws IOException {
        readInputStream(toolMLInput, parsedInput -> {
            assertEquals("TestTool", parsedInput.getToolName());
            assertEquals(FunctionName.TOOL, parsedInput.getAlgorithm());
            assertNotNull(parsedInput.getInputDataset());
        });
    }

    @Test
    public void testXContentParsing() throws IOException {
        XContentParser parser = createParser(json);
        ToolMLInput parsed = new ToolMLInput(parser, FunctionName.TOOL);

        assertEquals("TestTool", parsed.getToolName());
        assertEquals(FunctionName.TOOL, parsed.getAlgorithm());
        assertNotNull(parsed.getInputDataset());
        assertTrue(parsed.getInputDataset() instanceof RemoteInferenceInputDataSet);
    }

    @Test(expected = IOException.class)
    public void testParseInvalidJson() throws IOException {
        String invalidJson = "{\"tool_name\":\"TestTool\",\"parameters\":{\"question\":\"test\""; // Missing closing braces
        XContentParser parser = createParser(invalidJson);
        new ToolMLInput(parser, FunctionName.TOOL);
    }

    @Test
    public void testParseMissingToolName() throws IOException {
        String jsonWithoutToolName = "{\"parameters\":{\"question\":\"test\",\"model_id\":\"123\"}}";
        XContentParser parser = createParser(jsonWithoutToolName);
        ToolMLInput parsed = new ToolMLInput(parser, FunctionName.TOOL);

        assertEquals(null, parsed.getToolName());
        assertEquals(FunctionName.TOOL, parsed.getAlgorithm());
    }

    @Test
    public void testParseMissingParameters() throws IOException {
        String jsonWithoutParams = "{\"tool_name\":\"TestTool\"}";
        XContentParser parser = createParser(jsonWithoutParams);
        ToolMLInput parsed = new ToolMLInput(parser, FunctionName.TOOL);

        assertEquals("TestTool", parsed.getToolName());
        assertEquals(null, parsed.getInputDataset());
    }

    private XContentParser createParser(String jsonString) throws IOException {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                jsonString
            );
        parser.nextToken();
        return parser;
    }

    private void readInputStream(ToolMLInput input, Consumer<ToolMLInput> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        ToolMLInput parsedInput = new ToolMLInput(streamInput);
        verify.accept(parsedInput);
    }
}
