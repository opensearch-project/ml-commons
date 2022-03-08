/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.http.HttpHost;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.commons.rest.SecureRestClientBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.KMeansParams;
import org.opensearch.search.builder.SearchSourceBuilder;

public class SecureMLRestIT extends MLCommonsRestTestCase {
    private String irisIndex = "iris_data_secure_ml_it";

    String mlNoAccessUser = "ml_no_access";
    RestClient mlNoAccessClient;
    String mlReadOnlyUser = "ml_readonly";
    RestClient mlReadOnlyClient;
    String mlFullAccessNoIndexAccessUser = "ml_full_access_no_index_access";
    RestClient mlFullAccessNoIndexAccessClient;
    String mlFullAccessUser = "ml_full_access";
    RestClient mlFullAccessClient;
    private String indexSearchAccessRole = "ml_test_index_all_search";

    private String opensearchBackendRole = "opensearch";
    private SearchSourceBuilder searchSourceBuilder;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setup() throws IOException {
        if (!isHttps()) {
            throw new IllegalArgumentException("Secure Tests are running but HTTPS is not set");
        }
        createSearchRole(indexSearchAccessRole, "*");

        createUser(mlNoAccessUser, mlNoAccessUser, new ArrayList<>(Arrays.asList(opensearchBackendRole)));
        mlNoAccessClient = new SecureRestClientBuilder(
            getClusterHosts().toArray(new HttpHost[0]),
            isHttps(),
            mlNoAccessUser,
            mlNoAccessUser
        ).setSocketTimeout(60000).build();

        createUser(mlReadOnlyUser, mlReadOnlyUser, new ArrayList<>(Arrays.asList(opensearchBackendRole)));
        mlReadOnlyClient = new SecureRestClientBuilder(
            getClusterHosts().toArray(new HttpHost[0]),
            isHttps(),
            mlReadOnlyUser,
            mlReadOnlyUser
        ).setSocketTimeout(60000).build();

        createUser(mlFullAccessNoIndexAccessUser, mlFullAccessNoIndexAccessUser, new ArrayList<>(Arrays.asList(opensearchBackendRole)));
        mlFullAccessNoIndexAccessClient = new SecureRestClientBuilder(
            getClusterHosts().toArray(new HttpHost[0]),
            isHttps(),
            mlFullAccessNoIndexAccessUser,
            mlFullAccessNoIndexAccessUser
        ).setSocketTimeout(60000).build();

        createUser(mlFullAccessUser, mlFullAccessUser, new ArrayList<>(Arrays.asList(opensearchBackendRole)));
        mlFullAccessClient = new SecureRestClientBuilder(
            getClusterHosts().toArray(new HttpHost[0]),
            isHttps(),
            mlFullAccessUser,
            mlFullAccessUser
        ).setSocketTimeout(60000).build();

        createRoleMapping("ml_read_access", new ArrayList<>(Arrays.asList(mlReadOnlyUser)));
        createRoleMapping("ml_full_access", new ArrayList<>(Arrays.asList(mlFullAccessNoIndexAccessUser, mlFullAccessUser)));
        createRoleMapping(indexSearchAccessRole, new ArrayList<>(Arrays.asList(mlFullAccessUser)));

        ingestIrisData(irisIndex);
        searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(new MatchAllQueryBuilder());
        searchSourceBuilder.size(1000);
        searchSourceBuilder.fetchSource(new String[] { "petal_length_in_cm", "petal_width_in_cm" }, null);
    }

    @After
    public void deleteUserSetup() throws IOException {
        mlNoAccessClient.close();
        mlReadOnlyClient.close();
        mlFullAccessNoIndexAccessClient.close();
        mlFullAccessClient.close();
        deleteUser(mlNoAccessUser);
        deleteUser(mlReadOnlyUser);
        deleteUser(mlFullAccessNoIndexAccessUser);
        deleteUser(mlFullAccessUser);
        deleteIndexWithAdminClient(irisIndex);
    }

    public void testTrainAndPredictWithNoAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/trainAndPredict]");
        trainAndPredict(mlNoAccessClient, FunctionName.KMEANS, irisIndex, KMeansParams.builder().build(), searchSourceBuilder, null);
    }

    public void testTrainAndPredictWithReadOnlyAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/trainAndPredict]");
        trainAndPredict(mlReadOnlyClient, FunctionName.KMEANS, irisIndex, KMeansParams.builder().build(), searchSourceBuilder, null);
    }

    public void testTrainAndPredictWithFullMLAccessNoIndexAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [indices:data/read/search]");
        trainAndPredict(
            mlFullAccessNoIndexAccessClient,
            FunctionName.KMEANS,
            irisIndex,
            KMeansParams.builder().build(),
            searchSourceBuilder,
            null
        );
    }

    public void testTrainAndPredictWithFullAccess() throws IOException {
        trainAndPredict(
            mlFullAccessClient,
            FunctionName.KMEANS,
            irisIndex,
            KMeansParams.builder().build(),
            searchSourceBuilder,
            predictionResult -> {
                ArrayList rows = (ArrayList) predictionResult.get("rows");
                assertTrue(rows.size() > 0);
            }
        );
    }
}
