/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;
import static org.opensearch.cluster.node.DiscoveryNodeRole.DATA_ROLE;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ALGORITHM;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.logging.log4j.util.Strings;
import org.opensearch.Version;
import org.opensearch.client.Request;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.WarningsHandler;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.execute.metricscorrelation.MetricsCorrelationInput;
import org.opensearch.ml.common.input.execute.samplecalculator.LocalSampleCalculatorInput;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.ml.stats.MLStatsInput;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.SearchModule;
import org.opensearch.test.rest.FakeRestRequest;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

public class TestHelper {

    public static final Setting<Boolean> IS_ML_NODE_SETTING = Setting.boolSetting("node.ml", false, Setting.Property.NodeScope);

    public static final DiscoveryNodeRole ML_ROLE = new DiscoveryNodeRole("ml", "ml") {
        @Override
        public Setting<Boolean> legacySetting() {
            return IS_ML_NODE_SETTING;
        }
    };

    public static XContentParser parser(String xc) throws IOException {
        return parser(xc, true);
    }

    public static XContentParser parser(String xc, boolean skipFirstToken) throws IOException {
        XContentParser parser = XContentType.JSON.xContent().createParser(xContentRegistry(), LoggingDeprecationHandler.INSTANCE, xc);
        if (skipFirstToken) {
            parser.nextToken();
        }
        return parser;
    }

    public static NamedXContentRegistry xContentRegistry() {
        SearchModule searchModule = new SearchModule(Settings.EMPTY, Collections.emptyList());
        return new NamedXContentRegistry(searchModule.getNamedXContents());
    }

