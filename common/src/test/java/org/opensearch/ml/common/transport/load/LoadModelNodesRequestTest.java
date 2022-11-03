package org.opensearch.ml.common.transport.load;

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
import java.util.Collections;

import static org.junit.Assert.*;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

@RunWith(MockitoJUnitRunner.class)
public class LoadModelNodesRequestTest {

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
                .workerNode("node1")
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
        LoadModelInput loadModelInput = new LoadModelInput("modelId", "taskId", "modelContentHash", 3, "coordinatingNodeId", mlTask);
        LoadModelNodeRequest loadModelNodeRequest = new LoadModelNodeRequest(
                new LoadModelNodesRequest(nodeIds, loadModelInput)
        );
        BytesStreamOutput output = new BytesStreamOutput();

        loadModelNodeRequest.writeTo(output);

        assertNotNull(loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput());
        assertEquals("modelId", loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getModelId());
        assertEquals("taskId", loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getTaskId());
        assertEquals("modelContentHash", loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getModelContentHash());
        assertEquals(3, loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getNodeCount().intValue());
        assertEquals("coordinatingNodeId", loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getCoordinatingNodeId());
        assertEquals(mlTask.getTaskId(), loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getMlTask().getTaskId());
    }

    @Test
    public void testConstructorSerialization2() throws IOException {
        DiscoveryNode [] nodeIds = {localNode1, localNode2, localNode3};
        LoadModelInput loadModelInput = new LoadModelInput("modelId", "taskId", "modelContentHash", 3, "coordinatingNodeId", mlTask);
        LoadModelNodeRequest loadModelNodeRequest = new LoadModelNodeRequest(
                new LoadModelNodesRequest(nodeIds, loadModelInput)
        );
        assertNotNull(loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput());
        assertEquals("modelId", loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getModelId());
        assertEquals("taskId", loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getTaskId());
        assertEquals("modelContentHash", loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getModelContentHash());
        assertEquals(3, loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getNodeCount().intValue());
        assertEquals("coordinatingNodeId", loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getCoordinatingNodeId());
        assertEquals(mlTask.getTaskId(), loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getMlTask().getTaskId());
    }

    @Test
    public void testConstructorSerialization3() throws IOException {
        LoadModelNodeRequest loadModelNodeRequest = new LoadModelNodeRequest(
                new LoadModelNodesRequest(localNode1, localNode2, localNode3)
        );
        loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().setModelId("modelIdSetDuringTest");
        loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().setTaskId("taskIdSetDuringTest");
        loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().setModelContentHash("modelContentHashSetDuringTest");
        loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().setNodeCount(2);
        loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().setCoordinatingNodeId("coordinatingNodeIdSetDuringTest");
        loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().setMlTask(mlTask);
        assertNotNull(loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput());
        assertEquals("modelIdSetDuringTest", loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getModelId());
        assertEquals("taskIdSetDuringTest", loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getTaskId());
        assertEquals("modelContentHashSetDuringTest", loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getModelContentHash());
        assertEquals(2, loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getNodeCount().intValue());
        assertEquals("coordinatingNodeIdSetDuringTest", loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getCoordinatingNodeId());
        assertEquals(mlTask.getTaskId(), loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getMlTask().getTaskId());
    }

    @Test
    public void testConstructorFromInputStream() throws IOException {
        String [] nodeIds = {"id1", "id2", "id3"};
        LoadModelInput loadModelInput = new LoadModelInput("modelId", "taskId", "modelContentHash", 3, "coordinatingNodeId", mlTask);
        LoadModelNodeRequest loadModelNodeRequest = new LoadModelNodeRequest(
                new LoadModelNodesRequest(nodeIds, loadModelInput)
        );
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        loadModelNodeRequest.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        LoadModelNodeRequest parsedNodeRequest = new LoadModelNodeRequest(streamInput);

        assertNotNull(parsedNodeRequest.getLoadModelNodesRequest().getLoadModelInput());
        assertEquals(loadModelNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getModelId(),
                parsedNodeRequest.getLoadModelNodesRequest().getLoadModelInput().getModelId());
    }

}
