/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.transport.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.utils.TestHelper;

public class RestMLDeleteModelGroupActionIT extends MLCommonsRestTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    private MLRegisterModelGroupInput mlRegisterModelGroupInput;
    private String modelGroupId;

    @Before
    public void setup() throws IOException {
        mlRegisterModelGroupInput = MLRegisterModelGroupInput.builder().name("testGroupID").description("This is test Group").build();
        registerModelGroup(client(), TestHelper.toJsonString(mlRegisterModelGroupInput), registerModelGroupResult -> {
            this.modelGroupId = (String) registerModelGroupResult.get("model_group_id");
        });
    }

    public void testDeleteModelGroupAPI_Success() throws IOException {

        Response deleteModelGroupResponse = TestHelper
            .makeRequest(client(), "DELETE", "/_plugins/_ml/model_groups/" + modelGroupId, null, "", null);
        assertNotNull(deleteModelGroupResponse);
        assertEquals(RestStatus.OK, TestHelper.restStatus(deleteModelGroupResponse));
    }

    public void testDeleteAndRegisterModelGroup_Success() throws IOException {

        Response deleteModelGroupResponse = TestHelper
            .makeRequest(client(), "DELETE", "/_plugins/_ml/model_groups/" + modelGroupId, null, "", null);

        if (TestHelper.restStatus(deleteModelGroupResponse).equals(RestStatus.OK)) {
            MLRegisterModelGroupInput newMlRegisterModelGroupInput = MLRegisterModelGroupInput
                .builder()
                .name("testGroupID")
                .description("This is a new test Group")
                .build();

            registerModelGroup(client(), TestHelper.toJsonString(newMlRegisterModelGroupInput), registerModelGroupResponse -> {
                assertNotNull(registerModelGroupResponse);
                assertEquals("CREATED", registerModelGroupResponse.get("status"));
            });
        }
    }
}
