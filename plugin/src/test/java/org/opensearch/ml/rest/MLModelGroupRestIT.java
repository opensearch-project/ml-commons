/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Map;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.commons.rest.SecureRestClientBuilder;
import org.opensearch.ml.common.ModelAccessMode;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupInput;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

public class MLModelGroupRestIT extends MLCommonsRestTestCase {

    String mlNoAccessUser = "ml_no_access";
    RestClient mlNoAccessClient;
    String mlReadOnlyUser = "ml_readonly";
    RestClient mlReadOnlyClient;
    String mlFullAccessNoIndexAccessUser = "ml_full_access_no_index_access";
    RestClient mlFullAccessNoIndexAccessClient;
    String mlFullAccessUser = "ml_full_access";
    RestClient mlFullAccessClient;
    String mlNonAdminFullAccessWithoutBackendRoleUser = "ml_non_admin_full_access_without_backend_role_user";
    RestClient mlNonAdminFullAccessWithoutBackendRoleClient;

    String mlNonOwnerFullAccessWithBackendRoleUser = "ml_non_owner_full_access_with_backend_role_user";
    RestClient mlNonOwnerFullAccessWithBackendRoleClient;

    private String indexSearchAccessRole = "ml_test_index_all_search";

    private String opensearchBackendRole = "opensearch";

    private MLRegisterModelGroupInput mlRegisterModelGroupInput;

    private MLUpdateModelGroupInput mlUpdateModelGroupInput;

    private final String MATCH_ALL_QUERY = "{\n" + "    \"query\": {\n" + "        \"match_all\": {}\n" + "    }\n" + "}";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private String modelGroupId;

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

        createUser(mlNoAccessUser, mlNoAccessUser, ImmutableList.of(opensearchBackendRole));
        mlNoAccessClient = new SecureRestClientBuilder(
            getClusterHosts().toArray(new HttpHost[0]),
            isHttps(),
            mlNoAccessUser,
            mlNoAccessUser
        ).setSocketTimeout(60000).build();

        createUser(mlReadOnlyUser, mlReadOnlyUser, ImmutableList.of(opensearchBackendRole));
        mlReadOnlyClient = new SecureRestClientBuilder(
            getClusterHosts().toArray(new HttpHost[0]),
            isHttps(),
            mlReadOnlyUser,
            mlReadOnlyUser
        ).setSocketTimeout(60000).build();

        createUser(mlFullAccessNoIndexAccessUser, mlFullAccessNoIndexAccessUser, ImmutableList.of(opensearchBackendRole));
        mlFullAccessNoIndexAccessClient = new SecureRestClientBuilder(
            getClusterHosts().toArray(new HttpHost[0]),
            isHttps(),
            mlFullAccessNoIndexAccessUser,
            mlFullAccessNoIndexAccessUser
        ).setSocketTimeout(60000).build();

        createUser(mlFullAccessUser, mlFullAccessUser, ImmutableList.of(opensearchBackendRole));
        mlFullAccessClient = new SecureRestClientBuilder(
            getClusterHosts().toArray(new HttpHost[0]),
            isHttps(),
            mlFullAccessUser,
            mlFullAccessUser
        ).setSocketTimeout(60000).build();

        createUser(mlNonAdminFullAccessWithoutBackendRoleUser, mlNonAdminFullAccessWithoutBackendRoleUser, ImmutableList.of());
        mlNonAdminFullAccessWithoutBackendRoleClient = new SecureRestClientBuilder(
            getClusterHosts().toArray(new HttpHost[0]),
            isHttps(),
            mlNonAdminFullAccessWithoutBackendRoleUser,
            mlNonAdminFullAccessWithoutBackendRoleUser
        ).setSocketTimeout(60000).build();

        createUser(
            mlNonOwnerFullAccessWithBackendRoleUser,
            mlNonOwnerFullAccessWithBackendRoleUser,
            ImmutableList.of(opensearchBackendRole)
        );
        mlNonOwnerFullAccessWithBackendRoleClient = new SecureRestClientBuilder(
            getClusterHosts().toArray(new HttpHost[0]),
            isHttps(),
            mlNonOwnerFullAccessWithBackendRoleUser,
            mlNonOwnerFullAccessWithBackendRoleUser
        ).setSocketTimeout(60000).build();

        createRoleMapping("ml_read_access", ImmutableList.of(mlReadOnlyUser));
        createRoleMapping(
            "ml_full_access",
            ImmutableList
                .of(
                    mlFullAccessNoIndexAccessUser,
                    mlFullAccessUser,
                    mlNonAdminFullAccessWithoutBackendRoleUser,
                    mlNonOwnerFullAccessWithBackendRoleUser
                )
        );
        createRoleMapping(
            indexSearchAccessRole,
            ImmutableList.of(mlFullAccessUser, mlNonAdminFullAccessWithoutBackendRoleUser, mlNonOwnerFullAccessWithBackendRoleUser)
        );

        mlRegisterModelGroupInput = createRegisterModelGroupInput(
            ImmutableList.of(opensearchBackendRole),
            ModelAccessMode.RESTRICTED,
            false
        );

