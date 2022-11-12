/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.commons.rest.SecureRestClientBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.transport.upload.MLUploadInput;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.builder.SearchSourceBuilder;

import com.google.common.base.Throwables;

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
    private MLUploadInput mlUploadInput;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setup() throws IOException, ParseException {
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

        mlUploadInput = createUploadModelInput();
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

    public void testUploadModelWithNoAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/upload_model]");
        uploadModel(mlNoAccessClient, TestHelper.toJsonString(mlUploadInput), null);
    }

    public void testUploadModelWithReadOnlyMLAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/upload_model]");
        uploadModel(mlReadOnlyClient, TestHelper.toJsonString(mlUploadInput), null);
    }

    public void testUploadModelWithFullAccess() throws IOException {
        uploadModel(mlFullAccessClient, TestHelper.toJsonString(mlUploadInput), uploadModelResult -> {
            assertFalse(uploadModelResult.containsKey("model_id"));
            String taskId = (String) uploadModelResult.get("task_id");
            assertNotNull(taskId);
            String status = (String) uploadModelResult.get("status");
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
    }

    public void testLoadModelWithNoAccess() throws IOException, InterruptedException {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/load_model]");
        loadModel(mlNoAccessClient, mlUploadInput, null);
    }

    public void testLoadModelWithReadOnlyMLAccess() throws IOException, InterruptedException {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/load_model]");
        loadModel(mlReadOnlyClient, mlUploadInput, null);
    }

    public void testLoadModelWithFullAccess() throws IOException, InterruptedException {
        loadModel(mlFullAccessClient, mlUploadInput, loadModelResult -> {
            assertFalse(loadModelResult.containsKey("model_id"));
            String taskId = (String) loadModelResult.get("task_id");
            assertNotNull(taskId);
            String status = (String) loadModelResult.get("status");
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
