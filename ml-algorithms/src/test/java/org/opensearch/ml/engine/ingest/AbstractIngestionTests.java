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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.mockito.ArgumentCaptor;
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
    String[] ingestFields;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        s3DataIngestion = new S3DataIngestion(client);

        fieldMap = new HashMap<>();
        fieldMap.put("chapter", "$.content[0]");
        fieldMap.put("title", "$.content[1]");
        fieldMap.put("chapter_embedding", "$.SageMakerOutput[0]");
        fieldMap.put("title_embedding", "$.SageMakerOutput[1]");

        ingestFields = new String[] { "$.id" };
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
    public void testFilterFieldMapping_ValidInput_EmptyPrefix() {
        // Arrange
        MLBatchIngestionInput mlBatchIngestionInput = new MLBatchIngestionInput(
            "indexName",
            fieldMap,
            ingestFields,
            new HashMap<>(),
            new HashMap<>()
        );
        Map<String, Object> result = s3DataIngestion.filterFieldMapping(mlBatchIngestionInput, 0);

        // Assert
        assertEquals(0, result.size());
        assertEquals(true, result.isEmpty());
    }

    @Test
    public void testFilterFieldMapping_MatchingPrefix() {
        // Arrange
        Map<String, Object> fieldMap = new HashMap<>();
        fieldMap.put("question", "source[1].$.body.input[0]");
        fieldMap.put("question_embedding", "source[0].$.response.body.data[0].embedding");
        fieldMap.put("answer", "source[1].$.body.input[1]");
        fieldMap.put("answer_embedding", "source[0].$.response.body.data[1].embedding");
        fieldMap.put("_id", Arrays.asList("source[0].$.custom_id", "source[1].$.custom_id"));

        MLBatchIngestionInput mlBatchIngestionInput = new MLBatchIngestionInput(
            "indexName",
            fieldMap,
            ingestFields,
            new HashMap<>(),
            new HashMap<>()
        );

        // Act
        Map<String, Object> result = s3DataIngestion.filterFieldMapping(mlBatchIngestionInput, 0);

        // Assert
        assertEquals(3, result.size());

        assertEquals("$.response.body.data[0].embedding", result.get("question_embedding"));
        assertEquals("$.response.body.data[1].embedding", result.get("answer_embedding"));
        assertEquals(Arrays.asList("$.custom_id"), result.get("_id"));
    }

    @Test
    public void testFilterFieldMappingSoleSource_MatchingPrefix() {
        // Arrange
        Map<String, Object> fieldMap = new HashMap<>();
        fieldMap.put("question", "source[0].$.body.input[0]");
        fieldMap.put("question_embedding", "source[0].$.response.body.data[0].embedding");
        fieldMap.put("answer", "source[0].$.body.input[1]");
        fieldMap.put("answer_embedding", "$.response.body.data[1].embedding");
        fieldMap.put("_id", Arrays.asList("$.custom_id", "source[1].$.custom_id"));

        MLBatchIngestionInput mlBatchIngestionInput = new MLBatchIngestionInput(
            "indexName",
            fieldMap,
            ingestFields,
            new HashMap<>(),
            new HashMap<>()
        );

        // Act
        Map<String, Object> result = s3DataIngestion.filterFieldMappingSoleSource(mlBatchIngestionInput);

        // Assert
        assertEquals(6, result.size());

        assertEquals("$.body.input[0]", result.get("question"));
        assertEquals("$.response.body.data[0].embedding", result.get("question_embedding"));
        assertEquals(Arrays.asList("$.custom_id"), result.get("_id"));
    }

    @Test
    public void testProcessFieldMapping_FromSM() {
        String jsonStr =
            "{\"SageMakerOutput\":[[-0.017166402, 0.055771016],[-0.004301484,-0.042826906]],\"content\":[\"this is chapter 1\",\"harry potter\"],\"id\":1}";
        // Arrange

        // Act
        Map<String, Object> processedFieldMapping = s3DataIngestion.processFieldMapping(jsonStr, fieldMap);

        // Assert
        assertEquals("this is chapter 1", processedFieldMapping.get("chapter"));
        assertEquals("harry potter", processedFieldMapping.get("title"));
    }

    @Test
    public void testProcessFieldMapping_FromOpenAI() {
        String jsonStr =
            "{\"id\": \"batch_req_pgNqCfERGHOcMwAHGUWSO0nV\", \"custom_id\": \"request-1\", \"response\": {\"status_code\": 200, \"request_id\": \"fca3d548770f1f299d067c64c11a14fd\", \"body\": {\"object\": \"list\", \"data\": [{\"object\": \"embedding\", \"index\": 0, \"embedding\": [0.0044326545, -0.029703418]}, {\"object\": \"embedding\", \"index\": 1, \"embedding\": [0.002297497, -0.009297881]}], \"model\": \"text-embedding-ada-002\", \"usage\": {\"prompt_tokens\": 15, \"total_tokens\": 15}}}, \"error\": null}";
        // Arrange
        Map<String, Object> fieldMap = new HashMap<>();
        fieldMap.put("question_embedding", "$.response.body.data[0].embedding");
        fieldMap.put("answer_embedding", "$.response.body.data[1].embedding");
        fieldMap.put("_id", Arrays.asList("$.custom_id"));

        // Act
        Map<String, Object> processedFieldMapping = s3DataIngestion.processFieldMapping(jsonStr, fieldMap);

        // Assert
        assertEquals("request-1", processedFieldMapping.get("_id"));
    }

    @Test
    public void testProcessFieldMapping_EmptyFieldInput() {
        String jsonStr =
            "{\"SageMakerOutput\":[[-0.017166402, 0.055771016],[-0.004301484,-0.042826906]],\"content\":[\"this is chapter 1\",\"harry potter\"],\"id\":1}";
        // Arrange

        // Act
        Map<String, Object> result = s3DataIngestion.processFieldMapping(jsonStr, new HashMap<>());
        assertEquals(true, result.isEmpty());
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
        MLBatchIngestionInput mlBatchIngestionInput = new MLBatchIngestionInput(
            "indexName",
            fieldMap,
            ingestFields,
            new HashMap<>(),
            new HashMap<>()
        );
        ActionListener<BulkResponse> bulkResponseListener = mock(ActionListener.class);
        s3DataIngestion.batchIngest(sourceLines, mlBatchIngestionInput, bulkResponseListener, 0, true);

        verify(client).bulk(isA(BulkRequest.class), isA(ActionListener.class));
        verify(bulkResponseListener).onResponse(isA(BulkResponse.class));
    }

    @Test
    public void testBatchIngestSuccess_returnForNullJasonMap() {
        doAnswer(invocation -> {
            ActionListener<BulkResponse> bulkResponseListener = invocation.getArgument(1);
            bulkResponseListener.onResponse(mock(BulkResponse.class));
            return null;
        }).when(client).bulk(any(), any());

        List<String> sourceLines = Arrays
            .asList(
                "{\"SageMakerOutput\":[[-0.017166402, 0.055771016],[-0.004301484,-0.042826906]],\"content\":[\"this is chapter 1\",\"harry potter\"],\"id\":1}"
            );
        MLBatchIngestionInput mlBatchIngestionInput = new MLBatchIngestionInput(
            "indexName",
            fieldMap,
            ingestFields,
            new HashMap<>(),
            new HashMap<>()
        );
        ActionListener<BulkResponse> bulkResponseListener = mock(ActionListener.class);
        s3DataIngestion.batchIngest(sourceLines, mlBatchIngestionInput, bulkResponseListener, 0, false);

        verify(client, never()).bulk(isA(BulkRequest.class), isA(ActionListener.class));
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(bulkResponseListener).onFailure(argumentCaptor.capture());
        assert (argumentCaptor
            .getValue()
            .getMessage()
            .equals("the bulk ingestion is empty: please check your field mapping to match your sources"));
    }
}
