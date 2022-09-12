/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.profile;

import static org.opensearch.common.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.test.OpenSearchTestCase;

public class MLProfileInputTests extends OpenSearchTestCase {

    private MLProfileInput mlProfileInput;

    @Before
    public void setup() {
        Set<String> modelIds = new HashSet<>(Arrays.asList("model_id1", "model_id2"));
        Set<String> taskIds = new HashSet<>(Arrays.asList("task_id1", "task_id2"));
        Set<String> nodeIds = new HashSet<>(Arrays.asList("node_id1"));
        mlProfileInput = MLProfileInput.builder().modelIds(modelIds).taskIds(taskIds).nodeIds(nodeIds).build();
    }

    public void testSerializationDeserialization() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mlProfileInput.writeTo(output);
        MLProfileInput parsedMLProfileInput = new MLProfileInput(output.bytes().streamInput());
        verifyParsedMLProfileInput(parsedMLProfileInput);
    }

    public void testSerializationDeserialization_emptyIds() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        MLProfileInput profileInput = new MLProfileInput();
        profileInput.writeTo(output);
        MLProfileInput parsedMLProfileInput = new MLProfileInput(output.bytes().streamInput());
        assertTrue(parsedMLProfileInput.emptyModels());
        assertTrue(parsedMLProfileInput.emptyTasks());
    }

    public void testParseMLProfileInput() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlProfileInput.toXContent(builder, EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = TestHelper.parser(content);
        MLProfileInput parsedMLProfileInput = MLProfileInput.parse(parser);
        verifyParsedMLProfileInput(parsedMLProfileInput);
    }

    public void testEmptyModelIds() throws IOException {
        mlProfileInput = MLProfileInput.builder().build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlProfileInput.toXContent(builder, EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = TestHelper.parser(content);
        MLProfileInput parsedMLProfileInput = MLProfileInput.parse(parser);
        assertTrue(parsedMLProfileInput.retrieveProfileOnAllNodes());
        assertTrue(parsedMLProfileInput.emptyModels());
        assertTrue(parsedMLProfileInput.emptyTasks());
    }

    private void verifyParsedMLProfileInput(MLProfileInput parsedMLProfileInput) {
        assertTrue(parsedMLProfileInput.getModelIds().contains("model_id1"));
        assertTrue(parsedMLProfileInput.getTaskIds().contains("task_id2"));
        assertFalse(parsedMLProfileInput.emptyModels());
        assertFalse(parsedMLProfileInput.emptyTasks());
        assertFalse(parsedMLProfileInput.retrieveProfileOnAllNodes());
    }
}
