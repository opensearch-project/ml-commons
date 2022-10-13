/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.indices;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.META;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX_MAPPING;
import static org.opensearch.ml.common.CommonValue.SCHEMA_VERSION_FIELD;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Ignore;
import org.opensearch.action.ActionListener;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.client.AdminClient;
import org.opensearch.client.Client;
import org.opensearch.client.IndicesAdminClient;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.TEST, numDataNodes = 2)
public class MLIndicesHandlerTests extends OpenSearchIntegTestCase {
    ClusterService clusterService;
    Client client;
    MLIndicesHandler mlIndicesHandler;

    String OLD_ML_MODEL_INDEX_MAPPING_V0 = "{\n"
        + "    \"properties\": {\n"
        + "      \"task_id\": { \"type\": \"keyword\" },\n"
        + "      \"algorithm\": {\"type\": \"keyword\"},\n"
        + "      \"model_name\" : { \"type\": \"keyword\"},\n"
        + "      \"model_version\" : { \"type\": \"keyword\"},\n"
        + "      \"model_content\" : { \"type\": \"binary\"}\n"
        + "    }\n"
        + "}";

    String OLD_ML_TASK_INDEX_MAPPING_V0 = "{\n"
        + "    \"properties\": {\n"
        + "      \"model_id\": {\"type\": \"keyword\"},\n"
        + "      \"task_type\": {\"type\": \"keyword\"},\n"
        + "      \"function_name\": {\"type\": \"keyword\"},\n"
        + "      \"state\": {\"type\": \"keyword\"},\n"
        + "      \"input_type\": {\"type\": \"keyword\"},\n"
        + "      \"progress\": {\"type\": \"float\"},\n"
        + "      \"output_index\": {\"type\": \"keyword\"},\n"
        + "      \"worker_node\": {\"type\": \"keyword\"},\n"
        + "      \"create_time\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "      \"last_update_time\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "      \"error\": {\"type\": \"text\"},\n"
        + "      \"user\": {\n"
        + "        \"type\": \"nested\",\n"
        + "        \"properties\": {\n"
        + "          \"name\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\", \"ignore_above\":256}}},\n"
        + "          \"backend_roles\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\"}}},\n"
        + "          \"roles\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\"}}},\n"
        + "          \"custom_attribute_names\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\"}}}\n"
        + "        }\n"
        + "      }\n"
        + "    }\n"
        + "}";;

    @Before
    public void setup() {
        clusterService = clusterService();
        client = client();
        mlIndicesHandler = new MLIndicesHandler(clusterService, client);
    }

    public void testInitMLTaskIndex() {
        ActionListener<Boolean> listener = ActionListener.wrap(r -> { assertTrue(r); }, e -> { throw new RuntimeException(e); });
        mlIndicesHandler.initMLTaskIndex(listener);
    }

    public void testInitMLTaskIndexWithExistingIndex() throws ExecutionException, InterruptedException {
        CreateIndexRequest request = new CreateIndexRequest(ML_TASK_INDEX).mapping(ML_TASK_INDEX_MAPPING);
        client.admin().indices().create(request).get();
        testInitMLTaskIndex();
    }

    @Ignore
    public void testInitMLModelIndexIfAbsentWithExistingIndex() throws ExecutionException, InterruptedException, IOException {
        testInitMLIndexIfAbsentWithExistingIndex(ML_MODEL_INDEX, OLD_ML_MODEL_INDEX_MAPPING_V0);
    }

    public void testInitMLTaskIndexIfAbsentWithExistingIndex() throws ExecutionException, InterruptedException, IOException {
        testInitMLIndexIfAbsentWithExistingIndex(ML_TASK_INDEX, OLD_ML_TASK_INDEX_MAPPING_V0);
    }

