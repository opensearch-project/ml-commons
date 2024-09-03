/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.ingest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.ingest.AbstractIngestion.IDFIELD;
import static org.opensearch.ml.engine.ingest.AbstractIngestion.INGESTFIELDS;
import static org.opensearch.ml.engine.ingest.AbstractIngestion.INPUT;
import static org.opensearch.ml.engine.ingest.AbstractIngestion.INPUTFIELDS;
import static org.opensearch.ml.engine.ingest.AbstractIngestion.OUTPUT;
import static org.opensearch.ml.engine.ingest.AbstractIngestion.OUTPUTIELDS;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionInput;

public class AbstractIngestionTests {
    @Mock
    Client client;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    S3DataIngestion s3DataIngestion = new S3DataIngestion(client);

    Map<String, Object> fieldMap;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        s3DataIngestion = new S3DataIngestion(client);

        fieldMap = new HashMap<>();
        fieldMap.put(INPUT, "source[1].$.content");
        fieldMap.put(OUTPUT, "source[1].$.SageMakerOutput");
        fieldMap.put(INPUTFIELDS, Arrays.asList("chapter", "title"));
        fieldMap.put(OUTPUTIELDS, Arrays.asList("chapter_embedding", "title_embedding"));
        fieldMap.put(INGESTFIELDS, Arrays.asList("source[1].$.id"));
    }

    @Test
    public void testBulkResponseListener_Success() {
        // Arrange
        AtomicInteger successfulBatches = new AtomicInteger(0);
        AtomicInteger failedBatches = new AtomicInteger(0);
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Mock BulkResponse
        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(false);

        S3DataIngestion instance = new S3DataIngestion(client);

        // Act
        ActionListener<BulkResponse> listener = instance.getBulkResponseListener(successfulBatches, failedBatches, future);
        listener.onResponse(bulkResponse);

        // Assert
        assertFalse(future.isCompletedExceptionally());
        assertEquals(1, successfulBatches.get());
        assertEquals(0, failedBatches.get());
    }

    @Test
    public void testBulkResponseListener_Failure() {
        // Arrange
        AtomicInteger successfulBatches = new AtomicInteger(0);
        AtomicInteger failedBatches = new AtomicInteger(0);
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Mock BulkResponse
        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.hasFailures()).thenReturn(true);
        when(bulkResponse.buildFailureMessage()).thenReturn("Failure message");

        S3DataIngestion instance = new S3DataIngestion(client);

        // Act
        ActionListener<BulkResponse> listener = instance.getBulkResponseListener(successfulBatches, failedBatches, future);
        listener.onResponse(bulkResponse);

        // Assert
        assertTrue(future.isCompletedExceptionally());
        assertEquals(0, successfulBatches.get());
        assertEquals(1, failedBatches.get());
    }

    @Test
    public void testBulkResponseListener_Exception() {
        // Arrange
        AtomicInteger successfulBatches = new AtomicInteger(0);
        AtomicInteger failedBatches = new AtomicInteger(0);
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Create an exception
        RuntimeException exception = new RuntimeException("Test exception");

        S3DataIngestion instance = new S3DataIngestion(client);

        // Act
        ActionListener<BulkResponse> listener = instance.getBulkResponseListener(successfulBatches, failedBatches, future);
        listener.onFailure(exception);

        // Assert
        assertTrue(future.isCompletedExceptionally());
        assertEquals(0, successfulBatches.get());
        assertEquals(1, failedBatches.get());
        assertThrows(Exception.class, () -> future.join());  // Ensure that future throws exception
    }

    @Test
    public void testCalculateSuccessRate_MultipleValues() {
        // Arrange
        List<Double> successRates = Arrays.asList(90.0, 85.5, 92.0, 88.0);

        // Act
        double result = s3DataIngestion.calculateSuccessRate(successRates);

        // Assert
        assertEquals(85.5, result, 0.0001);
    }

    @Test
    public void testCalculateSuccessRate_SingleValue() {
        // Arrange
        List<Double> successRates = Collections.singletonList(99.9);

        // Act
        double result = s3DataIngestion.calculateSuccessRate(successRates);

        // Assert
        assertEquals(99.9, result, 0.0001);
    }

    @Test
    public void testFilterFieldMapping_ValidInput_MatchingPrefix() {
        // Arrange
        MLBatchIngestionInput mlBatchIngestionInput = new MLBatchIngestionInput("indexName", fieldMap, new HashMap<>(), new HashMap<>());
        Map<String, Object> result = s3DataIngestion.filterFieldMapping(mlBatchIngestionInput, 0);

        // Assert
        assertEquals(5, result.size());
        assertEquals("source[1].$.content", result.get(INPUT));
        assertEquals("source[1].$.SageMakerOutput", result.get(OUTPUT));
        assertEquals(Arrays.asList("chapter", "title"), result.get(INPUTFIELDS));
        assertEquals(Arrays.asList("chapter_embedding", "title_embedding"), result.get(OUTPUTIELDS));
        assertEquals(Arrays.asList("source[1].$.id"), result.get(INGESTFIELDS));
    }

    @Test
    public void testFilterFieldMapping_NoMatchingPrefix() {
        // Arrange
        Map<String, Object> fieldMap = new HashMap<>();
        fieldMap.put("field1", "source[3].$.response.body.data[*].embedding");
        fieldMap.put("field2", "source[4].$.body.input");

        MLBatchIngestionInput mlBatchIngestionInput = new MLBatchIngestionInput("indexName", fieldMap, new HashMap<>(), new HashMap<>());

        // Act
        Map<String, Object> result = s3DataIngestion.filterFieldMapping(mlBatchIngestionInput, 0);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void testProcessFieldMapping_ValidInput() {
        String jsonStr =
            "{\"SageMakerOutput\":[[-0.017166402, 0.055771016],[-0.004301484,-0.042826906]],\"content\":[\"this is chapter 1\",\"harry potter\"],\"id\":1}";
        // Arrange

        // Act
        Map<String, Object> processedFieldMapping = s3DataIngestion.processFieldMapping(jsonStr, fieldMap);

        // Assert
        assertEquals("this is chapter 1", processedFieldMapping.get("chapter"));
        assertEquals("harry potter", processedFieldMapping.get("title"));
        assertEquals(1, processedFieldMapping.get("id"));
    }

    @Test
    public void testProcessFieldMapping_NoIdFieldInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("The Id field must contains only 1 jsonPath for each source");

        String jsonStr =
            "{\"SageMakerOutput\":[[-0.017166402, 0.055771016],[-0.004301484,-0.042826906]],\"content\":[\"this is chapter 1\",\"harry potter\"],\"id\":1}";
        // Arrange
        fieldMap.put(IDFIELD, null);

        // Act
        s3DataIngestion.processFieldMapping(jsonStr, fieldMap);
    }

    @Test
    public void testBatchIngestSuccess_SoleSource() {
        doAnswer(invocation -> {
            ActionListener<BulkResponse> bulkResponseListener = invocation.getArgument(1);
            bulkResponseListener.onResponse(mock(BulkResponse.class));
            return null;
        }).when(client).bulk(any(), any());

        List<String> sourceLines = Arrays
            .asList(
                "{\"SageMakerOutput\":[[-0.017166402, 0.055771016],[-0.004301484,-0.042826906]],\"content\":[\"this is chapter 1\",\"harry potter\"],\"id\":1}"
            );
        MLBatchIngestionInput mlBatchIngestionInput = new MLBatchIngestionInput("indexName", fieldMap, new HashMap<>(), new HashMap<>());
        ActionListener<BulkResponse> bulkResponseListener = mock(ActionListener.class);
        s3DataIngestion.batchIngest(sourceLines, mlBatchIngestionInput, bulkResponseListener, 0, true);

        verify(client).bulk(isA(BulkRequest.class), isA(ActionListener.class));
        verify(bulkResponseListener).onResponse(isA(BulkResponse.class));
    }

    @Test
    public void testBatchIngestSuccess_NoIdError() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("The id filed must be provided to match documents for multiple sources");

        doAnswer(invocation -> {
            ActionListener<BulkResponse> bulkResponseListener = invocation.getArgument(1);
            bulkResponseListener.onResponse(mock(BulkResponse.class));
            return null;
        }).when(client).bulk(any(), any());

        List<String> sourceLines = Arrays
            .asList(
                "{\"SageMakerOutput\":[[-0.017166402, 0.055771016],[-0.004301484,-0.042826906]],\"content\":[\"this is chapter 1\",\"harry potter\"],\"id\":1}"
            );
        MLBatchIngestionInput mlBatchIngestionInput = new MLBatchIngestionInput("indexName", fieldMap, new HashMap<>(), new HashMap<>());
        ActionListener<BulkResponse> bulkResponseListener = mock(ActionListener.class);
        s3DataIngestion.batchIngest(sourceLines, mlBatchIngestionInput, bulkResponseListener, 1, false);
    }
}
