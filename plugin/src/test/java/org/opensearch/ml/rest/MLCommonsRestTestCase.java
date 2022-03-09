/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_ENABLED;
import static org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_FILEPATH;
import static org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_KEYPASSWORD;
import static org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_PASSWORD;
import static org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_PEMCERT_FILEPATH;
import static org.opensearch.ml.stats.StatNames.ML_TOTAL_FAILURE_COUNT;
import static org.opensearch.ml.stats.StatNames.ML_TOTAL_REQUEST_COUNT;
import static org.opensearch.ml.utils.TestData.trainModelDataJson;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.common.io.PathUtils;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.DeprecationHandler;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.rest.SecureRestClientBuilder;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLAlgoParams;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.StatNames;
import org.opensearch.ml.utils.TestData;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.rest.RestStatus;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.rest.OpenSearchRestTestCase;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;

public abstract class MLCommonsRestTestCase extends OpenSearchRestTestCase {
    protected Gson gson = new Gson();

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
        XContentType xContentType = XContentType.fromMediaTypeOrFormat(response.getEntity().getContentType().getValue());
        try (
            XContentParser parser = xContentType
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
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, password));
            try {
                return httpClientBuilder
                    .setDefaultCredentialsProvider(credentialsProvider)
                    // disable the certificate since our testing cluster just uses the default security configuration
                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .setSSLContext(SSLContextBuilder.create().loadTrustMaterial(null, (chains, authType) -> true).build());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        final String socketTimeoutString = settings.get(CLIENT_SOCKET_TIMEOUT);
        final TimeValue socketTimeout = TimeValue
            .parseTimeValue(socketTimeoutString == null ? "60s" : socketTimeoutString, CLIENT_SOCKET_TIMEOUT);
        builder.setRequestConfigCallback(conf -> conf.setSocketTimeout(Math.toIntExact(socketTimeout.getMillis())));
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

    protected Response ingestIrisData(String indexName) throws IOException {
        String irisDataIndexMapping = "";
        TestHelper
            .makeRequest(
                client(),
                "PUT",
                indexName,
                null,
                TestHelper.toHttpEntity(irisDataIndexMapping),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
            );

        Response statsResponse = TestHelper.makeRequest(client(), "GET", indexName, ImmutableMap.of(), "", null);
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
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
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
        HttpEntity entity = statsResponse.getEntity();
        assertNotNull(statsResponse);
        String entityString = TestHelper.httpEntityToString(entity);
        Map<String, Object> map = gson.fromJson(entityString, Map.class);
        int totalFailureCount = 0;
        int totalAlgoFailureCount = 0;
        int totalRequestCount = 0;
        int totalAlgoRequestCount = 0;
        for (String key : map.keySet()) {
            Map<String, Object> nodeStatsMap = (Map<String, Object>) map.get(key);
            if (nodeStatsMap.containsKey(ML_TOTAL_FAILURE_COUNT)) {
                totalFailureCount += (Double) nodeStatsMap.get(ML_TOTAL_FAILURE_COUNT);
            }
            String failureCountStat = StatNames.failureCountStat(functionName, actionName);
            if (nodeStatsMap.containsKey(failureCountStat)) {
                totalAlgoFailureCount += (Double) nodeStatsMap.get(failureCountStat);
            }
            if (nodeStatsMap.containsKey(ML_TOTAL_REQUEST_COUNT)) {
                totalRequestCount += (Double) nodeStatsMap.get(ML_TOTAL_REQUEST_COUNT);
            }
            String requestCountStat = StatNames.requestCountStat(functionName, actionName);
            if (nodeStatsMap.containsKey(requestCountStat)) {
                totalAlgoRequestCount += (Double) nodeStatsMap.get(requestCountStat);
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

    public void trainAsyncWithSample(Consumer<Map<String, Object>> consumer, boolean async) throws IOException {
        String endpoint = "/_plugins/_ml/_train/sample_algo";
        if (async) {
            endpoint += "?async=true";
        }
        Response response = TestHelper
            .makeRequest(client(), "POST", endpoint, ImmutableMap.of(), TestHelper.toHttpEntity(trainModelDataJson()), null);
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
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
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
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
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
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
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
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
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
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
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
            .indices(ImmutableList.of(indexName))
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLInput kmeansInput = MLInput.builder().algorithm(functionName).parameters(params).inputDataset(inputData).build();
        Response response = TestHelper
            .makeRequest(
                client,
                "POST",
                "/_plugins/_ml/_train_predict/" + functionName.name().toLowerCase(Locale.ROOT),
                ImmutableMap.of(),
                TestHelper.toHttpEntity(kmeansInput),
                null
            );
        HttpEntity entity = response.getEntity();
        assertNotNull(response);
        String entityString = TestHelper.httpEntityToString(entity);
        Map map = gson.fromJson(entityString, Map.class);
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
            .indices(ImmutableList.of(indexName))
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLInput kmeansInput = MLInput.builder().algorithm(functionName).parameters(params).inputDataset(inputData).build();
        String endpoint = "/_plugins/_ml/_train/" + functionName.name().toLowerCase(Locale.ROOT);
        if (async) {
            endpoint += "?async=true";
        }
        Response response = TestHelper.makeRequest(client, "POST", endpoint, ImmutableMap.of(), TestHelper.toHttpEntity(kmeansInput), null);
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
            .indices(ImmutableList.of(indexName))
            .searchSourceBuilder(searchSourceBuilder)
            .build();
        MLInput kmeansInput = MLInput.builder().algorithm(functionName).parameters(params).inputDataset(inputData).build();
        String endpoint = "/_plugins/_ml/_predict/" + functionName.name().toLowerCase(Locale.ROOT) + "/" + modelId;
        Response response = TestHelper.makeRequest(client, "POST", endpoint, ImmutableMap.of(), TestHelper.toHttpEntity(kmeansInput), null);
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

    private void verifyResponse(Consumer<Map<String, Object>> function, Response response) throws IOException {
        HttpEntity entity = response.getEntity();
        assertNotNull(response);
        String entityString = TestHelper.httpEntityToString(entity);
        Map<String, Object> map = gson.fromJson(entityString, Map.class);
        if (function != null) {
            function.accept(map);
        }
    }
}
