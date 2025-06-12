/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
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

public class MLProfileModelResponseTests extends OpenSearchTestCase {

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
            .workerNodes(Arrays.asList("test_node"))
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
            .modelState(MLModelState.DEPLOYED)
            .modelInferenceStats(MLPredictRequestStats.builder().count(10L).average(11.0).max(20.0).min(5.0).build())
            .build();
    }

    public void test_create_MLProfileModelResponse_withArgs() throws IOException {
        String[] targetWorkerNodes = new String[] { "node1", "node2" };
        String[] workerNodes = new String[] { "node1" };
        Map<String, MLModelProfile> profileMap = new HashMap<>();
        Map<String, MLTask> taskMap = new HashMap<>();
        profileMap.put("node1", mlModelProfile);
        taskMap.put("node1", mlTask);
        MLProfileModelResponse response = new MLProfileModelResponse(targetWorkerNodes, workerNodes);
        response.getMlModelProfileMap().putAll(profileMap);
        response.getMlTaskMap().putAll(taskMap);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLProfileModelResponse newResponse = new MLProfileModelResponse(output.bytes().streamInput());
        assertNotNull(newResponse.getTargetWorkerNodes());
        assertNotNull(response.getTargetWorkerNodes());
        assertEquals(newResponse.getTargetWorkerNodes().length, response.getTargetWorkerNodes().length);
        assertEquals(newResponse.getMlModelProfileMap().size(), response.getMlModelProfileMap().size());
        assertEquals(newResponse.getMlTaskMap().size(), response.getMlTaskMap().size());
    }

    public void test_create_MLProfileModelResponse_NoArgs() throws IOException {
        MLProfileModelResponse response = new MLProfileModelResponse();
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLProfileModelResponse newResponse = new MLProfileModelResponse(output.bytes().streamInput());
        assertNull(response.getWorkerNodes());
        assertNull(newResponse.getWorkerNodes());
    }

    public void test_toXContent() throws IOException {
        String[] targetWorkerNodes = new String[] { "node1", "node2" };
        String[] workerNodes = new String[] { "node1" };
        Map<String, MLModelProfile> profileMap = new HashMap<>();
        Map<String, MLTask> taskMap = new HashMap<>();
        profileMap.put("node1", mlModelProfile);
        taskMap.put("node1", mlTask);
        MLProfileModelResponse response = new MLProfileModelResponse(targetWorkerNodes, workerNodes);
        response.getMlModelProfileMap().putAll(profileMap);
        response.getMlTaskMap().putAll(taskMap);

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String xContentString = TestHelper.xContentBuilderToString(builder);
    }

}
