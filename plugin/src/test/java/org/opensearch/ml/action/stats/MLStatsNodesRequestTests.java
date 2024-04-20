/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;
import java.util.Collections;

import org.junit.Assert;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStatsInput;
import org.opensearch.test.OpenSearchTestCase;

import com.google.common.collect.ImmutableSet;

public class MLStatsNodesRequestTests extends OpenSearchTestCase {

    public void testSerializationDeserialization() throws IOException {
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(new String[] { "testNodeId" }, new MLStatsInput());
        mlStatsNodesRequest.setHiddenModelIds(Collections.singleton("modelID"));

        mlStatsNodesRequest.addNodeLevelStats(ImmutableSet.of(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT));
        BytesStreamOutput output = new BytesStreamOutput();
        MLStatsNodeRequest request = new MLStatsNodeRequest(mlStatsNodesRequest);
        request.writeTo(output);
        MLStatsNodeRequest newRequest = new MLStatsNodeRequest(output.bytes().streamInput());
        Assert
            .assertEquals(
                newRequest.getMlStatsNodesRequest().getMlStatsInput().getNodeLevelStats().size(),
                request.getMlStatsNodesRequest().getMlStatsInput().getNodeLevelStats().size()
            );
        for (Enum stat : newRequest.getMlStatsNodesRequest().getMlStatsInput().getNodeLevelStats()) {
            Assert.assertTrue(request.getMlStatsNodesRequest().getMlStatsInput().getNodeLevelStats().contains(stat));
        }
        Assert.assertTrue(request.getMlStatsNodesRequest().getHiddenModelIds().contains("modelID"));
    }

    public void testSerializationDeserializationWithVersionThreshold() throws IOException {
        Version thresholdVersion = MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_AGENT_FRAMEWORK;
        MLStatsNodesRequest request = new MLStatsNodesRequest(new String[] { "testNodeId" }, new MLStatsInput());
        request.setHiddenModelIds(Collections.singleton("modelID"));
        request.addNodeLevelStats(ImmutableSet.of(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT));

        // Test serialization and deserialization above the threshold
        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(thresholdVersion);
        request.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        input.setVersion(thresholdVersion);

        MLStatsNodesRequest newRequest = new MLStatsNodesRequest(input);
        assertNotNull(newRequest.getHiddenModelIds());
        assertTrue(newRequest.getHiddenModelIds().contains("modelID"));

        // Test serialization and deserialization below the threshold
        Version belowThresholdVersion = Version.fromString("1.0.0"); // Assuming this is below the threshold
        output = new BytesStreamOutput();
        output.setVersion(belowThresholdVersion);
        request.writeTo(output);

        input = output.bytes().streamInput();
        input.setVersion(belowThresholdVersion);

        newRequest = new MLStatsNodesRequest(input);
        assertTrue(newRequest.getHiddenModelIds().isEmpty());
    }

    public void testNodeLevelStatsHandling() throws IOException {
        MLStatsNodesRequest request = new MLStatsNodesRequest(new String[] { "testNodeId" }, new MLStatsInput());
        request.addNodeLevelStats(ImmutableSet.of(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT));

        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);

        MLStatsNodesRequest newRequest = new MLStatsNodesRequest(output.bytes().streamInput());
        assertTrue(newRequest.getMlStatsInput().getNodeLevelStats().contains(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT));
    }

}
