/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.bwc;

import static org.opensearch.client.RestClientBuilder.DEFAULT_MAX_CONN_PER_ROUTE;
import static org.opensearch.client.RestClientBuilder.DEFAULT_MAX_CONN_TOTAL;
import static org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_ENABLED;
import static org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_FILEPATH;
import static org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_KEYPASSWORD;
import static org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_PASSWORD;
import static org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_PEMCERT_FILEPATH;
import static org.opensearch.ml.common.MLTask.FUNCTION_NAME_FIELD;
import static org.opensearch.ml.common.MLTask.MODEL_ID_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTask.TASK_ID_FIELD;
import static org.opensearch.ml.stats.MLNodeLevelStat.ML_FAILURE_COUNT;
import static org.opensearch.ml.stats.MLNodeLevelStat.ML_REQUEST_COUNT;
import static org.opensearch.ml.utils.TestData.SENTENCE_TRANSFORMER_MODEL_URL;
import static org.opensearch.ml.utils.TestData.trainModelDataJson;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.junit.After;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.common.io.PathUtils;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.rest.SecureRestClientBuilder;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.MLActionLevelStat;
import org.opensearch.ml.utils.TestData;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.rest.OpenSearchRestTestCase;

import com.google.gson.Gson;
import com.google.gson.JsonArray;

// TODO: Need to refactor this code in the future because the whole part of it is a copy of MLCommonsRestTestCase.java

public class MLCommonsBackwardsCompatibilityRestTestCase extends OpenSearchRestTestCase {
    protected Gson gson = new Gson();
    public static long CUSTOM_MODEL_TIMEOUT = 20_000; // 20 seconds

    protected boolean isHttps() {
        boolean isHttps = Optional.ofNullable(System.getProperty("https")).map("true"::equalsIgnoreCase).orElse(false);
        if (isHttps) {
            // currently only external cluster is supported for security enabled testing
            if (!Optional.ofNullable(System.getProperty("tests.rest.cluster")).isPresent()) {
                throw new RuntimeException("cluster url should be provided for security enabled testing");
            }
        }

        return isHttps;
    }

    @Override
    protected String getProtocol() {
        return isHttps() ? "https" : "http";
    }

    @Override
    protected Settings restAdminSettings() {
        return Settings
            .builder()
            // disable the warning exception for admin client since it's only used for cleanup.
            .put("strictDeprecationMode", false)
            .put("http.port", 9200)
            .put(OPENSEARCH_SECURITY_SSL_HTTP_ENABLED, isHttps())
            .put(OPENSEARCH_SECURITY_SSL_HTTP_PEMCERT_FILEPATH, "sample.pem")
            .put(OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_FILEPATH, "test-kirk.jks")
            .put(OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_PASSWORD, "changeit")
            .put(OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_KEYPASSWORD, "changeit")
            .build();
    }

    // Utility fn for deleting indices. Should only be used when not allowed in a regular context
    // (e.g., deleting system indices)
    protected static void deleteIndexWithAdminClient(String name) throws IOException {
        Request request = new Request("DELETE", "/" + name);
        adminClient().performRequest(request);
    }

    // Utility fn for checking if an index exists. Should only be used when not allowed in a regular context
    // (e.g., checking existence of system indices)
    protected static boolean indexExistsWithAdminClient(String indexName) throws IOException {
        Request request = new Request("HEAD", "/" + indexName);
        Response response = adminClient().performRequest(request);
        return RestStatus.OK.getStatus() == response.getStatusLine().getStatusCode();
    }

