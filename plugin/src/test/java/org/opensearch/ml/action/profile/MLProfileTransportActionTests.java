/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.opensearch.Version;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.settings.Settings;
import org.opensearch.commons.authuser.User;
import org.opensearch.env.Environment;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.ml.profile.MLPredictRequestStats;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.ml.task.MLTaskCache;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.test.OpenSearchIntegTestCase;
import org.opensearch.transport.TransportService;

public class MLProfileTransportActionTests extends OpenSearchIntegTestCase {
    private MLProfileTransportAction action;
    private Environment environment;
    private MLTaskManager mlTaskManager;
    private MLModelManager mlModelManager;
    private MLTask mlTask;
    private MLModelProfile mlModelProfile;
    private String testTaskId;
    private String testModelId;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        environment = mock(Environment.class);
        Settings settings = Settings.builder().build();
        when(environment.settings()).thenReturn(settings);

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
        Map<String, MLTaskCache> taskCacheMap = new HashMap<>();
        taskCacheMap.put("test_id", new MLTaskCache(mlTask));
        mlTaskManager = mock(MLTaskManager.class);
        testTaskId = "test_task_id";
        when(mlTaskManager.getAllTaskIds()).thenReturn(new String[] { testTaskId });
        when(mlTaskManager.getMLTask(testTaskId)).thenReturn(mlTask);

        mlModelProfile = MLModelProfile
            .builder()
            .predictor("test_predictor")
            .workerNodes(new String[] { "node1", "node2" })
            .modelState(MLModelState.LOADED)
            .modelInferenceStats(MLPredictRequestStats.builder().count(10L).average(11.0).max(20.0).min(5.0).build())
            .build();
        testModelId = "test_model_id";
        mlModelManager = mock(MLModelManager.class);
        when(mlModelManager.getAllModelIds()).thenReturn(new String[] { testModelId });
        when(mlModelManager.getModelProfile(testModelId)).thenReturn(mlModelProfile);

        action = new MLProfileTransportAction(
            client().threadPool(),
            clusterService(),
            mock(TransportService.class),
            mock(ActionFilters.class),
            mlTaskManager,
            environment,
            mlModelManager
        );
    }

    public void testNewResponse() {
        String nodeId = "nodeId1";
        MLProfileRequest request = new MLProfileRequest(new String[] { nodeId }, new MLProfileInput());

        MLProfileResponse mlProfileResponse = action.newResponse(request, new ArrayList<>(), new ArrayList<>());
        assertNotNull(mlProfileResponse.getNodes());
    }

    public void testNewNodeRequest() {
        String nodeId = "nodeId1";
        MLProfileRequest mlTaskProfileRequest = new MLProfileRequest(new String[] { nodeId }, new MLProfileInput());

        MLProfileNodeRequest mlStatsNodeRequest1 = new MLProfileNodeRequest(mlTaskProfileRequest);
        MLProfileNodeRequest mlStatsNodeRequest2 = action.newNodeRequest(mlTaskProfileRequest);

        assertEquals(
            mlStatsNodeRequest1.getMlProfileRequest().getMlProfileInput(),
            mlStatsNodeRequest2.getMlProfileRequest().getMlProfileInput()
        );
    }

    public void testNewNodeResponse() throws IOException {
        Map<String, MLTask> taskProfileValues = new HashMap<>();
        DiscoveryNode localNode = new DiscoveryNode("node0", buildNewFakeTransportAddress(), Version.CURRENT);
        Map<String, MLModelProfile> modelProfile = new HashMap<>();
        MLProfileNodeResponse mlProfileNodeResponse = new MLProfileNodeResponse(localNode, taskProfileValues, modelProfile);
        BytesStreamOutput out = new BytesStreamOutput();
        mlProfileNodeResponse.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLProfileNodeResponse newProfileNodeResponse = action.newNodeResponse(in);
        Assert.assertEquals(mlProfileNodeResponse.getNodeTasksSize(), newProfileNodeResponse.getNodeTasksSize());
    }

    public void testNodeOperation() {
        String nodeId = clusterService().localNode().getId();
        MLProfileInput mlProfileInput1 = new MLProfileInput(
            new HashSet<>(),
            new HashSet<>(Arrays.asList(testTaskId)),
            new HashSet<>(),
            false,
            false,
            null
        );
        MLProfileInput mlProfileInput2 = new MLProfileInput(
            new HashSet<>(Arrays.asList(testModelId)),
            new HashSet<>(),
            new HashSet<>(),
            false,
            false,
            null
        );
        MLProfileRequest mlTaskProfileRequest1 = new MLProfileRequest(new String[] { nodeId }, mlProfileInput1);
        MLProfileRequest mlTaskProfileRequest2 = new MLProfileRequest(new String[] { nodeId }, mlProfileInput2);

        MLProfileNodeResponse response1 = action.nodeOperation(new MLProfileNodeRequest(mlTaskProfileRequest1));
        MLProfileNodeResponse response2 = action.nodeOperation(new MLProfileNodeRequest(mlTaskProfileRequest2));

        Assert.assertEquals(1, response1.getNodeTasksSize());
        assertNotNull(response1.getMlNodeTasks().get(testTaskId));
        Assert.assertEquals(1, response2.getNodeModelsSize());
    }

    public void testNodeOperation_emptyInputs() {
        String nodeId = clusterService().localNode().getId();
        MLProfileInput mlProfileInput = new MLProfileInput(new HashSet<>(), new HashSet<>(), new HashSet<>(), false, false, null);
        MLProfileRequest mlTaskProfileRequest = new MLProfileRequest(new String[] { nodeId }, mlProfileInput);

        MLProfileNodeResponse response = action.nodeOperation(new MLProfileNodeRequest(mlTaskProfileRequest));
        Assert.assertEquals(0, response.getNodeTasksSize());
        assertNull(response.getMlNodeTasks().get(testTaskId));
    }

    public void testNodeOperation_emptyResponses() {
        String nodeId = clusterService().localNode().getId();
        MLProfileInput mlProfileInput1 = new MLProfileInput(
            new HashSet<>(),
            new HashSet<>(Arrays.asList("newtest_id")),
            new HashSet<>(),
            false,
            false,
            null
        );
        MLProfileInput mlProfileInput2 = new MLProfileInput(
            new HashSet<>(Arrays.asList("newmodel_id")),
            new HashSet<>(),
            new HashSet<>(),
            false,
            false,
            null
        );
        MLProfileRequest mlTaskProfileRequest1 = new MLProfileRequest(new String[] { nodeId }, mlProfileInput1);
        MLProfileRequest mlTaskProfileRequest2 = new MLProfileRequest(new String[] { nodeId }, mlProfileInput2);

        MLProfileNodeResponse response1 = action.nodeOperation(new MLProfileNodeRequest(mlTaskProfileRequest1));
        MLProfileNodeResponse response2 = action.nodeOperation(new MLProfileNodeRequest(mlTaskProfileRequest2));

        Assert.assertEquals(0, response1.getNodeTasksSize());
        assertNull(response1.getMlNodeTasks().get(testTaskId));
        Assert.assertEquals(0, response2.getNodeTasksSize());
    }

    public void testNodeOperation_NoResponseIdNotMatch() {
        String nodeId = clusterService().localNode().getId();
        MLProfileRequest mlTaskProfileRequest = new MLProfileRequest(new String[] { nodeId }, new MLProfileInput());
        MLProfileNodeResponse response = action.nodeOperation(new MLProfileNodeRequest(mlTaskProfileRequest));

        assertEquals(0, response.getNodeTasksSize());
    }
}
