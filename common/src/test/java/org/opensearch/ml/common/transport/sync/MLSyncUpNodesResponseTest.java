package org.opensearch.ml.common.transport.sync;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.*;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.action.FailedNodeException;
import org.opensearch.cluster.ClusterName;
import org.opensearch.common.io.stream.BytesStreamOutput;

@RunWith(MockitoJUnitRunner.class)
public class MLSyncUpNodesResponseTest {

    private ClusterName clusterName;

    @Before
    public void setUp() throws Exception {
        clusterName = new ClusterName("clusterName");
    }

    @Test
    public void testSerializationDeserialization1() throws IOException {
        List<MLSyncUpNodeResponse> responseList = new ArrayList<>();
        List<FailedNodeException> failuresList = new ArrayList<>();
        MLSyncUpNodesResponse response = new MLSyncUpNodesResponse(clusterName, responseList, failuresList);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLSyncUpNodesResponse newResponse = new MLSyncUpNodesResponse(output.bytes().streamInput());
        assertEquals(newResponse.getNodes().size(), response.getNodes().size());
    }

}
