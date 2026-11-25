/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.tools;

import static org.opensearch.ml.utils.TestHelper.makeRequest;

import org.junit.Before;
import org.opensearch.ml.rest.MLCommonsRestTestCase;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 3)
public class ScratchPadToolIT extends MLCommonsRestTestCase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    public void testScratchpadSizeLimit() throws Exception {
        String largeContent = "A".repeat(100 * 1024 * 1024);
        String requestBody = String.format("{\"parameters\":{\"notes\":\"%s\"}}", largeContent);

        Exception exception = expectThrows(Exception.class, () -> {
            makeRequest(client(), "POST", "/_plugins/_ml/tools/_execute/WriteToScratchPadTool", null, requestBody, null);
        });

        String errorMessage = exception.getMessage().toLowerCase();
        assertTrue(
            "Expected HTTP content length error, got: " + errorMessage,
            errorMessage.contains("content length")
                || errorMessage.contains("too large")
                || errorMessage.contains("entity too large")
                || errorMessage.contains("413")
        );
    }
}
