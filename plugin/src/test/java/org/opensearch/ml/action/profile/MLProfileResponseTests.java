/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.opensearch.Version;
import org.opensearch.action.FailedNodeException;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.ml.profile.MLPredictRequestStats;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.test.OpenSearchTestCase;

public class MLProfileResponseTests extends OpenSearchTestCase {
    MLTask mlTask;
    MLModelProfile mlModelProfile;

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
        mlModelProfile = MLModelProfile
            .builder()
            .predictor("test_predictor")
            .workerNodes(new String[] { "node1", "node2" })
            .modelState(MLModelState.LOADED)
            .predictStats(MLPredictRequestStats.builder().count(10L).average(11.0).max(20.0).min(5.0).build())
            .build();
    }

    public void testSerializationDeserialization() throws IOException {
        ClusterName clusterName = new ClusterName("clusterName");
        List<MLProfileNodeResponse> responseList = new ArrayList<>();
        List<FailedNodeException> failuresList = new ArrayList<>();
        MLProfileResponse mlTaskProfileResponse = new MLProfileResponse(clusterName, responseList, failuresList);

        BytesStreamOutput output = new BytesStreamOutput();
        mlTaskProfileResponse.writeTo(output);
        MLProfileResponse newResponse = new MLProfileResponse(output.bytes().streamInput());
        Assert.assertEquals(newResponse.getNodes().size(), mlTaskProfileResponse.getNodes().size());
    }

    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        ClusterName clusterName = new ClusterName("test");
        List<MLProfileNodeResponse> nodes = new ArrayList<>();

        DiscoveryNode node1 = new DiscoveryNode("node1", buildNewFakeTransportAddress(), Version.CURRENT);
        Map<String, MLTask> nodeLevelTaskProdile1 = new HashMap<>();
        nodeLevelTaskProdile1.put("task_1", mlTask);

        Map<String, MLModelProfile> nodelLevelModelProfile = new HashMap<>();
        nodelLevelModelProfile.put("model1", mlModelProfile);
        nodes.add(new MLProfileNodeResponse(node1, nodeLevelTaskProdile1, nodelLevelModelProfile));

        List<FailedNodeException> failures = new ArrayList<>();
        MLProfileResponse response = new MLProfileResponse(clusterName, nodes, failures);
        builder.startObject();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject();
        String taskContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"nodes\":{\"node1\":{\"tasks\":{\"task_1\":{\"task_id\":\"test_id\",\"model_id\":\"model_id\","
                + "\"task_type\":\"TRAINING\",\"function_name\":\"AD_LIBSVM\",\"state\":\"CREATED\",\"input_type\":"
                + "\"DATA_FRAME\",\"progress\":0.4,\"output_index\":\"test_index\",\"worker_node\":\"test_node\","
                + "\"create_time\":123,\"last_update_time\":123,\"error\":\"error\",\"user\":{\"name\":\"\","
                + "\"backend_roles\":[],\"roles\":[],\"custom_attribute_names\":[],\"user_requested_tenant\":null},"
                + "\"is_async\":false}},\"models\":{\"model1\":{\"model_state\":\"LOADED\",\"predictor\":\"test_predictor\","
                + "\"worker_nodes\":[\"node1\",\"node2\"],\"predict_request_stats\":{\"count\":10,\"max\":20.0,"
                + "\"min\":5.0,\"average\":11.0}}}}}}",
            taskContent
        );
    }
}