    private void testInitMLIndexIfAbsentWithExistingIndex(String indexName, String oldIndexMapping) throws ExecutionException,
        InterruptedException,
        IOException {
        mlIndicesHandler
            .shouldUpdateIndex(
                indexName,
                1,
                ActionListener.wrap(shouldUpdate -> { assertFalse(shouldUpdate); }, e -> { throw new RuntimeException(e); })
            );
        CreateIndexRequest request = new CreateIndexRequest(indexName).mapping(oldIndexMapping);
        client.admin().indices().create(request).get();
        mlIndicesHandler
            .shouldUpdateIndex(
                indexName,
                1,
                ActionListener.wrap(shouldUpdate -> { assertTrue(shouldUpdate); }, e -> { throw new RuntimeException(e); })
            );
        assertNull(getIndexSchemaVersion(indexName));
        ActionListener<Boolean> listener = ActionListener.wrap(r -> {
            assertTrue(r);
            Integer indexSchemaVersion = getIndexSchemaVersion(indexName);
            if (indexSchemaVersion != null) {
                assertEquals(1, indexSchemaVersion.intValue());
                mlIndicesHandler
                    .shouldUpdateIndex(
                        indexName,
                        1,
                        ActionListener.wrap(shouldUpdate -> { assertFalse(shouldUpdate); }, e -> { throw new RuntimeException(e); })
                    );
            }
        }, e -> { throw new RuntimeException(e); });
        mlIndicesHandler.initModelIndexIfAbsent(listener);
    }

    public void testInitMLModelIndexIfAbsentWithNonExistingIndex() {
        ActionListener<Boolean> listener = ActionListener.wrap(r -> { assertTrue(r); }, e -> { throw new RuntimeException(e); });
        mlIndicesHandler.initModelIndexIfAbsent(listener);
    }

    public void testInitMLModelIndexIfAbsentWithNonExistingIndex_Exception() {
        Client mockClient = mock(Client.class);
        Object[] objects = setUpMockClient(mockClient);
        IndicesAdminClient adminClient = (IndicesAdminClient) objects[0];
        MLIndicesHandler mlIndicesHandler = (MLIndicesHandler) objects[1];
        String errorMessage = "test exception";
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException(errorMessage));
            return null;
        }).when(adminClient).create(any(), any());
        ActionListener<Boolean> listener = ActionListener
            .wrap(r -> { throw new RuntimeException("unexpected result"); }, e -> { assertEquals(errorMessage, e.getMessage()); });
        mlIndicesHandler.initModelIndexIfAbsent(listener);

        when(mockClient.threadPool()).thenThrow(new RuntimeException(errorMessage));
        mlIndicesHandler.initModelIndexIfAbsent(listener);
    }

    public void testInitMLModelIndexIfAbsentWithNonExistingIndex_FalseAcknowledge() {
        Client mockClient = mock(Client.class);
        Object[] objects = setUpMockClient(mockClient);
        IndicesAdminClient adminClient = (IndicesAdminClient) objects[0];
        MLIndicesHandler mlIndicesHandler = (MLIndicesHandler) objects[1];
        doAnswer(invocation -> {
            ActionListener<CreateIndexResponse> actionListener = invocation.getArgument(1);
            CreateIndexResponse response = new CreateIndexResponse(false, false, ML_MODEL_INDEX);
            actionListener.onResponse(response);
            return null;
        }).when(adminClient).create(any(), any());
        ActionListener<Boolean> listener = ActionListener.wrap(r -> { assertFalse(r); }, e -> { throw new RuntimeException(e); });
        mlIndicesHandler.initModelIndexIfAbsent(listener);
    }

    private Object[] setUpMockClient(Client mockClient) {
        AdminClient admin = spy(client.admin());
        when(mockClient.admin()).thenReturn(admin);
        IndicesAdminClient adminClient = spy(client.admin().indices());

        MLIndicesHandler mlIndicesHandler = new MLIndicesHandler(clusterService, mockClient);
        when(admin.indices()).thenReturn(adminClient);

        when(mockClient.threadPool()).thenReturn(client.threadPool());

        return new Object[] { adminClient, mlIndicesHandler };
    }

    private Integer getIndexSchemaVersion(String indexName) {
        IndexMetadata indexMetaData = clusterService.state().getMetadata().indices().get(indexName);
        if (indexMetaData == null) {
            return null;
        }
        Integer oldVersion = null;
        Map<String, Object> indexMapping = indexMetaData.mapping().getSourceAsMap();
        Object meta = indexMapping.get(META);
        if (meta != null && meta instanceof Map) {
            Map<String, Object> metaMapping = (Map<String, Object>) meta;
            Object schemaVersion = metaMapping.get(SCHEMA_VERSION_FIELD);
            if (schemaVersion instanceof Integer) {
                oldVersion = (Integer) schemaVersion;
            }
        }
        return oldVersion;
    }
}
