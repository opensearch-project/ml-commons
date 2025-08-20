/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.model.MLModelState;

public class MLModelProfileTests {

    @Test
    public void testConstructorAndGetters() {
        String[] targetNodes = { "node1", "node2" };
        String[] workerNodes = { "worker1", "worker2" };
        MLPredictRequestStats stats = MLPredictRequestStats.builder().count(10L).max(5.0).min(1.0).average(3.0).build();

        MLModelProfile profile = MLModelProfile
            .builder()
            .modelState(MLModelState.DEPLOYED)
            .predictor("test-predictor")
            .targetWorkerNodes(targetNodes)
            .workerNodes(workerNodes)
            .modelInferenceStats(stats)
            .predictRequestStats(stats)
            .memSizeEstimationCPU(1024L)
            .memSizeEstimationGPU(2048L)
            .build();

        assertEquals(MLModelState.DEPLOYED, profile.getModelState());
        assertEquals("test-predictor", profile.getPredictor());
        assertEquals(targetNodes, profile.getTargetWorkerNodes());
        assertEquals(workerNodes, profile.getWorkerNodes());
        assertEquals(stats, profile.getModelInferenceStats());
        assertEquals(stats, profile.getPredictRequestStats());
        assertEquals(Long.valueOf(1024L), profile.getMemSizeEstimationCPU());
        assertEquals(Long.valueOf(2048L), profile.getMemSizeEstimationGPU());
        assertNull(profile.getIsHidden());
    }

    @Test
    public void testConstructorWithNullValues() {
        MLModelProfile profile = MLModelProfile.builder().build();

        assertNull(profile.getModelState());
        assertNull(profile.getPredictor());
        assertNull(profile.getTargetWorkerNodes());
        assertNull(profile.getWorkerNodes());
        assertNull(profile.getModelInferenceStats());
        assertNull(profile.getPredictRequestStats());
        assertNull(profile.getMemSizeEstimationCPU());
        assertNull(profile.getMemSizeEstimationGPU());
        assertNull(profile.getIsHidden());
    }

    @Test
    public void testSetIsHidden() {
        MLModelProfile profile = MLModelProfile.builder().build();
        profile.setIsHidden(true);
        assertTrue(profile.getIsHidden());

        profile.setIsHidden(false);
        assertFalse(profile.getIsHidden());
    }

    @Test
    public void testToXContentWithAllFields() throws IOException {
        String[] targetNodes = { "node1" };
        String[] workerNodes = { "worker1" };
        MLPredictRequestStats stats = MLPredictRequestStats.builder().count(10L).max(5.0).build();

        MLModelProfile profile = MLModelProfile
            .builder()
            .modelState(MLModelState.DEPLOYED)
            .predictor("test-predictor")
            .targetWorkerNodes(targetNodes)
            .workerNodes(workerNodes)
            .modelInferenceStats(stats)
            .predictRequestStats(stats)
            .memSizeEstimationCPU(1024L)
            .memSizeEstimationGPU(2048L)
            .build();
        profile.setIsHidden(true);

        XContentBuilder builder = XContentFactory.jsonBuilder();
        profile.toXContent(builder, null);

        String json = builder.toString();
        assertTrue(json.contains("\"model_state\":\"DEPLOYED\""));
        assertTrue(json.contains("\"predictor\":\"test-predictor\""));
        assertTrue(json.contains("\"target_worker_nodes\":[\"node1\"]"));
        assertTrue(json.contains("\"worker_nodes\":[\"worker1\"]"));
        assertTrue(json.contains("\"model_inference_stats\""));
        assertTrue(json.contains("\"predict_request_stats\""));
        assertTrue(json.contains("\"memory_size_estimation_cpu\":1024"));
        assertTrue(json.contains("\"memory_size_estimation_gpu\":2048"));
        assertTrue(json.contains("\"is_hidden\":true"));
    }

    @Test
    public void testToXContentWithNullFields() throws IOException {
        MLModelProfile profile = MLModelProfile.builder().build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        profile.toXContent(builder, null);

        String json = builder.toString();
        assertEquals("{}", json);
    }

    @Test
    public void testToXContentWithIsHiddenFalse() throws IOException {
        MLModelProfile profile = MLModelProfile.builder().build();
        profile.setIsHidden(false);

        XContentBuilder builder = XContentFactory.jsonBuilder();
        profile.toXContent(builder, null);

        String json = builder.toString();
        assertEquals("{}", json); // is_hidden is only included when true
    }

    @Test
    public void testStreamSerializationWithAllFields() throws IOException {
        String[] targetNodes = { "node1" };
        String[] workerNodes = { "worker1" };
        MLPredictRequestStats stats = MLPredictRequestStats.builder().count(10L).max(5.0).build();

        MLModelProfile original = MLModelProfile
            .builder()
            .modelState(MLModelState.DEPLOYED)
            .predictor("test-predictor")
            .targetWorkerNodes(targetNodes)
            .workerNodes(workerNodes)
            .modelInferenceStats(stats)
            .predictRequestStats(stats)
            .memSizeEstimationCPU(1024L)
            .memSizeEstimationGPU(2048L)
            .build();
        original.setIsHidden(true);

        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(Version.CURRENT);
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        input.setVersion(Version.CURRENT);
        MLModelProfile deserialized = new MLModelProfile(input);

        assertEquals(original.getModelState(), deserialized.getModelState());
        assertEquals(original.getPredictor(), deserialized.getPredictor());
        Assert.assertNotNull(deserialized.getTargetWorkerNodes());
        assertEquals(original.getTargetWorkerNodes()[0], deserialized.getTargetWorkerNodes()[0]);
        Assert.assertNotNull(deserialized.getWorkerNodes());
        assertEquals(original.getWorkerNodes()[0], deserialized.getWorkerNodes()[0]);
        assertEquals(original.getMemSizeEstimationCPU(), deserialized.getMemSizeEstimationCPU());
        assertEquals(original.getMemSizeEstimationGPU(), deserialized.getMemSizeEstimationGPU());
        assertEquals(original.getIsHidden(), deserialized.getIsHidden());
    }

    @Test
    public void testStreamSerializationWithNullFields() throws IOException {
        MLModelProfile original = MLModelProfile.builder().build();

        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(Version.CURRENT);
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        input.setVersion(Version.CURRENT);
        MLModelProfile deserialized = new MLModelProfile(input);

        assertNull(deserialized.getModelState());
        assertNull(deserialized.getPredictor());
        assertNull(deserialized.getTargetWorkerNodes());
        assertNull(deserialized.getWorkerNodes());
        assertNull(deserialized.getModelInferenceStats());
        assertNull(deserialized.getPredictRequestStats());
        assertNull(deserialized.getMemSizeEstimationCPU());
        assertNull(deserialized.getMemSizeEstimationGPU());
    }
}
