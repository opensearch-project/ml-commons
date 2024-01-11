/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.commons.rest.SecureRestClientBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupInput;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

public class MLModelGroupRestIT extends MLCommonsRestTestCase {

    String mlNoAccessUser = "ml_no_access";
    RestClient mlNoAccessClient;
    String mlReadOnlyUser = "ml_readonly";
    RestClient mlReadOnlyClient;

    String mlFullAccessUser = "ml_full_access";
    RestClient mlFullAccessClient;

    String user1 = "user1";
    RestClient user1Client;

    String user2 = "user2";
    RestClient user2Client;

    String user3 = "user3";
    RestClient user3Client;

    String user4 = "user4";
    RestClient user4Client;

    private String indexSearchAccessRole = "ml_test_index_all_search";

    private String opensearchBackendRole = "opensearch";

    private MLRegisterModelGroupInput mlRegisterModelGroupInput;

    private MLUpdateModelGroupInput mlUpdateModelGroupInput;

    private final String MATCH_ALL_QUERY = "{\n" + "    \"query\": {\n" + "        \"match_all\": {}\n" + "    }\n" + "}";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private String modelGroupId;
    private String password = "IntegTest@MLModelGroupRestIT123";

    public void disableModelAccessControl(boolean isSecurityEnabled) throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"plugins.ml_commons.model_access_control_enabled\":" + isSecurityEnabled + "}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, response.getStatusLine().getStatusCode());

    }

    @Before
    public void setup() throws IOException {
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

        createUser(mlNoAccessUser, password, ImmutableList.of(opensearchBackendRole));
        mlNoAccessClient = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), mlNoAccessUser, password)
            .setSocketTimeout(60000)
            .build();

        createUser(mlReadOnlyUser, password, ImmutableList.of(opensearchBackendRole));
        mlReadOnlyClient = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), mlReadOnlyUser, password)
            .setSocketTimeout(60000)
            .build();

        createUser(mlFullAccessUser, password, new ArrayList<>(Arrays.asList(opensearchBackendRole)));
        mlFullAccessClient = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), mlFullAccessUser, password)
            .setSocketTimeout(60000)
            .build();

        createUser(user1, password, ImmutableList.of("IT", "HR"));
        user1Client = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), user1, password)
            .setSocketTimeout(60000)
            .build();

        createUser(user2, password, ImmutableList.of("IT"));
        user2Client = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), user2, password)
            .setSocketTimeout(60000)
            .build();

        createUser(user3, password, ImmutableList.of("Finance"));
        user3Client = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), user3, password)
            .setSocketTimeout(60000)
            .build();

        createUser(user4, password, ImmutableList.of());
        user4Client = new SecureRestClientBuilder(getClusterHosts().toArray(new HttpHost[0]), isHttps(), user4, password)
            .setSocketTimeout(60000)
            .build();

        createRoleMapping("ml_read_access", ImmutableList.of(mlReadOnlyUser));
        createRoleMapping("ml_full_access", ImmutableList.of(mlFullAccessUser, user1, user2, user3, user4));
        createRoleMapping(indexSearchAccessRole, ImmutableList.of(mlFullAccessUser, user1, user2, user3, user4));
    }

    @After
    public void deleteUserSetup() throws IOException {
        mlNoAccessClient.close();
        mlReadOnlyClient.close();
        mlFullAccessClient.close();
        user1Client.close();
        user2Client.close();
        user3Client.close();
        user4Client.close();
        deleteUser(mlNoAccessUser);
        deleteUser(mlReadOnlyUser);
        deleteUser(mlFullAccessUser);
        deleteUser(user1);
        deleteUser(user2);
        deleteUser(user3);
        deleteUser(user4);
    }

    public void test_registerModelGroup_withNoAccess() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.PUBLIC, false);
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/register_model_group]");
        registerModelGroup(mlNoAccessClient, TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_registerModelGroup_WithReadOnlyMLAccess() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.PUBLIC, false);
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/register_model_group]");
        registerModelGroup(mlReadOnlyClient, TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_updateModelGroup_withNoAccess() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.PUBLIC, false);

        registerModelGroup(mlFullAccessClient, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            this.modelGroupId = (String) registerModelGroupResult.get("model_group_id");
        });

        mlUpdateModelGroupInput = createUpdateModelGroupInput(
            this.modelGroupId,
            "new_name",
            "new description",
            ImmutableList.of(opensearchBackendRole),
            AccessMode.RESTRICTED,
            false
        );

        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/update_model_group]");
        updateModelGroup(mlNoAccessClient, this.modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
    }

    public void test_updateModelGroup_WithReadOnlyMLAccess() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.PUBLIC, false);

        registerModelGroup(mlFullAccessClient, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            this.modelGroupId = (String) registerModelGroupResult.get("model_group_id");
        });

        mlUpdateModelGroupInput = createUpdateModelGroupInput(
            this.modelGroupId,
            "new_name",
            "new description",
            ImmutableList.of(opensearchBackendRole),
            AccessMode.RESTRICTED,
            false
        );

        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/update_model_group]");
        updateModelGroup(mlReadOnlyClient, this.modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
    }

    public void test_searchModelGroup_withNoAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/model_groups/search]");
        searchModelGroups(mlNoAccessClient, MATCH_ALL_QUERY, null);
    }

    public void test_RegisterModelGroupForUser1WithAddAllBackendRoles() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.RESTRICTED, true);
        registerModelGroup(user1Client, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            modelGroupId = (String) registerModelGroupResult.get("model_group_id");
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));
            try {
                // User2 successfully updates model group with no access data
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name2", "description2", null, null, null);
                updateModelGroup(user2Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
                // User1 successfully updates model group with access data because user1 is owner
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    null,
                    Arrays.asList("IT"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
                // Admin successfully updates model group with access data
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    null,
                    "description",
                    Arrays.asList("IT", "HR"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
            } catch (IOException e) {
                assertNull(e);
            }
            // User1 fails to update model group when trying to give backend role as Finance
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    Arrays.asList("Finance"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("You don't have the backend roles specified."));
            }
            // User2 fails to update model group with access data because user2 is not the owner
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    Arrays.asList("IT"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(user2Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("Only owner or admin can update access control data."));
            }
            // User3 fails to update model group
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, null, null);
                updateModelGroup(user3Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("You don't have permission to update this model group."));
            }
            // User1 fails to update model group when specifying backend roles to public access mode
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    Arrays.asList("HR"),
                    AccessMode.PUBLIC,
                    null
                );
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains("You can specify backend roles only for a model group with the restricted access mode.")
                );
            }
        });
    }

    public void test_RegisterModelGroupForUser1WithBackendRolesField() throws IOException {

        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", ImmutableList.of("HR"), AccessMode.RESTRICTED, null);
        registerModelGroup(user1Client, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            modelGroupId = (String) registerModelGroupResult.get("model_group_id");
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));

            try {
                // Admin successfully updates model group with access data
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name2",
                    "description2",
                    ImmutableList.of("IT"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
                // User1 successfully updates model group to add all backend roles because user1 is the owner
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, null, null, null, AccessMode.RESTRICTED, true);
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

                // User1 successfully updates model group to HR because user1 is the owner
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name2",
                    "description2",
                    ImmutableList.of("HR"),
                    AccessMode.RESTRICTED,
                    false
                );
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
            } catch (IOException e) {
                assertNull(e);
            }
            // User2 fails to update model group because user does not have HR backend role
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    ImmutableList.of("IT"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(user2Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("Only owner or admin can update access control data."));
            }
            // User3 fails to update model group because user does not have HR backend role
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, null, null);
                updateModelGroup(user3Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("You don't have permission to update this model group."));
            }
            // User4 fails to update model group because the user does not have HR backend role
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, AccessMode.PRIVATE, null);
                updateModelGroup(user4Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("Only owner or admin can update access control data."));
            }
            // Admin fails to update model group when trying to set add_all_backend_roles to true
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    null,
                    AccessMode.RESTRICTED,
                    true
                );
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("Admin users cannot add all backend roles to a model group."));
            }
            // User1 fails to update model group when setting add_all_backend_roles to false and not specifying any backend roles
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    null,
                    AccessMode.RESTRICTED,
                    false
                );
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains("You have to specify backend roles when add all backend roles is set to false.")
                );
            }
            // User1 fails to update model group when neither setting add_all_backend_roles to true nor specifying any backend roles
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    null,
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains(
                            "You must specify one or more backend roles or add all backend roles to register a restricted model group."
                        )
                );
            }
            // User1 fails to update model group when trying to five Finance as backend roles
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    ImmutableList.of("Finance"),
                    AccessMode.RESTRICTED,
                    false
                );
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("You don't have the backend roles specified."));
            }
        });

    }

    public void test_RegisterModelGroupForUser1WithPublic() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.PUBLIC, null);
        registerModelGroup(user1Client, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            String modelGroupId = (String) registerModelGroupResult.get("model_group_id");
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));
            try {
                // User2 successfully updates model group if no access data is specified
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, null, "description1", null, null, null);
                updateModelGroup(user2Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

                // User3 successfully updates model group if no access data is specified
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name1", null, null, null, null);
                updateModelGroup(user3Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

                // User4 successfully updates model group if no access data is specified
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name2", "description2", null, null, null);
                updateModelGroup(user4Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

                // Admin successfully updates model group to restricted
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    null,
                    null,
                    Arrays.asList("IT"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

                // User1 successfully updates model group with access data because user1 is the owner
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, AccessMode.PUBLIC, null);
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
            } catch (IOException e) {
                assertNull(e);
            }
            // User2 fails to update model group when access data is given because user2 is non-owner
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name1",
                    null,
                    Arrays.asList("IT"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(user2Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("Only owner or admin can update access control data."));
            }
            // User1 fails to update model group when specifying both backend_roles and add_all_backend_roles fields
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name2",
                    null,
                    Arrays.asList("HR"),
                    AccessMode.RESTRICTED,
                    true
                );
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains("You cannot specify backend roles and add all backend roles at the same time.")
                );
            }
        });
    }

    public void test_RegisterModelGroupForUser1WithPrivate() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.PRIVATE, null);
        registerModelGroup(user1Client, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            String modelGroupId = (String) registerModelGroupResult.get("model_group_id");
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));
            try {
                // Admin successfully updates model group
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name1",
                    "description1",
                    Arrays.asList("IT"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
                // User1 successfully updates model group specifying access data because user1 is the owner
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, AccessMode.PRIVATE, null);
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
            } catch (IOException e) {
                assertNull(e);
            }
            // User2 fails to update model group because it is private to user1
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name1", "description1", null, null, null);
                updateModelGroup(user2Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("You don't have permission to update this model group."));
            }
            // Admin fails to update model group when trying to set add_all_backend_roles to true
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    null,
                    AccessMode.RESTRICTED,
                    true
                );
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("Admin users cannot add all backend roles to a model group."));
            }
            // User1 fails to update model group when trying to specify backend roles for public access mode
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, AccessMode.PUBLIC, true);
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains("You can specify backend roles only for a model group with the restricted access mode.")
                );
            }
        });
    }

    public void test_RegisterModelGroupOnAccessControlDisabledCluster() throws IOException {
        disableModelAccessControl(false);
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, null, null);
        registerModelGroup(user1Client, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            String modelGroupId = (String) registerModelGroupResult.get("model_group_id");
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));
            try {
                // Admin successfully updates model group
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name1", "description1", null, null, null);
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
                // User1 successfully updates model group
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name2", "description2", null, null, null);
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
                // User4 successfully updates model group
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, null, null);
                updateModelGroup(user4Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
            } catch (IOException e) {
                assertNull(e);
            }
            // User1 fails to update model group with access data
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name1",
                    "description1",
                    Arrays.asList("IT"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains(
                            "You cannot specify model access control parameters because the Security plugin or model access control is disabled on your cluster."
                        )
                );
            }
            // Admin fails to update model group with access data
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    null,
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains(
                            "You cannot specify model access control parameters because the Security plugin or model access control is disabled on your cluster."
                        )
                );
            }
            // User3 fails to update model group with model acces data
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, AccessMode.PUBLIC, null);
                updateModelGroup(user3Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains(
                            "You cannot specify model access control parameters because the Security plugin or model access control is disabled on your cluster."
                        )
                );
            }
        });
    }

    public void test_RegisterModelGroupForAdminWithRestricted() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput(
            "modelGroupName",
            ImmutableList.of("Finance"),
            AccessMode.RESTRICTED,
            false
        );
        registerModelGroup(client(), TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            String modelGroupId = (String) registerModelGroupResult.get("model_group_id");
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));
            try {
                // Admin successfully updates model group with access data
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name1",
                    "description1",
                    ImmutableList.of("IT"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
                // Admin successfully updates model group with access data
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    null,
                    null,
                    ImmutableList.of("Finance"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
                // User3 successfully updates model group without access data because user3 has Finance backend role but is not the owner
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, null, null);
                updateModelGroup(user3Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

            } catch (IOException e) {
                assertNull(e);
            }
            // User2 fails to update model group
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name1", "description1", null, null, null);
                updateModelGroup(user2Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("You don't have permission to update this model group."));
            }
            // User3 fails to update model group when trying to specify access control data
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    null,
                    AccessMode.RESTRICTED,
                    true
                );
                updateModelGroup(user3Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("Only owner or admin can update access control data."));
            }
            // Admin fails to update model group when trying to specify add all backend roles field
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    null,
                    AccessMode.RESTRICTED,
                    true
                );
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("Admin users cannot add all backend roles to a model group."));
            }
        });
    }

    public void test_RegisterModelGroupForAdminWithPublic() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.PUBLIC, null);
        registerModelGroup(client(), TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            String modelGroupId = (String) registerModelGroupResult.get("model_group_id");
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));
            try {
                // User3 successfully updates model group without access data
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name1", null, null, null, null);
                updateModelGroup(user3Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

                // User3 successfully updates model group without access data
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, null, "description1", null, null, null);
                updateModelGroup(user4Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

                // Admin successfully updates model group with access data
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    ImmutableList.of("Finance"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

                // Admin successfully updates model group with access data
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, null, null, null, AccessMode.PUBLIC, null);
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
            } catch (IOException e) {
                assertNull(e);
            }
            // User1 fails to update model group when trying to specify access control data
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    Arrays.asList("IT"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("Only owner or admin can update access control data."));
            }
            // User2 fails to update model group when trying to specify access control data
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    Arrays.asList("IT"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(user2Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("Only owner or admin can update access control data."));
            }
            // Admin fails to update model group when trying to specify backend roles for public access mode
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    Arrays.asList("IT"),
                    AccessMode.PUBLIC,
                    null
                );
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains("You can specify backend roles only for a model group with the restricted access mode.")
                );
            }
        });
    }

    public void test_RegisterModelGroupForAdminWithPrivate() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.PRIVATE, false);
        registerModelGroup(client(), TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            String modelGroupId = (String) registerModelGroupResult.get("model_group_id");
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name1", null, null, AccessMode.PUBLIC, null);
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name1", null, null, AccessMode.PRIVATE, null);
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

            } catch (IOException e) {
                assertNull(e);
            }
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, null, null);
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("You don't have permission to update this model group."));
            }
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, AccessMode.PUBLIC, null);
                updateModelGroup(user2Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("Only owner or admin can update access control data."));
            }
        });
    }

    public void test_RegisterModelGroupForUser4WithPublic() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.PUBLIC, null);
        registerModelGroup(user4Client, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            String modelGroupId = (String) registerModelGroupResult.get("model_group_id");
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name1", null, null, null, null);
                updateModelGroup(user3Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name1", null, null, AccessMode.PRIVATE, null);
                updateModelGroup(user4Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    ImmutableList.of("Finance"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, null, null, null, AccessMode.PUBLIC, null);
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
            } catch (IOException e) {
                assertNull(e);
            }

            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    null,
                    AccessMode.RESTRICTED,
                    true
                );
                updateModelGroup(user4Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("You don't have any backend roles."));
            }
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    Arrays.asList("IT"),
                    AccessMode.RESTRICTED,
                    null
                );
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("Only owner or admin can update access control data."));
            }
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, AccessMode.PRIVATE, true);
                updateModelGroup(user4Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains("You can specify backend roles only for a model group with the restricted access mode.")
                );
            }
        });
    }

    public void test_RegisterModelGroupForUser4WithPrivate() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.PRIVATE, null);
        registerModelGroup(user4Client, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            String modelGroupId = (String) registerModelGroupResult.get("model_group_id");
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, null, null);
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, null, null, null, AccessMode.PUBLIC, null);
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });

                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, null, null, null, AccessMode.PRIVATE, null);
                updateModelGroup(client(), modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), updateModelGroupResult -> {
                    assertTrue(updateModelGroupResult.containsKey("status"));
                });
            } catch (IOException e) {
                assertNull(e);
            }

            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(
                    modelGroupId,
                    "name",
                    "description",
                    null,
                    AccessMode.RESTRICTED,
                    true
                );
                updateModelGroup(user4Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("You don't have any backend roles."));
            }
            try {
                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, null, null);
                updateModelGroup(user1Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(Throwables.getStackTraceAsString(e).contains("You don't have permission to update this model group."));
            }
            try {

                mlUpdateModelGroupInput = createUpdateModelGroupInput(modelGroupId, "name", "description", null, AccessMode.PRIVATE, true);
                updateModelGroup(user4Client, modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains("You can specify backend roles only for a model group with the restricted access mode.")
                );
            }
        });
    }

    public void test_AdminCreateRestrictedWithAddAllBackendRoles() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.RESTRICTED, true);
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("Admin users cannot add all backend roles to a model group.");
        registerModelGroup(client(), TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_User1CreateRestrictedITWithAddAllBackendRoles() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", Arrays.asList("IT"), AccessMode.RESTRICTED, true);
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("You cannot specify backend roles and add all backend roles at the same time.");
        registerModelGroup(user1Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_User1CreatePublicWithBackendRole() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", Arrays.asList("IT"), AccessMode.PUBLIC, null);
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("You can specify backend roles only for a model group with the restricted access mode.");
        registerModelGroup(user1Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_User1CreatePrivateWithBackendRole() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", Arrays.asList("IT"), AccessMode.PRIVATE, null);
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("You can specify backend roles only for a model group with the restricted access mode.");
        registerModelGroup(user1Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_User1CreatePublicWithAddAllBackendRole() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.PUBLIC, true);
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("You can specify backend roles only for a model group with the restricted access mode.");
        registerModelGroup(user1Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_User1CreatePrivateWithAddAllBackendRole() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.PRIVATE, true);
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("You can specify backend roles only for a model group with the restricted access mode.");
        registerModelGroup(user1Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_User4CreateRestrictedWithAddAllBackendRoles() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.RESTRICTED, true);
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("You must have at least one backend role to register a restricted model group.");
        registerModelGroup(user4Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_User3CreateRestrictedWithNoBackendRolesFields() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", null, AccessMode.RESTRICTED, null);
        exceptionRule.expect(ResponseException.class);
        exceptionRule
            .expectMessage("You must specify one or more backend roles or add all backend roles to register a restricted model group.");
        registerModelGroup(user3Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_User2CreateRestrictedWithBackendRoleThatDoesNotBelongtoTheUser() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", Arrays.asList("Finance"), AccessMode.RESTRICTED, null);
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("You don't have the backend roles specified.");
        registerModelGroup(user2Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_User3CreateRestrictedWithBothBackendRolesFields() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", Arrays.asList("Finance"), AccessMode.RESTRICTED, true);
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("You cannot specify backend roles and add all backend roles at the same time.");
        registerModelGroup(user3Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_User1CreateModelGroupWithAccessDataOnAccessControlDisabledCluster() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName", Arrays.asList("IT"), AccessMode.RESTRICTED, true);
        disableModelAccessControl(false);
        exceptionRule.expect(ResponseException.class);
        exceptionRule
            .expectMessage(
                "You cannot specify model access control parameters because the Security plugin or model access control is disabled on your cluster."
            );
        registerModelGroup(user1Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_search_MatchAllQuery_For_ModelGroups() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName1", Arrays.asList("IT"), AccessMode.RESTRICTED, null);
        registerModelGroup(client(), TestHelper.toJsonString(mlRegisterModelGroupInput), null);

        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName2", null, AccessMode.PUBLIC, null);
        registerModelGroup(client(), TestHelper.toJsonString(mlRegisterModelGroupInput), null);

        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName3", null, AccessMode.PRIVATE, null);
        registerModelGroup(client(), TestHelper.toJsonString(mlRegisterModelGroupInput), null);

        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName4", null, AccessMode.RESTRICTED, true);
        registerModelGroup(user1Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);

        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName5", null, AccessMode.PRIVATE, null);
        registerModelGroup(user1Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);

        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName6", null, AccessMode.RESTRICTED, true);
        registerModelGroup(user2Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);

        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName7", null, AccessMode.RESTRICTED, true);
        registerModelGroup(user3Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);

        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName8", null, AccessMode.PUBLIC, null);
        registerModelGroup(user3Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);

        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName9", null, AccessMode.PRIVATE, null);
        registerModelGroup(user3Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);

        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName10", null, AccessMode.PUBLIC, null);
        registerModelGroup(user4Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);

        mlRegisterModelGroupInput = createRegisterModelGroupInput("modelGroupName11", null, AccessMode.PRIVATE, null);
        registerModelGroup(user4Client, TestHelper.toJsonString(mlRegisterModelGroupInput), null);

        try {
            searchModelGroups(client(), MATCH_ALL_QUERY, r -> {
                assertTrue(r.containsKey("hits"));
                assertTrue(((Map<String, Object>) r.get("hits")).containsKey("total"));
                Map<String, Object> total = (Map<String, Object>) ((Map<String, Object>) r.get("hits")).get("total");
                assertEquals(11.0, total.get("value"));
            });
        } catch (IOException e) {
            assertNull(e);
        }
        try {
            searchModelGroups(user1Client, MATCH_ALL_QUERY, r -> {
                assertTrue(r.containsKey("hits"));
                assertTrue(((Map<String, Object>) r.get("hits")).containsKey("total"));
                Map<String, Object> total = (Map<String, Object>) ((Map<String, Object>) r.get("hits")).get("total");
                assertEquals(7.0, total.get("value"));
            });
        } catch (IOException e) {
            assertNull(e);
        }
        try {
            searchModelGroups(user2Client, MATCH_ALL_QUERY, r -> {
                assertTrue(r.containsKey("hits"));
                assertTrue(((Map<String, Object>) r.get("hits")).containsKey("total"));
                Map<String, Object> total = (Map<String, Object>) ((Map<String, Object>) r.get("hits")).get("total");
                assertEquals(6.0, total.get("value"));
            });
        } catch (IOException e) {
            assertNull(e);
        }
        try {
            searchModelGroups(user3Client, MATCH_ALL_QUERY, r -> {
                assertTrue(r.containsKey("hits"));
                assertTrue(((Map<String, Object>) r.get("hits")).containsKey("total"));
                Map<String, Object> total = (Map<String, Object>) ((Map<String, Object>) r.get("hits")).get("total");
                assertEquals(5.0, total.get("value"));
            });
        } catch (IOException e) {
            assertNull(e);
        }
        try {
            searchModelGroups(user4Client, MATCH_ALL_QUERY, r -> {
                assertTrue(r.containsKey("hits"));
                assertTrue(((Map<String, Object>) r.get("hits")).containsKey("total"));
                Map<String, Object> total = (Map<String, Object>) ((Map<String, Object>) r.get("hits")).get("total");
                assertEquals(4.0, total.get("value"));
            });
        } catch (IOException e) {
            assertNull(e);
        }
    }

    public void test_get_modelGroup() throws IOException {
        mlRegisterModelGroupInput = createRegisterModelGroupInput("testModelGroup1", Arrays.asList("IT"), AccessMode.RESTRICTED, null);
        registerModelGroup(user1Client, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            String modelGroupId1 = (String) registerModelGroupResult.get("model_group_id");
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));
            try {
                // User2 successfully gets model group since user2 has IT backend role
                getModelGroup(
                    user2Client,
                    modelGroupId1,
                    getModelGroupResult -> { assertEquals(getModelGroupResult.get("name"), "testModelGroup1"); }
                );

                // Admin successfully gets model group
                getModelGroup(
                    client(),
                    modelGroupId1,
                    getModelGroupResult -> { assertEquals(getModelGroupResult.get("name"), "testModelGroup1"); }
                );
            } catch (IOException e) {
                assertNull(e);
            }
            // User2 fails to get model group
            try {
                getModelGroup(user3Client, modelGroupId1, null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains("User doesn't have privilege to perform this operation on this model group")
                );
            }
        });

        mlRegisterModelGroupInput = createRegisterModelGroupInput("testModelGroup2", null, AccessMode.PUBLIC, null);
        registerModelGroup(user2Client, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            String modelGroupId2 = (String) registerModelGroupResult.get("model_group_id");
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));
            try {
                // User1 successfully gets model group since user2 has IT backend role
                getModelGroup(
                    user1Client,
                    modelGroupId2,
                    getModelGroupResult -> { assertEquals(getModelGroupResult.get("name"), "testModelGroup2"); }
                );

                // User3 successfully gets model group
                getModelGroup(
                    user3Client,
                    modelGroupId2,
                    getModelGroupResult -> { assertEquals(getModelGroupResult.get("name"), "testModelGroup2"); }
                );

                // User4 successfully gets model group
                getModelGroup(
                    user4Client,
                    modelGroupId2,
                    getModelGroupResult -> { assertEquals(getModelGroupResult.get("name"), "testModelGroup2"); }
                );
            } catch (IOException e) {
                assertNull(e);
            }
        });

        mlRegisterModelGroupInput = createRegisterModelGroupInput("testModelGroup3", null, AccessMode.PRIVATE, null);
        registerModelGroup(user3Client, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            String modelGroupId3 = (String) registerModelGroupResult.get("model_group_id");
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));
            try {
                // User3 successfully gets model group since user2 has IT backend role
                getModelGroup(
                    user3Client,
                    modelGroupId3,
                    getModelGroupResult -> { assertEquals(getModelGroupResult.get("name"), "testModelGroup3"); }
                );

                // Admin successfully gets model group
                getModelGroup(
                    client(),
                    modelGroupId3,
                    getModelGroupResult -> { assertEquals(getModelGroupResult.get("name"), "testModelGroup3"); }
                );
            } catch (IOException e) {
                assertNull(e);
            }
            // User2 fails to get model group
            try {
                getModelGroup(user2Client, modelGroupId3, null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains("User doesn't have privilege to perform this operation on this model group")
                );
            }
        });

        mlRegisterModelGroupInput = createRegisterModelGroupInput("testModelGroup4", null, null, null);
        registerModelGroup(client(), TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            String modelGroupId4 = (String) registerModelGroupResult.get("model_group_id");
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));
            try {
                // Admin successfully gets model group
                getModelGroup(
                    client(),
                    modelGroupId4,
                    getModelGroupResult -> { assertEquals(getModelGroupResult.get("name"), "testModelGroup4"); }
                );
            } catch (IOException e) {
                assertNull(e);
            }

            // User1 fails to get model group
            try {
                getModelGroup(user1Client, modelGroupId4, null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains("User doesn't have privilege to perform this operation on this model group")
                );
            }

            // User2 fails to get model group
            try {
                getModelGroup(user2Client, modelGroupId4, null);
            } catch (Exception e) {
                assertEquals(ResponseException.class, e.getClass());
                assertTrue(
                    Throwables
                        .getStackTraceAsString(e)
                        .contains("User doesn't have privilege to perform this operation on this model group")
                );
            }
        });
    }

}
