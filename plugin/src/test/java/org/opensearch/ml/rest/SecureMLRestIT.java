/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.commons.rest.SecureRestClientBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.builder.SearchSourceBuilder;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

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
    private MLRegisterModelInput mlRegisterModelInput;

    private MLRegisterModelGroupInput mlRegisterModelGroupInput;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private String modelGroupId;
    private String password = "IntegTest@SecureMLRestIT123";

    @Before
    public void setup() throws IOException, ParseException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"plugins.ml_commons.model_access_control_enabled\":true}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, response.getStatusLine().getStatusCode());

        if (!isHttps()) {
            throw new IllegalArgumentException("Secure Tests are running but HTTPS is not set");
        }
        createSearchRole(indexSearchAccessRole, "*");

        createUser(mlNoAccessUser, password, new ArrayList<>(Arrays.asList(opensearchBackendRole)));
        mlNoAccessClient = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), mlNoAccessUser, password)
            .setSocketTimeout(60000)
            .build();

        createUser(mlReadOnlyUser, password, new ArrayList<>(Arrays.asList(opensearchBackendRole)));
        mlReadOnlyClient = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), mlReadOnlyUser, password)
            .setSocketTimeout(60000)
            .build();

        createUser(mlFullAccessNoIndexAccessUser, password, new ArrayList<>(Arrays.asList(opensearchBackendRole)));
        mlFullAccessNoIndexAccessClient = new SecureRestClientBuilder(
            getClusterHosts().toArray(new HttpHost[0]),
            isHttps(),
            mlFullAccessNoIndexAccessUser,
            password
        ).setSocketTimeout(60000).build();

        createUser(mlFullAccessUser, password, new ArrayList<>(Arrays.asList(opensearchBackendRole)));
        mlFullAccessClient = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), mlFullAccessUser, password)
            .setSocketTimeout(60000)
            .build();

        createRoleMapping("ml_read_access", new ArrayList<>(Arrays.asList(mlReadOnlyUser)));
        createRoleMapping("ml_full_access", new ArrayList<>(Arrays.asList(mlFullAccessNoIndexAccessUser, mlFullAccessUser)));
        createRoleMapping(indexSearchAccessRole, new ArrayList<>(Arrays.asList(mlFullAccessUser)));

        ingestIrisData(irisIndex);
        searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(new MatchAllQueryBuilder());
        searchSourceBuilder.size(1000);
        searchSourceBuilder.fetchSource(new String[] { "petal_length_in_cm", "petal_width_in_cm" }, null);

        // Create public model group
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.PUBLIC, false);

        registerModelGroup(mlFullAccessClient, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            this.modelGroupId = (String) registerModelGroupResult.get("model_group_id");
        });
        mlRegisterModelInput = createRegisterModelInput(modelGroupId);
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

    public void testRegisterModelWithNoAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/register_model]");
        registerModel(mlNoAccessClient, TestHelper.toJsonString(mlRegisterModelInput), null);
    }

    public void testRegisterModelWithReadOnlyMLAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/register_model]");
        registerModel(mlReadOnlyClient, TestHelper.toJsonString(mlRegisterModelInput), null);
    }

    public void testRegisterModelWithFullAccess() throws IOException {
        try {
            MLRegisterModelInput mlRegisterModelInput = createRegisterModelInput(modelGroupId);
            registerModel(mlFullAccessClient, TestHelper.toJsonString(mlRegisterModelInput), registerModelResult -> {
                assertFalse(registerModelResult.containsKey("model_id"));
                String taskId = (String) registerModelResult.get("task_id");
                assertNotNull(taskId);
                String status = (String) registerModelResult.get("status");
                assertEquals(MLTaskState.CREATED.name(), status);
                try {
                    getTask(mlFullAccessClient, taskId, task -> {
                        String algorithm = (String) task.get("function_name");
                        assertEquals(FunctionName.TEXT_EMBEDDING.name(), algorithm);
                    });
                } catch (IOException e) {
                    assertNull(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void testDeployModelWithNoAccess() throws IOException, InterruptedException {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/deploy_model]");
        deployModel(mlNoAccessClient, mlRegisterModelInput, null);
    }

    public void testDeployModelWithReadOnlyMLAccess() throws IOException, InterruptedException {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/deploy_model]");
        deployModel(mlReadOnlyClient, mlRegisterModelInput, null);
    }

    public void testDeployModelWithFullAccess() throws IOException, InterruptedException {
        deployModel(mlFullAccessClient, mlRegisterModelInput, deployModelResult -> {
            assertFalse(deployModelResult.containsKey("model_id"));
            String taskId = (String) deployModelResult.get("task_id");
            assertNotNull(taskId);
            String status = (String) deployModelResult.get("status");
            assertEquals(MLTaskState.CREATED.name(), status);
        });
    }

    public void testTrainWithReadOnlyMLAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/train]");
        KMeansParams kMeansParams = KMeansParams.builder().build();
        train(mlReadOnlyClient, FunctionName.KMEANS, irisIndex, kMeansParams, searchSourceBuilder, null, false);
    }

    public void testPredictWithReadOnlyMLAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/predict]");
        KMeansParams kMeansParams = KMeansParams.builder().build();
        predict(mlReadOnlyClient, FunctionName.KMEANS, "modelId", irisIndex, kMeansParams, searchSourceBuilder, null);
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

    public void testTrainModelWithFullAccessThenPredict() throws IOException {
        KMeansParams kMeansParams = KMeansParams.builder().build();
        // train model
        train(mlFullAccessClient, FunctionName.KMEANS, irisIndex, kMeansParams, searchSourceBuilder, trainResult -> {
            String modelId = (String) trainResult.get("model_id");
            assertNotNull(modelId);
            String status = (String) trainResult.get("status");
            assertEquals(MLTaskState.COMPLETED.name(), status);
            try {
                getModel(mlFullAccessClient, modelId, model -> {
                    String algorithm = (String) model.get("algorithm");
                    assertEquals(FunctionName.KMEANS.name(), algorithm);
                });
            } catch (IOException e) {
                assertNull(e);
            }
            try {
                // predict with trained model
                predict(mlFullAccessClient, FunctionName.KMEANS, modelId, irisIndex, kMeansParams, searchSourceBuilder, predictResult -> {
                    String predictStatus = (String) predictResult.get("status");
                    assertEquals(MLTaskState.COMPLETED.name(), predictStatus);
                    Map<String, Object> predictionResult = (Map<String, Object>) predictResult.get("prediction_result");
                    ArrayList rows = (ArrayList) predictionResult.get("rows");
                    assertTrue(rows.size() > 1);
                });
            } catch (IOException e) {
                assertNull(e);
            }
        }, false);
    }

    public void testTrainModelInAsyncWayWithFullAccess() throws IOException {
        train(mlFullAccessClient, FunctionName.KMEANS, irisIndex, KMeansParams.builder().build(), searchSourceBuilder, trainResult -> {
            assertFalse(trainResult.containsKey("model_id"));
            String taskId = (String) trainResult.get("task_id");
            assertNotNull(taskId);
            String status = (String) trainResult.get("status");
            assertEquals(MLTaskState.CREATED.name(), status);
            try {
                getTask(mlFullAccessClient, taskId, task -> {
                    String algorithm = (String) task.get("function_name");
                    assertEquals(FunctionName.KMEANS.name(), algorithm);
                });
            } catch (IOException e) {
                assertNull(e);
            }
        }, true);
    }

    public void testReadOnlyUser_CanGetModel_CanNotDeleteModel() throws IOException {
        KMeansParams kMeansParams = KMeansParams.builder().build();
        // train model with full access client
        train(mlFullAccessClient, FunctionName.KMEANS, irisIndex, kMeansParams, searchSourceBuilder, trainResult -> {
            String modelId = (String) trainResult.get("model_id");
            assertNotNull(modelId);
            String status = (String) trainResult.get("status");
            assertEquals(MLTaskState.COMPLETED.name(), status);
            try {
                // get model with readonly client
                getModel(mlReadOnlyClient, modelId, model -> {
                    String algorithm = (String) model.get("algorithm");
                    assertEquals(FunctionName.KMEANS.name(), algorithm);
                });
            } catch (IOException e) {
                assertNull(e);
            }
            try {
                // Failed to delete model with read only client
                deleteModel(mlReadOnlyClient, modelId, null);
                throw new RuntimeException("Delete model for readonly user does not fail");
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("no permissions for [cluster:admin/opensearch/ml/models/delete]"));
            }
        }, false);
    }

    public void testReadOnlyUser_CanGetTask_CanNotDeleteTask() throws IOException {
        KMeansParams kMeansParams = KMeansParams.builder().build();
        // train model with full access client
        train(mlFullAccessClient, FunctionName.KMEANS, irisIndex, kMeansParams, searchSourceBuilder, trainResult -> {
            assertFalse(trainResult.containsKey("model_id"));
            String taskId = (String) trainResult.get("task_id");
            assertNotNull(taskId);
            String status = (String) trainResult.get("status");
            assertEquals(MLTaskState.CREATED.name(), status);
            try {
                // get task with readonly client
                getTask(mlReadOnlyClient, taskId, task -> {
                    String algorithm = (String) task.get("function_name");
                    assertEquals(FunctionName.KMEANS.name(), algorithm);
                });
            } catch (IOException e) {
                assertNull(e);
            }
            try {
                // Failed to delete task with read only client
                deleteTask(mlReadOnlyClient, taskId, null);
                throw new RuntimeException("Delete task for readonly user does not fail");
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("no permissions for [cluster:admin/opensearch/ml/tasks/delete]"));
            }
        }, true);
    }

    public void testReadOnlyUser_CanSearchModels() throws IOException {
        KMeansParams kMeansParams = KMeansParams.builder().build();
        // train model with full access client
        train(mlFullAccessClient, FunctionName.KMEANS, irisIndex, kMeansParams, searchSourceBuilder, trainResult -> {
            String modelId = (String) trainResult.get("model_id");
            assertNotNull(modelId);
            String status = (String) trainResult.get("status");
            assertEquals(MLTaskState.COMPLETED.name(), status);
            try {
                // search model with readonly client
                searchModelsWithAlgoName(mlReadOnlyClient, FunctionName.KMEANS.name(), models -> {
                    ArrayList<Object> hits = (ArrayList) ((Map<String, Object>) models.get("hits")).get("hits");
                    assertTrue(hits.size() > 0);
                });
            } catch (IOException e) {
                assertNull(e);
            }
        }, false);
    }

    public void testReadOnlyUser_CanSearchTasks() throws IOException {
        KMeansParams kMeansParams = KMeansParams.builder().build();
        // train model with full access client
        train(mlFullAccessClient, FunctionName.KMEANS, irisIndex, kMeansParams, searchSourceBuilder, trainResult -> {
            assertFalse(trainResult.containsKey("model_id"));
            String taskId = (String) trainResult.get("task_id");
            assertNotNull(taskId);
            String status = (String) trainResult.get("status");
            assertEquals(MLTaskState.CREATED.name(), status);
            try {
                // search tasks with readonly client
                searchTasksWithAlgoName(mlReadOnlyClient, FunctionName.KMEANS.name(), tasks -> {
                    ArrayList<Object> hits = (ArrayList) ((Map<String, Object>) tasks.get("hits")).get("hits");
                    assertTrue(hits.size() > 0);
                });
            } catch (IOException e) {
                assertNull(e);
            }
        }, true);
    }
}
