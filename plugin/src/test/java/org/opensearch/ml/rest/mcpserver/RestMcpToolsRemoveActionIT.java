/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.rest.mcpserver;

import java.io.IOException;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.rest.MLCommonsRestTestCase;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

public class RestMcpToolsRemoveActionIT extends MLCommonsRestTestCase {

    @Before
    public void setupFeatureSettings() throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"" + MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED.getKey() + "\":true}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    public void testRemoveMcpTools() throws IOException {
        String registerRequestBody =
            """
                 {
                    "tools": [
                        {
                            "name": "ListIndexTool1",
                            "type": "ListIndexTool",
                            "description": "initial description",
                            "attributes": {
                                "input_schema": {
                                    "type": "object",
                                    "properties": {
                                        "indices": {
                                            "type": "array",
                                            "items": {
                                                "type": "string"
                                            },
                                            "description": "OpenSearch index name list, separated by comma. for example: [\\"index1\\", \\"index2\\"], use empty array [] to list all indices in the cluster"
                                        }
                                    },
                                    "additionalProperties": false
                                }
                            }
                        },
                        {
                            "name": "ListIndexTool2",
                            "type": "ListIndexTool",
                            "description": "initial description",
                            "attributes": {
                                "input_schema": {
                                    "type": "object",
                                    "properties": {
                                        "indices": {
                                            "type": "array",
                                            "items": {
                                                "type": "string"
                                            },
                                            "description": "OpenSearch index name list, separated by comma. for example: [\\"index1\\", \\"index2\\"], use empty array [] to list all indices in the cluster"
                                        }
                                    },
                                    "additionalProperties": false
                                }
                            }
                        }
                    ]
                }
                """;
        Response registerResponse = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/mcp/tools/_register", null, registerRequestBody, null);
        assert (registerResponse != null);
        assert (TestHelper.restStatus(registerResponse) == RestStatus.OK);
        HttpEntity registerResponseEntity = registerResponse.getEntity();
        String registerResString = TestHelper.httpEntityToString(registerResponseEntity);
        assertTrue(registerResString.contains("created"));

        String removeRequestBody = """
            [
                "ListIndexTool1, ListIndexTool2"
            ]
            """;
        Response removeResponse = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/mcp/tools/_remove", null, removeRequestBody, null);
        assert (removeResponse != null);
        assert (TestHelper.restStatus(removeResponse) == RestStatus.OK);
        HttpEntity removeResponseEntity = removeResponse.getEntity();
        String removeResString = TestHelper.httpEntityToString(removeResponseEntity);
        assertTrue(removeResString.contains("removed"));

        Response listResponse = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/mcp/tools/_list", null, "", null);
        assert (listResponse != null);
        assert (TestHelper.restStatus(listResponse) == RestStatus.OK);
        HttpEntity listHttpEntity = listResponse.getEntity();
        String listEntityString = TestHelper.httpEntityToString(listHttpEntity);
        assertFalse(listEntityString.contains("ListIndexTool1"));
        assertFalse(listEntityString.contains("ListIndexTool2"));
    }
}
