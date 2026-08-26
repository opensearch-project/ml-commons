/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;

import com.google.common.collect.ImmutableList;

public class BatchableInputRegistryTests {

    private BatchableInputRegistry registry;

    @Before
    public void setUp() {
        registry = new BatchableInputRegistry();
    }

    @Test
    public void get_TextDocs_ReturnsTextDocsHandler() {
        MLInput input = MLInput
            .builder()
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .inputDataset(TextDocsInputDataSet.builder().docs(ImmutableList.of("a", "b")).build())
            .build();
        assertTrue(registry.get(input) instanceof TextDocsBatchableInput);
    }

    @Test
    public void get_NullInput_ReturnsNull() {
        assertNull(registry.get(null));
    }

    @Test
    public void get_NullInputDataset_ReturnsNull() {
        MLInput input = MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(null).build();
        assertNull(registry.get(input));
    }

    /** No handler registered for this input type, so the caller learns batching does not apply. */
    @Test
    public void get_UnregisteredInputType_ReturnsNull() {
        MLInput input = MLInput
            .builder()
            .algorithm(FunctionName.REMOTE)
            .inputDataset(RemoteInferenceInputDataSet.builder().parameters(Collections.singletonMap("prompt", "hi")).build())
            .build();
        assertNull(registry.get(input));
    }
}
