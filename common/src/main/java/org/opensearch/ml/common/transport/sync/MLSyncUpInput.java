/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.sync;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;

import lombok.Builder;
import lombok.Data;

@Data
public class MLSyncUpInput implements Writeable {
    private boolean getDeployedModels;
    // key is model id, value is set of added worker node ids
    private Map<String, String[]> addedWorkerNodes;
    // key is model id, value is set of removed worker node ids
    private Map<String, String[]> removedWorkerNodes;
    // key is model id, value is set of worker node ids
    private Map<String, Set<String>> modelRoutingTable;
    // key is task id, value is set of worker node ids
    private Map<String, Set<String>> runningDeployModelTasks;
    // clear model routing table if no running model in cluster
    private boolean clearRoutingTable;
    // sync running deploy model tasks
    private boolean syncRunningDeployModelTasks;

    // deployToAll flag for models, when deploy/undeploy a model, this will passed to each node to update cache value to make sure
    // profile API has consistent data with model index.
    private Map<String, Boolean> deployToAllNodes;

    @Builder
    public MLSyncUpInput(
        boolean getDeployedModels,
        Map<String, String[]> addedWorkerNodes,
        Map<String, String[]> removedWorkerNodes,
        Map<String, Set<String>> modelRoutingTable,
        Map<String, Set<String>> runningDeployModelTasks,
        Map<String, Boolean> deployToAllNodes,
        boolean clearRoutingTable,
        boolean syncRunningDeployModelTasks
    ) {
        this.getDeployedModels = getDeployedModels;
        this.addedWorkerNodes = addedWorkerNodes;
        this.removedWorkerNodes = removedWorkerNodes;
        this.modelRoutingTable = modelRoutingTable;
        this.runningDeployModelTasks = runningDeployModelTasks;
        this.deployToAllNodes = deployToAllNodes;
        this.clearRoutingTable = clearRoutingTable;
        this.syncRunningDeployModelTasks = syncRunningDeployModelTasks;
    }

    public MLSyncUpInput() {}

    public MLSyncUpInput(StreamInput in) throws IOException {
        this.getDeployedModels = in.readBoolean();
        if (in.readBoolean()) {
            this.addedWorkerNodes = in.readMap(StreamInput::readString, StreamInput::readStringArray);
        }
        if (in.readBoolean()) {
            this.removedWorkerNodes = in.readMap(StreamInput::readString, StreamInput::readStringArray);
        }
        if (in.readBoolean()) {
            modelRoutingTable = in.readMap(StreamInput::readString, s -> s.readSet(StreamInput::readString));
        }
        if (in.readBoolean()) {
            runningDeployModelTasks = in.readMap(StreamInput::readString, s -> s.readSet(StreamInput::readString));
        }
        if (in.readBoolean()) {
            deployToAllNodes = in.readMap(StreamInput::readString, StreamInput::readOptionalBoolean);
        }
        this.clearRoutingTable = in.readBoolean();
        this.syncRunningDeployModelTasks = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(getDeployedModels);
        if (addedWorkerNodes != null && addedWorkerNodes.size() > 0) {
            out.writeBoolean(true);
            out.writeMap(addedWorkerNodes, StreamOutput::writeString, StreamOutput::writeStringArray);
        } else {
            out.writeBoolean(false);
        }
        if (removedWorkerNodes != null && removedWorkerNodes.size() > 0) {
            out.writeBoolean(true);
            out.writeMap(removedWorkerNodes, StreamOutput::writeString, StreamOutput::writeStringArray);
        } else {
            out.writeBoolean(false);
        }
        if (modelRoutingTable != null && modelRoutingTable.size() > 0) {
            out.writeBoolean(true);
            out.writeMap(modelRoutingTable, StreamOutput::writeString, StreamOutput::writeStringCollection);
        } else {
            out.writeBoolean(false);
        }
        if (runningDeployModelTasks != null && runningDeployModelTasks.size() > 0) {
            out.writeBoolean(true);
            out.writeMap(runningDeployModelTasks, StreamOutput::writeString, StreamOutput::writeStringCollection);
        } else {
            out.writeBoolean(false);
        }
        if (deployToAllNodes != null && deployToAllNodes.size() > 0) {
            out.writeBoolean(true);
            out.writeMap(deployToAllNodes, StreamOutput::writeString, StreamOutput::writeOptionalBoolean);
        } else {
            out.writeBoolean(false);
        }
        out.writeBoolean(clearRoutingTable);
        out.writeBoolean(syncRunningDeployModelTasks);
    }

}