    public static String toJsonString(ToXContentObject object) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        return xContentBuilderToString(object.toXContent(builder, ToXContent.EMPTY_PARAMS));
    }

    public static String xContentBuilderToString(XContentBuilder builder) {
        return BytesReference.bytes(builder).utf8ToString();
    }

    public static Response makeRequest(
        RestClient client,
        String method,
        String endpoint,
        Map<String, String> params,
        String jsonEntity,
        List<Header> headers
    ) throws IOException {
        HttpEntity httpEntity = Strings.isBlank(jsonEntity) ? null : new StringEntity(jsonEntity, APPLICATION_JSON);
        return makeRequest(client, method, endpoint, params, httpEntity, headers);
    }

    public static Response makeRequest(
        RestClient client,
        String method,
        String endpoint,
        Map<String, String> params,
        HttpEntity entity,
        List<Header> headers
    ) throws IOException {
        return makeRequest(client, method, endpoint, params, entity, headers, false);
    }

    public static Response makeRequest(
        RestClient client,
        String method,
        String endpoint,
        Map<String, String> params,
        HttpEntity entity,
        List<Header> headers,
        boolean strictDeprecationMode
    ) throws IOException {
        Request request = new Request(method, endpoint);

        RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
        if (headers != null) {
            headers.forEach(header -> options.addHeader(header.getName(), header.getValue()));
        }
        options.setWarningsHandler(strictDeprecationMode ? WarningsHandler.STRICT : WarningsHandler.PERMISSIVE);
        request.setOptions(options.build());

        if (params != null) {
            params.entrySet().forEach(it -> request.addParameter(it.getKey(), it.getValue()));
        }
        if (entity != null) {
            request.setEntity(entity);
        }
        return client.performRequest(request);
    }

    public static HttpEntity toHttpEntity(ToXContentObject object) throws IOException {
        return new StringEntity(toJsonString(object), APPLICATION_JSON);
    }

    public static HttpEntity toHttpEntity(String jsonString) throws IOException {
        return new StringEntity(jsonString, APPLICATION_JSON);
    }

    public static RestStatus restStatus(Response response) {
        return RestStatus.fromCode(response.getStatusLine().getStatusCode());
    }

    public static String httpEntityToString(HttpEntity entity) throws IOException {
        InputStream inputStream = entity.getContent();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "iso-8859-1"));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line + "\n");
        }
        return sb.toString();
    }

    public static RestRequest getKMeansRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_ALGORITHM, FunctionName.KMEANS.name());
        final String requestContent = "{\"parameters\":{\"centroids\":3,\"iterations\":10,\"distance_type\":"
            + "\"COSINE\"},\"input_query\":{\"_source\":[\"petal_length_in_cm\",\"petal_width_in_cm\"],"
            + "\"size\":10000},\"input_index\":[\"iris_data\"]}";
        RestRequest request = new FakeRestRequest.Builder(getXContentRegistry())
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    public static RestRequest getCreateConnectorRestRequest() {
        final String requestContent = "{\n"
            + "    \"name\": \"OpenAI Connector\",\n"
            + "    \"description\": \"The connector to public OpenAI model service for GPT 3.5\",\n"
            + "    \"version\": 1,\n"
            + "    \"protocol\": \"http\",\n"
            + "    \"parameters\": {\n"
            + "        \"endpoint\": \"api.openai.com\",\n"
            + "        \"auth\": \"API_Key\",\n"
            + "        \"content_type\": \"application/json\",\n"
            + "        \"max_tokens\": 7,\n"
            + "        \"temperature\": 0,\n"
            + "        \"model\": \"text-davinci-003\"\n"
            + "    },\n"
            + "    \"credential\": {\n"
            + "        \"openAI_key\": \"xxxxxxxx\"\n"
            + "    },\n"
            + "    \"actions\": [\n"
            + "        {\n"
            + "            \"action_type\": \"predict\",\n"
            + "            \"method\": \"POST\",\n"
            + "            \"url\": \"https://${parameters.endpoint}/v1/completions\",\n"
            + "            \"headers\": {\n"
            + "                \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
            + "            },\n"
            + "            \"request_body\": \"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"prompt\\\": \\\"${parameters.prompt}\\\", \\\"max_tokens\\\": ${parameters.max_tokens}, \\\"temperature\\\": ${parameters.temperature} }\"\n"
            + "        }\n"
            + "    ],\n"
            + "    \"access_mode\": \"public\"\n"
            + "}";
        RestRequest request = new FakeRestRequest.Builder(getXContentRegistry())
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    public static void verifyParsedCreateConnectorInput(MLCreateConnectorInput mlCreateConnectorInput) {
        assertEquals("OpenAI Connector", mlCreateConnectorInput.getName());
        assertEquals("http", mlCreateConnectorInput.getProtocol());
        assertNotNull(mlCreateConnectorInput.getActions());
        assertNotNull(mlCreateConnectorInput.getCredential());
    }

    public static RestRequest getStatsRestRequest(MLStatsInput input) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String requestContent = TestHelper.xContentBuilderToString(builder);

        RestRequest request = new FakeRestRequest.Builder(getXContentRegistry())
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    public static RestRequest getProfileRestRequest(MLProfileInput input) throws IOException {
        return new FakeRestRequest.Builder(getXContentRegistry())
            .withContent(new BytesArray(buildRequestContent(input)), XContentType.JSON)
            .build();
    }

    public static RestRequest getProfileRestRequestWithQueryParams(MLProfileInput input, Map<String, String> params) throws IOException {
        return new FakeRestRequest.Builder(getXContentRegistry())
            .withContent(new BytesArray(buildRequestContent(input)), XContentType.JSON)
            .withParams(params)
            .build();
    }

    private static String buildRequestContent(MLProfileInput input) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        return TestHelper.xContentBuilderToString(builder);
    }

    public static RestRequest getStatsRestRequest() {
        RestRequest request = new FakeRestRequest.Builder(getXContentRegistry()).build();
        return request;
    }

    public static RestRequest getStatsRestRequest(String nodeId, String stat) {
        RestRequest request = new FakeRestRequest.Builder(getXContentRegistry())
            .withParams(ImmutableMap.of("nodeId", nodeId, "stat", stat))
            .build();
        return request;
    }

    public static RestRequest getLocalSampleCalculatorRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_ALGORITHM, FunctionName.LOCAL_SAMPLE_CALCULATOR.name());
        final String requestContent = "{\"operation\": \"max\",\"input_data\":[1.0, 2.0, 3.0]}";
        RestRequest request = new FakeRestRequest.Builder(getXContentRegistry())
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    public static RestRequest getMetricsCorrelationRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_ALGORITHM, FunctionName.METRICS_CORRELATION.name());
        final String requestContent = "{\"metrics\":[[1.0, 2.0, 3.0], [1.0 ,2.0, 3.0]]}";
        return new FakeRestRequest.Builder(getXContentRegistry())
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
    }

    public static RestRequest getExecuteAgentRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_AGENT_ID, "test_agent_id");
        final String requestContent = "{\"name\":\"Test_Agent_For_RAG\",\"type\":\"flow\","
            + "\"description\":\"this is a test agent\",\"app_type\":\"my app\","
            + "\"tools\":[{\"type\":\"CatIndexTool\",\"name\":\"CatIndexTool\","
            + "\"description\":\"Use this tool to get OpenSearch index information: "
            + "(health, status, index, uuid, primary count, replica count, docs.count, docs.deleted, "
            + "store.size, primary.store.size).\",\"include_output_in_agent_response\":true}]}";
        return new FakeRestRequest.Builder(getXContentRegistry())
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .withPath("/_plugins/_ml/agents/test_agent_id/_execute")
            .build();
    }

    public static RestRequest getSearchAllRestRequest() {
        RestRequest request = new FakeRestRequest.Builder(getXContentRegistry())
            .withContent(new BytesArray(TestData.matchAllSearchQuery()), XContentType.JSON)
            .build();
        return request;
    }

    public static void verifyParsedKMeansMLInput(MLInput mlInput) {
        assertEquals(FunctionName.KMEANS, mlInput.getAlgorithm());
        assertEquals(MLInputDataType.SEARCH_QUERY, mlInput.getInputDataset().getInputDataType());
        SearchQueryInputDataset inputDataset = (SearchQueryInputDataset) mlInput.getInputDataset();
        assertEquals(1, inputDataset.getIndices().size());
        assertEquals("iris_data", inputDataset.getIndices().get(0));
        KMeansParams kMeansParams = (KMeansParams) mlInput.getParameters();
        assertEquals(3, kMeansParams.getCentroids().intValue());
    }

    private static NamedXContentRegistry getXContentRegistry() {
        SearchModule searchModule = new SearchModule(Settings.EMPTY, Collections.emptyList());
        List<NamedXContentRegistry.Entry> entries = new ArrayList<>();
        entries.addAll(searchModule.getNamedXContents());
        entries.add(KMeansParams.XCONTENT_REGISTRY);
        entries.add(LocalSampleCalculatorInput.XCONTENT_REGISTRY);
        entries.add(MetricsCorrelationInput.XCONTENT_REGISTRY);
        return new NamedXContentRegistry(entries);
    }

    public static ClusterState state(
        ClusterName name,
        String indexName,
        String mapping,
        DiscoveryNode localNode,
        DiscoveryNode clusterManagerNode,
        List<DiscoveryNode> allNodes
    ) throws IOException {
        DiscoveryNodes.Builder discoBuilder = DiscoveryNodes.builder();
        for (DiscoveryNode node : allNodes) {
            discoBuilder.add(node);
        }
        if (clusterManagerNode != null) {
            discoBuilder.masterNodeId(clusterManagerNode.getId());
        }
        discoBuilder.localNodeId(localNode.getId());

        Settings indexSettings = Settings
            .builder()
            .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
            .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 1)
            .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
            .build();
        final Settings.Builder existingSettings = Settings.builder().put(indexSettings).put(IndexMetadata.SETTING_INDEX_UUID, "test2UUID");
        IndexMetadata indexMetaData = IndexMetadata.builder(indexName).settings(existingSettings).putMapping(mapping).build();

        final Map<String, IndexMetadata> indices = Map.of(indexName, indexMetaData);

        return ClusterState.builder(name).metadata(Metadata.builder().indices(indices).build()).build();
    }

    public static ClusterState state(int numDataNodes, String indexName, String mapping) throws IOException {
        DiscoveryNode clusterManagerNode = new DiscoveryNode(
            "foo0",
            "foo0",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        List<DiscoveryNode> allNodes = new ArrayList<>();
        allNodes.add(clusterManagerNode);
        for (int i = 1; i <= numDataNodes - 1; i++) {
            allNodes
                .add(
                    new DiscoveryNode(
                        "foo" + i,
                        "foo" + i,
                        new TransportAddress(InetAddress.getLoopbackAddress(), 9300 + i),
                        Collections.emptyMap(),
                        Collections.singleton(DATA_ROLE),
                        Version.CURRENT
                    )
                );
        }
        return state(new ClusterName("test"), indexName, mapping, clusterManagerNode, clusterManagerNode, allNodes);
    }

    public static ClusterState setupTestClusterState() {
        Set<DiscoveryNodeRole> roleSet = new HashSet<>();
        roleSet.add(DiscoveryNodeRole.DATA_ROLE);
        DiscoveryNode node = new DiscoveryNode(
            "node",
            new TransportAddress(TransportAddress.META_ADDRESS, new AtomicInteger().incrementAndGet()),
            new HashMap<>(),
            roleSet,
            Version.CURRENT
        );
        Metadata metadata = new Metadata.Builder()
            .indices(
                ImmutableMap
                    .<String, IndexMetadata>builder()
                    .put(
                        ML_MODEL_INDEX,
                        IndexMetadata
                            .builder("test")
                            .settings(
                                Settings
                                    .builder()
                                    .put("index.number_of_shards", 1)
                                    .put("index.number_of_replicas", 1)
                                    .put("index.version.created", Version.CURRENT.id)
                            )
                            .build()
                    )
                    .build()
            )
            .build();
        return new ClusterState(
            new ClusterName("test cluster"),
            123l,
            "111111",
            metadata,
            null,
            DiscoveryNodes.builder().add(node).build(),
            null,
            Map.of(),
            0,
            false
        );
    }

    public static ClusterSettings clusterSetting(Settings settings, Setting<?>... setting) {
        final Set<Setting<?>> settingsSet = Stream
            .concat(ClusterSettings.BUILT_IN_CLUSTER_SETTINGS.stream(), Sets.newHashSet(setting).stream())
            .collect(Collectors.toSet());
        ClusterSettings clusterSettings = new ClusterSettings(settings, settingsSet);
        return clusterSettings;
    }

    public static XContentBuilder builder() throws IOException {
        return XContentBuilder.builder(XContentType.JSON.xContent());
    }

    public static void copyFile(String sourceFile, String destFile) throws IOException {
        File destF = new File(destFile);
        if (!destF.getParentFile().exists()) {
            destF.getParentFile().mkdirs();
        }
        FileUtils.copyFile(new File(sourceFile), new File(destFile));
    }

}