    @Override
    protected RestClient buildClient(Settings settings, HttpHost[] hosts) throws IOException {
        boolean strictDeprecationMode = settings.getAsBoolean("strictDeprecationMode", true);
        RestClientBuilder builder = RestClient.builder(hosts);
        if (isHttps()) {
            String keystore = settings.get(OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_FILEPATH);
            if (Objects.nonNull(keystore)) {
                URI uri = null;
                try {
                    uri = this.getClass().getClassLoader().getResource("security/sample.pem").toURI();
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
                Path configPath = PathUtils.get(uri).getParent().toAbsolutePath();
                return new SecureRestClientBuilder(settings, configPath).build();
            } else {
                configureHttpsClient(builder, settings);
                builder.setStrictDeprecationMode(strictDeprecationMode);
                return builder.build();
            }

        } else {
            configureClient(builder, settings);
            builder.setStrictDeprecationMode(strictDeprecationMode);
            return builder.build();
        }

    }

    @SuppressWarnings("unchecked")
    @After
    protected void wipeAllODFEIndices() throws IOException {
        Response response = adminClient().performRequest(new Request("GET", "/_cat/indices?format=json&expand_wildcards=all"));
        MediaType mediaType = MediaType.fromMediaType(response.getEntity().getContentType());
        try (
            XContentParser parser = mediaType
                .xContent()
                .createParser(
                    NamedXContentRegistry.EMPTY,
                    DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                    response.getEntity().getContent()
                )
        ) {
            XContentParser.Token token = parser.nextToken();
            List<Map<String, Object>> parserList = null;
            if (token == XContentParser.Token.START_ARRAY) {
                parserList = parser.listOrderedMap().stream().map(obj -> (Map<String, Object>) obj).collect(Collectors.toList());
            } else {
                parserList = Collections.singletonList(parser.mapOrdered());
            }

            for (Map<String, Object> index : parserList) {
                String indexName = (String) index.get("index");
                if (indexName != null && !".opendistro_security".equals(indexName)) {
                    adminClient().performRequest(new Request("DELETE", "/" + indexName));
                }
            }
        }
    }

    protected static void configureHttpsClient(RestClientBuilder builder, Settings settings) throws IOException {
        Map<String, String> headers = ThreadContext.buildDefaultHeaders(settings);
        Header[] defaultHeaders = new Header[headers.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            defaultHeaders[i++] = new BasicHeader(entry.getKey(), entry.getValue());
        }
        builder.setDefaultHeaders(defaultHeaders);
        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            String userName = Optional
                .ofNullable(System.getProperty("user"))
                .orElseThrow(() -> new RuntimeException("user name is missing"));
            String password = Optional
                .ofNullable(System.getProperty("password"))
                .orElseThrow(() -> new RuntimeException("password is missing"));
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(userName, password.toCharArray()));
            try {
                final TlsStrategy tlsStrategy = ClientTlsStrategyBuilder
                    .create()
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .setSslContext(SSLContextBuilder.create().loadTrustMaterial(null, (chains, authType) -> true).build())
                    .build();
                final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder
                    .create()
                    .setMaxConnPerRoute(DEFAULT_MAX_CONN_PER_ROUTE)
                    .setMaxConnTotal(DEFAULT_MAX_CONN_TOTAL)
                    .setTlsStrategy(tlsStrategy)
                    .build();
                return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider).setConnectionManager(connectionManager);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        final String socketTimeoutString = settings.get(CLIENT_SOCKET_TIMEOUT);
        final TimeValue socketTimeout = TimeValue
            .parseTimeValue(socketTimeoutString == null ? "60s" : socketTimeoutString, CLIENT_SOCKET_TIMEOUT);
        builder
            .setRequestConfigCallback(conf -> conf.setResponseTimeout(Timeout.ofMilliseconds(Math.toIntExact(socketTimeout.getMillis()))));
        if (settings.hasValue(CLIENT_PATH_PREFIX)) {
            builder.setPathPrefix(settings.get(CLIENT_PATH_PREFIX));
        }
    }

    /**
     * wipeAllIndices won't work since it cannot delete security index. Use wipeAllODFEIndices instead.
     */
    @Override
    protected boolean preserveIndicesUponCompletion() {
        return true;
    }

