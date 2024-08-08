/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.util.Collections;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.search.SearchModule;

public class MLUploadModelChunkInputTest {

    MLUploadModelChunkInput mlUploadModelChunkInput;
    private Function<XContentParser, MLUploadModelChunkInput> function = parser -> {
        try {
            return MLUploadModelChunkInput.parse(parser, new byte[] { 12, 4, 5, 3 });
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse MLUploadModelChunkInput", e);
        }
    };

    @Before
    public void setup() {
        mlUploadModelChunkInput = MLUploadModelChunkInput
            .builder()
            .modelId("modelId")
            .chunkNumber(1)
            .content(new byte[] { 1, 3, 4 })
            .build();
    }

    @Test
    public void parse_MLUploadModelChunkInput() throws IOException {
        TestHelper.testParse(mlUploadModelChunkInput, function);
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(mlUploadModelChunkInput);
    }

    private void readInputStream(MLUploadModelChunkInput input) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLUploadModelChunkInput newInput = new MLUploadModelChunkInput(streamInput);
        assertEquals(input.getChunkNumber(), newInput.getChunkNumber());
        assertEquals(input.getModelId(), newInput.getModelId());
    }

    @Test
    public void testMLUploadModelChunkInputConstructor() {
        MLUploadModelChunkInput input = new MLUploadModelChunkInput("modelId", 1, new byte[] { 12, 3 });
        assertNotNull(input);
    }

    @Test
    public void testMLUploadModelChunkInputWriteToSuccess() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlUploadModelChunkInput.writeTo(bytesStreamOutput);
        final var newLlUploadModelChunkInput = new MLUploadModelChunkInput(bytesStreamOutput.bytes().streamInput());
        assertEquals(mlUploadModelChunkInput.getModelId(), newLlUploadModelChunkInput.getModelId());
        assertEquals(mlUploadModelChunkInput.getChunkNumber(), newLlUploadModelChunkInput.getChunkNumber());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlUploadModelChunkInput.toXContent(builder, EMPTY_PARAMS);
        String mlModelContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{\"model_id\":\"modelId\",\"chunk_number\":1,\"model_content\":\"AQME\"}", mlModelContent);
    }

    @Test
    public void testMLUploadModelChunkInputParser() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder = mlUploadModelChunkInput.toXContent(builder, null);
        String json = builder.toString();
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                json
            );
        parser.nextToken();
        MLUploadModelChunkInput newMlUploadModelChunkInput = MLUploadModelChunkInput.parse(parser, new byte[] { 1, 3, 4 });
        assertEquals(mlUploadModelChunkInput, newMlUploadModelChunkInput);
    }

    @Test
    public void testMLUploadModelChunkInputParser_XContentParser() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlUploadModelChunkInput.toXContent(builder, EMPTY_PARAMS);
        String mlModelContent = TestHelper.xContentBuilderToString(builder);
        TestHelper.testParseFromString(mlUploadModelChunkInput, mlModelContent, function);
    }
}
