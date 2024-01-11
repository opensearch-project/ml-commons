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
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupResponse;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupRequest;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 1)
public class UpdateModelGroupITTests extends MLCommonsIntegTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private String modelGroupId;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        registerModelGroup();
    }

    private void registerModelGroup() {
        MLRegisterModelGroupInput input = new MLRegisterModelGroupInput("mock_model_group_name", "mock_model_group_desc", null, null, null);
        MLRegisterModelGroupRequest createModelGroupRequest = new MLRegisterModelGroupRequest(input);
        MLRegisterModelGroupResponse response = client().execute(MLRegisterModelGroupAction.INSTANCE, createModelGroupRequest).actionGet();
        this.modelGroupId = response.getModelGroupId();
    }

    public void test_update_public_model_group() {
        exceptionRule.expect(IllegalArgumentException.class);
        MLUpdateModelGroupInput input = new MLUpdateModelGroupInput(
            modelGroupId,
            "mock_model_group_name",
            "mock_model_group_desc",
            null,
            AccessMode.PUBLIC,
            false
        );
        MLUpdateModelGroupRequest createModelGroupRequest = new MLUpdateModelGroupRequest(input);
        client().execute(MLUpdateModelGroupAction.INSTANCE, createModelGroupRequest).actionGet();
    }

    public void test_update_private_model_group() {
        exceptionRule.expect(IllegalArgumentException.class);
        MLUpdateModelGroupInput input = new MLUpdateModelGroupInput(
            modelGroupId,
            "mock_model_group_name",
            "mock_model_group_desc",
            null,
            AccessMode.PRIVATE,
            false
        );
        MLUpdateModelGroupRequest createModelGroupRequest = new MLUpdateModelGroupRequest(input);
        client().execute(MLUpdateModelGroupAction.INSTANCE, createModelGroupRequest).actionGet();
    }

    public void test_update_model_group_without_access_fields() {
        MLUpdateModelGroupInput input = new MLUpdateModelGroupInput(
            modelGroupId,
            "mock_model_group_name2",
            "mock_model_group_desc",
            null,
            null,
            null
        );
        MLUpdateModelGroupRequest createModelGroupRequest = new MLUpdateModelGroupRequest(input);
        client().execute(MLUpdateModelGroupAction.INSTANCE, createModelGroupRequest).actionGet();
    }

    public void test_update_protected_model_group_with_addAllBackendRoles_true() {
        exceptionRule.expect(IllegalArgumentException.class);
        MLUpdateModelGroupInput input = new MLUpdateModelGroupInput(
            modelGroupId,
            "mock_model_group_name",
            "mock_model_group_desc",
            null,
            AccessMode.RESTRICTED,
            true
        );
        MLUpdateModelGroupRequest createModelGroupRequest = new MLUpdateModelGroupRequest(input);
        client().execute(MLUpdateModelGroupAction.INSTANCE, createModelGroupRequest).actionGet();
    }

    public void test_update_protected_model_group_with_backendRoles_notEmpty() {
        exceptionRule.expect(IllegalArgumentException.class);
        MLUpdateModelGroupInput input = new MLUpdateModelGroupInput(
            modelGroupId,
            "mock_model_group_name",
            "mock_model_group_desc",
            List.of("role-1"),
            AccessMode.RESTRICTED,
            null
        );
        MLUpdateModelGroupRequest createModelGroupRequest = new MLUpdateModelGroupRequest(input);
        client().execute(MLUpdateModelGroupAction.INSTANCE, createModelGroupRequest).actionGet();
    }
}
