/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.ingest;

import static org.opensearch.ml.engine.ingest.AbstractIngestion.INGEST_FIELDS;
import static org.opensearch.ml.engine.ingest.AbstractIngestion.INPUT_FIELD_NAMES;
import static org.opensearch.ml.engine.ingest.AbstractIngestion.OUTPUT_FIELD_NAMES;
import static org.opensearch.ml.engine.ingest.S3DataIngestion.SOURCE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.Client;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionInput;

import software.amazon.awssdk.services.s3.S3Client;

public class S3DataIngestionTests {

    private MLBatchIngestionInput mlBatchIngestionInput;
    private S3DataIngestion s3DataIngestion;

    @Mock
    Client client;

    @Mock
    S3Client s3Client;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        s3DataIngestion = new S3DataIngestion(client);

        Map<String, Object> fieldMap = new HashMap<>();
        fieldMap.put("input", "$.content");
        fieldMap.put("output", "$.SageMakerOutput");
        fieldMap.put(INPUT_FIELD_NAMES, Arrays.asList("chapter", "title"));
        fieldMap.put(OUTPUT_FIELD_NAMES, Arrays.asList("chapter_embedding", "title_embedding"));
        fieldMap.put(INGEST_FIELDS, Arrays.asList("$.id"));

        Map<String, String> credential = Map
            .of("region", "us-east-1", "access_key", "some accesskey", "secret_key", "some secret", "session_token", "some token");
        Map<String, Object> dataSource = new HashMap<>();
        dataSource.put("type", "s3");
        dataSource.put(SOURCE, Arrays.asList("s3://offlinebatch/output/sagemaker_djl_batch_input.json.out"));

        mlBatchIngestionInput = MLBatchIngestionInput
            .builder()
            .indexName("testIndex")
            .fieldMapping(fieldMap)
            .credential(credential)
            .dataSources(dataSource)
            .build();
    }
}
