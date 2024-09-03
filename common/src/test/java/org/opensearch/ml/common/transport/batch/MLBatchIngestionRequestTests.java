/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLBatchIngestionRequestTests {
    private MLBatchIngestionInput mlBatchIngestionInput;
    private MLBatchIngestionRequest mlBatchIngestionRequest;

    @Before
    public void setUp() {
        mlBatchIngestionInput = MLBatchIngestionInput
            .builder()
            .indexName("test_index_name")
            .credential(Map.of("region", "test region"))
            .fieldMapping(Map.of("chapter", "chapter_embedding"))
            .dataSources(Map.of("type", "s3"))
            .build();
        mlBatchIngestionRequest = MLBatchIngestionRequest.builder().mlBatchIngestionInput(mlBatchIngestionInput).build();
    }

    @Test
    public void writeToSuccess() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mlBatchIngestionRequest.writeTo(output);
        MLBatchIngestionRequest parsedRequest = new MLBatchIngestionRequest(output.bytes().streamInput());
        assertEquals(
            mlBatchIngestionRequest.getMlBatchIngestionInput().getIndexName(),
            parsedRequest.getMlBatchIngestionInput().getIndexName()
        );
        assertEquals(
            mlBatchIngestionRequest.getMlBatchIngestionInput().getCredential(),
            parsedRequest.getMlBatchIngestionInput().getCredential()
        );
        assertEquals(
            mlBatchIngestionRequest.getMlBatchIngestionInput().getFieldMapping(),
            parsedRequest.getMlBatchIngestionInput().getFieldMapping()
        );
        assertEquals(
            mlBatchIngestionRequest.getMlBatchIngestionInput().getDataSources(),
            parsedRequest.getMlBatchIngestionInput().getDataSources()
        );
    }

    @Test
    public void validateSuccess() {
        assertNull(mlBatchIngestionRequest.validate());
    }

    @Test
    public void validateWithNullInputException() {
        MLBatchIngestionRequest mlBatchIngestionRequest1 = MLBatchIngestionRequest.builder().build();
        ActionRequestValidationException exception = mlBatchIngestionRequest1.validate();
        assertEquals("Validation Failed: 1: The input for ML batch ingestion cannot be null.;", exception.getMessage());
    }

    @Test
    public void fromActionRequestWithBatchRequestSuccess() {
        assertSame(MLBatchIngestionRequest.fromActionRequest(mlBatchIngestionRequest), mlBatchIngestionRequest);
    }

    @Test
    public void fromActionRequestWithNonRequestSuccess() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlBatchIngestionRequest.writeTo(out);
            }
        };
        MLBatchIngestionRequest result = MLBatchIngestionRequest.fromActionRequest(actionRequest);
        assertNotSame(result, mlBatchIngestionRequest);
        assertEquals(mlBatchIngestionRequest.getMlBatchIngestionInput().getIndexName(), result.getMlBatchIngestionInput().getIndexName());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequestIOException() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLBatchIngestionRequest.fromActionRequest(actionRequest);
    }
}