        registerModelGroup(mlFullAccessClient, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            this.modelGroupId = (String) registerModelGroupResult.get("model_group_id");
        });

        mlUpdateModelGroupInput = createUpdateModelGroupInput(
            this.modelGroupId,
            "new_name",
            "new description",
            ImmutableList.of(opensearchBackendRole),
            ModelAccessMode.RESTRICTED,
            false
        );
    }

    @After
    public void deleteUserSetup() throws IOException {
        mlNoAccessClient.close();
        mlReadOnlyClient.close();
        mlFullAccessNoIndexAccessClient.close();
        mlFullAccessClient.close();
        mlNonAdminFullAccessWithoutBackendRoleClient.close();
        mlNonOwnerFullAccessWithBackendRoleClient.close();
        deleteUser(mlNoAccessUser);
        deleteUser(mlReadOnlyUser);
        deleteUser(mlFullAccessNoIndexAccessUser);
        deleteUser(mlFullAccessUser);
        deleteUser(mlNonAdminFullAccessWithoutBackendRoleUser);
        deleteUser(mlNonOwnerFullAccessWithBackendRoleUser);
    }

    public void test_registerModelGroup_withNoAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/register_model_group]");
        registerModelGroup(mlNoAccessClient, TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_registerModelGroup_WithReadOnlyMLAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/register_model_group]");
        registerModelGroup(mlReadOnlyClient, TestHelper.toJsonString(mlRegisterModelGroupInput), null);
    }

    public void test_registerModelGroup_withFullAccess() throws IOException {
        registerModelGroup(mlFullAccessClient, TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            assertTrue(registerModelGroupResult.containsKey("model_group_id"));
        });
    }

    public void test_updateModelGroup_withNoAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/update_model_group]");
        updateModelGroup(mlNoAccessClient, this.modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
    }

    public void test_updateModelGroup_WithReadOnlyMLAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/update_model_group]");
        updateModelGroup(mlReadOnlyClient, this.modelGroupId, TestHelper.toJsonString(mlUpdateModelGroupInput), null);
    }

    public void test_updateModelGroup_userIsOwner() throws IOException {
        updateModelGroup(
            mlFullAccessClient,
            this.modelGroupId,
            TestHelper.toJsonString(mlUpdateModelGroupInput),
            registerModelGroupResult -> {
                assertTrue(registerModelGroupResult.containsKey("status"));
            }
        );
    }

    public void test_updateModelGroup_userIsNonOwnerHasBackendRole() throws IOException {
        MLUpdateModelGroupInput mlUpdateModelGroupInput = createUpdateModelGroupInput(
            this.modelGroupId,
            "new_name",
            "new description",
            null,
            null,
            null
        );
        updateModelGroup(
            mlNonOwnerFullAccessWithBackendRoleClient,
            this.modelGroupId,
            TestHelper.toJsonString(mlUpdateModelGroupInput),
            registerModelGroupResult -> {
                assertTrue(registerModelGroupResult.containsKey("status"));
            }
        );
    }

    public void test_updateModelGroup_userIsNonOwnerNoBackendRole_withPermissionFields() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("Only owner/admin has valid privilege to perform update access control data");
        updateModelGroup(
            mlNonAdminFullAccessWithoutBackendRoleClient,
            this.modelGroupId,
            TestHelper.toJsonString(mlUpdateModelGroupInput),
            null
        );
    }

    public void test_updateModelGroup_userIsNonOwner_withoutPermissionFields() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("User doesn't have corresponding backend role to perform update action");
        MLUpdateModelGroupInput mlUpdateModelGroupInput = createUpdateModelGroupInput(
            this.modelGroupId,
            "new_name",
            "new description",
            null,
            null,
            null
        );
        updateModelGroup(
            mlNonAdminFullAccessWithoutBackendRoleClient,
            this.modelGroupId,
            TestHelper.toJsonString(mlUpdateModelGroupInput),
            null
        );
    }

    public void test_searchModelGroup_withNoAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/model_groups/search]");
        searchModelGroups(mlNoAccessClient, MATCH_ALL_QUERY, null);
    }

    public void test_searchModelGroup_WithReadOnlyMLAccess() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("no permissions for [cluster:admin/opensearch/ml/model_groups/search]");
        searchModelGroups(mlReadOnlyClient, MATCH_ALL_QUERY, null);
    }

    public void test_searchModelGroup_userIsOwner() throws IOException {
        searchModelGroups(mlFullAccessClient, MATCH_ALL_QUERY, r -> {
            assertTrue(r.containsKey("hits"));
            assertTrue(((Map<String, Object>) r.get("hits")).containsKey("total"));
            Map<String, Object> total = (Map<String, Object>) ((Map<String, Object>) r.get("hits")).get("total");
            assertEquals(1.0, total.get("value"));
        });
    }

    public void test_searchModelGroup_userNonOwnerHasBackendRole() throws IOException {
        searchModelGroups(mlNonOwnerFullAccessWithBackendRoleClient, MATCH_ALL_QUERY, r -> {
            assertTrue(r.containsKey("hits"));
            assertTrue(((Map<String, Object>) r.get("hits")).containsKey("total"));
            Map<String, Object> total = (Map<String, Object>) ((Map<String, Object>) r.get("hits")).get("total");
            assertEquals(1.0, total.get("value"));
        });
    }

    public void test_searchModelGroup_userHasNoModelAccess() throws IOException {
        searchModelGroups(mlNonAdminFullAccessWithoutBackendRoleClient, MATCH_ALL_QUERY, r -> {
            assertTrue(r.containsKey("hits"));
            assertTrue(((Map<String, Object>) r.get("hits")).containsKey("total"));
            Map<String, Object> total = (Map<String, Object>) ((Map<String, Object>) r.get("hits")).get("total");
            assertEquals(0.0, total.get("value"));
        });
    }

}
