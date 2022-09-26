/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import static org.opensearch.ml.action.profile.MLProfileNodeResponse.readProfile;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.commons.authuser.User;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.test.OpenSearchTestCase;

public class MLProfileNodeResponseTests extends OpenSearchTestCase {
    MLTask mlTask;
    DiscoveryNode localNode;

    @Before
    public void setup() {
        mlTask = MLTask
            .builder()
            .taskId("test_id")
            .modelId("model_id")
            .taskType(MLTaskType.TRAINING)
            .functionName(FunctionName.AD_LIBSVM)
            .state(MLTaskState.CREATED)
            .inputType(MLInputDataType.DATA_FRAME)
            .progress(0.4f)
            .outputIndex("test_index")
            .workerNode("test_node")
            .createTime(Instant.ofEpochMilli(123))
            .lastUpdateTime(Instant.ofEpochMilli(123))
            .error("error")
            .user(new User())
            .async(false)
            .build();

        localNode = new DiscoveryNode("node0", buildNewFakeTransportAddress(), Version.CURRENT);
    }

    public void testSerializationDeserialization() throws IOException {
        Map<String, MLTask> idToTasks = new HashMap<>();
        idToTasks.put("test_id", mlTask);
        MLProfileNodeResponse response = new MLProfileNodeResponse(localNode, idToTasks);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLProfileNodeResponse newResponse = new MLProfileNodeResponse(output.bytes().streamInput());
        Assert.assertEquals(newResponse.getNodeTasksSize(), response.getNodeTasksSize());
    }

    public void testSerializationDeserialization_NullNodeTasks() throws IOException {
        MLProfileNodeResponse response = new MLProfileNodeResponse(localNode, null);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLProfileNodeResponse newResponse = new MLProfileNodeResponse(output.bytes().streamInput());
        Assert.assertEquals(newResponse.getMlNodeTasks(), null);
        Assert.assertEquals(newResponse.isEmpty(), true);
        Assert.assertEquals(newResponse.getNodeTasksSize(), 0);
    }

    public void testReadProfile() throws IOException {
        MLProfileNodeResponse response = new MLProfileNodeResponse(localNode, new HashMap<>());
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLProfileNodeResponse newResponse = readProfile(output.bytes().streamInput());
        Assert.assertNotEquals(newResponse, response);
    }
}
