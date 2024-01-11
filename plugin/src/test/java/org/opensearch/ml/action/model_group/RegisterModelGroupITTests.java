/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupRequest;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 1)
public class RegisterModelGroupITTests extends MLCommonsIntegTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    public void test_register_public_model_group() {
        exceptionRule.expect(IllegalArgumentException.class);
        MLRegisterModelGroupInput input = new MLRegisterModelGroupInput(
            "mock_model_group_name",
            "mock_model_group_desc",
            null,
            AccessMode.PUBLIC,
            false
        );
        MLRegisterModelGroupRequest createModelGroupRequest = new MLRegisterModelGroupRequest(input);
        client().execute(MLRegisterModelGroupAction.INSTANCE, createModelGroupRequest).actionGet();
    }

    public void test_register_private_model_group() {
        exceptionRule.expect(IllegalArgumentException.class);
        MLRegisterModelGroupInput input = new MLRegisterModelGroupInput(
            "mock_model_group_name",
            "mock_model_group_desc",
            null,
            AccessMode.PRIVATE,
            false
        );
        MLRegisterModelGroupRequest createModelGroupRequest = new MLRegisterModelGroupRequest(input);
        client().execute(MLRegisterModelGroupAction.INSTANCE, createModelGroupRequest).actionGet();
    }

    public void test_register_model_group_without_access_fields() {
        MLRegisterModelGroupInput input = new MLRegisterModelGroupInput("mock_model_group_name", "mock_model_group_desc", null, null, null);
        MLRegisterModelGroupRequest createModelGroupRequest = new MLRegisterModelGroupRequest(input);
        client().execute(MLRegisterModelGroupAction.INSTANCE, createModelGroupRequest).actionGet();
    }

    public void test_register_protected_model_group_with_addAllBackendRoles_true() {
        exceptionRule.expect(IllegalArgumentException.class);
        MLRegisterModelGroupInput input = new MLRegisterModelGroupInput(
            "mock_model_group_name",
            "mock_model_group_desc",
            null,
            AccessMode.RESTRICTED,
            true
        );
        MLRegisterModelGroupRequest createModelGroupRequest = new MLRegisterModelGroupRequest(input);
        client().execute(MLRegisterModelGroupAction.INSTANCE, createModelGroupRequest).actionGet();
    }

    public void test_register_protected_model_group_with_backendRoles_notEmpty() {
        exceptionRule.expect(IllegalArgumentException.class);
        MLRegisterModelGroupInput input = new MLRegisterModelGroupInput(
            "mock_model_group_name",
            "mock_model_group_desc",
            List.of("role-1"),
            AccessMode.RESTRICTED,
            null
        );
        MLRegisterModelGroupRequest createModelGroupRequest = new MLRegisterModelGroupRequest(input);
        client().execute(MLRegisterModelGroupAction.INSTANCE, createModelGroupRequest).actionGet();
    }
}
