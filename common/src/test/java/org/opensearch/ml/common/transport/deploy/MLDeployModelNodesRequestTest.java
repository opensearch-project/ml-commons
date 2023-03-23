package org.opensearch.ml.common.transport.deploy;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.transport.TransportAddress;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataset.MLInputDataType;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

@RunWith(MockitoJUnitRunner.class)
public class MLDeployModelNodesRequestTest {

    private DiscoveryNode localNode1;
    private DiscoveryNode localNode2;
    private DiscoveryNode localNode3;

    @Mock
    private MLTask mlTask;

    @Before
    public void setUp() throws Exception {
        localNode1 = new DiscoveryNode(
                "foo1",
                "foo1",
                new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
                Collections.emptyMap(),
                Collections.singleton(CLUSTER_MANAGER_ROLE),
                Version.CURRENT
        );
        localNode2 = new DiscoveryNode(
                "foo2",
                "foo2",
                new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
                Collections.emptyMap(),
                Collections.singleton(CLUSTER_MANAGER_ROLE),
                Version.CURRENT
        );
        localNode3 = new DiscoveryNode(
                "foo3",
                "foo3",
                new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
                Collections.emptyMap(),
                Collections.singleton(CLUSTER_MANAGER_ROLE),
                Version.CURRENT
        );

        Instant time = Instant.now();
        mlTask = MLTask.builder()
                .taskId("mlTaskTaskId")
                .modelId("mlTaskModelId")
                .taskType(MLTaskType.PREDICTION)
                .functionName(FunctionName.LINEAR_REGRESSION)
                .state(MLTaskState.RUNNING)
                .inputType(MLInputDataType.DATA_FRAME)
                .workerNodes(Arrays.asList("node1"))
                .progress(0.0f)
                .outputIndex("test_index")
                .error("test_error")
                .createTime(time.minus(1, ChronoUnit.MINUTES))
                .lastUpdateTime(time)
                .build();

    }

    @Test
    public void testConstructorSerialization1() throws IOException {
        String [] nodeIds = {"id1", "id2", "id3"};
        MLDeployModelInput MLDeployModelInput = new MLDeployModelInput("modelId", "taskId", "modelContentHash", 3, "coordinatingNodeId", mlTask);
        MLDeployModelNodeRequest MLDeployModelNodeRequest = new MLDeployModelNodeRequest(
                new MLDeployModelNodesRequest(nodeIds, MLDeployModelInput)
        );
        BytesStreamOutput output = new BytesStreamOutput();

        MLDeployModelNodeRequest.writeTo(output);

        assertNotNull(MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput());
        assertEquals("modelId", MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getModelId());
        assertEquals("taskId", MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getTaskId());
        assertEquals("modelContentHash", MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getModelContentHash());
        assertEquals(3, MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getNodeCount().intValue());
        assertEquals("coordinatingNodeId", MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getCoordinatingNodeId());
        assertEquals(mlTask.getTaskId(), MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getMlTask().getTaskId());
    }

    @Test
    public void testConstructorSerialization2() throws IOException {
        DiscoveryNode [] nodeIds = {localNode1, localNode2, localNode3};
        MLDeployModelInput MLDeployModelInput = new MLDeployModelInput("modelId", "taskId", "modelContentHash", 3, "coordinatingNodeId", mlTask);
        MLDeployModelNodeRequest MLDeployModelNodeRequest = new MLDeployModelNodeRequest(
                new MLDeployModelNodesRequest(nodeIds, MLDeployModelInput)
        );
        assertNotNull(MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput());
        assertEquals("modelId", MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getModelId());
        assertEquals("taskId", MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getTaskId());
        assertEquals("modelContentHash", MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getModelContentHash());
        assertEquals(3, MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getNodeCount().intValue());
        assertEquals("coordinatingNodeId", MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getCoordinatingNodeId());
        assertEquals(mlTask.getTaskId(), MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getMlTask().getTaskId());
    }

    @Test
    public void testConstructorSerialization3() throws IOException {
        MLDeployModelNodeRequest MLDeployModelNodeRequest = new MLDeployModelNodeRequest(
                new MLDeployModelNodesRequest(localNode1, localNode2, localNode3)
        );
        MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().setModelId("modelIdSetDuringTest");
        MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().setTaskId("taskIdSetDuringTest");
        MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().setModelContentHash("modelContentHashSetDuringTest");
        MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().setNodeCount(2);
        MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().setCoordinatingNodeId("coordinatingNodeIdSetDuringTest");
        MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().setMlTask(mlTask);
        assertNotNull(MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput());
        assertEquals("modelIdSetDuringTest", MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getModelId());
        assertEquals("taskIdSetDuringTest", MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getTaskId());
        assertEquals("modelContentHashSetDuringTest", MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getModelContentHash());
        assertEquals(2, MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getNodeCount().intValue());
        assertEquals("coordinatingNodeIdSetDuringTest", MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getCoordinatingNodeId());
        assertEquals(mlTask.getTaskId(), MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getMlTask().getTaskId());
    }

    @Test
    public void testConstructorFromInputStream() throws IOException {
        String [] nodeIds = {"id1", "id2", "id3"};
        MLDeployModelInput MLDeployModelInput = new MLDeployModelInput("modelId", "taskId", "modelContentHash", 3, "coordinatingNodeId", mlTask);
        MLDeployModelNodeRequest MLDeployModelNodeRequest = new MLDeployModelNodeRequest(
                new MLDeployModelNodesRequest(nodeIds, MLDeployModelInput)
        );
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        MLDeployModelNodeRequest.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLDeployModelNodeRequest parsedNodeRequest = new MLDeployModelNodeRequest(streamInput);

        assertNotNull(parsedNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput());
        assertEquals(MLDeployModelNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getModelId(),
                parsedNodeRequest.getMLDeployModelNodesRequest().getMLDeployModelInput().getModelId());
    }

}
