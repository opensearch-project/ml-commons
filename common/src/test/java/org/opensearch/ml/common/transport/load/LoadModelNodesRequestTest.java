package org.opensearch.ml.common.transport.load;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.ml.common.MLTask;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class LoadModelNodesRequestTest {
    @Mock
    private MLTask mlTask;

    @Test
    public void testConstructorSerialization() throws IOException {

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
}
