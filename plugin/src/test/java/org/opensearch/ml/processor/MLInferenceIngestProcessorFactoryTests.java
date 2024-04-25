/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.processor;

import static org.opensearch.ml.processor.InferenceProcessorAttributes.*;

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

    public void testExceedMaxPredictionTasks() throws Exception {
        Map<String, Processor.Factory> registry = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        config.put(MODEL_ID, "model2");
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input0 = new HashMap<>();
        input0.put("inputs", "text");
        inputMap.add(input0);
        Map<String, String> input1 = new HashMap<>();
        input1.put("inputs", "hashtag");
        inputMap.add(input1);
        Map<String, String> input2 = new HashMap<>();
        input2.put("inputs", "timestamp");
        inputMap.add(input2);
        config.put(INPUT_MAP, inputMap);
        config.put(MAX_PREDICTION_TASKS, 2);
        String processorTag = randomAlphaOfLength(10);

        try {
            factory.create(registry, processorTag, null, config);
        } catch (IllegalArgumentException e) {
            assertEquals(
                e.getMessage(),
                ("The number of prediction task setting in this process is 3. It exceeds the max_prediction_tasks of 2. Please reduce the length of input_map or increase max_prediction_tasks.")
            );
        }
    }

    public void testOutputMapsExceedInputMaps() throws Exception {
        Map<String, Processor.Factory> registry = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        config.put(MODEL_ID, "model2");
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input0 = new HashMap<>();
        input0.put("inputs", "text");
        inputMap.add(input0);
        Map<String, String> input1 = new HashMap<>();
        input1.put("inputs", "hashtag");
        inputMap.add(input1);
        config.put(INPUT_MAP, inputMap);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output1 = new HashMap<>();
        output1.put("response", "text_embedding");
        outputMap.add(output1);
        Map<String, String> output2 = new HashMap<>();
        output2.put("response", "hashtag_embedding");
        outputMap.add(output2);
        Map<String, String> output3 = new HashMap<>();
        output2.put("response", "hashtvg_embedding");
        outputMap.add(output3);
        config.put(OUTPUT_MAP, outputMap);
        config.put(MAX_PREDICTION_TASKS, 2);
        String processorTag = randomAlphaOfLength(10);

        try {
            factory.create(registry, processorTag, null, config);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), ("The length of output_map and the length of input_map do no match."));
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
        input.put("inputs", "text");
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("response", "text_embedding");
        outputMap.add(output);
        config.put(INPUT_MAP, inputMap);
        config.put(OUTPUT_MAP, outputMap);
        config.put(MAX_PREDICTION_TASKS, 5);
        String processorTag = randomAlphaOfLength(10);

        MLInferenceIngestProcessor mLInferenceIngestProcessor = factory.create(registry, processorTag, null, config);
        assertNotNull(mLInferenceIngestProcessor);
        assertEquals(mLInferenceIngestProcessor.getTag(), processorTag);
        assertEquals(mLInferenceIngestProcessor.getType(), MLInferenceIngestProcessor.TYPE);
    }
}
