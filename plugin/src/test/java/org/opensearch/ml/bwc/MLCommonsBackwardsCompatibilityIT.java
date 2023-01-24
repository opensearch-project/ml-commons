/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.bwc;

import static org.opensearch.ml.common.input.parameter.clustering.KMeansParams.DistanceType.COSINE;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Assume;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.utils.TestData;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.rest.OpenSearchRestTestCase;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MLCommonsBackwardsCompatibilityIT extends MLCommonsBackwardsCompatibilityRestTestCase {

    private final ClusterType CLUSTER_TYPE = ClusterType.parse(System.getProperty("tests.rest.bwcsuite"));
    private final String CLUSTER_NAME = System.getProperty("tests.clustername");
    private String MIXED_CLUSTER_TEST_ROUND = System.getProperty("tests.rest.bwcsuite_round");
    private final String irisIndex = "iris_data_backwards_compatibility_it";
    private SearchSourceBuilder searchSourceBuilder;
    private KMeansParams kMeansParams;

    @Before
    public void setup() throws Exception {
        Assume
            .assumeTrue(
                "Test cannot be run outside the BWC gradle task 'bwcTestSuite' or its dependent tasks",
                System.getProperty("tests.rest.bwcsuite") != null
            );
        searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(new MatchAllQueryBuilder());
        searchSourceBuilder.size(1000);
        searchSourceBuilder.fetchSource(new String[] { "petal_length_in_cm", "petal_width_in_cm" }, null);

        kMeansParams = KMeansParams.builder().centroids(3).iterations(10).distanceType(COSINE).build();
    }

    @Override
    protected final boolean preserveIndicesUponCompletion() {
        return true;
    }

    @Override
    protected final boolean preserveReposUponCompletion() {
        return true;
    }

    @Override
    protected boolean preserveTemplatesUponCompletion() {
        return true;
    }

    @Override
    protected final Settings restClientSettings() {
        return Settings
            .builder()
            .put(super.restClientSettings())
            // increase the timeout here to 90 seconds to handle long waits for a green
            // cluster health. the waits for green need to be longer than a minute to
            // account for delayed shards
            .put(OpenSearchRestTestCase.CLIENT_SOCKET_TIMEOUT, "90s")
            .build();
    }

    private enum ClusterType {
        OLD,
        MIXED,
        UPGRADED;

        public static ClusterType parse(String value) {
            switch (value) {
                case "old_cluster":
                    return OLD;
                case "mixed_cluster":
                    return MIXED;
                case "upgraded_cluster":
                    return UPGRADED;
                default:
                    throw new AssertionError("unknown cluster type: " + value);
            }
        }
    }

    private String getUri() {
        switch (CLUSTER_TYPE) {
            case OLD:
                return "_nodes/" + CLUSTER_NAME + "-0/plugins";
            case MIXED:
                String round = System.getProperty("tests.rest.bwcsuite_round");
                if (round.equals("second")) {
                    return "_nodes/" + CLUSTER_NAME + "-1/plugins";
                } else if (round.equals("third")) {
                    return "_nodes/" + CLUSTER_NAME + "-2/plugins";
                } else {
                    return "_nodes/" + CLUSTER_NAME + "-0/plugins";
                }
            case UPGRADED:
                return "_nodes/plugins";
            default:
                throw new AssertionError("unknown cluster type: " + CLUSTER_TYPE);
        }
    }

    private int getMixedClusterTestRound() {
        int mixedClusterTestRound = 0;
        switch (MIXED_CLUSTER_TEST_ROUND) {
            case "first":
                mixedClusterTestRound = 1;
                break;
            case "second":
                mixedClusterTestRound = 2;
                break;
            case "third":
                mixedClusterTestRound = 3;
                break;
            default:
                break;
        }
        return mixedClusterTestRound;
    }

    public void testBackwardsCompatibility() throws Exception {
        String uri = getUri();
        Map<String, Map<String, Object>> responseMap = (Map<String, Map<String, Object>>) getAsMap(uri).get("nodes");
        for (Map<String, Object> response : responseMap.values()) {
            List<Map<String, Object>> plugins = (List<Map<String, Object>>) response.get("plugins");
            Set<Object> pluginNames = plugins.stream().map(map -> map.get("name")).collect(Collectors.toSet());
            String opensearchVersion = plugins
                .stream()
                .map(map -> map.get("opensearch_version"))
                .collect(Collectors.toSet())
                .iterator()
                .next()
                .toString();
            switch (CLUSTER_TYPE) {
                case OLD:
                    assertTrue(pluginNames.contains("opensearch-ml"));
                    assertEquals("2.4.0", opensearchVersion);
                    ingestIrisData(irisIndex);
                    // train model
                    train(client(), FunctionName.KMEANS, irisIndex, kMeansParams, searchSourceBuilder, trainResult -> {
                        String modelId = (String) trainResult.get("model_id");
                        assertNotNull(modelId);
                        String status = (String) trainResult.get("status");
                        assertEquals(MLTaskState.COMPLETED.name(), status);
                    }, false);
                case MIXED:
                    assertTrue(pluginNames.contains("opensearch-ml"));
                    // then predict with old model
                    if (opensearchVersion.equals("2.4.0")) {
                        String modelId = getModelIdWithFunctionName(FunctionName.KMEANS);
                        predict(client(), FunctionName.KMEANS, modelId, irisIndex, kMeansParams, searchSourceBuilder, predictResult -> {
                            String predictStatus = (String) predictResult.get("status");
                            assertEquals(MLTaskState.COMPLETED.name(), predictStatus);
                            Map<String, Object> predictionResult = (Map<String, Object>) predictResult.get("prediction_result");
                            ArrayList rows = (ArrayList) predictionResult.get("rows");
                            assertTrue(rows.size() > 1);
                        });
                    } else if (isNewerVersion(opensearchVersion)) {
                        ingestIrisData(irisIndex);
                        try {
                            trainAndPredict(
                                client(),
                                FunctionName.KMEANS,
                                irisIndex,
                                kMeansParams,
                                searchSourceBuilder,
                                predictionResult -> {
                                    ArrayList rows = (ArrayList) predictionResult.get("rows");
                                    assertTrue(rows.size() > 0);
                                }
                            );
                        } catch (ResponseException e1) {
                            mlNodeSettingShifting();
                            try {
                                trainAndPredict(
                                    client(),
                                    FunctionName.KMEANS,
                                    irisIndex,
                                    kMeansParams,
                                    searchSourceBuilder,
                                    predictionResult -> {
                                        ArrayList rows = (ArrayList) predictionResult.get("rows");
                                        assertTrue(rows.size() > 0);
                                    }
                                );
                            } catch (ResponseException e2) {
                                Map modelResponseMap = gson.fromJson(("{" + e2.getMessage().split("[{]", 2)[1]), Map.class);
                                Map errorMap = (Map) modelResponseMap.get("error");
                                List<Map<String, Object>> rootCauses = (List<Map<String, Object>>) errorMap.get("root_cause");
                                Set<Object> rootCauseTypeSet = rootCauses.stream().map(map -> map.get("type")).collect(Collectors.toSet());
                                assertEquals("m_l_limit_exceeded_exception", rootCauseTypeSet.iterator().next().toString());
                                break;
                            }
                        }
                    } else {
                        throw new AssertionError("Cannot get the correct version for opensearch ml-commons plugin for the bwc test.");
                    }
                    break;
                case UPGRADED:
                    assertTrue(pluginNames.contains("opensearch-ml"));
                    assertTrue(isNewerVersion(opensearchVersion));
                    ingestIrisData(irisIndex);
                    try {
                        trainAndPredict(client(), FunctionName.KMEANS, irisIndex, kMeansParams, searchSourceBuilder, predictionResult -> {
                            ArrayList rows = (ArrayList) predictionResult.get("rows");
                            assertTrue(rows.size() > 0);
                        });
                    } catch (ResponseException e1) {
                        mlNodeSettingShifting();
                        try {
                            trainAndPredict(
                                client(),
                                FunctionName.KMEANS,
                                irisIndex,
                                kMeansParams,
                                searchSourceBuilder,
                                predictionResult -> {
                                    ArrayList rows = (ArrayList) predictionResult.get("rows");
                                    assertTrue(rows.size() > 0);
                                }
                            );
                        } catch (ResponseException e2) {
                            Map modelResponseMap = gson.fromJson(("{" + e2.getMessage().split("[{]", 2)[1]), Map.class);
                            Map errorMap = (Map) modelResponseMap.get("error");
                            List<Map<String, Object>> rootCauses = (List<Map<String, Object>>) errorMap.get("root_cause");
                            Set<Object> rootCauseTypeSet = rootCauses.stream().map(map -> map.get("type")).collect(Collectors.toSet());
                            assertEquals("m_l_limit_exceeded_exception", rootCauseTypeSet.iterator().next().toString());
                            memoryThresholdSettingShifting();
                            trainAndPredict(
                                client(),
                                FunctionName.KMEANS,
                                irisIndex,
                                kMeansParams,
                                searchSourceBuilder,
                                predictionResult -> {
                                    ArrayList rows = (ArrayList) predictionResult.get("rows");
                                    assertTrue(rows.size() > 0);
                                }
                            );
                        }
                    }
                    break;
            }
            break;
        }
    }

    private void mlNodeSettingShifting() throws IOException {
        Response bwcResponse = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"plugins.ml_commons.only_run_on_ml_node\":false}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, bwcResponse.getStatusLine().getStatusCode());
    }

    private void memoryThresholdSettingShifting() throws IOException {
        String jsonEntity = "{\n"
            + "  \"persistent\" : {\n"
            + "    \"plugins.ml_commons.native_memory_threshold\" : 100 \n"
            + "  }\n"
            + "}";
        Response bwcResponse = TestHelper
            .makeRequest(client(), "PUT", "_cluster/settings", ImmutableMap.of(), TestHelper.toHttpEntity(jsonEntity), null);
        assertEquals(200, bwcResponse.getStatusLine().getStatusCode());
    }

    private String getModelIdWithFunctionName(FunctionName functionName) throws IOException {
        String modelQuery = "{\"query\": {"
            + "\"term\": {"
            + "\"algorithm\":{\"value\": "
            + "\""
            + functionName.name().toUpperCase(Locale.ROOT)
            + "\""
            + "}"
            + "}"
            + "}"
            + "}";
        Response searchModelResponse = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/models/_search", null, modelQuery, null);
        HttpEntity entity = searchModelResponse.getEntity();
        String entityString = TestHelper.httpEntityToString(entity);
        Map modelResponseMap = gson.fromJson(entityString, Map.class);
        Map hitsModelsMap = (Map) modelResponseMap.get("hits");
        List<Map<String, Object>> hitsModels = (List<Map<String, Object>>) hitsModelsMap.get("hits");
        Set<Object> modelIdSet = hitsModels.stream().map(map -> map.get("_id")).collect(Collectors.toSet());
        return modelIdSet.iterator().next().toString();
    }

    private boolean isNewerVersion(String osVersion) {
        return (Integer.parseInt(osVersion.substring(2, 3)) > 4) || (Integer.parseInt(osVersion.substring(0, 1)) > 2);
    }

    private void verifyMlResponse(String uri) throws Exception {
        Response response = TestHelper.makeRequest(client(), "GET", uri, null, TestData.matchAllSearchQuery(), null);
        HttpEntity entity = response.getEntity();
        String entityString = TestHelper.httpEntityToString(entity);
        assertNull(entityString);
    }
}
