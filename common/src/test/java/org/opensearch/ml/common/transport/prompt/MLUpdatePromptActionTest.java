/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class MLUpdatePromptActionTest {
    @Test
    public void testMLUpdatePromptActionInstance() {
        assertNotNull(MLUpdatePromptAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/prompts/update", MLUpdatePromptAction.NAME);
    }
}
