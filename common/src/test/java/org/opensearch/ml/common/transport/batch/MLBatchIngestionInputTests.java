/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchModule;

public class MLBatchIngestionInputTests {

    private MLBatchIngestionInput mlBatchIngestionInput;

    private Map<String, Object> dataSource;

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    private final String expectedInputStr = "{"
        + "\"index_name\":\"test index\","
        + "\"field_map\":{"
        + "\"chapter\":\"chapter_embedding\""
        + "},"
        + "\"credential\":{"
        + "\"region\":\"test region\""
        + "},"
        + "\"data_source\":{"
        + "\"source\":[\"s3://samplebucket/output/sampleresults.json.out\"],"
        + "\"type\":\"s3\""
        + "}"
        + "}";

    @Before
    public void setUp() {
        dataSource = new HashMap<>();
        dataSource.put("type", "s3");
        dataSource.put("source", Arrays.asList("s3://samplebucket/output/sampleresults.json.out"));

        Map<String, String> credentials = Map.of("region", "test region");
        Map<String, Object> fieldMapping = Map.of("chapter", "chapter_embedding");

        mlBatchIngestionInput = MLBatchIngestionInput
            .builder()
            .indexName("test index")
            .credential(credentials)
            .fieldMapping(fieldMapping)
            .dataSources(dataSource)
            .build();
    }

    @Test
    public void constructorMLBatchIngestionInput_NullName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("The index name for data ingestion is missing. Please provide a valid index name to proceed.");

        MLBatchIngestionInput.builder().indexName(null).dataSources(dataSource).build();
    }

    @Test
    public void constructorMLBatchIngestionInput_NullSource() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule
            .expectMessage("No data sources were provided for ingestion. Please specify at least one valid data source to proceed.");
        MLBatchIngestionInput.builder().indexName("test index").dataSources(null).build();
    }

    @Test
    public void testToXContent_FullFields() throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        mlBatchIngestionInput.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals(expectedInputStr, jsonStr);
    }

    @Test
    public void testParse() throws Exception {
        testParseFromJsonString(expectedInputStr, parsedInput -> {
            assertEquals("test index", parsedInput.getIndexName());
            assertEquals("test region", parsedInput.getCredential().get("region"));
            assertEquals("chapter_embedding", parsedInput.getFieldMapping().get("chapter"));
            assertEquals("s3", parsedInput.getDataSources().get("type"));
        });
    }

    private void testParseFromJsonString(String expectedInputString, Consumer<MLBatchIngestionInput> verify) throws Exception {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                expectedInputString
            );
        parser.nextToken();
        MLBatchIngestionInput parsedInput = MLBatchIngestionInput.parse(parser);
        verify.accept(parsedInput);
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(
            mlBatchIngestionInput,
            parsedInput -> assertEquals(mlBatchIngestionInput.getIndexName(), parsedInput.getIndexName())
        );
    }

    private void readInputStream(MLBatchIngestionInput input, Consumer<MLBatchIngestionInput> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLBatchIngestionInput parsedInput = new MLBatchIngestionInput(streamInput);
        verify.accept(parsedInput);
    }
}
