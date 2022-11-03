package org.opensearch.ml.common.transport.sync;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class MLSyncUpInputTest {


    @Test
    public void testConstructorSerialization_SuccessWithNullFields() throws IOException {
        MLSyncUpInput syncUpInputWithNullFields = MLSyncUpInput.builder()
                .getLoadedModels(true)
                .clearRoutingTable(true)
                .syncRunningLoadModelTasks(true)
                .build();

        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        syncUpInputWithNullFields.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLSyncUpInput parsedInput = new MLSyncUpInput(streamInput);

        assertNull(parsedInput.getAddedWorkerNodes());
        assertNull(parsedInput.getRemovedWorkerNodes());
        assertNull(parsedInput.getModelRoutingTable());
        assertNull(parsedInput.getAddedWorkerNodes());
    }

    @Test
    public void testConstructorSerialization_SuccessWithFullFields() throws IOException {
        Map<String, String[]> addedWorkerNodes = new HashMap<>();
        Map<String, String[]> removedWorkerNodes = new HashMap<>();
        Map<String, Set<String>> modelRoutingTable = new HashMap<>();
        Map<String, Set<String>> runningLoadModelTasks = new HashMap<>();

        MLSyncUpInput syncUpInput = MLSyncUpInput.builder()
                .getLoadedModels(true)
                .addedWorkerNodes(addedWorkerNodes)
                .removedWorkerNodes(removedWorkerNodes)
                .modelRoutingTable(modelRoutingTable)
                .runningLoadModelTasks(runningLoadModelTasks)
                .clearRoutingTable(true)
                .syncRunningLoadModelTasks(true)
                .build();

        Set<String> modelRoutingTableSet = new HashSet<>();
        Set<String> runningLoadModelTaskSet = new HashSet<>();
        modelRoutingTableSet.add("modelRoutingTable1");
        runningLoadModelTaskSet.add("runningLoadModelTask1");
        addedWorkerNodes.put("addedWorkerNodesKey1", new String [] {"addedWorkerNode1"});
        removedWorkerNodes.put("removedWorkerNodesKey1", new String [] {"removedWorkerNode1"});
        modelRoutingTable.put("modelRoutingTableKey1",modelRoutingTableSet);
        runningLoadModelTasks.put("runningLoadModelTaskKey1",runningLoadModelTaskSet);

        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        syncUpInput.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLSyncUpInput parsedInput = new MLSyncUpInput(streamInput);

        assertArrayEquals(syncUpInput.getAddedWorkerNodes().get("addedWorkerNodesKey1"), parsedInput.getAddedWorkerNodes().get("addedWorkerNodesKey1"));
        assertArrayEquals(syncUpInput.getRemovedWorkerNodes().get("removedWorkerNodesKey1"), parsedInput.getRemovedWorkerNodes().get("removedWorkerNodesKey1"));
        assertEquals(syncUpInput.getModelRoutingTable().get("modelRoutingTableKey1"), parsedInput.getModelRoutingTable().get("modelRoutingTableKey1"));
        assertEquals(syncUpInput.getRunningLoadModelTasks().get("runningLoadModelTaskKey1"), parsedInput.getRunningLoadModelTasks().get("runningLoadModelTaskKey1"));

    }
}