    protected Response ingestIrisData(String indexName) throws IOException, ParseException {
        String irisDataIndexMapping = "";
        TestHelper
            .makeRequest(
                client(),
                "PUT",
                indexName,
                null,
                TestHelper.toHttpEntity(irisDataIndexMapping),
                List.of(new BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
            );

        Response statsResponse = TestHelper.makeRequest(client(), "GET", indexName, Map.of(), "", null);
        assertEquals(RestStatus.OK, TestHelper.restStatus(statsResponse));
        String result = EntityUtils.toString(statsResponse.getEntity());
        assertTrue(result.contains(indexName));

        Response bulkResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                "_bulk?refresh=true",
                null,
                TestHelper.toHttpEntity(TestData.IRIS_DATA.replaceAll("iris_data", indexName)),
                List.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(RestStatus.OK, TestHelper.restStatus(statsResponse));
        return bulkResponse;
    }

    protected void validateStats(
        FunctionName functionName,
        ActionName actionName,
        int expectedMinimumTotalFailureCount,
        int expectedMinimumTotalAlgoFailureCount,
        int expectedMinimumTotalRequestCount,
        int expectedMinimumTotalAlgoRequestCount
    ) throws IOException {
        Response statsResponse = TestHelper.makeRequest(client(), "GET", "_plugins/_ml/stats", null, "", null);
        Map<String, Object> map = parseResponseToMap(statsResponse);
        int totalFailureCount = 0;
        int totalAlgoFailureCount = 0;
        int totalRequestCount = 0;
        int totalAlgoRequestCount = 0;
        Map<String, Object> allNodeStats = (Map<String, Object>) map.get("nodes");
        for (String key : allNodeStats.keySet()) {
            Map<String, Object> nodeStatsMap = (Map<String, Object>) allNodeStats.get(key);
            String statKey = ML_FAILURE_COUNT.name().toLowerCase(Locale.ROOT);
            if (nodeStatsMap.containsKey(statKey)) {
                totalFailureCount += (Double) nodeStatsMap.get(statKey);
            }
            statKey = ML_REQUEST_COUNT.name().toLowerCase(Locale.ROOT);
            if (nodeStatsMap.containsKey(statKey)) {
                totalRequestCount += (Double) nodeStatsMap.get(statKey);
            }
            Map<String, Object> allAlgoStats = (Map<String, Object>) nodeStatsMap.get("algorithms");
            statKey = functionName.name().toLowerCase(Locale.ROOT);
            if (allAlgoStats.containsKey(statKey)) {
                Map<String, Object> allActionStats = (Map<String, Object>) allAlgoStats.get(statKey);
                String actionKey = actionName.name().toLowerCase(Locale.ROOT);
                Map<String, Object> actionStats = (Map<String, Object>) allActionStats.get(actionKey);

                String actionStatKey = MLActionLevelStat.ML_ACTION_FAILURE_COUNT.name().toLowerCase(Locale.ROOT);
                if (actionStats.containsKey(actionStatKey)) {
                    totalAlgoFailureCount += (Double) actionStats.get(actionStatKey);
                }
                actionStatKey = MLActionLevelStat.ML_ACTION_REQUEST_COUNT.name().toLowerCase(Locale.ROOT);
                if (actionStats.containsKey(actionStatKey)) {
                    totalAlgoRequestCount += (Double) actionStats.get(actionStatKey);
                }
            }
        }
        assertTrue(totalFailureCount >= expectedMinimumTotalFailureCount);
        assertTrue(totalAlgoFailureCount >= expectedMinimumTotalAlgoFailureCount);
        assertTrue(totalRequestCount >= expectedMinimumTotalRequestCount);
        assertTrue(totalAlgoRequestCount >= expectedMinimumTotalAlgoRequestCount);
    }

    protected Response ingestModelData() throws IOException {
        Response trainModelResponse = TestHelper
            .makeRequest(client(), "POST", "_plugins/_ml/_train/sample_algo", null, TestHelper.toHttpEntity(trainModelDataJson()), null);
        HttpEntity entity = trainModelResponse.getEntity();
        assertNotNull(trainModelResponse);
        return trainModelResponse;
    }

    public void trainAsyncWithSample(Consumer<Map<String, Object>> consumer, boolean async) throws IOException, InterruptedException {
        String endpoint = "/_plugins/_ml/_train/sample_algo";
        if (async) {
            endpoint += "?async=true";
        }
        Response response = TestHelper
            .makeRequest(client(), "POST", endpoint, Map.of(), TestHelper.toHttpEntity(trainModelDataJson()), null);
        TimeUnit.SECONDS.sleep(5);
        verifyResponse(consumer, response);
    }

    public Response createIndexRole(String role, String index) throws IOException {
        return TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_opendistro/_security/api/roles/" + role,
                null,
                TestHelper
                    .toHttpEntity(
                        "{\n"
                            + "\"cluster_permissions\": [\n"
                            + "],\n"
                            + "\"index_permissions\": [\n"
                            + "{\n"
                            + "\"index_patterns\": [\n"
                            + "\""
                            + index
                            + "\"\n"
                            + "],\n"
                            + "\"dls\": \"\",\n"
                            + "\"fls\": [],\n"
                            + "\"masked_fields\": [],\n"
                            + "\"allowed_actions\": [\n"
                            + "\"crud\",\n"
                            + "\"indices:admin/create\"\n"
                            + "]\n"
                            + "}\n"
                            + "],\n"
                            + "\"tenant_permissions\": []\n"
                            + "}"
                    ),
                List.of(new BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
            );
    }

    public Response createSearchRole(String role, String index) throws IOException {
        return TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_opendistro/_security/api/roles/" + role,
                null,
                TestHelper
                    .toHttpEntity(
                        "{\n"
                            + "\"cluster_permissions\": [\n"
                            + "],\n"
                            + "\"index_permissions\": [\n"
                            + "{\n"
                            + "\"index_patterns\": [\n"
                            + "\""
                            + index
                            + "\"\n"
                            + "],\n"
                            + "\"dls\": \"\",\n"
                            + "\"fls\": [],\n"
                            + "\"masked_fields\": [],\n"
                            + "\"allowed_actions\": [\n"
                            + "\"indices:data/read/search\"\n"
                            + "]\n"
                            + "}\n"
                            + "],\n"
                            + "\"tenant_permissions\": []\n"
                            + "}"
                    ),
                List.of(new BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
            );
    }

    public Response createUser(String name, String password, ArrayList<String> backendRoles) throws IOException {
        JsonArray backendRolesString = new JsonArray();
        for (int i = 0; i < backendRoles.size(); i++) {
            backendRolesString.add(backendRoles.get(i));
        }
        return TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_opendistro/_security/api/internalusers/" + name,
                null,
                TestHelper
                    .toHttpEntity(
                        " {\n"
                            + "\"password\": \""
                            + password
                            + "\",\n"
                            + "\"backend_roles\": "
                            + backendRolesString
                            + ",\n"
                            + "\"attributes\": {\n"
                            + "}} "
                    ),
                List.of(new BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
            );
    }

    public Response deleteUser(String user) throws IOException {
        return TestHelper
            .makeRequest(
                client(),
                "DELETE",
                "/_opendistro/_security/api/internalusers/" + user,
                null,
                "",
                List.of(new BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
            );
    }

    public Response createRoleMapping(String role, ArrayList<String> users) throws IOException {
        JsonArray usersString = new JsonArray();
        for (int i = 0; i < users.size(); i++) {
            usersString.add(users.get(i));
        }
        return TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_opendistro/_security/api/rolesmapping/" + role,
                null,
                TestHelper
                    .toHttpEntity(
                        "{\n" + "  \"backend_roles\" : [  ],\n" + "  \"hosts\" : [  ],\n" + "  \"users\" : " + usersString + "\n" + "}"
                    ),
                List.of(new BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
            );
    }

    public void trainAndPredict(
        RestClient client,
        FunctionName functionName,
        String indexName,
        MLAlgoParams params,
        SearchSourceBuilder searchSourceBuilder,
        Consumer<Map<String, Object>> function
    ) throws IOException {
        MLInputDataset inputData = SearchQueryInputDataset
            .builder()
            .indices(List.of(indexName))
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLInput kmeansInput = MLInput.builder().algorithm(functionName).parameters(params).inputDataset(inputData).build();
        Response response = TestHelper
            .makeRequest(
                client,
                "POST",
                "/_plugins/_ml/_train_predict/" + functionName.name().toLowerCase(Locale.ROOT),
                Map.of(),
                TestHelper.toHttpEntity(kmeansInput),
                null
            );
        Map map = parseResponseToMap(response);
        Map<String, Object> predictionResult = (Map<String, Object>) map.get("prediction_result");
        if (function != null) {
            function.accept(predictionResult);
        }
    }

    public void train(
        RestClient client,
        FunctionName functionName,
        String indexName,
        MLAlgoParams params,
        SearchSourceBuilder searchSourceBuilder,
        Consumer<Map<String, Object>> function,
        boolean async
    ) throws IOException {
        MLInputDataset inputData = SearchQueryInputDataset
            .builder()
            .indices(List.of(indexName))
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLInput kmeansInput = MLInput.builder().algorithm(functionName).parameters(params).inputDataset(inputData).build();
        String endpoint = "/_plugins/_ml/_train/" + functionName.name().toLowerCase(Locale.ROOT);
        if (async) {
            endpoint += "?async=true";
        }
        Response response = TestHelper.makeRequest(client, "POST", endpoint, Map.of(), TestHelper.toHttpEntity(kmeansInput), null);
        verifyResponse(function, response);
    }

    public void predict(
        RestClient client,
        FunctionName functionName,
        String modelId,
        String indexName,
        MLAlgoParams params,
        SearchSourceBuilder searchSourceBuilder,
        Consumer<Map<String, Object>> function
    ) throws IOException {
        MLInputDataset inputData = SearchQueryInputDataset
            .builder()
            .indices(List.of(indexName))
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLInput kmeansInput = MLInput.builder().algorithm(functionName).parameters(params).inputDataset(inputData).build();
        String endpoint = "/_plugins/_ml/_predict/" + functionName.name().toLowerCase(Locale.ROOT) + "/" + modelId;
        Response response = TestHelper.makeRequest(client, "POST", endpoint, Map.of(), TestHelper.toHttpEntity(kmeansInput), null);
        verifyResponse(function, response);
    }

    public void getModel(RestClient client, String modelId, Consumer<Map<String, Object>> function) throws IOException {
        Response response = TestHelper.makeRequest(client, "GET", "/_plugins/_ml/models/" + modelId, null, "", null);
        verifyResponse(function, response);
    }

    public void getTask(RestClient client, String taskId, Consumer<Map<String, Object>> function) throws IOException {
        Response response = TestHelper.makeRequest(client, "GET", "/_plugins/_ml/tasks/" + taskId, null, "", null);
        verifyResponse(function, response);
    }

    public void deleteModel(RestClient client, String modelId, Consumer<Map<String, Object>> function) throws IOException {
        Response response = TestHelper.makeRequest(client, "DELETE", "/_plugins/_ml/models/" + modelId, null, "", null);
        verifyResponse(function, response);
    }

    public void deleteTask(RestClient client, String taskId, Consumer<Map<String, Object>> function) throws IOException {
        Response response = TestHelper.makeRequest(client, "DELETE", "/_plugins/_ml/tasks/" + taskId, null, "", null);
        verifyResponse(function, response);
    }

    public void searchModelsWithAlgoName(RestClient client, String algoName, Consumer<Map<String, Object>> function) throws IOException {
        String query = String.format(Locale.ROOT, "{\"query\":{\"bool\":{\"filter\":[{\"term\":{\"algorithm\":\"%s\"}}]}}}", algoName);
        searchModels(client, query, function);
    }

    public void searchModels(RestClient client, String query, Consumer<Map<String, Object>> function) throws IOException {
        Response response = TestHelper.makeRequest(client, "GET", "/_plugins/_ml/models/_search", null, query, null);
        verifyResponse(function, response);
    }

    public void searchTasksWithAlgoName(RestClient client, String algoName, Consumer<Map<String, Object>> function) throws IOException {
        String query = String.format(Locale.ROOT, "{\"query\":{\"bool\":{\"filter\":[{\"term\":{\"function_name\":\"%s\"}}]}}}", algoName);
        searchTasks(client, query, function);
    }

    public void searchTasks(RestClient client, String query, Consumer<Map<String, Object>> function) throws IOException {
        Response response = TestHelper.makeRequest(client, "GET", "/_plugins/_ml/tasks/_search", null, query, null);
        verifyResponse(function, response);
    }

    private void verifyResponse(Consumer<Map<String, Object>> verificationConsumer, Response response) throws IOException {
        Map<String, Object> map = parseResponseToMap(response);
        if (verificationConsumer != null) {
            verificationConsumer.accept(map);
        }
    }

    public MLRegisterModelInput createRegisterModelInput() {
        MLModelConfig modelConfig = TextEmbeddingModelConfig
            .builder()
            .modelType("bert")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(768)
            .build();
        return MLRegisterModelInput
            .builder()
            .modelName("test_model_name")
            .version("1.0.0")
            .functionName(FunctionName.TEXT_EMBEDDING)
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .modelConfig(modelConfig)
            .url(SENTENCE_TRANSFORMER_MODEL_URL)
            .deployModel(false)
            .build();
    }

    public void registerModel(RestClient client, String input, Consumer<Map<String, Object>> function) throws IOException {
        Response response = TestHelper.makeRequest(client, "POST", "/_plugins/_ml/models/_register", null, input, null);
        verifyResponse(function, response);
    }

    public String registerModel(String input) throws IOException {
        Response response = TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/models/_register", null, input, null);
        return parseTaskIdFromResponse(response);
    }

    public void deployModel(RestClient client, MLRegisterModelInput registerModelInput, Consumer<Map<String, Object>> function)
        throws IOException,
        InterruptedException {
        String taskId = registerModel(TestHelper.toJsonString(registerModelInput));
        waitForTask(taskId, MLTaskState.COMPLETED);
        getTask(client(), taskId, response -> {
            String algorithm = (String) response.get(FUNCTION_NAME_FIELD);
            assertEquals(registerModelInput.getFunctionName().name(), algorithm);
            assertNotNull(response.get(MODEL_ID_FIELD));
            assertEquals(MLTaskState.COMPLETED.name(), response.get(STATE_FIELD));
            String modelId = (String) response.get(MODEL_ID_FIELD);
            try {
                // deploy model
                deployModel(client, modelId, function);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void deployModel(RestClient client, String modelId, Consumer<Map<String, Object>> function) throws IOException {
        Response response = TestHelper
            .makeRequest(client, "POST", "/_plugins/_ml/models/" + modelId + "/_deploy", null, (String) null, null);
        verifyResponse(function, response);
    }

    public String deployModel(String modelId) throws IOException {
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/models/" + modelId + "/_deploy", null, (String) null, null);
        return parseTaskIdFromResponse(response);
    }

    private String parseTaskIdFromResponse(Response response) throws IOException {
        Map map = parseResponseToMap(response);
        String taskId = (String) map.get(TASK_ID_FIELD);
        return taskId;
    }

    private Map parseResponseToMap(Response response) throws IOException {
        HttpEntity entity = response.getEntity();
        assertNotNull(response);
        String entityString = TestHelper.httpEntityToString(entity);
        return gson.fromJson(entityString, Map.class);
    }

    public Map getModelProfile(String modelId, Consumer verifyFunction) throws IOException {
        Response response = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/profile/models/" + modelId, null, (String) null, null);
        Map profile = parseResponseToMap(response);
        Map<String, Object> nodeProfiles = (Map) profile.get("nodes");
        for (Map.Entry<String, Object> entry : nodeProfiles.entrySet()) {
            Map<String, Object> modelProfiles = (Map) entry.getValue();
            assertNotNull(modelProfiles);
            for (Map.Entry<String, Object> modelProfileEntry : modelProfiles.entrySet()) {
                Map<String, Object> modelProfile = (Map) ((Map) modelProfileEntry.getValue()).get(modelId);
                if (verifyFunction != null) {
                    verifyFunction.accept(modelProfile);
                }
            }
        }
        return profile;
    }

    public MLInput createPredictTextEmbeddingInput() {
        TextDocsInputDataSet textDocsInputDataSet = TextDocsInputDataSet
            .builder()
            .docs(Arrays.asList("today is sunny", "this is a happy dog"))
            .build();
        return MLInput.builder().inputDataset(textDocsInputDataSet).algorithm(FunctionName.TEXT_EMBEDDING).build();
    }

    public Map predictTextEmbedding(String modelId) throws IOException {
        MLInput input = createPredictTextEmbeddingInput();
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/models/" + modelId + "/_predict", null, TestHelper.toJsonString(input), null);
        Map result = parseResponseToMap(response);
        List<Object> embeddings = (List) result.get("inference_results");
        assertEquals(2, embeddings.size());
        for (Object embedding : embeddings) {
            Map<String, Object> embeddingMap = (Map) embedding;
            List<Object> tensors = (List) embeddingMap.get("output");
            assertEquals(1, tensors.size());
            Map<String, Object> tensorMap = (Map) tensors.get(0);
            assertEquals(4, tensorMap.size());
            assertEquals("sentence_embedding", tensorMap.get("name"));
            assertEquals("FLOAT32", tensorMap.get("data_type"));
            List shape = (List) tensorMap.get("shape");
            assertEquals(1, shape.size());
            assertEquals(768, ((Double) shape.get(0)).longValue());
            List data = (List) tensorMap.get("data");
            assertEquals(768, data.size());
        }
        return result;
    }

    public Consumer<Map<String, Object>> verifyTextEmbeddingModelDeployed() {
        return (modelProfile) -> {
            if (modelProfile.containsKey("model_state")) {
                assertEquals(MLModelState.DEPLOYED.name(), modelProfile.get("model_state"));
                assertTrue(((String) modelProfile.get("predictor")).startsWith("org.opensearch.ml.engine.algorithms.TextEmbeddingModel@"));
            }
            List<String> workNodes = (List) modelProfile.get("worker_nodes");
            assertTrue(workNodes.size() > 0);
        };
    }

    public Map undeployModel(String modelId) throws IOException {
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/models/" + modelId + "/_undeploy", null, (String) null, null);
        return parseResponseToMap(response);
    }

    public String getTaskState(String taskId) throws IOException {
        Response response = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/tasks/" + taskId, null, "", null);
        Map<String, Object> task = parseResponseToMap(response);
        return (String) task.get("state");
    }

    public void waitForTask(String taskId, MLTaskState targetState) throws InterruptedException {
        AtomicBoolean taskDone = new AtomicBoolean(false);
        waitUntil(() -> {
            try {
                String state = getTaskState(taskId);
                if (targetState.name().equals(state)) {
                    taskDone.set(true);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return taskDone.get();
        }, CUSTOM_MODEL_TIMEOUT, TimeUnit.SECONDS);
        assertTrue(taskDone.get());
    }
}
