/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class MLPromptGetActionTest {
    @Test
    public void testMLPromptGetActionInstance() {
        assertNotNull(MLPromptGetAction.INSTANCE);
        assertEquals(MLPromptGetAction.NAME, "cluster:admin/opensearch/ml/prompts/get");
    }
}
