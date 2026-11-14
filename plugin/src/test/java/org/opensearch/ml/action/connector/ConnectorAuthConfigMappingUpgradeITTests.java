/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorRequest;
import org.opensearch.ml.common.transport.model.MLUpdateModelAction;
import org.opensearch.ml.common.transport.model.MLUpdateModelInput;
import org.opensearch.ml.common.transport.model.MLUpdateModelRequest;
import org.opensearch.ml.common.utils.IndexUtils;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.test.OpenSearchIntegTestCase;

/**
 * The update paths bring the connector and model index mappings up to date before every write, as create and register
 * do: starting from the previous mapping, the first update that carries a configuration must not leave a dynamic
 * object mapping behind, or every later mapping upgrade of the index would fail.
 *
 * <p>Each case gets a fresh cluster. The sync-up cron is off, so only the update paths can refresh a mapping, and the
 * per-process mapping flags are reset before each case.
 */
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.TEST, numDataNodes = 1)
public class ConnectorAuthConfigMappingUpgradeITTests extends MLCommonsIntegTestCase {

    private static final String ACTIONS = "[{\"action_type\":\"PREDICT\",\"method\":\"POST\","
        + "\"url\":\"https://api.openai.com/v1/chat/completions\",\"request_body\":\"{}\"}]";
    private static final String CONNECTOR_DOCUMENT = "{\"name\":\"c\",\"version\":\"1\",\"description\":\"stored\",\"protocol\":\"http\","
        + "\"parameters\":{\"model\":\"x\"},\"actions\":"
        + ACTIONS
        + ",\"created_time\":1700000000000,\"last_updated_time\":1700000000000}";

    @Override
    protected Settings nodeSettings(int ordinal) {
        return Settings.builder().put(super.nodeSettings(ordinal)).put(ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS.getKey(), 0).build();
    }

    @Before
    public void resetMappingFlags() throws Exception {
        setMappingUpdated(ML_CONNECTOR_INDEX, false);
        setMappingUpdated(ML_MODEL_INDEX, false);
    }

    @SuppressWarnings("unchecked")
    private static void setMappingUpdated(String indexName, boolean value) throws Exception {
        Field field = MLIndicesHandler.class.getDeclaredField("indexMappingUpdated");
        field.setAccessible(true);
        ((Map<String, AtomicBoolean>) field.get(null)).get(indexName).set(value);
    }

    private static XContentParser parser(String json) throws IOException {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        return parser;
    }

    private static String toJson(Map<String, Object> map) throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.map(map);
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> mapping) {
        return (Map<String, Object>) mapping.get("properties");
    }

    /** The current mapping file with the given schema version and without auth_config. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> previousMapping(MLIndex index, int schemaVersion) {
        Map<String, Object> mapping = XContentHelper.convertToMap(XContentType.JSON.xContent(), index.getMapping(), true);
        ((Map<String, Object>) mapping.get("_meta")).put("schema_version", schemaVersion);
        Map<String, Object> connectorProperties = index == MLIndex.MODEL
            ? (Map<String, Object>) ((Map<String, Object>) properties(mapping).get("connector")).get("properties")
            : properties(mapping);
        assertNotNull("the current mapping declares auth_config", connectorProperties.remove("auth_config"));
        return mapping;
    }

    private void createIndex(String indexName, Map<String, Object> mapping) throws IOException {
        if (client().admin().indices().prepareExists(indexName).get().isExists()) {
            client().admin().indices().delete(new DeleteIndexRequest(indexName)).actionGet();
        }
        client()
            .admin()
            .indices()
            .create(
                new CreateIndexRequest(indexName).mapping(toJson(mapping), XContentType.JSON).settings(IndexUtils.DEFAULT_INDEX_SETTINGS)
            )
            .actionGet();
    }

    private void index(String indexName, String id, String document) {
        client()
            .index(
                new IndexRequest(indexName)
                    .id(id)
                    .source(document, XContentType.JSON)
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
            )
            .actionGet();
    }

    private Map<String, Object> mappingOf(String indexName) {
        GetMappingsResponse response = client().admin().indices().prepareGetMappings(indexName).get();
        return response.getMappings().get(indexName).getSourceAsMap();
    }

    @SuppressWarnings("unchecked")
    private static Object schemaVersion(Map<String, Object> mapping) {
        return ((Map<String, Object>) mapping.get("_meta")).get("schema_version");
    }

    private static MLCreateConnectorInput declareNone() throws IOException {
        return MLCreateConnectorInput.parse(parser("{\"auth_config\":{\"type\":\"none\"}}"), true);
    }

    private void updateConnector(String connectorId) throws IOException {
        client()
            .execute(
                MLUpdateConnectorAction.INSTANCE,
                MLUpdateConnectorRequest.builder().connectorId(connectorId).updateContent(declareNone()).build()
            )
            .actionGet();
    }

    @SuppressWarnings("unchecked")
    public void testConnectorUpdateRefreshesTheConnectorMappingBeforeItWrites() throws Exception {
        createIndex(ML_CONNECTOR_INDEX, previousMapping(MLIndex.CONNECTOR, 5));
        index(ML_CONNECTOR_INDEX, "c1", CONNECTOR_DOCUMENT);

        updateConnector("c1");

        // Right after the update, before any create could refresh it.
        Map<String, Object> mapping = mappingOf(ML_CONNECTOR_INDEX);
        assertEquals(6, schemaVersion(mapping));
        assertEquals("flat_object", ((Map<String, Object>) properties(mapping).get("auth_config")).get("type"));
        GetResponse stored = client().prepareGet(ML_CONNECTOR_INDEX, "c1").get();
        assertEquals(Map.of("type", "none"), stored.getSourceAsMap().get("auth_config"));
    }

    @SuppressWarnings("unchecked")
    public void testModelUpdateRefreshesTheModelMappingBeforeItWrites() throws Exception {
        createIndex(ML_MODEL_INDEX, previousMapping(MLIndex.MODEL, 14));
        index(
            ML_MODEL_INDEX,
            "m1",
            "{\"name\":\"m\",\"algorithm\":\"REMOTE\",\"model_version\":\"1\",\"model_state\":\"REGISTERED\",\"connector\":"
                + CONNECTOR_DOCUMENT
                + ",\"created_time\":1700000000000,\"last_updated_time\":1700000000000}"
        );

        MLUpdateModelInput input = MLUpdateModelInput.builder().modelId("m1").connector(declareNone()).build();
        client().execute(MLUpdateModelAction.INSTANCE, MLUpdateModelRequest.builder().updateModelInput(input).build()).actionGet();

        Map<String, Object> mapping = mappingOf(ML_MODEL_INDEX);
        assertEquals(15, schemaVersion(mapping));
        Map<String, Object> connector = (Map<String, Object>) properties(mapping).get("connector");
        assertEquals("flat_object", ((Map<String, Object>) properties(connector).get("auth_config")).get("type"));
        GetResponse stored = client().prepareGet(ML_MODEL_INDEX, "m1").get();
        assertEquals(Map.of("type", "none"), ((Map<String, Object>) stored.getSourceAsMap().get("connector")).get("auth_config"));
    }
}
