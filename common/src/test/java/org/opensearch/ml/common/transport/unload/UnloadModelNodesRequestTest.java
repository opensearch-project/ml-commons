package org.opensearch.ml.common.transport.unload;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.common.io.stream.BytesStreamOutput;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;

@RunWith(MockitoJUnitRunner.class)
public class UnloadModelNodesRequestTest {

    @Test
    public void testConstructorSerialization() throws IOException {

        String[] modelIds = {"modelId1", "modelId2", "modelId3"};
        String[] nodeIds = {"nodeId1", "nodeId2", "nodeId3"};

        UnloadModelNodeRequest unloadModelNodeRequest = new UnloadModelNodeRequest(
                new UnloadModelNodesRequest(nodeIds, modelIds)
        );
        BytesStreamOutput output = new BytesStreamOutput();

        unloadModelNodeRequest.writeTo(output);
        assertArrayEquals(new String[] {"modelId1", "modelId2", "modelId3"}, unloadModelNodeRequest.getUnloadModelNodesRequest().getModelIds());

    }
}
