/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import static org.opensearch.ml.processor.MLModelUtil.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.Mock;
import org.opensearch.OpenSearchParseException;
import org.opensearch.client.Client;
import org.opensearch.ingest.Processor;
import org.opensearch.script.ScriptService;
import org.opensearch.test.OpenSearchTestCase;

public class MLInferenceIngestProcessorFactoryTests extends OpenSearchTestCase {
    private MLInferenceIngestProcessor.Factory factory;
    @Mock
    private Client client;
    @Mock
    private ScriptService scriptService;

    @Before
    public void init() {
        factory = new MLInferenceIngestProcessor.Factory(scriptService, client);
    }

    public void testCreateRequiredFields() throws Exception {
        Map<String, Processor.Factory> registry = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        config.put(MODEL_ID, "model1");
        String processorTag = randomAlphaOfLength(10);
        MLInferenceIngestProcessor mLInferenceIngestProcessor = factory.create(registry, processorTag, null, config);
        assertNotNull(mLInferenceIngestProcessor);
        assertEquals(mLInferenceIngestProcessor.getTag(), processorTag);
        assertEquals(mLInferenceIngestProcessor.getType(), MLInferenceIngestProcessor.TYPE);
    }

    public void testCreateNoFieldPresent() throws Exception {
        Map<String, Object> config = new HashMap<>();
        try {
            factory.create(null, null, null, config);
            fail("factory create should have failed");
        } catch (OpenSearchParseException e) {
            assertEquals(e.getMessage(), ("[model_id] required property is missing"));
        }
    }

    public void testCreateOptionalFields() throws Exception {
        Map<String, Processor.Factory> registry = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        config.put(MODEL_ID, "model2");
        Map<String, Object> model_config = new HashMap<>();
        model_config.put("hidden_size", 768);
        model_config.put("gradient_checkpointing", false);
        model_config.put("position_embedding_type", "absolute");
        config.put(MODEL_CONFIG, model_config);
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("text", "inputs");
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("response", "text_embedding");
        outputMap.add(output);
        config.put(INPUT_MAP, inputMap);
        config.put(OUTPUT_MAP, outputMap);
        String processorTag = randomAlphaOfLength(10);

        MLInferenceIngestProcessor mLInferenceIngestProcessor = factory.create(registry, processorTag, null, config);
        assertNotNull(mLInferenceIngestProcessor);
        assertEquals(mLInferenceIngestProcessor.getTag(), processorTag);
        assertEquals(mLInferenceIngestProcessor.getType(), MLInferenceIngestProcessor.TYPE);
    }
}
